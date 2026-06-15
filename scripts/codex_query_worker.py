#!/usr/bin/env python3
"""
Permissioned local worker for Agent/Codex query tasks.

The Spring Boot backend creates query tasks. This script is the local,
user-controlled execution boundary:

1. claim one pending task from /api/agent/queries/claim-next
2. show the task in the terminal
3. ask for explicit user confirmation
4. collect only public or non-sensitive fields
5. post the result to /api/agent/queries/{taskId}/result

It does not read cookies, tokens, sessions, private messages, contacts,
payment records, passwords, verification codes, or protected platform internals.
"""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from agent_reach_adapter import enrich_public_url
from llm_gateway import LLMGateway
from worker_llm_gateway import WorkerLLMGateway

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_ALLOWED_DIRS = ["data/imports", "data/local-notes"]
SAFE_TEXT_LIMIT = 500
SUMMARY_TEXT_LIMIT = 420
CONTENT_TEXT_LIMIT = 1200
LOCAL_TOOL_DIRS = [
    Path.home() / ".local" / "bin",
    Path(os.environ.get("APPDATA", "")) / "Python" / "Python314" / "Scripts",
]
SENSITIVE_OBJECTS = [
    "cookie",
    "cookies",
    "token",
    "tokens",
    "session",
    "sessions",
    "authorization",
    "password",
    "passwd",
    "private message",
    "private messages",
    "dm",
    "dms",
    "chat history",
    "wechat database",
    "payment",
    "verification code",
    "captcha",
    "私信",
    "聊天记录",
    "通讯录",
    "支付记录",
    "微信数据库",
    "验证码",
    "账号密码",
    "账号信息",
]
SECRET_VALUE_PATTERNS = [
    r"(?i)\bcookie\s*[=:]\s*\S+",
    r"(?i)\btoken\s*[=:]\s*\S+",
    r"(?i)\bsession\s*[=:]\s*\S+",
    r"(?i)\bauthorization\s*:\s*bearer\s+\S+",
    r"(?i)\bpassword\s*[=:]\s*\S+",
]
DANGEROUS_ACTIONS = [
    "read",
    "get",
    "fetch",
    "extract",
    "export",
    "dump",
    "sync",
    "copy",
    "return",
    "include",
    "upload",
    "decrypt",
    "读取",
    "获取",
    "提取",
    "导出",
    "同步",
    "抓取",
    "爬取",
    "复制",
    "返回",
    "包含",
    "上传",
    "解密",
    "查看",
]
NEGATION_MARKERS = [
    "do not read",
    "do not get",
    "do not fetch",
    "do not extract",
    "do not export",
    "do not upload",
    "don't read",
    "dont read",
    "never read",
    "without reading",
    "do not",
    "don't",
    "dont",
    "does not",
    "not",
    "never",
    "without",
    "exclude",
    "禁止",
    "不要",
    "不读取",
    "不读",
    "不会读取",
    "不采集",
    "不获取",
    "不导出",
    "不上传",
    "不包含",
    "不携带",
    "不返回",
    "无需",
]
CONTRAST_MARKERS = ["但", "但是", "然而", "不过", "but", "however"]
ALLOWED_ITEM_FIELDS = {
    "userId",
    "platform",
    "source",
    "eventType",
    "type",
    "externalId",
    "title",
    "url",
    "author",
    "contentSnippet",
    "summary",
    "tags",
    "confidence",
    "dataLevel",
    "detectionReason",
    "matchedKeyword",
    "interestTags",
    "interestLabels",
    "recommendationHints",
    "contentType",
    "interestCategory",
    "contentCategory",
    "intent",
    "summaryForProfile",
    "occurredAt",
    "rawMetadata",
    "rawEvidence",
    "content",
}
ALLOWED_PLATFORMS = {
    "xiaohongshu",
    "youtube",
    "bilibili",
    "baidu",
    "baidu_search",
    "wechat",
    "browser",
    "desktop-app",
    "web",
    "generic_web",
    "github",
}
ALLOWED_TYPES = {"VISIT", "WATCH", "SEARCH", "FAVORITE", "APP_USAGE"}
ALLOWED_CONFIDENCE = {"LOW", "MEDIUM", "HIGH"}
ALLOWED_DATA_LEVELS = {"APP_USAGE_SNAPSHOT", "BROWSER_HISTORY", "PAGE_VISIBLE_CONTENT", "PUBLIC_URL", "LOCAL_NOTE"}
ALLOWED_DETECTION_REASONS = {
    "process_name",
    "window_title",
    "url_domain",
    "browser_history",
    "page_visible_content",
    "public_url_enrichment",
}
YOUTUBE_INTEREST_CATEGORIES = {"backend", "AI", "entertainment", "education", "other"}
TRACKING_QUERY_PARAMS = {
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content",
    "source",
    "from",
    "feature",
    "si",
    "fbclid",
    "gclid",
}
SENSITIVE_FIELD_NAMES = {
    "cookie",
    "cookies",
    "token",
    "tokens",
    "session",
    "sessions",
    "password",
    "passwd",
    "authorization",
    "auth",
    "secret",
    "secrets",
    "chat",
    "chats",
    "privateMessage",
    "privateMessages",
    "privatemessage",
    "privatemessages",
    "dm",
    "dms",
    "payment",
    "captcha",
}


@dataclass
class WorkerConfig:
    base_url: str
    once: bool
    dry_run: bool
    yes: bool
    allowed_dirs: list[str]
    limit: int
    poll_seconds: float
    use_codex_cli: bool
    codex_command: str
    print_codex_prompt: bool
    print_codex_output: bool
    codex_timeout: int
    direct_behavior_batch: bool
    verbose: bool


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat()


def dto_local_datetime(value: Any = None) -> str:
    if value is None or str(value).strip() == "":
        return datetime.now().replace(microsecond=0).isoformat(timespec="seconds")

    text = str(value).strip()
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone().replace(tzinfo=None)
        return parsed.replace(microsecond=0).isoformat(timespec="seconds")
    except ValueError:
        return datetime.now().replace(microsecond=0).isoformat(timespec="seconds")


def sanitize(value: Any, limit: int = SAFE_TEXT_LIMIT) -> str:
    text = "" if value is None else str(value)
    text = re.sub(r"(?i)authorization\s*:\s*bearer\s+\S+", "[REDACTED_AUTHORIZATION]", text)
    text = re.sub(r"(?i)bearer\s+\S+", "[REDACTED_AUTHORIZATION]", text)
    text = re.sub(r"(?i)(token\s*[=:]\s*)[^\s,;&]+", r"\1[REDACTED_TOKEN]", text)
    text = re.sub(r"(?i)(cookie\s*[=:]\s*)[^\n;]+", r"\1[REDACTED_COOKIE]", text)
    text = re.sub(r"(?i)(session\s*[=:]\s*)[^\s,;&]+", r"\1[REDACTED_SESSION]", text)
    text = re.sub(r"(?i)(password\s*[=:]\s*)[^\s,;&]+", r"\1[REDACTED_PASSWORD]", text)
    text = re.sub(r"(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)", r"\1****\2", text)
    text = re.sub(
        r"([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)@([A-Za-z0-9.-]+\.[A-Za-z]{2,})",
        r"\1***@\3",
        text,
    )
    return text[:limit]


def contains_sensitive(value: Any) -> bool:
    text = normalize_policy_text(value)
    if any(re.search(pattern, text) for pattern in SECRET_VALUE_PATTERNS):
        return True

    for sensitive_object in SENSITIVE_OBJECTS:
        for match in re.finditer(re.escape(sensitive_object.lower()), text):
            start, end = match.span()
            before = text[max(0, start - 40):start]
            after = text[end:min(len(text), end + 28)]
            context = before + text[start:end] + after
            if has_negation_marker(before, context):
                continue
            if has_dangerous_action(before, after, context):
                return True
    return False


def normalize_policy_text(value: Any) -> str:
    return ("" if value is None else str(value)).lower()


def has_negation_marker(before: str, context: str) -> bool:
    before_window = before[-48:]
    context_window = context[:80]
    for marker in NEGATION_MARKERS:
        index = before_window.rfind(marker)
        if index < 0 and context_window.startswith(marker):
            return True
        if index < 0:
            continue

        tail = before_window[index + len(marker):]
        if any(contrast in tail for contrast in CONTRAST_MARKERS):
            continue
        if any(action in tail for action in DANGEROUS_ACTIONS if action not in marker):
            continue
        return True
    return False


def has_dangerous_action(before: str, after: str, context: str) -> bool:
    return any(
        action in before[-28:] or action in after[:16] or action in context
        for action in DANGEROUS_ACTIONS
    )


def infer_tags(*parts: Any) -> list[str]:
    text = " ".join(str(part or "") for part in parts).lower()
    tags: list[str] = []
    rules = [
        ("Java", ["java", "spring", "redis", "mysql", "jvm", "mq"]),
        ("AI", ["ai", "chatgpt", "codex", "agent", "llm"]),
        ("Bilibili", ["bilibili", "b站", "bv"]),
        ("YouTube", ["youtube", "youtu.be"]),
        ("Xiaohongshu", ["xiaohongshu", "小红书", "xhs"]),
        ("Douyin", ["douyin", "抖音"]),
        ("WeChat", ["wechat", "微信", "weixin"]),
        ("Local app", ["terminal", "process", "window", "app"]),
        ("Local notes", ["note", "markdown", ".md", "local-notes"]),
    ]
    for tag, needles in rules:
        if any(needle in text for needle in needles):
            tags.append(tag)
    return tags or ["agent-worker"]


def tool_env() -> dict[str, str]:
    env = os.environ.copy()
    existing_path = env.get("PATH", "")
    extras = [str(path) for path in LOCAL_TOOL_DIRS if path and path.exists()]
    if extras:
        env["PATH"] = os.pathsep.join(extras + [existing_path])
    env.setdefault("PYTHONIOENCODING", "utf-8")
    return env


def find_tool(name: str) -> str | None:
    return shutil.which(name, path=tool_env().get("PATH"))


def find_codex_cli(command: str) -> str | None:
    command = (command or "codex").strip()
    if not command:
        command = "codex"
    first_token = split_command(command)[0]
    return shutil.which(first_token, path=tool_env().get("PATH"))


