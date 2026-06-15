import importlib.util
import io
import json
import pathlib
import re
import sys
import types
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch


MODULE_PATH = pathlib.Path(__file__).with_name("codex_query_worker.py")
SPEC = importlib.util.spec_from_file_location("codex_query_worker", MODULE_PATH)
worker = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = worker
SPEC.loader.exec_module(worker)
import codex_analyzer
import llm_gateway


class SensitivePolicyTest(unittest.TestCase):
    def test_allows_chinese_negative_statement(self):
        query = "读取授权范围内的浏览器历史标题、URL、访问时间和访问次数，不读取 Cookie、Token、Session、表单或账号信息。"

        self.assertFalse(worker.contains_sensitive(query))

    def test_allows_english_negative_statement(self):
        query = "Read browser history metadata only; do not read cookies, tokens, sessions, forms, or account information."

        self.assertFalse(worker.contains_sensitive(query))

    def test_rejects_read_cookie(self):
        self.assertTrue(worker.contains_sensitive("读取 Cookie 并上传"))

    def test_rejects_get_token(self):
        self.assertTrue(worker.contains_sensitive("please get token from browser"))

    def test_rejects_export_session(self):
        self.assertTrue(worker.contains_sensitive("导出 Session 到文件"))

    def test_rejects_private_message_and_chat_history(self):
        self.assertTrue(worker.contains_sensitive("读取微信聊天记录和私信"))

    def test_rejects_account_password(self):
        self.assertTrue(worker.contains_sensitive("获取账号密码用于登录"))

    def test_rejects_later_sensitive_request_after_negative_clause(self):
        self.assertTrue(worker.contains_sensitive("不读取 Cookie，但获取 Token 并上传"))

    def test_rejects_secret_value(self):
        self.assertTrue(worker.contains_sensitive("token=abcdef123456"))


