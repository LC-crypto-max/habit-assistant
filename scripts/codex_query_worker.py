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
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_ALLOWED_DIRS = ["data/imports", "data/local-notes"]
SAFE_TEXT_LIMIT = 500
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


@dataclass
class WorkerConfig:
    base_url: str
    once: bool
    dry_run: bool
    yes: bool
    allowed_dirs: list[str]
    limit: int
    poll_seconds: float


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


def post_json(url: str, payload: dict[str, Any], timeout: int = 15) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json; charset=utf-8"},
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
    normalized = dict(item)
    normalized["occurredAt"] = dto_local_datetime(normalized.get("occurredAt"))
    return normalized


def collect_task(task: dict[str, Any], cfg: WorkerConfig) -> list[dict[str, Any]]:
    platform = str(task.get("platform") or "").lower()
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
    if platform in {"web", "browser"} or intent in {"read-page", "browser-history-summary"}:
        return collect_browser_or_public_url(str(url), str(query), cfg.limit)

    return [{
        "platform": sanitize(platform or "agent"),
        "type": "SEARCH",
        "externalId": "",
        "title": sanitize(query or url or "Agent query"),
        "url": sanitize(url),
        "author": "",
        "summary": "No local collector matched this platform/intent.",
        "tags": infer_tags(platform, intent, query, url),
        "occurredAt": dto_local_datetime(),
    }]


def collect_visible_apps(limit: int) -> list[dict[str, Any]]:
    if os.name != "nt":
        return [{
            "platform": "local-terminal",
            "type": "VISIT",
            "externalId": "",
            "title": "App usage summary unavailable",
            "url": "",
            "author": "",
            "summary": "Visible app collection currently supports Windows only.",
            "tags": ["Local app", "unsupported-os"],
            "occurredAt": dto_local_datetime(),
        }]

    command = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        (
            "Get-Process | "
            "Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle.Trim().Length -gt 0 } | "
            f"Select-Object -First {limit} Id,ProcessName,MainWindowTitle | "
            "ConvertTo-Json -Depth 3"
        ),
    ]
    completed = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=10,
        env=tool_env(),
    )
    raw = completed.stdout.strip()
    if not raw:
        return []
    decoded = json.loads(raw)
    rows = decoded if isinstance(decoded, list) else [decoded]
    items: list[dict[str, Any]] = []
    for row in rows[:limit]:
        process_name = sanitize(row.get("ProcessName") or "")
        window_title = sanitize(row.get("MainWindowTitle") or "")
        if not process_name or not window_title:
            continue
        items.append({
            "platform": "local-terminal",
            "type": "VISIT",
            "externalId": f"{process_name}-{row.get('Id') or ''}",
            "title": f"Visible app: {process_name}",
            "url": "",
            "author": "",
            "summary": (
                "Current visible-window snapshot only. "
                "It does not include exact daily duration or launch count. "
                f"Window title: {window_title}"
            ),
            "tags": infer_tags("app", process_name, window_title),
            "occurredAt": dto_local_datetime(),
        })
    return items


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
            "platform": "local-notes",
            "type": "VISIT",
            "externalId": str(path),
            "title": sanitize(title),
            "url": str(path),
            "author": "",
            "summary": sanitize(text, 240),
            "tags": infer_tags(path.name, text),
            "occurredAt": dto_local_datetime(),
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
            "type": "SEARCH",
            "externalId": "",
            "title": sanitize(query),
            "url": "",
            "author": "",
            "summary": "YouTube search is not implemented in this worker. Provide a public video URL or use an official/search API adapter.",
            "tags": infer_tags("youtube", query),
            "occurredAt": dto_local_datetime(),
        }]
    return []


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
            "type": "SEARCH",
            "externalId": "",
            "title": sanitize(query),
            "url": "",
            "author": "",
            "summary": sanitize(completed.stdout, 400),
            "tags": infer_tags("bilibili", query),
            "occurredAt": dto_local_datetime(),
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
            "type": "SEARCH",
            "externalId": bvid,
            "title": sanitize(row.get("title") or query),
            "url": url,
            "author": sanitize(row.get("author") or row.get("owner") or ""),
            "summary": sanitize(f"play={row.get('play') or ''}; duration={row.get('duration') or ''}", 240),
            "tags": infer_tags("bilibili", query, row.get("title")),
            "occurredAt": dto_local_datetime(),
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
    return [{
        "platform": platform,
        "type": "WATCH",
        "externalId": sanitize(data.get("id") or ""),
        "title": sanitize(data.get("title") or ""),
        "url": sanitize(data.get("webpage_url") or url),
        "author": sanitize(data.get("uploader") or data.get("channel") or ""),
        "summary": sanitize(data.get("description") or "", 420),
        "tags": infer_tags(platform, data.get("title"), data.get("description")),
        "occurredAt": dto_local_datetime(),
    }]


def collect_browser_or_public_url(url: str, query: str, limit: int) -> list[dict[str, Any]]:
    if url.startswith(("http://", "https://")):
        return [{
            "platform": "web",
            "type": "VISIT",
            "externalId": "",
            "title": sanitize(query or url),
            "url": sanitize(url),
            "author": "",
            "summary": "Public URL task accepted. The backend PublicUrlMetadataCollector can extract title/description/keywords.",
            "tags": infer_tags("web", url, query),
            "occurredAt": dto_local_datetime(),
        }]

    history_path = Path("data/imports/browser_history_sample.json")
    if not history_path.exists():
        return []
    rows = json.loads(history_path.read_text(encoding="utf-8"))
    if not isinstance(rows, list):
        return []
    return [{
        "platform": "browser",
        "type": "VISIT",
        "externalId": "",
        "title": sanitize(row.get("title") or ""),
        "url": sanitize(row.get("url") or ""),
        "author": "",
        "summary": f"visitCount={row.get('visitCount') or 0}",
        "tags": infer_tags(row.get("title"), row.get("url")),
        "occurredAt": dto_local_datetime(row.get("visitTime")),
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

    if not confirm(task, cfg):
        if not cfg.dry_run:
            complete_task(cfg.base_url, task_id, {
                "success": False,
                "errorMessage": "User denied local worker permission.",
            })
        print("Permission denied. Task marked failed.")
        return 1

    try:
        items = collect_task(task, cfg)
        result = build_result(items)
        if cfg.dry_run:
            print(json.dumps(result, ensure_ascii=False, indent=2))
            return 0
        response = complete_task(cfg.base_url, task_id, result)
        print(json.dumps(response, ensure_ascii=False, indent=2))
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
    args = parser.parse_args()
    return WorkerConfig(
        base_url=args.base_url,
        once=args.once,
        dry_run=args.dry_run,
        yes=args.yes,
        allowed_dirs=args.allowed_dir or DEFAULT_ALLOWED_DIRS,
        limit=max(1, min(args.limit, 100)),
        poll_seconds=max(1.0, args.poll_seconds),
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