def split_command(command: str) -> list[str]:
    command = (command or "codex").strip()
    if not command:
        return ["codex"]
    return shlex.split(command, posix=os.name != "nt")


def verbose_log(enabled: bool, message: str) -> None:
    if enabled:
        print(message)


def verbose_json(enabled: bool, title: str, value: Any) -> None:
    if not enabled:
        return
    print(f"\n{title}")
    print("=" * 72)
    print(json.dumps(value, ensure_ascii=False, indent=2))
    print("=" * 72)


def post_json(url: str, payload: dict[str, Any], timeout: int = 15) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            response_body = response.read().decode("utf-8", errors="replace")
            return json.loads(response_body) if response_body.strip() else {}
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {error_body}") from exc


def claim_next(base_url: str) -> dict[str, Any] | None:
    try:
        task = post_json(base_url.rstrip("/") + "/api/agent/queries/claim-next", {})
    except RuntimeError as exc:
        if "NO_PENDING_AGENT_QUERY" in str(exc):
            return None
        raise
    return task or None


def complete_task(base_url: str, task_id: str, payload: dict[str, Any]) -> dict[str, Any]:
    return post_json(base_url.rstrip("/") + f"/api/agent/queries/{task_id}/result", payload)


def post_behavior_batch(base_url: str, items: list[dict[str, Any]], verbose: bool = False) -> dict[str, Any]:
    url = base_url.rstrip("/") + "/api/v1/behavior-events/batch"
    payload = {"events": [normalize_result_item(item) for item in items]}
    verbose_log(verbose, f"\nBackend POST URL: {url}")
    verbose_json(verbose, "behavior_event JSON", payload)
    response = post_json(url, payload)
    verbose_json(verbose, "Backend POST response", response)
    return response


def print_task(task: dict[str, Any]) -> None:
    print("\nAgent query task")
    print("=" * 72)
    for key in ["taskId", "userId", "adapter", "platform", "intent", "url", "query", "status"]:
        print(f"{key}: {sanitize(task.get(key) or '', 300)}")
    prompt = sanitize(task.get("prompt") or "", 1000)
    if prompt:
        print("\nPrompt:")
        print(prompt)
    print("=" * 72)


def confirm(task: dict[str, Any], cfg: WorkerConfig) -> bool:
    if cfg.yes:
        return True
    print_task(task)
    print("\nThis worker will only return public/non-sensitive fields.")
    print("Blocked: cookies, tokens, chats, private messages, contacts, payment records, passwords.")
    answer = input("Allow this local worker to execute this task? [y/N] ").strip().lower()
    return answer in {"y", "yes"}


def build_result(items: list[dict[str, Any]]) -> dict[str, Any]:
    normalized_items = [normalize_result_item(item) for item in items]
    return {
        "success": True,
        "ingest": True,
        "summary": f"Local worker returned {len(normalized_items)} public/non-sensitive item(s).",
        "items": normalized_items,
        "metadata": {
            "worker": "codex_query_worker.py",
            "privacy": "public-interest-only",
            "collectedAt": now_iso(),
        },
    }


def normalize_result_item(item: dict[str, Any]) -> dict[str, Any]:
    normalized = sanitize_item(item, allow_sensitive_check=False)
    normalized["occurredAt"] = dto_local_datetime(normalized.get("occurredAt"))
    return normalized


def build_codex_prompt(task: dict[str, Any], raw_items: list[dict[str, Any]]) -> str:
    safe_task = {
        "taskId": sanitize(task.get("taskId") or "", 120),
        "userId": sanitize(task.get("userId") or "", 120),
        "platform": sanitize(task.get("platform") or "", 80),
        "intent": sanitize(task.get("intent") or "", 120),
        "url": sanitize(task.get("url") or "", 300),
        "query": sanitize(task.get("query") or "", 300),
    }
    safe_items = [sanitize_item(item, allow_sensitive_check=False) for item in raw_items]
    return (
        "你是 Habit Assistant 的本地兴趣数据分析器。\n"
        "你只能基于我提供的 raw_items 做分析。\n"
        "不要访问文件系统。\n"
        "不要读取 Cookie、Token、Session、账号密码、聊天记录、私信、支付记录。\n"
        "不要调用外部网络。\n"
        "不要推测不存在的访问记录。\n"
        "如果 raw_items 中 url 为空，不允许补写或编造 URL。\n"
        "如果数据来自 visible-window，只能标记为 LOW confidence。\n"
        "如果数据来自 browser-history，可标记为 MEDIUM confidence。\n"
        "如果数据来自 browser-extension/page-visit，可标记为 HIGH confidence。\n"
        "请只返回 JSON，不要返回 Markdown。\n\n"
        "输出格式必须是：\n"
        "{\n"
        '  "items": [\n'
        "    {\n"
        '      "userId": "me",\n'
        '      "platform": "youtube | bilibili | baidu | xiaohongshu | web",\n'
        '      "source": "visible-window | browser-history | page-visit | local-notes | public-url | agent-reach-enrichment | codex-cli-analysis",\n'
        '      "eventType": "VISIT | WATCH | SEARCH | FAVORITE | APP_USAGE",\n'
        '      "externalId": "",\n'
        '      "title": "",\n'
        '      "url": "",\n'
        '      "author": "",\n'
        '      "contentSnippet": "",\n'
        '      "tags": [],\n'
        '      "confidence": "LOW | MEDIUM | HIGH",\n'
        '      "dataLevel": "APP_USAGE_SNAPSHOT | BROWSER_HISTORY | PAGE_VISIBLE_CONTENT | PUBLIC_URL | LOCAL_NOTE",\n'
        '      "detectionReason": "process_name | window_title | url_domain | browser_history | page_visible_content",\n'
        '      "matchedKeyword": "",\n'
        '      "interestTags": [],\n'
        '      "interestLabels": [],\n'
        '      "contentType": "video | note | article | web-page",\n'
        '      "interestCategory": "",\n'
        '      "intent": "",\n'
        '      "summaryForProfile": "",\n'
        '      "recommendationHints": [],\n'
        '      "occurredAt": "",\n'
        '      "rawMetadata": {"processName": "", "windowTitle": "", "domain": "", "visitCount": 0, "query": ""}\n'
        "    }\n"
        "  ]\n"
        "}\n\n"
        "task:\n"
        f"{json.dumps(safe_task, ensure_ascii=False, indent=2)}\n\n"
        "raw_items:\n"
        f"{json.dumps(safe_items, ensure_ascii=False, indent=2)}\n"
    )


def run_codex_cli(prompt: str, command: str, timeout: int, verbose: bool = False) -> str:
    verbose_log(verbose, f"\nCodex CLI command: {command}")
    verbose_log(verbose, "Codex CLI input follows:")
    if verbose:
        print("=" * 72)
        print(prompt)
        print("=" * 72)
    gateway = LLMGateway(command=command, timeout=timeout, env_provider=tool_env, sanitizer=sanitize)
    resolved = gateway.resolve_command()
    if not resolved:
        raise RuntimeError(f"Codex CLI command not found: {command}")
    args, shell = resolved
    completed = subprocess.run(
        args,
        input=prompt,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        env=tool_env(),
        shell=shell,
    )
    if completed.returncode != 0:
        raise RuntimeError(sanitize(completed.stderr or f"Codex CLI exited with {completed.returncode}", 1000))
    output = completed.stdout or completed.stderr or ""
    if verbose:
        print("\nCodex CLI output")
        print("=" * 72)
        print(output)
        print("=" * 72)
    return output


def analyze_with_codex_cli(task: dict[str, Any], raw_items: list[dict[str, Any]], cfg: WorkerConfig) -> list[dict[str, Any]]:
    command = cfg.codex_command or "codex"
    raw_items = enrich_content_for_llm(raw_items, verbose=cfg.verbose)
    if not find_codex_cli(command):
        verbose_log(cfg.verbose, f"\nCodex CLI command: {command}")
        print(f"Warning: Codex CLI not found for command '{command}'. Falling back to raw_items.", file=sys.stderr)
        return raw_items

    youtube_items: list[dict[str, Any]] = []
    general_items: list[dict[str, Any]] = []
    for item in raw_items:
        if is_valid_youtube_event(item):
            youtube_items.append(item)
        else:
            general_items.append(item)

    analyzed_youtube = [
        analyze_youtube_item_with_codex(item, command, cfg)
        for item in youtube_items
    ]
    if not general_items:
        verbose_json(cfg.verbose, "Codex analyzed items", analyzed_youtube)
        return analyzed_youtube

    prompt = build_codex_prompt(task, general_items)
    if cfg.verbose:
        verbose_json(True, "Codex raw_items", general_items)

    if cfg.print_codex_prompt:
        print("\nCodex prompt")
        print("=" * 72)
        print(prompt)
        print("=" * 72)

    try:
        output = run_codex_cli(prompt, command, cfg.codex_timeout, verbose=cfg.verbose)
    except subprocess.TimeoutExpired:
        print("Warning: Codex CLI timed out. Falling back to raw_items.", file=sys.stderr)
        return raw_items
    except Exception as exc:
        print(f"Warning: Codex CLI failed: {sanitize(exc, 400)}. Falling back to raw_items.", file=sys.stderr)
        return raw_items

    if cfg.print_codex_output:
        print("\nCodex output")
        print("=" * 72)
        print(output)
        print("=" * 72)

    try:
        decoded = parse_codex_json_output(output)
        analyzed = mark_codex_analysis_items(validate_codex_items(decoded))
        if has_invented_url(general_items, analyzed):
            raise ValueError("Codex output invented URL for empty raw_items")
    except Exception as exc:
        print(f"Warning: Codex output rejected: {sanitize(exc, 400)}. Falling back to raw_items.", file=sys.stderr)
        return raw_items
    final_items = analyzed_youtube + (analyzed or general_items)
    verbose_json(cfg.verbose, "Codex analyzed items", final_items)
    return final_items