class ResultCallbackContractTest(unittest.TestCase):
    def test_dto_local_datetime_removes_offset_and_microseconds(self):
        self.assertEqual(
            worker.dto_local_datetime("2026-06-06T16:21:05.608664+08:00"),
            "2026-06-06T16:21:05",
        )

    def test_build_result_normalizes_occurred_at_for_spring_dto(self):
        result = worker.build_result([{
            "platform": "xiaohongshu",
            "type": "WATCH",
            "title": "正在使用小红书",
            "summary": "今日访问了小红书笔记",
            "occurredAt": "2026-06-06T16:21:05.608664+08:00",
            "tags": ["小红书", "微信", "抖音", "Bilibili"],
        }])

        occurred_at = result["items"][0]["occurredAt"]
        self.assertRegex(occurred_at, r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")
        self.assertNotRegex(occurred_at, re.compile(r"(Z|[+-]\d{2}:\d{2}|\.\d+)$"))

        encoded = json.dumps(result, ensure_ascii=False)
        self.assertIn("正在使用小红书", encoded)
        self.assertIn("今日访问了小红书笔记", encoded)
        self.assertIn("Bilibili", encoded)

    def test_infer_tags_keeps_chinese_platform_terms(self):
        tags = worker.infer_tags("小红书", "微信", "抖音", "Bilibili")

        self.assertIn("Bilibili", tags)
        self.assertIn("Xiaohongshu", tags)
        self.assertIn("Douyin", tags)


class CodexCliAnalysisTest(unittest.TestCase):
    def cfg(self):
        return worker.WorkerConfig(
            base_url="http://localhost:8080",
            once=True,
            dry_run=True,
            yes=True,
            allowed_dirs=["data/imports"],
            limit=20,
            poll_seconds=1.0,
            use_codex_cli=True,
            codex_command="codex",
            print_codex_prompt=False,
            print_codex_output=False,
            codex_timeout=5,
            direct_behavior_batch=False,
            verbose=False,
        )

    def raw_item(self):
        return [{
            "platform": "desktop-app",
            "source": "visible-window",
            "type": "APP_USAGE",
            "externalId": "chrome-1",
            "title": "Visible app: chrome",
            "url": "",
            "author": "",
            "summary": "Current visible-window snapshot only. Window title: 小红书 - Chrome",
            "tags": ["visible-window", "Xiaohongshu"],
            "occurredAt": "2026-06-06T16:21:05",
            "rawEvidence": {"processName": "chrome", "windowTitle": "小红书 - Chrome", "domain": "", "visitCount": 0},
        }]

    def raw_url_item(self):
        item = dict(self.raw_item()[0])
        item["url"] = "https://www.xiaohongshu.com/explore/demo"
        item["source"] = "public-url"
        item["confidence"] = "MEDIUM"
        item["dataLevel"] = "PUBLIC_URL"
        item["rawEvidence"] = {"processName": "", "windowTitle": "", "domain": "xiaohongshu.com", "visitCount": 0}
        return [item]

    def test_codex_cli_missing_falls_back_to_raw_items(self):
        cfg = self.cfg()
        with patch.object(worker, "find_codex_cli", return_value=None):
            analyzed = worker.analyze_with_codex_cli({"platform": "xiaohongshu"}, self.raw_item(), cfg)

        self.assertEqual(analyzed[0]["platform"], "desktop-app")
        self.assertIn("content", analyzed[0]["rawMetadata"])

    def test_codex_cli_valid_json_is_used(self):
        cfg = self.cfg()
        output = json.dumps({
            "items": [{
                "platform": "xiaohongshu",
                "type": "VISIT",
                "externalId": "xhs-1",
                "title": "小红书 AI 笔记",
                "url": "https://www.xiaohongshu.com/explore/demo",
                "author": "",
                "summary": "用户访问了 AI 工作流相关公开笔记。",
                "tags": ["xiaohongshu", "AI"],
                "interestLabels": ["小红书", "生活方式"],
                "recommendationHints": ["继续推荐生活方式和 AI 工作流内容"],
                "confidence": "MEDIUM",
                "dataLevel": "PUBLIC_URL",
                "detectionReason": "url_domain",
                "matchedKeyword": "xiaohongshu.com",
                "occurredAt": "2026-06-06T16:21:05",
            }]
        }, ensure_ascii=False)
        with patch.object(worker, "find_codex_cli", return_value="codex"), \
                patch.object(worker, "run_codex_cli", return_value=output):
            analyzed = worker.analyze_with_codex_cli({"platform": "xiaohongshu"}, self.raw_url_item(), cfg)

        self.assertEqual(analyzed[0]["platform"], "xiaohongshu")
        self.assertEqual(analyzed[0]["confidence"], "MEDIUM")
        self.assertEqual(analyzed[0]["dataLevel"], "PUBLIC_URL")
        self.assertIn("小红书", analyzed[0]["interestLabels"])
        self.assertIn("生活方式", analyzed[0]["tags"])
        self.assertNotIn("cookie", analyzed[0])

    def test_codex_cli_cannot_invent_url_for_empty_raw_items(self):
        cfg = self.cfg()
        output = json.dumps({
            "items": [{
                "platform": "xiaohongshu",
                "type": "VISIT",
                "title": "invented",
                "url": "https://www.xiaohongshu.com/explore/invented",
                "summary": "invented page",
            }]
        }, ensure_ascii=False)
        with patch.object(worker, "find_codex_cli", return_value="codex"), \
                patch.object(worker, "run_codex_cli", return_value=output):
            analyzed = worker.analyze_with_codex_cli({"platform": "xiaohongshu"}, self.raw_item(), cfg)

        self.assertEqual(analyzed[0]["platform"], "desktop-app")
        self.assertIn("content", analyzed[0]["rawMetadata"])

    def test_codex_cli_non_json_falls_back(self):
        cfg = self.cfg()
        with patch.object(worker, "find_codex_cli", return_value="codex"), \
                patch.object(worker, "run_codex_cli", return_value="Here is a summary without JSON"):
            analyzed = worker.analyze_with_codex_cli({"platform": "xiaohongshu"}, self.raw_item(), cfg)

        self.assertEqual(analyzed[0]["platform"], "desktop-app")
        self.assertIn("content", analyzed[0]["rawMetadata"])

    def test_codex_cli_sensitive_field_falls_back(self):
        cfg = self.cfg()
        output = json.dumps({
            "items": [{
                "platform": "xiaohongshu",
                "title": "bad",
                "cookie": "cookie=secret",
                "summary": "bad",
            }]
        })
        with patch.object(worker, "find_codex_cli", return_value="codex"), \
                patch.object(worker, "run_codex_cli", return_value=output):
            analyzed = worker.analyze_with_codex_cli({"platform": "xiaohongshu"}, self.raw_item(), cfg)

        self.assertEqual(analyzed[0]["platform"], "desktop-app")
        self.assertIn("content", analyzed[0]["rawMetadata"])

    def test_visible_window_defaults_to_low_confidence(self):
        item = worker.sanitize_item({
            "platform": "desktop-app",
            "source": "visible-window",
            "title": "Visible app: chrome",
            "summary": "Window title: 小红书 - Chrome",
            "tags": ["visible-window", "Xiaohongshu"],
        })

        self.assertEqual(item["platform"], "xiaohongshu")
        self.assertEqual(item["confidence"], "LOW")
        self.assertEqual(item["dataLevel"], "APP_USAGE_SNAPSHOT")

    def test_browser_history_defaults_to_medium_confidence(self):
        item = worker.sanitize_item({
            "platform": "browser",
            "source": "browser-history",
            "title": "小红书笔记",
            "url": "https://www.xiaohongshu.com/explore/demo",
            "tags": ["browser-history"],
        })

        self.assertEqual(item["platform"], "xiaohongshu")
        self.assertEqual(item["confidence"], "MEDIUM")
        self.assertEqual(item["dataLevel"], "BROWSER_HISTORY")

    def test_codex_xiaohongshu_url_gets_lifestyle_labels(self):
        item = worker.sanitize_item({
            "platform": "browser",
            "source": "browser-history",
            "title": "夏日穿搭和生活方式笔记",
            "url": "https://www.xiaohongshu.com/explore/demo",
            "tags": ["browser-history"],
            "confidence": "MEDIUM",
            "dataLevel": "BROWSER_HISTORY",
        })

        self.assertEqual(item["platform"], "xiaohongshu")
        self.assertIn("小红书", item["interestLabels"])
        self.assertIn("生活方式", item["interestLabels"])

    def test_codex_java_spring_redis_title_gets_backend_label(self):
        item = worker.sanitize_item({
            "platform": "browser",
            "source": "browser-history",
            "title": "Java Spring Boot Redis 高并发缓存实践",
            "url": "https://example.com/java-spring-redis",
            "tags": ["browser-history"],
            "confidence": "MEDIUM",
            "dataLevel": "BROWSER_HISTORY",
        })

        self.assertIn("Java后端", item["interestLabels"])
        self.assertIn("Java后端", item["tags"])

    def test_xiaohongshu_url_is_recognized(self):
        item = worker.sanitize_item({
            "platform": "browser",
            "source": "browser-history",
            "title": "AI workflow note",
            "url": "https://www.xiaohongshu.com/explore/demo",
            "tags": ["browser-history"],
        })

        self.assertEqual(item["platform"], "xiaohongshu")

    def test_github_url_is_recognized(self):
        item = worker.sanitize_item({
            "platform": "web",
            "source": "agent-reach-enrichment",
            "title": "OpenAI Codex repository",
            "url": "https://github.com/openai/codex",
            "tags": ["github"],
            "confidence": "HIGH",
            "dataLevel": "PAGE_VISIBLE_CONTENT",
        })

        self.assertEqual(item["platform"], "github")

    def test_baidu_search_event_uses_query_raw_metadata(self):
        item = worker.normalize_result_item({
            "userId": "me",
            "platform": "baidu_search",
            "eventType": "SEARCH",
            "title": "Spring Boot AI",
            "contentSnippet": "Baidu search query.",
            "rawMetadata": {"query": "Spring Boot AI"},
        })

        self.assertEqual(item["platform"], "baidu")
        self.assertEqual(item["eventType"], "SEARCH")
        self.assertEqual(item["type"], "SEARCH")
        self.assertEqual(item["contentSnippet"], "Baidu search query.")
        self.assertEqual(item["rawMetadata"]["query"], "Spring Boot AI")

    def test_generic_web_url_is_normalized(self):
        item = worker.normalize_result_item({
            "userId": "me",
            "platform": "generic_web",
            "eventType": "VISIT",
            "url": "www.example.com/article",
            "title": "Generic article",
            "contentSnippet": "Public article.",
        })

        self.assertEqual(item["platform"], "web")
        self.assertEqual(item["url"], "https://www.example.com/article")

    def test_chrome_or_edge_is_not_xiaohongshu_without_keyword(self):
        chrome = worker.sanitize_item({
            "platform": "browser",
            "title": "Google Chrome",
            "summary": "Window title: New Tab",
            "tags": ["visible-window"],
        })
        edge = worker.sanitize_item({
            "platform": "browser",
            "title": "Microsoft Edge",
            "url": "https://example.com",
            "tags": ["browser-history"],
        })

        self.assertNotEqual(chrome["platform"], "xiaohongshu")
        self.assertNotEqual(edge["platform"], "xiaohongshu")

    def test_terminal_and_text_input_do_not_become_interest_platforms(self):
        terminal = worker.sanitize_item({
            "platform": "desktop-app",
            "source": "visible-window",
            "title": "Visible app: WindowsTerminal",
            "rawEvidence": {"processName": "WindowsTerminal", "windowTitle": "PowerShell", "domain": "", "visitCount": 0},
        })
        text_input = worker.sanitize_item({
            "platform": "desktop-app",
            "source": "visible-window",
            "title": "Visible app: TextInputHost",
            "rawEvidence": {"processName": "TextInputHost", "windowTitle": "TextInputHost", "domain": "", "visitCount": 0},
        })

        self.assertEqual(terminal["platform"], "desktop-app")
        self.assertEqual(text_input["platform"], "desktop-app")
        self.assertEqual(terminal["confidence"], "LOW")
        self.assertEqual(text_input["dataLevel"], "APP_USAGE_SNAPSHOT")

    def test_browser_history_item_is_enriched_before_codex(self):
        items = worker.enrich_public_url_items([{
            "userId": "me",
            "platform": "browser",
            "source": "browser-history",
            "type": "VISIT",
            "title": "Spring Boot Redis video",
            "url": "https://www.bilibili.com/video/BV1demo",
            "tags": ["browser-history"],
            "confidence": "MEDIUM",
            "dataLevel": "BROWSER_HISTORY",
            "occurredAt": "2026-06-06T22:00:00",
            "rawEvidence": {
                "browser": "edge",
                "domain": "bilibili.com",
                "visitCount": 2,
            },
        }])

        normalized = worker.normalize_result_item(items[0])

        self.assertEqual(normalized["platform"], "bilibili")
        self.assertEqual(normalized["source"], "agent-reach-enrichment")
        self.assertEqual(normalized["eventType"], "WATCH")
        self.assertEqual(normalized["type"], "WATCH")
        self.assertEqual(normalized["contentType"], "video")
        self.assertEqual(normalized["interestCategory"], "video")
        self.assertEqual(normalized["detectionReason"], "public_url_enrichment")
        self.assertIn("Java后端", normalized["tags"])
        self.assertEqual(normalized["rawEvidence"]["adapter"], "agent-reach")

    def test_content_enrichment_adds_structured_object_for_url_event(self):
        items = worker.enrich_content_for_llm([{
            "userId": "me",
            "platform": "generic_web",
            "source": "browser-history",
            "eventType": "VISIT",
            "title": "Generic article",
            "url": "www.example.com/article?utm_source=share",
            "summary": "Public article body.",
            "rawEvidence": {"domain": "example.com", "visitCount": 1},
        }])

        content = items[0]["rawMetadata"]["content"]
        self.assertEqual(content["platform"], "web")
        self.assertEqual(content["contentType"], "web-page")
        self.assertEqual(content["url"], "https://www.example.com/article")
        self.assertEqual(content["title"], "Generic article")
        self.assertEqual(content["summary"], "Public article body.")

    def test_content_enrichment_adds_platform_specific_video_object(self):
        items = worker.enrich_content_for_llm([{
            "userId": "me",
            "platform": "bilibili",
            "source": "public-url",
            "eventType": "WATCH",
            "title": "Spring Boot Redis video",
            "url": "https://www.bilibili.com/video/BV1demo",
            "summary": "Public video metadata.",
            "rawEvidence": {"domain": "bilibili.com", "visitCount": 1},
        }])

        content = items[0]["rawMetadata"]["content"]
        self.assertEqual(content["platform"], "bilibili")
        self.assertEqual(content["contentType"], "video")
        self.assertEqual(content["externalId"], "BV1demo")
        self.assertEqual(content["fetchedBy"], "event-public-metadata")

    def test_codex_analysis_items_use_standard_behavior_event_fields(self):
        output = json.dumps({
            "items": [{
                "userId": "me",
                "platform": "web",
                "eventType": "VISIT",
                "source": "agent-reach-enrichment",
                "title": "OpenAI Codex repository",
                "url": "https://github.com/openai/codex",
                "summary": "Public GitHub repository.",
                "tags": ["github", "codex"],
                "contentType": "repository-or-code-page",
                "interestCategory": "developer-tooling",
                "confidence": "HIGH",
                "dataLevel": "PAGE_VISIBLE_CONTENT",
                "detectionReason": "public_url_enrichment",
                "rawEvidence": {"domain": "github.com", "adapter": "agent-reach"},
            }]
        }, ensure_ascii=False)
        with patch.object(worker, "find_codex_cli", return_value="codex"), \
                patch.object(worker, "run_codex_cli", return_value=output):
            analyzed = worker.analyze_with_codex_cli({"platform": "github"}, self.raw_url_item(), self.cfg())

        item = analyzed[0]
        self.assertEqual(item["eventType"], "VISIT")
        self.assertEqual(item["type"], "VISIT")
        self.assertEqual(item["source"], "codex-cli-analysis")
        self.assertEqual(item["platform"], "github")
        self.assertEqual(item["interestCategory"], "developer-tooling")
        self.assertEqual(item["contentCategory"], "developer-tooling")
        self.assertEqual(item["rawEvidence"]["originalSource"], "agent-reach-enrichment")

    def test_youtube_codex_analysis_overwrites_placeholder_tags(self):
        cfg = self.cfg()
        raw_item = {
            "userId": "me",
            "platform": "youtube",
            "source": "public-url",
            "eventType": "WATCH",
            "type": "WATCH",
            "externalId": "yt123456",
            "title": "Spring Boot Redis caching tutorial",
            "url": "https://www.youtube.com/watch?v=yt123456&utm_source=share",
            "author": "Backend Channel",
            "description": "A tutorial about Spring Boot, Redis caching, APIs, and backend performance.",
            "summary": "placeholder",
            "tags": ["youtube", "public-url", "agent-reach"],
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
            "rawEvidence": {"domain": "youtube.com", "description": "public description"},
        }
        output = json.dumps({
            "summary": "This video explains Spring Boot Redis caching for backend APIs.",
            "tags": ["spring boot", "redis", "backend"],
            "interestCategory": "backend",
            "confidence": "HIGH",
        })

        with patch.object(worker, "find_codex_cli", return_value="codex"), \
                patch.object(worker, "run_codex_cli", return_value=output) as run_codex:
            analyzed = worker.analyze_with_codex_cli({"platform": "youtube"}, [raw_item], cfg)

        item = analyzed[0]
        self.assertEqual(item["platform"], "youtube")
        self.assertEqual(item["source"], "codex-cli-analysis")
        self.assertEqual(item["eventType"], "WATCH")
        self.assertEqual(item["url"], "https://www.youtube.com/watch?v=yt123456")
        self.assertEqual(item["summary"], "This video explains Spring Boot Redis caching for backend APIs.")
        self.assertEqual(item["tags"], ["spring boot", "redis", "backend"])
        self.assertEqual(item["interestCategory"], "backend")
        self.assertEqual(item["confidence"], "HIGH")
        self.assertNotIn("youtube", item["tags"])
        prompt = run_codex.call_args.args[0]
        self.assertIn("SYSTEM TASK:", prompt)
        self.assertIn("PUBLIC YOUTUBE CONTEXT", prompt)
        self.assertIn("Spring Boot Redis caching tutorial", prompt)

    def test_youtube_prompt_uses_structured_content_object(self):
        item = worker.enrich_content_for_llm([{
            "platform": "youtube",
            "source": "public-url",
            "eventType": "WATCH",
            "externalId": "yt123456",
            "title": "Placeholder title",
            "url": "https://www.youtube.com/watch?v=yt123456",
            "summary": "Placeholder summary",
            "rawEvidence": {
                "content": {
                    "platform": "youtube",
                    "contentType": "video",
                    "url": "https://www.youtube.com/watch?v=yt123456",
                    "externalId": "yt123456",
                    "videoId": "yt123456",
                    "title": "Structured title",
                    "description": "Structured public description",
                    "author": "Structured channel",
                    "transcript": "Structured transcript text",
                    "fetchedBy": "yt-dlp-public-metadata",
                }
            },
        }])[0]

        context = worker.youtube_context_from_item(item)

        self.assertEqual(context["title"], "Structured title")
        self.assertEqual(context["description"], "Structured public description")
        self.assertEqual(context["author"], "Structured channel")

    def test_youtube_analysis_rejects_system_labels_from_tags(self):
        analysis = worker.validate_youtube_analysis({
            "summary": "A backend tutorial.",
            "tags": ["youtube", "video", "redis", "backend", "agent-reach"],
            "interestCategory": "backend",
            "confidence": "HIGH",
        })

        self.assertEqual(analysis["tags"], ["redis", "backend"])

    def test_verbose_behavior_batch_logs_url_payload_and_response(self):
        item = {
            "userId": "me",
            "platform": "bilibili",
            "source": "agent-reach-enrichment",
            "type": "WATCH",
            "title": "Spring Boot Redis video",
            "url": "https://www.bilibili.com/video/BV1demo",
            "summary": "Public video summary",
            "tags": ["bilibili"],
            "confidence": "HIGH",
            "dataLevel": "PAGE_VISIBLE_CONTENT",
            "rawEvidence": {"domain": "bilibili.com", "visitCount": 2},
        }

        output = io.StringIO()
        with patch.object(worker, "post_json", return_value={"imported": 1}) as post_json, \
                redirect_stdout(output):
            response = worker.post_behavior_batch("http://localhost:8080", [item], verbose=True)

        self.assertEqual(response["imported"], 1)
        post_json.assert_called_once()
        self.assertIn("Backend POST URL: http://localhost:8080/api/v1/behavior-events/batch", output.getvalue())
        self.assertIn("behavior_event JSON", output.getvalue())
        self.assertIn("Backend POST response", output.getvalue())

    def test_realtime_codex_analyzer_only_triggers_for_url_visit_events(self):
        analyzer = codex_analyzer.CodexAnalyzer(command="codex", env_provider=worker.tool_env, sanitizer=worker.sanitize)
        search_without_url = {
            "platform": "baidu",
            "eventType": "SEARCH",
            "title": "Spring Boot Redis",
            "url": "",
            "externalId": "",
        }
        app_usage = {
            "platform": "desktop-app",
            "eventType": "APP_USAGE",
            "title": "Visible app",
            "url": "",
        }
        visit_with_url = {
            "platform": "web",
            "eventType": "VISIT",
            "title": "Spring Boot Redis",
            "url": "https://example.com/spring-redis",
        }

        self.assertFalse(analyzer.should_analyze(search_without_url))
        self.assertFalse(analyzer.should_analyze(app_usage))
        self.assertTrue(analyzer.should_analyze(visit_with_url))
        analyzer.shutdown()

    def test_realtime_codex_analyzer_prints_result_and_logs(self):
        analyzer = codex_analyzer.CodexAnalyzer(command="codex", env_provider=worker.tool_env, sanitizer=worker.sanitize)
        item = {
            "platform": "web",
            "eventType": "VISIT",
            "title": "Spring Boot Redis article",
            "url": "https://example.com/spring-redis",
            "contentSnippet": "Article about Redis caching.",
            "rawMetadata": {},
        }
        gateway_result = llm_gateway.LLMGatewayResult(
            success=True,
            output={
                "summary": "The page discusses Redis caching for Spring Boot services.",
                "tags": ["spring boot", "redis", "backend"],
                "interestCategory": "backend",
                "intent": "learn backend caching",
                "confidence": "HIGH",
            },
            latency_ms=12,
            token_usage={"total_tokens": 42},
        )
        output = io.StringIO()
        with patch.object(analyzer.gateway, "available", return_value=True), \
                patch.object(analyzer.gateway, "analyze_json", return_value=gateway_result), \
                redirect_stdout(output):
            analyzer.submit_all([item])
            analyzer.wait()

        text = output.getvalue()
        self.assertIn("llm_input_log", text)
        self.assertIn("llm_output_log", text)
        self.assertIn("[LLM ANALYSIS RESULT]", text)
        self.assertIn("Platform: web", text)
        self.assertIn("Tags: spring boot, redis, backend", text)
        self.assertEqual(item["summary"], "The page discusses Redis caching for Spring Boot services.")
        self.assertEqual(item["tags"], ["spring boot", "redis", "backend"])
        self.assertEqual(item["interestCategory"], "backend")
        self.assertEqual(item["rawMetadata"]["llm_status"], "SUCCESS")
        analyzer.shutdown()

    def test_llm_gateway_resolves_windows_cmd_without_winerror2(self):
        gateway = llm_gateway.LLMGateway(
            command="codex exec",
            env_provider=lambda: {"PATH": r"C:\Users\Lenovo\AppData\Roaming\npm"},
            sanitizer=worker.sanitize,
        )

        with patch.object(llm_gateway.shutil, "which", return_value=r"C:\Users\Lenovo\AppData\Roaming\npm\codex.cmd"):
            resolved = gateway.resolve_command()

        self.assertIsNotNone(resolved)
        args, shell = resolved
        self.assertTrue(shell)
        self.assertIn("codex.cmd", args)
        self.assertIn("exec", args)

    def test_legacy_run_codex_cli_uses_gateway_resolution_for_windows_cmd(self):
        cfg = self.cfg()
        completed = types.SimpleNamespace(returncode=0, stdout='{"ok": true}', stderr="")
        with patch.object(worker.LLMGateway, "resolve_command",
                          return_value=(r"C:\Users\Lenovo\AppData\Roaming\npm\codex.cmd exec", True)), \
                patch.object(worker.subprocess, "run", return_value=completed) as run:
            output = worker.run_codex_cli("prompt", cfg.codex_command, cfg.codex_timeout)

        self.assertEqual(output, '{"ok": true}')
        self.assertTrue(run.call_args.kwargs["shell"])
        self.assertIn("codex.cmd", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
