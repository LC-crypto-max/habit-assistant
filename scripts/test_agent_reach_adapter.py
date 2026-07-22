import importlib.util
import pathlib
import subprocess
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).with_name("agent_reach_adapter.py")
SPEC = importlib.util.spec_from_file_location("agent_reach_adapter", MODULE_PATH)
adapter = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = adapter
SPEC.loader.exec_module(adapter)


class AgentReachAdapterTest(unittest.TestCase):
    def test_windows_bili_gbk_output_is_decoded(self):
        self.assertEqual(adapter.decode_process_bytes("中文标题".encode("gbk")), "中文标题")

    def test_live_bilibili_result_uses_public_tool_output(self):
        commands = []

        def run(args, _timeout):
            commands.append(args)
            return subprocess.CompletedProcess(
                args, 0,
                stdout='{"title":"Spring Boot Redis cache tutorial","description":"Java backend caching guide","author":"Backend UP"}',
                stderr="")

        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live"),
            tool_resolver=lambda name: "C:/tools/bili.cmd" if name == "bili" else None,
            process_runner=run,
        )
        enriched = bridge.enrich({
            "userId": "me",
            "platform": "browser",
            "source": "browser-history",
            "title": "Old browser title",
            "url": "https://www.bilibili.com/video/BV1demo?utm_source=share",
            "confidence": "MEDIUM",
            "dataLevel": "BROWSER_HISTORY",
            "rawEvidence": {"browser": "edge", "visitCount": 3},
        })

        self.assertEqual(commands[0], ["C:/tools/bili.cmd", "video", "BV1demo"])
        self.assertEqual(enriched["source"], "agent-reach-enrichment")
        self.assertEqual(enriched["title"], "Spring Boot Redis cache tutorial")
        self.assertEqual(enriched["author"], "Backend UP")
        self.assertEqual(enriched["confidence"], "HIGH")
        self.assertEqual(enriched["dataLevel"], "PAGE_VISIBLE_CONTENT")
        self.assertEqual(enriched["rawMetadata"]["adapterMode"], "live")
        self.assertEqual(enriched["rawMetadata"]["agentReachRoute"], "bilibili-video")
        self.assertNotIn("utm_source", enriched["url"])

    def test_unavailable_tool_preserves_browser_history_confidence(self):
        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="auto"),
            tool_resolver=lambda _name: None,
        )
        enriched = bridge.enrich({
            "platform": "browser",
            "source": "browser-history",
            "title": "Bilibili visit",
            "url": "https://www.bilibili.com/video/BV1demo",
            "confidence": "MEDIUM",
            "dataLevel": "BROWSER_HISTORY",
        })

        self.assertEqual(enriched["source"], "browser-history")
        self.assertEqual(enriched["confidence"], "MEDIUM")
        self.assertEqual(enriched["dataLevel"], "BROWSER_HISTORY")
        self.assertEqual(enriched["rawMetadata"]["adapterMode"], "fallback")
        self.assertEqual(enriched["rawMetadata"]["agentReachStatus"], "UNAVAILABLE")

    def test_xiaohongshu_requires_explicit_browser_session_permission(self):
        calls = []
        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live", allow_authenticated_browser=False),
            tool_resolver=lambda name: "C:/tools/opencli.cmd" if name == "opencli" else None,
            process_runner=lambda args, timeout: calls.append((args, timeout)),
        )
        enriched = bridge.enrich({
            "platform": "xiaohongshu",
            "source": "public-url",
            "url": "https://www.xiaohongshu.com/explore/note123",
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
        })

        self.assertEqual(calls, [])
        self.assertEqual(enriched["rawMetadata"]["adapterMode"], "fallback")
        self.assertIn("not explicitly enabled", enriched["rawMetadata"]["agentReachError"])

    def test_xiaohongshu_opencli_gets_full_url_but_token_is_not_persisted(self):
        calls = []

        def resolve(name):
            return "C:/tools/opencli.cmd" if name == "opencli" else None

        def run(args, _timeout):
            calls.append(args)
            if "browser" in args:
                return subprocess.CompletedProcess(args, 2, stdout="", stderr="no visible tab")
            return subprocess.CompletedProcess(
                args, 0,
                stdout="title: AI 工作流笔记\ndescription: 分享 Spring AI 自动化经验\nauthor: demo-user",
                stderr="")

        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live", allow_authenticated_browser=True),
            tool_resolver=resolve,
            process_runner=run,
        )
        original_url = "https://www.xiaohongshu.com/explore/note123?xsec_token=secret-value&utm_source=share"
        enriched = bridge.enrich({
            "platform": "xiaohongshu",
            "source": "public-url",
            "url": original_url,
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
        })

        self.assertTrue(any(original_url in call for call in calls))
        self.assertNotIn("xsec_token", enriched["url"])
        self.assertNotIn("secret-value", str(enriched))
        self.assertEqual(enriched["rawMetadata"]["agentReachBackend"], "OpenCLI")
        self.assertEqual(enriched["title"], "AI 工作流笔记")

    def test_xiaohongshu_unsigned_url_does_not_launch_search_after_browser_failure(self):
        calls = []

        def resolve(name):
            return "C:/tools/opencli.cmd" if name == "opencli" else None

        def run(args, _timeout):
            calls.append(args)
            if "browser" in args:
                return subprocess.CompletedProcess(
                    args, 2, stdout="", stderr="no matching visible browser tab")
            return subprocess.CompletedProcess(args, 0, stdout="unexpected", stderr="")

        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live", allow_authenticated_browser=True),
            tool_resolver=resolve,
            process_runner=run,
        )
        enriched = bridge.enrich({
            "platform": "xiaohongshu",
            "source": "public-url",
            "title": "公积金的9个档次，看到最后我酸了 | 小红书",
            "url": "https://www.xiaohongshu.com/discovery/item/note123?xhsshare=pc_web",
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
        })

        self.assertFalse(any("search" in call for call in calls))
        self.assertEqual(enriched["rawMetadata"]["agentReachStatus"], "FAILED")
        self.assertEqual(enriched["rawMetadata"]["agentReachRoute"], "xiaohongshu-visible-tab-bind")
        self.assertEqual(enriched["confidence"], "MEDIUM")
        self.assertEqual(enriched["title"], "公积金的9个档次，看到最后我酸了 | 小红书")

    def test_xiaohongshu_reads_matching_visible_tab_before_search(self):
        calls = []

        def resolve(name):
            return "C:/tools/opencli.cmd" if name == "opencli" else None

        def run(args, _timeout):
            calls.append(args)
            command = " ".join(args)
            if command.endswith(" bind"):
                return subprocess.CompletedProcess(args, 0, stdout="bound", stderr="")
            if command.endswith(" state"):
                return subprocess.CompletedProcess(
                    args, 0,
                    stdout="url: https://www.xiaohongshu.com/discovery/item/note123?xsec_token=secret",
                    stderr="")
            if " eval " in command:
                return subprocess.CompletedProcess(
                    args, 0,
                    stdout='{"title":"公积金的9个档次","author":"弘毅咨询服务",'
                           '"summary":"公开笔记正文内容",'
                           '"url":"https://www.xiaohongshu.com/explore/note123"}',
                    stderr="")
            return subprocess.CompletedProcess(args, 0, stdout="unbound", stderr="")

        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live", allow_authenticated_browser=True),
            tool_resolver=resolve,
            process_runner=run,
        )
        enriched = bridge.enrich({
            "platform": "xiaohongshu",
            "source": "public-url",
            "title": "公积金的9个档次",
            "url": "https://www.xiaohongshu.com/discovery/item/note123",
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
        })

        self.assertEqual(enriched["rawMetadata"]["agentReachRoute"], "xiaohongshu-visible-tab")
        self.assertEqual(enriched["rawMetadata"]["agentReachStatus"], "SUCCESS")
        self.assertIn("公开笔记正文内容", enriched["summary"])
        self.assertFalse(any(" search " in " ".join(call) for call in calls))
        self.assertNotIn("secret", str(enriched))
        self.assertNotIn("xsec_token", str(enriched))

    def test_unsigned_matching_tab_does_not_launch_note_or_search(self):
        calls = []

        def run(args, _timeout):
            calls.append(args)
            command = " ".join(args)
            if command.endswith(" bind"):
                return subprocess.CompletedProcess(args, 0, stdout="bound", stderr="")
            if command.endswith(" state"):
                return subprocess.CompletedProcess(
                    args, 0, stdout="url: https://www.xiaohongshu.com/explore/note123", stderr="")
            if " eval " in command:
                return subprocess.CompletedProcess(
                    args, 0, stdout='{"title":"真实标题","summary":"真实正文"}', stderr="")
            return subprocess.CompletedProcess(args, 0, stdout="unbound", stderr="")

        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live", allow_authenticated_browser=True),
            tool_resolver=lambda name: "C:/tools/opencli.cmd" if name == "opencli" else None,
            process_runner=run,
        )
        enriched = bridge.enrich({
            "platform": "xiaohongshu", "source": "public-url",
            "title": "真实标题", "url": "https://www.xiaohongshu.com/discovery/item/note123",
            "confidence": "MEDIUM", "dataLevel": "PUBLIC_URL",
        })

        self.assertEqual(enriched["summary"], "真实正文")
        self.assertFalse(any("xiaohongshu" in call and "note" in call for call in calls))
        self.assertFalse(any("search" in call for call in calls))

    def test_timeout_error_does_not_persist_command_or_sensitive_url(self):
        def resolve(name):
            return "C:/tools/opencli.cmd" if name == "opencli" else None

        def run(args, timeout):
            raise subprocess.TimeoutExpired(args, timeout)

        bridge = adapter.AgentReachAdapter(
            adapter.AgentReachSettings(mode="live", timeout=5, allow_authenticated_browser=True),
            tool_resolver=resolve,
            process_runner=run,
        )
        enriched = bridge.enrich({
            "platform": "xiaohongshu",
            "source": "public-url",
            "url": "https://www.xiaohongshu.com/explore/note123?xsec_token=secret-value",
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
        })

        self.assertNotIn("secret-value", str(enriched))
        self.assertEqual(enriched["rawMetadata"]["agentReachError"], "OpenCLI timed out after 5 seconds")

    def test_private_or_credentialed_url_is_not_processed(self):
        item = {"platform": "web", "url": "http://user:pass@127.0.0.1/private", "title": "private"}
        bridge = adapter.AgentReachAdapter(tool_resolver=lambda _name: None)

        self.assertEqual(bridge.enrich(item), item)


if __name__ == "__main__":
    unittest.main()