def is_valid_youtube_event(item: dict[str, Any]) -> bool:
    if not isinstance(item, dict):
        return False
    platform = normalize_platform(item.get("platform"), item)
    event_type = normalize_event_type(item.get("eventType") or item.get("type"), item)
    url = normalize_url(item.get("url"))
    video_id = sanitize(item.get("externalId") or external_id_from_url(platform, url), 160)
    return platform == "youtube" and event_type in {"WATCH", "VISIT", "FAVORITE"} and bool(url or video_id)


def youtube_context_from_item(item: dict[str, Any]) -> dict[str, str]:
    content = content_from_item(item)
    url = normalize_url(item.get("url"))
    video_id = sanitize(item.get("externalId") or external_id_from_url("youtube", url), 160)
    raw_metadata = item.get("rawMetadata") if isinstance(item.get("rawMetadata"), dict) else {}
    raw_evidence = item.get("rawEvidence") if isinstance(item.get("rawEvidence"), dict) else {}
    description = sanitize(
        content.get("description")
        or item.get("description")
        or item.get("contentSnippet")
        or item.get("summary")
        or raw_metadata.get("description")
        or raw_evidence.get("description")
        or "",
        CONTENT_TEXT_LIMIT,
    )
    return {
        "title": sanitize(content.get("title") or item.get("title") or "", 300),
        "description": description,
        "author": sanitize(content.get("author") or item.get("author") or "", 160),
        "url": normalize_url(content.get("url") or url),
        "videoId": sanitize(content.get("externalId") or content.get("videoId") or video_id, 160),
    }


def build_youtube_analysis_prompt(context: dict[str, str]) -> str:
    safe_context = {key: sanitize(value, 1200 if key == "description" else 300) for key, value in context.items()}
    return (
        "SYSTEM TASK:\n"
        "You are analyzing a user's YouTube viewing behavior to build an interest profile.\n\n"
        "INPUT:\n"
        "- video title\n"
        "- description\n"
        "- channel\n"
        "- url\n\n"
        "OUTPUT JSON ONLY:\n"
        "{\n"
        '  "summary": "...",\n'
        '  "tags": ["tag1","tag2","tag3"],\n'
        '  "interestCategory": "backend | AI | entertainment | education | other",\n'
        '  "confidence": "HIGH | MEDIUM | LOW"\n'
        "}\n\n"
        "RULES:\n"
        "- tags must reflect content meaning, not system labels\n"
        "- summary must describe what the video is about\n"
        "- do NOT include sensitive inference (health, identity, etc.)\n"
        "- max 5 tags\n"
        "- output must be strict JSON\n\n"
        "PUBLIC YOUTUBE CONTEXT:\n"
        f"{json.dumps(safe_context, ensure_ascii=False, indent=2)}\n"
    )


def analyze_youtube_item_with_codex(item: dict[str, Any], command: str, cfg: WorkerConfig) -> dict[str, Any]:
    context = youtube_context_from_item(item)
    verbose_json(cfg.verbose, "YouTube public context for Codex", context)
    prompt = build_youtube_analysis_prompt(context)
    try:
        output = run_codex_cli(prompt, command, cfg.codex_timeout, verbose=cfg.verbose)
        analysis = validate_youtube_analysis(parse_codex_json_output(output))
        return apply_youtube_analysis(item, context, analysis)
    except subprocess.TimeoutExpired:
        print("Warning: YouTube Codex analysis timed out. Falling back to metadata item.", file=sys.stderr)
    except Exception as exc:
        print(f"Warning: YouTube Codex analysis failed: {sanitize(exc, 400)}. Falling back to metadata item.", file=sys.stderr)
    return sanitize_item(item, allow_sensitive_check=True)


def validate_youtube_analysis(decoded: dict[str, Any]) -> dict[str, Any]:
    if has_sensitive_field(decoded):
        raise ValueError("sensitive field name found")
    if contains_sensitive(json.dumps(decoded, ensure_ascii=False)):
        raise ValueError("sensitive content found")
    summary = sanitize(decoded.get("summary") or "", SUMMARY_TEXT_LIMIT)
    if not summary:
        raise ValueError("summary is required")
    tags_value = decoded.get("tags")
    if not isinstance(tags_value, list):
        raise ValueError("tags must be a list")
    tags = unique_values([sanitize(tag, 80) for tag in tags_value if sanitize(tag, 80)])[:5]
    tags = [tag for tag in tags if tag.lower() not in {"youtube", "video", "public-url", "agent-reach", "browser-history"}]
    category = sanitize(decoded.get("interestCategory") or "other", 40)
    if category not in YOUTUBE_INTEREST_CATEGORIES:
        category = "other"
    confidence = normalize_choice(decoded.get("confidence"), ALLOWED_CONFIDENCE, "MEDIUM")
    return {
        "summary": summary,
        "tags": tags,
        "interestCategory": category,
        "confidence": confidence,
    }


def apply_youtube_analysis(item: dict[str, Any], context: dict[str, str], analysis: dict[str, Any]) -> dict[str, Any]:
    raw_evidence = sanitize_raw_evidence(item.get("rawMetadata") or item.get("rawEvidence"))
    raw_evidence["domain"] = "youtube.com"
    raw_evidence["externalId"] = context["videoId"]
    raw_evidence["url"] = context["url"]
    raw_evidence["contentSnippet"] = analysis["summary"]
    raw_evidence["contentType"] = "video"
    raw_evidence["interestCategory"] = analysis["interestCategory"]
    raw_evidence["contentCategory"] = analysis["interestCategory"]
    raw_evidence["originalSource"] = sanitize(item.get("source") or "", 120)
    raw_evidence["content"] = content_from_item(item)
    enriched = {
        **item,
        "platform": "youtube",
        "source": "codex-cli-analysis",
        "eventType": "WATCH",
        "type": "WATCH",
        "externalId": context["videoId"],
        "title": context["title"],
        "url": context["url"],
        "author": context["author"],
        "contentSnippet": analysis["summary"],
        "summary": analysis["summary"],
        "tags": analysis["tags"],
        "interestTags": analysis["tags"],
        "interestLabels": analysis["tags"],
        "contentType": "video",
        "interestCategory": analysis["interestCategory"],
        "contentCategory": analysis["interestCategory"],
        "confidence": analysis["confidence"],
        "dataLevel": "PAGE_VISIBLE_CONTENT",
        "detectionReason": "public_url_enrichment",
        "matchedKeyword": context["videoId"] or "youtube.com",
        "rawMetadata": raw_evidence,
        "rawEvidence": raw_evidence,
    }
    sanitized = sanitize_item(enriched, allow_sensitive_check=True)
    content_tags = unique_values(analysis["tags"])[:5]
    sanitized["tags"] = content_tags
    sanitized["interestTags"] = content_tags
    sanitized["interestLabels"] = content_tags
    return sanitized


def parse_codex_json_output(output: str) -> dict[str, Any]:
    text = (output or "").strip()
    if not text:
        raise ValueError("empty output")
    try:
        decoded = json.loads(text)
        if isinstance(decoded, dict):
            return decoded
    except json.JSONDecodeError:
        pass

    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.IGNORECASE | re.DOTALL)
    if fenced:
        decoded = json.loads(fenced.group(1))
        if isinstance(decoded, dict):
            return decoded

    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        decoded = json.loads(text[start:end + 1])
        if isinstance(decoded, dict):
            return decoded
    raise ValueError("no JSON object found")


def extract_json_object(output: str) -> dict[str, Any]:
    return parse_codex_json_output(output)


def enrich_content_for_llm(items: list[dict[str, Any]], verbose: bool = False) -> list[dict[str, Any]]:
    enriched: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        content = build_structured_content_object(item)
        updated = attach_content_object(item, content)
        verbose_json(verbose, "Structured content object for LLM", content)
        enriched.append(updated)
    return enriched


def attach_content_object(item: dict[str, Any], content: dict[str, Any]) -> dict[str, Any]:
    raw_evidence = sanitize_raw_evidence(item.get("rawMetadata") or item.get("rawEvidence"))
    raw_evidence["content"] = content
    raw_evidence["contentType"] = sanitize(content.get("contentType") or raw_evidence.get("contentType") or "", 80)
    if content.get("url"):
        raw_evidence["url"] = sanitize(content.get("url"), 600)
    if content.get("externalId"):
        raw_evidence["externalId"] = sanitize(content.get("externalId"), 160)
    if content.get("summary"):
        raw_evidence["contentSnippet"] = sanitize(content.get("summary"), SUMMARY_TEXT_LIMIT)
    updated = dict(item)
    updated["rawMetadata"] = raw_evidence
    updated["rawEvidence"] = raw_evidence
    if not updated.get("contentSnippet") and content.get("summary"):
        updated["contentSnippet"] = sanitize(content.get("summary"), SUMMARY_TEXT_LIMIT)
        updated["summary"] = updated["contentSnippet"]
    if not updated.get("title") and content.get("title"):
        updated["title"] = sanitize(content.get("title"), 300)
    if not updated.get("author") and content.get("author"):
        updated["author"] = sanitize(content.get("author"), 160)
    return updated


def content_from_item(item: dict[str, Any]) -> dict[str, Any]:
    direct = item.get("content")
    if isinstance(direct, dict):
        return sanitize_content_object(direct)
    for key in ("rawMetadata", "rawEvidence"):
        raw = item.get(key)
        if isinstance(raw, dict) and isinstance(raw.get("content"), dict):
            return sanitize_content_object(raw["content"])
    return build_structured_content_object(item)


def build_structured_content_object(item: dict[str, Any]) -> dict[str, Any]:
    existing = existing_content_object(item)
    if existing:
        return existing
    platform = normalize_platform(item.get("platform"), item)
    url = normalize_url(item.get("url"))
    external_id = sanitize(item.get("externalId") or external_id_from_url(platform, url), 160)
    if platform == "youtube":
        return build_youtube_content_object(item, url, external_id)
    if platform == "bilibili":
        return build_video_content_object(item, "bilibili", url, external_id)
    if platform == "xiaohongshu":
        return build_xiaohongshu_content_object(item, url, external_id)
    if url:
        return build_web_page_content_object(item, platform, url, external_id)
    return sanitize_content_object({
        "platform": platform,
        "source": "content-enrichment",
        "contentType": sanitize(item.get("contentType") or "event", 80),
        "url": "",
        "externalId": external_id,
        "title": item.get("title") or "",
        "author": item.get("author") or "",
        "description": item.get("contentSnippet") or item.get("summary") or "",
        "bodyText": item.get("text") or "",
        "summary": item.get("contentSnippet") or item.get("summary") or "",
        "transcript": "",
        "dataLevel": item.get("dataLevel") or "APP_USAGE_SNAPSHOT",
        "fetchedBy": "local-event-fields",
    })


def existing_content_object(item: dict[str, Any]) -> dict[str, Any]:
    direct = item.get("content")
    if isinstance(direct, dict) and has_content_payload(direct):
        return sanitize_content_object(direct)
    for key in ("rawMetadata", "rawEvidence"):
        raw = item.get(key)
        if isinstance(raw, dict) and isinstance(raw.get("content"), dict) and has_content_payload(raw["content"]):
            return sanitize_content_object(raw["content"])
    return {}


def has_content_payload(value: dict[str, Any]) -> bool:
    return bool(value.get("title") or value.get("description") or value.get("bodyText") or value.get("transcript"))


def build_youtube_content_object(item: dict[str, Any], url: str, external_id: str) -> dict[str, Any]:
    fetched = fetch_video_content_with_ytdlp("youtube", url) if url and needs_public_fetch(item) else {}
    description = (
        fetched.get("description")
        or item.get("description")
        or item.get("contentSnippet")
        or item.get("summary")
        or raw_field(item, "description")
        or ""
    )
    transcript = fetched.get("transcript") or raw_field(item, "transcript")
    return sanitize_content_object({
        "platform": "youtube",
        "source": "content-enrichment",
        "contentType": "video",
        "url": fetched.get("url") or url,
        "externalId": fetched.get("externalId") or external_id,
        "videoId": fetched.get("externalId") or external_id,
        "title": fetched.get("title") or item.get("title") or "",
        "author": fetched.get("author") or item.get("author") or "",
        "description": description,
        "bodyText": description,
        "summary": summarize_public_description(description, item.get("title") or ""),
        "transcript": transcript,
        "transcriptAvailable": bool(transcript or fetched.get("transcriptAvailable")),
        "dataLevel": "PAGE_VISIBLE_CONTENT" if fetched else item.get("dataLevel") or "PUBLIC_URL",
        "fetchedBy": fetched.get("fetchedBy") or "event-public-metadata",
    })


def build_video_content_object(item: dict[str, Any], platform: str, url: str, external_id: str) -> dict[str, Any]:
    fetched = fetch_video_content_with_ytdlp(platform, url) if url and needs_public_fetch(item) else {}
    description = fetched.get("description") or item.get("contentSnippet") or item.get("summary") or ""
    return sanitize_content_object({
        "platform": platform,
        "source": "content-enrichment",
        "contentType": "video",
        "url": fetched.get("url") or url,
        "externalId": fetched.get("externalId") or external_id,
        "title": fetched.get("title") or item.get("title") or "",
        "author": fetched.get("author") or item.get("author") or "",
        "description": description,
        "bodyText": description,
        "summary": summarize_public_description(description, item.get("title") or ""),
        "transcript": fetched.get("transcript") or "",
        "transcriptAvailable": bool(fetched.get("transcriptAvailable")),
        "dataLevel": "PAGE_VISIBLE_CONTENT" if fetched else item.get("dataLevel") or "PUBLIC_URL",
        "fetchedBy": fetched.get("fetchedBy") or "event-public-metadata",
    })


def build_xiaohongshu_content_object(item: dict[str, Any], url: str, external_id: str) -> dict[str, Any]:
    text = item.get("contentSnippet") or item.get("summary") or raw_field(item, "contentSnippet") or ""
    return sanitize_content_object({
        "platform": "xiaohongshu",
        "source": "content-enrichment",
        "contentType": "note",
        "url": url,
        "externalId": external_id,
        "noteId": external_id,
        "title": item.get("title") or "",
        "author": item.get("author") or "",
        "description": text,
        "bodyText": text,
        "summary": text,
        "transcript": "",
        "dataLevel": item.get("dataLevel") or "PUBLIC_URL",
        "fetchedBy": "agent-reach-or-event-public-fields",
    })


def build_web_page_content_object(item: dict[str, Any], platform: str, url: str, external_id: str) -> dict[str, Any]:
    text = item.get("contentSnippet") or item.get("summary") or raw_field(item, "contentSnippet") or ""
    return sanitize_content_object({
        "platform": "web" if platform in {"browser", "generic_web"} else platform,
        "source": "content-enrichment",
        "contentType": sanitize(item.get("contentType") or "web-page", 80),
        "url": url,
        "externalId": external_id,
        "title": item.get("title") or "",
        "author": item.get("author") or "",
        "description": text,
        "bodyText": text,
        "summary": text,
        "transcript": "",
        "dataLevel": item.get("dataLevel") or "PUBLIC_URL",
        "fetchedBy": "agent-reach-or-event-public-fields",
    })


def fetch_video_content_with_ytdlp(platform: str, url: str) -> dict[str, Any]:
    if not find_tool("yt-dlp"):
        return {}
    try:
        completed = subprocess.run(
            ["yt-dlp", "--dump-json", "--skip-download", url],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=40,
            env=tool_env(),
        )
        data = json.loads(completed.stdout)
    except Exception as exc:
        print(f"Warning: content enrichment metadata fetch failed: {sanitize(exc, 300)}", file=sys.stderr)
        return {}
    webpage_url = normalize_url(data.get("webpage_url") or url)
    transcript_available = bool(data.get("subtitles") or data.get("automatic_captions"))
    return {
        "platform": platform,
        "url": webpage_url,
        "externalId": sanitize(data.get("id") or external_id_from_url(platform, webpage_url), 160),
        "title": sanitize(data.get("title") or "", 300),
        "author": sanitize(data.get("uploader") or data.get("channel") or "", 160),
        "description": sanitize(data.get("description") or "", CONTENT_TEXT_LIMIT),
        "transcript": sanitize(data.get("transcript") or "", CONTENT_TEXT_LIMIT),
        "transcriptAvailable": transcript_available,
        "fetchedBy": "yt-dlp-public-metadata",
    }


def needs_public_fetch(item: dict[str, Any]) -> bool:
    content = item.get("content")
    if isinstance(content, dict) and (content.get("title") or content.get("description") or content.get("bodyText")):
        return False
    return not (item.get("title") and (item.get("description") or item.get("contentSnippet") or item.get("summary")))


def raw_field(item: dict[str, Any], key: str) -> str:
    for raw_key in ("rawMetadata", "rawEvidence"):
        raw = item.get(raw_key)
        if isinstance(raw, dict) and raw.get(key) is not None:
            return sanitize(raw.get(key), CONTENT_TEXT_LIMIT)
    return ""


def sanitize_content_object(value: dict[str, Any]) -> dict[str, Any]:
    return {
        "platform": sanitize(value.get("platform") or "", 80),
        "source": sanitize(value.get("source") or "content-enrichment", 80),
        "contentType": sanitize(value.get("contentType") or "", 80),
        "url": normalize_url(value.get("url")),
        "externalId": sanitize(value.get("externalId") or value.get("videoId") or value.get("noteId") or "", 160),
        "videoId": sanitize(value.get("videoId") or value.get("externalId") or "", 160),
        "noteId": sanitize(value.get("noteId") or value.get("externalId") or "", 160),
        "title": sanitize(value.get("title") or "", 300),
        "author": sanitize(value.get("author") or "", 160),
        "description": sanitize(value.get("description") or "", CONTENT_TEXT_LIMIT),
        "bodyText": sanitize(value.get("bodyText") or value.get("description") or "", CONTENT_TEXT_LIMIT),
        "summary": sanitize(value.get("summary") or value.get("description") or "", SUMMARY_TEXT_LIMIT),
        "transcript": sanitize(value.get("transcript") or "", CONTENT_TEXT_LIMIT),
        "transcriptAvailable": bool(value.get("transcriptAvailable")),
        "dataLevel": sanitize(value.get("dataLevel") or "PUBLIC_URL", 80),
        "fetchedBy": sanitize(value.get("fetchedBy") or "", 120),
    }


def validate_codex_items(decoded: dict[str, Any]) -> list[dict[str, Any]]:
    if has_sensitive_field(decoded):
        raise ValueError("sensitive field name found")
    if contains_sensitive(json.dumps(decoded, ensure_ascii=False)):
        raise ValueError("sensitive content found")
    rows = decoded.get("items")
    if not isinstance(rows, list):
        raise ValueError("items must be a list")
    sanitized: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        item = sanitize_item(row, allow_sensitive_check=True)
        if item:
            sanitized.append(item)
    return sanitized


def mark_codex_analysis_items(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    marked: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        updated = dict(item)
        previous_source = sanitize(updated.get("source") or "", 120)
        raw_evidence = dict(updated.get("rawEvidence") or {}) if isinstance(updated.get("rawEvidence"), dict) else {}
        if previous_source and not raw_evidence.get("originalSource"):
            raw_evidence["originalSource"] = previous_source
        updated["source"] = "codex-cli-analysis"
        updated["rawEvidence"] = raw_evidence
        marked.append(updated)
    return marked


def has_invented_url(raw_items: list[dict[str, Any]], analyzed_items: list[dict[str, Any]]) -> bool:
    raw_urls = [str(item.get("url") or "").strip() for item in raw_items if isinstance(item, dict)]
    if any(raw_urls):
        return False
    return any(str(item.get("url") or "").strip() for item in analyzed_items if isinstance(item, dict))


def sanitize_raw_evidence(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {"processName": "", "windowTitle": "", "domain": "", "visitCount": 0}
    safe: dict[str, Any] = {
        "browser": sanitize(value.get("browser") or "", 80),
        "processName": sanitize(value.get("processName") or "", 120),
        "windowTitle": sanitize(value.get("windowTitle") or "", 240),
        "domain": sanitize(value.get("domain") or "", 160),
        "visitCount": 0,
        "query": sanitize(value.get("query") or "", 240),
        "url": sanitize(value.get("url") or "", 600),
        "externalId": sanitize(value.get("externalId") or "", 160),
        "videoId": sanitize(value.get("videoId") or value.get("externalId") or "", 160),
        "contentSnippet": sanitize(value.get("contentSnippet") or "", SUMMARY_TEXT_LIMIT),
        "adapter": sanitize(value.get("adapter") or "", 80),
        "adapterMode": sanitize(value.get("adapterMode") or "", 80),
        "agentReachCommand": sanitize(value.get("agentReachCommand") or "", 500),
        "originalSource": sanitize(value.get("originalSource") or "", 120),
        "contentType": sanitize(value.get("contentType") or "", 80),
        "interestCategory": sanitize(value.get("interestCategory") or value.get("contentCategory") or "", 120),
        "contentCategory": sanitize(value.get("contentCategory") or value.get("interestCategory") or "", 120),
        "intent": sanitize(value.get("intent") or "", 120),
    }
    if isinstance(value.get("content"), dict):
        safe["content"] = sanitize_content_object(value["content"])
    try:
        safe["visitCount"] = max(0, int(value.get("visitCount") or 0))
    except (TypeError, ValueError):
        safe["visitCount"] = 0
    return safe


def domain_from_url(url: str) -> str:
    if not url:
        return ""
    try:
        host = urllib.parse.urlparse(url).hostname or ""
        return host.lower().removeprefix("www.")
    except ValueError:
        return ""


def normalize_url(value: Any) -> str:
    url = sanitize(value or "", 600).strip()
    if not url:
        return ""
    if url.startswith("www."):
        url = "https://" + url
    if not url.startswith(("http://", "https://")):
        return url
    try:
        parsed = urllib.parse.urlparse(url)
        query = urllib.parse.parse_qsl(parsed.query, keep_blank_values=True)
        cleaned_query = urllib.parse.urlencode(
            [(key, value) for key, value in query if key.lower() not in TRACKING_QUERY_PARAMS],
            doseq=True,
        )
        return urllib.parse.urlunparse((
            "https",
            parsed.netloc.lower(),
            parsed.path or "",
            "",
            cleaned_query,
            "",
        ))
    except ValueError:
        return ""


def external_id_from_url(platform: str, url: str) -> str:
    if not url:
        return ""
    if platform == "youtube":
        parsed = urllib.parse.urlparse(url)
        query = urllib.parse.parse_qs(parsed.query)
        if query.get("v"):
            return sanitize(query["v"][0], 160)
        if "youtu.be" in parsed.netloc:
            return sanitize(parsed.path.strip("/").split("/")[0], 160)
    if platform == "bilibili":
        match = re.search(r"/video/((?:BV|av)[A-Za-z0-9]+)", url, flags=re.IGNORECASE)
        return sanitize(match.group(1), 160) if match else ""
    if platform == "xiaohongshu":
        match = re.search(r"/(?:explore|discovery/item)/([A-Za-z0-9_-]+)", url)
        return sanitize(match.group(1), 160) if match else ""
    return ""


def has_keyword(text: str, *keywords: str) -> bool:
    return any(keyword.lower() in text for keyword in keywords)


def unique_values(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        text = sanitize(value, 120)
        key = text.lower()
        if text and key not in seen:
            seen.add(key)
            result.append(text)
    return result


def infer_interest_labels(item: dict[str, Any]) -> list[str]:
    text = " ".join([
        str(item.get("platform") or ""),
        str(item.get("title") or ""),
        str(item.get("url") or ""),
        str(item.get("summary") or ""),
        " ".join(str(tag) for tag in item.get("tags") or [] if tag is not None),
    ]).lower()
    labels: list[str] = []
    if "xiaohongshu.com" in text or "xhslink.com" in text or "xiaohongshu" in text or "小红书" in text:
        labels.extend(["小红书", "生活方式"])
    if "spring" in text or "redis" in text or "java" in text:
        labels.append("Java后端")
    if "youtube.com" in text or "youtu.be" in text or "youtube" in text:
        labels.append("视频学习")
    if "bilibili.com" in text or "bilibili" in text or "b站" in text:
        labels.append("B站视频")
    return unique_values(labels)


def has_sensitive_field(value: Any) -> bool:
    if isinstance(value, dict):
        for key, nested in value.items():
            if str(key).strip() in SENSITIVE_FIELD_NAMES or str(key).strip().lower() in SENSITIVE_FIELD_NAMES:
                return True
            if has_sensitive_field(nested):
                return True
    if isinstance(value, list):
        return any(has_sensitive_field(item) for item in value)
    return False


def sanitize_item(item: dict[str, Any], allow_sensitive_check: bool = True) -> dict[str, Any]:
    if allow_sensitive_check and has_sensitive_field(item):
        raise ValueError("sensitive field name found")

    filtered = {key: item.get(key) for key in ALLOWED_ITEM_FIELDS if key in item}
    if allow_sensitive_check and contains_sensitive(json.dumps(filtered, ensure_ascii=False)):
        raise ValueError("sensitive content found")

    tags = filtered.get("tags")
    if isinstance(tags, list):
        safe_tags = [sanitize(tag, 80) for tag in tags if sanitize(tag, 80)]
    elif tags:
        safe_tags = [sanitize(tags, 80)]
    else:
        safe_tags = []
    labels = filtered.get("interestLabels")
    if isinstance(labels, list):
        safe_labels = [sanitize(label, 120) for label in labels if sanitize(label, 120)]
    elif labels:
        safe_labels = [sanitize(labels, 120)]
    else:
        safe_labels = []
    interest_tags = filtered.get("interestTags")
    if isinstance(interest_tags, list):
        safe_interest_tags = [sanitize(tag, 120) for tag in interest_tags if sanitize(tag, 120)]
    elif interest_tags:
        safe_interest_tags = [sanitize(interest_tags, 120)]
    else:
        safe_interest_tags = []
    hints = filtered.get("recommendationHints")
    if isinstance(hints, list):
        safe_hints = [sanitize(hint, 160) for hint in hints if sanitize(hint, 160)]
    elif hints:
        safe_hints = [sanitize(hints, 160)]
    else:
        safe_hints = []

    raw_evidence = sanitize_raw_evidence(filtered.get("rawMetadata") or filtered.get("rawEvidence"))
    if raw_evidence:
        filtered = {**filtered, "rawEvidence": raw_evidence}
    content_type = sanitize(filtered.get("contentType") or raw_evidence.get("contentType") or "", 80)
    interest_category = sanitize(
        filtered.get("interestCategory")
        or filtered.get("contentCategory")
        or raw_evidence.get("interestCategory")
        or raw_evidence.get("contentCategory")
        or "",
        120,
    )
    content_category = interest_category
    item_intent = sanitize(filtered.get("intent") or raw_evidence.get("intent") or "", 120)
    summary_for_profile = sanitize(filtered.get("summaryForProfile") or "", SUMMARY_TEXT_LIMIT)
    if content_type:
        raw_evidence["contentType"] = content_type
    if content_category:
        raw_evidence["interestCategory"] = content_category
        raw_evidence["contentCategory"] = content_category
    if item_intent:
        raw_evidence["intent"] = item_intent

    source = normalize_source(filtered.get("source"), filtered)
    platform = normalize_platform(filtered.get("platform"), filtered)
    event_type = normalize_event_type(filtered.get("eventType") or filtered.get("type"), filtered)
    url = normalize_url(filtered.get("url"))
    external_id = sanitize(filtered.get("externalId") or external_id_from_url(platform, url), 160)
    confidence = normalize_choice(filtered.get("confidence"), ALLOWED_CONFIDENCE, default_confidence(filtered))
    data_level = normalize_choice(filtered.get("dataLevel"), ALLOWED_DATA_LEVELS, default_data_level(filtered))
    detection_reason = normalize_choice(
        filtered.get("detectionReason"),
        ALLOWED_DETECTION_REASONS,
        default_detection_reason(filtered),
    )
    safe_labels = unique_values([*safe_labels, *safe_interest_tags, *infer_interest_labels(filtered)])[:8]
    safe_tags = unique_values([*safe_tags, *safe_interest_tags, *safe_labels, content_type, content_category])[:12]
    summary = sanitize(summary_for_profile or filtered.get("contentSnippet") or filtered.get("summary") or "", SUMMARY_TEXT_LIMIT)
    if summary:
        raw_evidence["contentSnippet"] = summary
    if url:
        raw_evidence["url"] = url
    if external_id:
        raw_evidence["externalId"] = external_id
    if platform == "baidu" and event_type == "SEARCH" and not raw_evidence.get("query"):
        raw_evidence["query"] = sanitize(filtered.get("matchedKeyword") or filtered.get("title") or "", 240)

    return {
        "userId": sanitize(filtered.get("userId") or "", 120),
        "platform": platform,
        "source": source,
        "eventType": event_type,
        "type": event_type,
        "externalId": external_id,
        "title": sanitize(filtered.get("title") or "", 300),
        "url": url,
        "author": sanitize(filtered.get("author") or "", 160),
        "contentSnippet": summary,
        "summary": summary,
        "tags": safe_tags[:12],
        "confidence": confidence,
        "dataLevel": data_level,
        "detectionReason": detection_reason,
        "matchedKeyword": sanitize(filtered.get("matchedKeyword") or "", 120),
        "interestTags": safe_interest_tags[:8],
        "interestLabels": safe_labels,
        "recommendationHints": safe_hints[:5],
        "contentType": content_type,
        "interestCategory": interest_category,
        "contentCategory": content_category,
        "intent": item_intent,
        "summaryForProfile": summary_for_profile,
        "occurredAt": dto_local_datetime(filtered.get("occurredAt")),
        "rawMetadata": raw_evidence,
        "rawEvidence": raw_evidence,
    }


def normalize_platform(value: Any, item: dict[str, Any]) -> str:
    platform = sanitize(value or "", 80).strip().lower().replace("-", "_")
    url = str(item.get("url") or "")
    domain = domain_from_url(url)
    title_haystack = " ".join([
        str(item.get("title") or ""),
        str(item.get("summary") or ""),
        str((item.get("rawEvidence") or {}).get("windowTitle") or "") if isinstance(item.get("rawEvidence"), dict) else "",
        " ".join(str(tag) for tag in item.get("tags") or [] if tag is not None),
    ]).lower()
    if domain.endswith("xiaohongshu.com") or domain.endswith("xhslink.com") or has_keyword(title_haystack, "xiaohongshu", "小红书", "xhs"):
        return "xiaohongshu"
    if domain.endswith("bilibili.com") or has_keyword(title_haystack, "bilibili", "b站"):
        return "bilibili"
    if domain.endswith("youtube.com") or domain.endswith("youtu.be") or has_keyword(title_haystack, "youtube"):
        return "youtube"
    if domain.endswith("baidu.com") or platform == "baidu_search" or has_keyword(title_haystack, "baidu", "百度"):
        return "baidu"
    if domain.endswith("github.com") or has_keyword(title_haystack, "github"):
        return "github"
    if domain.endswith("weixin.qq.com") or has_keyword(title_haystack, "wechat", "微信", "weixin"):
        return "wechat"
    if platform == "generic_web":
        return "web"
    if platform in ALLOWED_PLATFORMS:
        return platform
    return "web" if url.startswith(("http://", "https://")) else "desktop-app"


def normalize_event_type(value: Any, item: dict[str, Any]) -> str:
    default = "APP_USAGE" if default_data_level(item) == "APP_USAGE_SNAPSHOT" else "VISIT"
    event_type = sanitize(value or default, 40).strip().upper()
    return event_type if event_type in ALLOWED_TYPES else "VISIT"


def normalize_source(value: Any, item: dict[str, Any]) -> str:
    source = sanitize(value or "", 80).strip().lower()
    allowed = {
        "visible-window",
        "browser-history",
        "page-visit",
        "local-notes",
        "public-url",
        "codex-cli-analysis",
        "agent-reach-mock",
        "agent-reach-enrichment",
    }
    if source in allowed:
        return source
    data_level = str(item.get("dataLevel") or "").upper()
    tags = {str(tag).lower() for tag in item.get("tags") or []}
    if data_level == "PAGE_VISIBLE_CONTENT" or "page-visit" in tags or "browser-extension" in tags:
        return "page-visit"
    if data_level == "BROWSER_HISTORY" or "browser-history" in tags:
        return "browser-history"
    if data_level == "LOCAL_NOTE" or "local-notes" in tags:
        return "local-notes"
    if str(item.get("url") or "").startswith(("http://", "https://")):
        return "public-url"
    return "visible-window"


def normalize_choice(value: Any, allowed: set[str], default: str) -> str:
    choice = sanitize(value or "", 80).strip().upper()
    return choice if choice in allowed else default


def default_confidence(item: dict[str, Any]) -> str:
    tags = {str(tag).lower() for tag in item.get("tags") or []}
    source = str(item.get("source") or "").lower()
    if source in {"visible-window"} or "visible-window" in tags:
        return "LOW"
    if "browser-extension" in tags or "page-visit" in tags or "page_visible_content" in source or source == "page-visit":
        return "HIGH"
    if "browser-history" in tags or "browser_history" in source or source == "browser-history" or source.startswith("agent-reach") or str(item.get("url") or "").startswith(("http://", "https://")):
        return "MEDIUM"
    return "LOW"


def default_data_level(item: dict[str, Any]) -> str:
    tags = {str(tag).lower() for tag in item.get("tags") or []}
    source = str(item.get("source") or "").lower()
    if source == "local-notes" or "local-notes" in tags:
        return "LOCAL_NOTE"
    if "browser-extension" in tags or "page-visit" in tags or "page_visible_content" in source or source == "page-visit":
        return "PAGE_VISIBLE_CONTENT"
    if "browser-history" in tags or "browser_history" in source or source == "browser-history":
        return "BROWSER_HISTORY"
    if source.startswith("agent-reach"):
        return "PUBLIC_URL"
    if str(item.get("url") or "").startswith(("http://", "https://")):
        return "PUBLIC_URL"
    return "APP_USAGE_SNAPSHOT"


def default_detection_reason(item: dict[str, Any]) -> str:
    tags = {str(tag).lower() for tag in item.get("tags") or []}
    source = str(item.get("source") or "").lower()
    if "browser-history" in tags or source == "browser-history":
        return "browser_history"
    if "browser-extension" in tags or "page-visit" in tags or source == "page-visit":
        return "page_visible_content"
    if source.startswith("agent-reach"):
        return "public_url_enrichment"
    if str(item.get("url") or "").startswith(("http://", "https://")):
        return "url_domain"
    if item.get("title"):
        return "window_title"
    return "process_name"


def collect_task(task: dict[str, Any], cfg: WorkerConfig) -> list[dict[str, Any]]:
    platform = str(task.get("platform") or "").lower().replace("-", "_")
    intent = str(task.get("intent") or "").lower()
    query = task.get("query") or ""
    url = task.get("url") or ""

    if contains_sensitive(" ".join([platform, intent, str(query), str(url), str(task.get("prompt") or "")])):
        raise RuntimeError("Task contains blocked sensitive keywords.")

    if platform in {"local-terminal", "desktop-app"} or intent == "app-usage-summary":
        return collect_visible_apps(cfg.limit)
    if platform == "local-notes" or intent == "read-note":
        return collect_local_notes(str(url or "data/local-notes"), cfg.allowed_dirs, cfg.limit)
    if platform == "bilibili":
        return collect_bilibili(str(url), str(query), cfg.limit)
    if platform == "youtube":
        return collect_youtube(str(url), str(query))
    if platform in {"xiaohongshu", "xhs"}:
        return collect_xiaohongshu(str(url), str(query), cfg.limit)
    if platform in {"baidu", "baidu_search"} or intent in {"baidu-search", "search"}:
        return collect_baidu_search(str(query), str(url))
    if platform in {"web", "generic_web", "browser"} or intent in {"read-page", "browser-history-summary"}:
        return collect_browser_or_public_url(str(url), str(query), cfg.limit)

    return [{
        "platform": sanitize(platform or "web"),
        "source": "visible-window",
        "type": "SEARCH",
        "externalId": "",
        "title": sanitize(query or url or "Agent query"),
        "url": sanitize(url),
        "author": "",
        "summary": "No local collector matched this platform/intent.",
        "tags": infer_tags(platform, intent, query, url),
        "confidence": "LOW",
        "dataLevel": "APP_USAGE_SNAPSHOT",
        "detectionReason": "window_title",
        "matchedKeyword": sanitize(query or platform or intent, 120),
        "occurredAt": dto_local_datetime(),
        "rawEvidence": {"processName": "", "windowTitle": "", "domain": "", "visitCount": 0},
    }]


def should_enrich_public_url(item: dict[str, Any]) -> bool:
    url = str(item.get("url") or "")
    source = str(item.get("source") or "").lower()
    data_level = str(item.get("dataLevel") or "").upper()
    return (
        url.startswith(("http://", "https://"))
        and source in {"browser-history", "public-url"}
        and data_level in {"", "BROWSER_HISTORY", "PUBLIC_URL"}
    )


def enrich_public_url_items(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    enriched: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            continue
        if should_enrich_public_url(item):
            try:
                enriched.append(enrich_public_url(item))
                continue
            except Exception as exc:
                print(f"Warning: Agent Reach mock enrichment failed: {sanitize(exc, 300)}", file=sys.stderr)
        enriched.append(item)
    return enriched


def collect_visible_apps(limit: int) -> list[dict[str, Any]]:
    if os.name != "nt":
        return [{
            "platform": "desktop-app",
            "source": "visible-window",
            "type": "APP_USAGE",
            "externalId": "",
            "title": "App usage summary unavailable",
            "url": "",
            "author": "",
            "summary": "Visible app collection currently supports Windows only.",
            "tags": ["Local app", "unsupported-os"],
            "confidence": "LOW",
            "dataLevel": "APP_USAGE_SNAPSHOT",
            "detectionReason": "process_name",
            "matchedKeyword": "unsupported-os",
            "occurredAt": dto_local_datetime(),
            "rawEvidence": {"processName": "", "windowTitle": "", "domain": "", "visitCount": 0},
        }]

    items: list[dict[str, Any]] = []
    for row in collect_windows_via_api(limit):
        process_name = sanitize(row["processName"])
        window_title = sanitize(row["windowTitle"])
        if not process_name or not window_title:
            continue
        raw_evidence = {
            "processName": process_name,
            "windowTitle": window_title,
            "domain": "",
            "visitCount": 0,
        }
        platform_tags = infer_tags("app", process_name, window_title)
        items.append({
            "platform": normalize_platform("desktop-app", {"rawEvidence": raw_evidence, "title": window_title, "tags": platform_tags}),
            "source": "visible-window",
            "type": "APP_USAGE",
            "externalId": f"{process_name}-{row['processId']}",
            "title": f"Visible app: {process_name}",
            "url": "",
            "author": "",
            "summary": (
                "Current visible-window snapshot only. "
                "It does not include exact daily duration or launch count. "
                f"Window title: {window_title}"
            ),
            "tags": ["visible-window", *platform_tags],
            "confidence": "LOW",
            "dataLevel": "APP_USAGE_SNAPSHOT",
            "detectionReason": "window_title" if window_title else "process_name",
            "matchedKeyword": sanitize(window_title or process_name, 120),
            "occurredAt": dto_local_datetime(),
            "rawEvidence": raw_evidence,
        })
    return items


def collect_windows_via_api(limit: int) -> list[dict[str, Any]]:
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    enum_windows_proc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    process_query_limited_information = 0x1000
    rows: list[dict[str, Any]] = []

    def process_name(pid: int) -> str:
        handle = kernel32.OpenProcess(process_query_limited_information, False, pid)
        if not handle:
            return ""
        try:
            size = wintypes.DWORD(32768)
            buffer = ctypes.create_unicode_buffer(size.value)
            ok = kernel32.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size))
            return Path(buffer.value).stem if ok else ""
        finally:
            kernel32.CloseHandle(handle)

    def callback(hwnd: int, _lparam: int) -> bool:
        if len(rows) >= limit:
            return False
        if not user32.IsWindowVisible(hwnd):
            return True
        length = user32.GetWindowTextLengthW(hwnd)
        if length <= 0:
            return True
        buffer = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buffer, length + 1)
        title = buffer.value.strip()
        if not title:
            return True
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        rows.append({
            "processId": pid.value,
            "processName": process_name(pid.value),
            "windowTitle": title,
        })
        return True

    user32.EnumWindows(enum_windows_proc(callback), 0)
    return rows


def collect_local_notes(target: str, allowed_dirs: list[str], limit: int) -> list[dict[str, Any]]:
    root = Path(target).resolve()
    allowed_roots = [Path(path).resolve() for path in allowed_dirs]
    if not any(root == allowed or root.is_relative_to(allowed) for allowed in allowed_roots):
        raise RuntimeError("Requested path is outside allowed local directories.")
    if not root.exists():
        return []

    files = [root] if root.is_file() else [path for path in root.rglob("*") if path.is_file()]
    items: list[dict[str, Any]] = []
    for path in files:
        if len(items) >= limit:
            break
        if path.suffix.lower() not in {".md", ".txt", ".csv", ".json", ".html"}:
            continue
        if path.stat().st_size > 2 * 1024 * 1024:
            continue
        text = sanitize(path.read_text(encoding="utf-8", errors="replace"), 1200)
        title = next((line.strip("# ").strip() for line in text.splitlines() if line.strip()), path.name)
        items.append({
            "platform": "desktop-app",
            "source": "local-notes",
            "type": "VISIT",
            "externalId": str(path),
            "title": sanitize(title),
            "url": "",
            "author": "",
            "summary": sanitize(text, 240),
            "tags": infer_tags(path.name, text),
            "confidence": "MEDIUM",
            "dataLevel": "LOCAL_NOTE",
            "detectionReason": "page_visible_content",
            "matchedKeyword": sanitize(path.name, 120),
            "occurredAt": dto_local_datetime(),
            "rawEvidence": {"processName": "", "windowTitle": path.name, "domain": "", "visitCount": 0},
        })
    return items


def collect_bilibili(url: str, query: str, limit: int) -> list[dict[str, Any]]:
    if url:
        return collect_video_metadata("bilibili", url)
    if query:
        return run_bili_search(query, limit)
    return []


def collect_youtube(url: str, query: str) -> list[dict[str, Any]]:
    if url:
        return collect_video_metadata("youtube", url)
    if query:
        return [{
            "platform": "youtube",
            "source": "public-url",
            "type": "SEARCH",
            "externalId": "",
            "title": sanitize(query),
            "url": "",
            "author": "",
            "summary": "YouTube search is not implemented in this worker. Provide a public video URL or use an official/search API adapter.",
            "tags": infer_tags("youtube", query),
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
            "detectionReason": "url_domain",
            "matchedKeyword": sanitize(query, 120),
            "occurredAt": dto_local_datetime(),
            "rawEvidence": {"processName": "", "windowTitle": "", "domain": "youtube.com", "visitCount": 0},
        }]
    return []


def collect_baidu_search(query: str, url: str = "") -> list[dict[str, Any]]:
    safe_query = sanitize(query or "", 240)
    search_url = normalize_url(url)
    if not search_url and safe_query:
        search_url = "https://www.baidu.com/s?wd=" + urllib.parse.quote(safe_query)
    return [{
        "platform": "baidu_search",
        "source": "public-url",
        "eventType": "SEARCH",
        "type": "SEARCH",
        "externalId": "",
        "title": safe_query or "Baidu search",
        "url": search_url,
        "author": "",
        "contentSnippet": f"Baidu search query: {safe_query}" if safe_query else "Baidu search query",
        "summary": f"Baidu search query: {safe_query}" if safe_query else "Baidu search query",
        "tags": unique_values(["baidu", "search", *infer_tags("baidu", safe_query)]),
        "confidence": "MEDIUM",
        "dataLevel": "PUBLIC_URL",
        "detectionReason": "url_domain",
        "matchedKeyword": safe_query,
        "occurredAt": dto_local_datetime(),
        "rawMetadata": {"processName": "", "windowTitle": safe_query, "domain": "baidu.com", "visitCount": 0, "query": safe_query},
        "rawEvidence": {"processName": "", "windowTitle": safe_query, "domain": "baidu.com", "visitCount": 0, "query": safe_query},
    }]


def collect_xiaohongshu(url: str, query: str, limit: int) -> list[dict[str, Any]]:
    if url.startswith(("http://", "https://")):
        items = collect_browser_or_public_url(url, query, limit)
        domain = domain_from_url(url)
        for item in items:
            if domain.endswith("xiaohongshu.com") or domain.endswith("xhslink.com"):
                item["platform"] = "xiaohongshu"
            item["type"] = "VISIT"
            item["summary"] = (
                "Codex local worker accepted a public URL task. "
                "Only public page metadata and user-provided query text are returned."
            )
            item["tags"] = sorted(set([*item.get("tags", []), item["platform"], "codex-proxy", "page-visit"]))
        return items

    visible_items = collect_visible_apps(limit)
    xhs_items = [
        item for item in visible_items
        if "xiaohongshu" in " ".join(item.get("tags", [])).lower()
        or "小红书" in str(item.get("summary") or "")
        or "xiaohongshu" in str(item.get("summary") or "").lower()
    ]
    if xhs_items:
        for item in xhs_items:
            item["platform"] = "xiaohongshu"
            item["tags"] = sorted(set([*item.get("tags", []), "xiaohongshu", "codex-proxy", "visible-window"]))
        return xhs_items

    return [{
        "platform": "xiaohongshu",
        "source": "visible-window",
        "type": "SEARCH",
        "externalId": "",
        "title": sanitize(query or "Xiaohongshu local Codex proxy task"),
        "url": "",
        "author": "",
        "summary": (
            "No Xiaohongshu visible window or public note URL was found in this authorized local run. "
            "Open Xiaohongshu web/app or provide a public note URL, then run the worker again."
        ),
        "tags": ["xiaohongshu", "codex-proxy", "empty-signal"],
        "confidence": "LOW",
        "dataLevel": "APP_USAGE_SNAPSHOT",
        "detectionReason": "window_title",
        "matchedKeyword": sanitize(query or "xiaohongshu", 120),
        "occurredAt": dto_local_datetime(),
        "rawEvidence": {"processName": "", "windowTitle": "", "domain": "", "visitCount": 0},
    }]


def run_bili_search(query: str, limit: int) -> list[dict[str, Any]]:
    if not find_tool("bili"):
        raise RuntimeError("bili CLI was not found on PATH. Install Agent Reach/Bilibili CLI first.")
    completed = subprocess.run(
        ["bili", "search", query, "--type", "video", "-n", str(min(limit, 10)), "--json"],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=25,
        env=tool_env(),
    )
    try:
        decoded = json.loads(completed.stdout)
    except json.JSONDecodeError:
        return [{
            "platform": "bilibili",
            "source": "public-url",
            "type": "SEARCH",
            "externalId": "",
            "title": sanitize(query),
            "url": "",
            "author": "",
            "summary": sanitize(completed.stdout, 400),
            "tags": infer_tags("bilibili", query),
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
            "detectionReason": "url_domain",
            "matchedKeyword": sanitize(query, 120),
            "occurredAt": dto_local_datetime(),
            "rawEvidence": {"processName": "", "windowTitle": "", "domain": "bilibili.com", "visitCount": 0},
        }]

    rows = decoded.get("data") if isinstance(decoded, dict) else decoded
    if not isinstance(rows, list):
        rows = []
    items: list[dict[str, Any]] = []
    for row in rows[:limit]:
        if not isinstance(row, dict):
            continue
        bvid = sanitize(row.get("bvid") or row.get("id") or "")
        url = f"https://www.bilibili.com/video/{bvid}" if bvid else sanitize(row.get("url") or "")
        items.append({
            "platform": "bilibili",
            "source": "public-url",
            "type": "SEARCH",
            "externalId": bvid,
            "title": sanitize(row.get("title") or query),
            "url": url,
            "author": sanitize(row.get("author") or row.get("owner") or ""),
            "summary": sanitize(f"play={row.get('play') or ''}; duration={row.get('duration') or ''}", 240),
            "tags": infer_tags("bilibili", query, row.get("title")),
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
            "detectionReason": "url_domain",
            "matchedKeyword": sanitize(query or bvid, 120),
            "occurredAt": dto_local_datetime(),
            "rawEvidence": {"processName": "", "windowTitle": "", "domain": domain_from_url(url), "visitCount": 0},
        })
    return items


def collect_video_metadata(platform: str, url: str) -> list[dict[str, Any]]:
    if not find_tool("yt-dlp"):
        raise RuntimeError("yt-dlp was not found on PATH. Install it first or use a manual result callback.")
    completed = subprocess.run(
        ["yt-dlp", "--dump-json", "--skip-download", url],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=40,
        env=tool_env(),
    )
    data = json.loads(completed.stdout)
    webpage_url = normalize_url(data.get("webpage_url") or url)
    video_id = sanitize(data.get("id") or external_id_from_url(platform, webpage_url), 160)
    title = sanitize(data.get("title") or "", 300)
    description = sanitize(data.get("description") or "", 1200)
    author = sanitize(data.get("uploader") or data.get("channel") or "", 160)
    if platform == "youtube":
        tags = infer_content_tags(title, description)
        summary = summarize_public_description(description, title)
        interest_category = infer_interest_category(title, description)
        raw_evidence = {
            "processName": "",
            "windowTitle": "",
            "domain": domain_from_url(webpage_url),
            "visitCount": 0,
            "videoId": video_id,
            "externalId": video_id,
            "url": webpage_url,
            "description": description,
            "contentType": "video",
            "interestCategory": interest_category,
        }
        return [{
            "platform": "youtube",
            "source": "public-url",
            "eventType": "WATCH",
            "type": "WATCH",
            "externalId": video_id,
            "title": title,
            "url": webpage_url,
            "author": author,
            "description": description,
            "contentSnippet": summary,
            "summary": summary,
            "tags": tags,
            "interestTags": tags,
            "contentType": "video",
            "interestCategory": interest_category,
            "contentCategory": interest_category,
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
            "detectionReason": "url_domain",
            "matchedKeyword": video_id or "youtube.com",
            "occurredAt": dto_local_datetime(),
            "rawMetadata": raw_evidence,
            "rawEvidence": raw_evidence,
        }]
    return [{
        "platform": platform,
        "source": "public-url",
        "type": "WATCH",
        "externalId": video_id,
        "title": title,
        "url": webpage_url,
        "author": author,
        "summary": sanitize(description, 420),
        "tags": infer_tags(platform, title, description),
        "confidence": "MEDIUM",
        "dataLevel": "PUBLIC_URL",
        "detectionReason": "url_domain",
        "matchedKeyword": sanitize(video_id or platform, 120),
        "occurredAt": dto_local_datetime(),
        "rawEvidence": {"processName": "", "windowTitle": "", "domain": domain_from_url(webpage_url), "visitCount": 0},
    }]


def summarize_public_description(description: str, title: str) -> str:
    text = sanitize(description or title, SUMMARY_TEXT_LIMIT)
    if not text:
        return "Public YouTube video metadata was fetched for interest analysis."
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    return "\n".join(lines[:5])[:SUMMARY_TEXT_LIMIT]


def infer_content_tags(title: str, description: str) -> list[str]:
    text = f"{title} {description}".lower()
    rules = [
        ("spring boot", ["spring boot", "spring"]),
        ("java", ["java", "jvm"]),
        ("backend", ["backend", "redis", "mysql", "database", "api", "server"]),
        ("ai", [" ai ", "llm", "agent", "chatgpt", "codex", "machine learning"]),
        ("tutorial", ["tutorial", "course", "lesson", "guide", "how to"]),
        ("music", ["music", "song", "live", "album"]),
        ("gaming", ["game", "gaming", "playthrough"]),
    ]
    tags = [tag for tag, needles in rules if any(needle in f" {text} " for needle in needles)]
    return unique_values(tags)[:5] or ["education"]


def infer_interest_category(title: str, description: str) -> str:
    text = f"{title} {description}".lower()
    if any(word in text for word in ["spring", "java", "redis", "mysql", "backend", "api", "database"]):
        return "backend"
    if any(word in text for word in ["ai", "llm", "agent", "chatgpt", "codex", "machine learning"]):
        return "AI"
    if any(word in text for word in ["tutorial", "course", "lesson", "lecture", "education"]):
        return "education"
    if any(word in text for word in ["music", "movie", "game", "comedy", "entertainment"]):
        return "entertainment"
    return "other"


def collect_browser_or_public_url(url: str, query: str, limit: int) -> list[dict[str, Any]]:
    if url.startswith(("http://", "https://")):
        domain = domain_from_url(url)
        return [{
            "platform": normalize_platform("web", {"url": url, "title": query}),
            "source": "public-url",
            "type": "VISIT",
            "externalId": "",
            "title": sanitize(query or url),
            "url": sanitize(url),
            "author": "",
            "summary": "Public URL task accepted. The backend PublicUrlMetadataCollector can extract title/description/keywords.",
            "tags": infer_tags("web", url, query),
            "confidence": "MEDIUM",
            "dataLevel": "PUBLIC_URL",
            "detectionReason": "url_domain",
            "matchedKeyword": sanitize(query or url, 120),
            "occurredAt": dto_local_datetime(),
            "rawEvidence": {"processName": "", "windowTitle": "", "domain": domain, "visitCount": 0},
        }]

    history_path = Path("data/imports/browser_history_sample.json")
    if not history_path.exists():
        return []
    rows = json.loads(history_path.read_text(encoding="utf-8"))
    if not isinstance(rows, list):
        return []
    return [{
        "platform": normalize_platform("browser", {"url": row.get("url") or "", "title": row.get("title") or ""}),
        "source": "browser-history",
        "type": "VISIT",
        "externalId": "",
        "title": sanitize(row.get("title") or ""),
        "url": sanitize(row.get("url") or ""),
        "author": "",
        "summary": f"visitCount={row.get('visitCount') or 0}",
        "tags": infer_tags(row.get("title"), row.get("url")),
        "confidence": "MEDIUM",
        "dataLevel": "BROWSER_HISTORY",
        "detectionReason": "browser_history",
        "matchedKeyword": sanitize(row.get("url") or row.get("title") or "", 120),
        "occurredAt": dto_local_datetime(row.get("visitTime")),
        "rawEvidence": {
            "processName": "",
            "windowTitle": sanitize(row.get("title") or "", 240),
            "domain": domain_from_url(str(row.get("url") or "")),
            "visitCount": row.get("visitCount") or 0,
        },
    } for row in rows[:limit] if isinstance(row, dict)]


def run_once(cfg: WorkerConfig) -> int:
    task = claim_next(cfg.base_url)
    if not task:
        print("No pending agent query.")
        return 0

    task_id = str(task.get("taskId") or "")
    if not task_id:
        print("Claimed task has no taskId.", file=sys.stderr)
        return 2

    if cfg.verbose:
        print_task(task)

    if not confirm(task, cfg):
        if not cfg.dry_run:
            complete_task(cfg.base_url, task_id, {
                "success": False,
                "errorMessage": "User denied local worker permission.",
            })
        print("Permission denied. Task marked failed.")
        return 1

    try:
        collected_items = collect_task(task, cfg)
        verbose_json(cfg.verbose, "Collected raw items", collected_items)
        raw_items = enrich_public_url_items(collected_items)
        verbose_json(cfg.verbose, "Agent Reach enriched items", raw_items)
        raw_items = enrich_content_for_llm(raw_items, verbose=cfg.verbose)
        verbose_json(cfg.verbose, "Content enriched items", raw_items)
        llm_gateway = WorkerLLMGateway(
            command=cfg.codex_command,
            timeout=cfg.codex_timeout,
            env_provider=tool_env,
            sanitizer=sanitize,
        )
        llm_gateway.submit_events(raw_items)
        analyzed_items = raw_items
        result = build_result(analyzed_items)
        verbose_json(cfg.verbose, "behavior_event JSON", {"events": result["items"]})
        if cfg.direct_behavior_batch:
            result["ingest"] = False
            result.setdefault("metadata", {})["directBehaviorBatch"] = True
        if cfg.dry_run:
            if cfg.direct_behavior_batch:
                print(json.dumps({"events": result["items"]}, ensure_ascii=False, indent=2))
            print(json.dumps(result, ensure_ascii=False, indent=2))
            llm_gateway.wait()
            llm_gateway.shutdown()
            return 0
        if cfg.direct_behavior_batch:
            result.setdefault("metadata", {})["behaviorBatchResponse"] = post_behavior_batch(
                cfg.base_url, analyzed_items, verbose=cfg.verbose)
        response = complete_task(cfg.base_url, task_id, result)
        verbose_json(cfg.verbose, "Task completion response", response)
        print(json.dumps(response, ensure_ascii=False, indent=2))
        llm_gateway.wait()
        llm_gateway.shutdown()
        return 0
    except Exception as exc:
        payload = {"success": False, "errorMessage": sanitize(str(exc), 1000)}
        if not cfg.dry_run:
            try:
                complete_task(cfg.base_url, task_id, payload)
            except Exception as callback_exc:
                print(f"Failed to post failure callback: {callback_exc}", file=sys.stderr)
        print(json.dumps(payload, ensure_ascii=False, indent=2), file=sys.stderr)
        return 2


def parse_args() -> WorkerConfig:
    parser = argparse.ArgumentParser(description="Claim and execute authorized Agent/Codex query tasks.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Spring Boot backend base URL.")
    parser.add_argument("--once", action="store_true", help="Claim one task and exit.")
    parser.add_argument("--dry-run", action="store_true", help="Print result instead of posting completion.")
    parser.add_argument("--yes", action="store_true", help="Skip interactive confirmation. Use only in trusted local runs.")
    parser.add_argument("--allowed-dir", action="append", default=[], help="Allowed local directory. Can be repeated.")
    parser.add_argument("--limit", type=int, default=20, help="Maximum items returned by a collector.")
    parser.add_argument("--poll-seconds", type=float, default=10.0, help="Polling interval when running continuously.")
    parser.add_argument("--use-codex-cli", action="store_true",
                        help="Compatibility flag. Real-time Codex/LLM analysis is triggered automatically per event.")
    parser.add_argument("--codex-command", default="codex", help="Codex CLI command. Defaults to 'codex'.")
    parser.add_argument("--print-codex-prompt", action="store_true", help="Print the prompt sent to Codex CLI.")
    parser.add_argument("--print-codex-output", action="store_true", help="Print raw Codex CLI output.")
    parser.add_argument("--codex-timeout", type=int, default=60, help="Seconds to wait for Codex CLI analysis.")
    parser.add_argument("--direct-behavior-batch", action="store_true",
                        help="POST normalized items directly to /api/v1/behavior-events/batch, then complete the task without duplicate ingestion.")
    parser.add_argument("--verbose", action="store_true", help="Print task, Agent Reach, Codex CLI, behavior event, and backend POST traces.")
    args = parser.parse_args()
    return WorkerConfig(
        base_url=args.base_url,
        once=args.once,
        dry_run=args.dry_run,
        yes=args.yes,
        allowed_dirs=args.allowed_dir or DEFAULT_ALLOWED_DIRS,
        limit=max(1, min(args.limit, 100)),
        poll_seconds=max(1.0, args.poll_seconds),
        use_codex_cli=args.use_codex_cli,
        codex_command=args.codex_command,
        print_codex_prompt=args.print_codex_prompt,
        print_codex_output=args.print_codex_output,
        codex_timeout=max(1, args.codex_timeout),
        direct_behavior_batch=args.direct_behavior_batch,
        verbose=args.verbose,
    )


def main() -> int:
    cfg = parse_args()
    if cfg.once or cfg.dry_run:
        return run_once(cfg)

    while True:
        try:
            code = run_once(cfg)
            if code not in {0}:
                return code
            time.sleep(cfg.poll_seconds)
        except KeyboardInterrupt:
            print("\nStopped.")
            return 0


if __name__ == "__main__":
    raise SystemExit(main())
