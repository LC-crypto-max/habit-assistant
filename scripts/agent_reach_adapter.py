#!/usr/bin/env python3
"""Privacy-safe bridge from authorized public URLs to Agent Reach tools.

The bridge never reads or stores cookies, tokens, sessions, passwords, private
messages, contacts, or payment data. Authenticated browser state is only used
when the caller explicitly enables it; OpenCLI owns that state and this process
only receives sanitized public page output.
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.parse
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Callable


PLATFORM_DOMAINS: dict[str, tuple[str, ...]] = {
    "xiaohongshu": ("xiaohongshu.com", "xhslink.com"),
    "youtube": ("youtube.com", "youtu.be"),
    "bilibili": ("bilibili.com", "b23.tv"),
    "baidu": ("baidu.com",),
    "github": ("github.com",),
    "zhihu": ("zhihu.com",),
    "csdn": ("csdn.net",),
    "juejin": ("juejin.cn",),
}
SENSITIVE_QUERY_KEYS = {
    "access_token", "auth", "authorization", "code", "cookie", "key",
    "password", "session", "signature", "token", "xsec_token", "xsec_source",
}
TRACKING_QUERY_KEYS = {
    "fbclid", "feature", "from", "gclid", "share_source", "si", "source",
    "utm_campaign", "utm_content", "utm_medium", "utm_source", "utm_term",
}
MAX_TOOL_OUTPUT = 20_000
LOCAL_TOOL_DIRS = [Path.home() / ".local" / "bin"]

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except (AttributeError, OSError):
    pass


@dataclass(frozen=True)
class AgentReachSettings:
    mode: str = "auto"
    timeout: int = 30
    allow_authenticated_browser: bool = False

    def normalized_mode(self) -> str:
        value = (self.mode or "auto").strip().lower()
        return value if value in {"auto", "live", "off"} else "auto"


@dataclass(frozen=True)
class ToolResult:
    success: bool
    route: str
    backend: str
    output: str = ""
    error: str = ""


class AgentReachAdapter:
    """Route public URLs through the commands documented by Agent Reach."""

    def __init__(
            self,
            settings: AgentReachSettings | None = None,
            tool_resolver: Callable[[str], str | None] | None = None,
            process_runner: Callable[[list[str], int], subprocess.CompletedProcess[str]] | None = None):
        self.settings = settings or AgentReachSettings()
        self.tool_resolver = tool_resolver or resolve_tool
        self.process_runner = process_runner or run_process
        self._doctor: dict[str, Any] | None = None

    def diagnose(self) -> dict[str, Any]:
        agent_reach = self.tool_resolver("agent-reach")
        doctor_error = ""
        doctor: dict[str, Any] = {}
        if agent_reach:
            # Current Agent Reach releases do not expose a --json flag here.
            completed = self.process_runner([agent_reach, "doctor"], self.settings.timeout)
            if completed.returncode == 0:
                doctor = decode_json_object(completed.stdout)
            else:
                doctor_error = sanitize_text(completed.stderr or "Agent Reach doctor failed", 300)
        else:
            doctor_error = "agent-reach command is not installed; direct channel tools will be detected"
        self._doctor = doctor
        return {
            "agentReach": bool(agent_reach),
            "doctor": doctor,
            "doctorError": doctor_error,
            "tools": {
                name: bool(self.tool_resolver(name))
                for name in ("opencli", "bili", "yt-dlp", "xhs", "curl")
            },
            "authenticatedBrowserAllowed": self.settings.allow_authenticated_browser,
        }

    def enrich(self, item: dict[str, Any]) -> dict[str, Any]:
        original_url = str(item.get("url") or "").strip()
        if self.settings.normalized_mode() == "off" or not is_safe_public_url(original_url):
            return dict(item)

        platform = platform_for_url(original_url, str(item.get("platform") or "web"))
        search_hint = public_search_hint(item) if platform == "xiaohongshu" else ""
        result = self._read(platform, original_url, search_hint)
        if result.success:
            return build_live_enrichment(item, platform, result)
        return build_fallback(item, platform, result)

    def _read(self, platform: str, original_url: str, search_hint: str = "") -> ToolResult:
        if platform == "xiaohongshu":
            return self._read_xiaohongshu(original_url, search_hint)
        if platform == "bilibili":
            return self._read_bilibili(original_url)
        if platform == "youtube":
            return self._read_youtube(original_url)
        return self._read_web(original_url)

    def _read_xiaohongshu(self, original_url: str, search_hint: str = "") -> ToolResult:
        if not self.settings.allow_authenticated_browser:
            return ToolResult(False, "xiaohongshu-note", "OpenCLI",
                              error="authenticated browser access was not explicitly enabled")

        backend = active_backend(self._doctor_data(), "xiaohongshu").lower()
        opencli = self.tool_resolver("opencli")
        if opencli and (not backend or "opencli" in backend):
            visible_result = self._read_current_xiaohongshu_tab(opencli, original_url)
            if visible_result.success:
                return visible_result

            # An unsigned canonical URL is paired with the signed page already
            # opened by Vue. Running `xiaohongshu note` on that unsigned URL
            # makes OpenCLI launch extra search tabs and can detach the browser
            # debugger. Only use the platform note command when its required
            # signing parameter was explicitly present in a non-Vue caller.
            if has_query_parameter(original_url, "xsec_token"):
                note_result = self._execute(
                    [opencli, "xiaohongshu", "note", original_url, "-f", "json"],
                    "xiaohongshu-note", "OpenCLI")
                if note_result.success or not search_hint:
                    return note_result
            else:
                # The browser-local handoff is the only safe source of the
                # signing credential. Do not launch title searches after a
                # failed handoff: OpenCLI search controls the same Chrome and
                # can create multiple tabs or detach the active debugger.
                return visible_result

            # Signed Xiaohongshu URLs contain xsec_token. The web UI deliberately
            # removes it before the task reaches the backend. Recover only public
            # metadata by searching the public title, then require the submitted
            # note id to be present in the already-sanitized OpenCLI output.
            search_result = self._execute(
                [opencli, "xiaohongshu", "search", search_hint, "--limit", "5", "-f", "json"],
                "xiaohongshu-search-fallback", "OpenCLI")
            note_id = external_id_for("xiaohongshu", original_url)
            matched_output = select_xiaohongshu_search_result(search_result.output, note_id)
            if search_result.success and matched_output:
                return ToolResult(
                    True, search_result.route, search_result.backend, output=matched_output)
            if search_result.success:
                return ToolResult(
                    False, "xiaohongshu-search-fallback", "OpenCLI",
                    error="public title search did not match the submitted Xiaohongshu note id")
            return search_result

        xhs = self.tool_resolver("xhs")
        if xhs and (not backend or "xhs" in backend):
            xhs_result = self._execute(
                [xhs, "read", original_url], "xiaohongshu-note", "xhs-cli")
            if xhs_result.success:
                return xhs_result

            # xhs-cli can fail when its locally managed cookies are stale.
            # If the user explicitly allowed authenticated-browser access and
            # already opened the submitted note, reuse only that visible tab.
            # This keeps the fallback local and never exports browser secrets.
            if opencli:
                visible_result = self._read_current_xiaohongshu_tab(opencli, original_url)
                if visible_result.success:
                    return visible_result
            if not opencli:
                return ToolResult(
                    False, "xiaohongshu-visible-tab", "unavailable",
                    error=(
                        "OpenCLI is not installed, so the authorized visible Chrome tab cannot be read; "
                        "xhs-cli authentication is also unavailable"
                    ))
            return xhs_result

        return ToolResult(False, "xiaohongshu-note", backend or "unavailable",
                          error="no supported Xiaohongshu Agent Reach backend is available")

    def _read_current_xiaohongshu_tab(self, opencli: str, original_url: str) -> ToolResult:
        session = "habit-assistant-xhs"
        bind_result = self._execute(
            [opencli, "browser", session, "bind"],
            "xiaohongshu-visible-tab-bind", "OpenCLI")
        if not bind_result.success:
            return bind_result
        try:
            state_result = self._execute(
                [opencli, "browser", session, "state"],
                "xiaohongshu-visible-tab-state", "OpenCLI")
            note_id = external_id_for("xiaohongshu", original_url)
            if not state_result.success or not note_id or note_id.lower() not in state_result.output.lower():
                return ToolResult(
                    False, "xiaohongshu-visible-tab", "OpenCLI",
                    error="the active Chrome tab does not match the submitted Xiaohongshu note id")
            # Xiaohongshu has no semantic <main>. Project only the note fields
            # instead of dumping the home feed, comments, and unrelated links.
            projection = (
                "(()=>{const text=s=>{for(const e of document.querySelectorAll(s)){"
                "const v=(e.innerText||e.textContent||'').trim();if(v)return v.slice(0,4000)}return ''};"
                "return {title:document.title.replace(/\\s*-\\s*小红书\\s*$/,''),"
                "author:text('.author-container .name,.note-detail-mask .user-name,.user-name,.username'),"
                "summary:text('#detail-desc,.note-content .desc,.note-detail-mask .desc,.note-content,.desc'),"
                "url:location.origin+location.pathname}})()"
            )
            return self._execute(
                [opencli, "browser", session, "eval", projection],
                "xiaohongshu-visible-tab", "OpenCLI")
        finally:
            try:
                self.process_runner([opencli, "browser", session, "unbind"], self.settings.timeout)
            except (OSError, subprocess.SubprocessError):
                pass

    def _read_bilibili(self, original_url: str) -> ToolResult:
        bili = self.tool_resolver("bili")
        external_id = external_id_for(platform="bilibili", url=original_url)
        if not bili or not external_id:
            return ToolResult(False, "bilibili-video", "bili-cli",
                              error="bili-cli or a valid BV id is unavailable")
        return self._execute([bili, "video", external_id], "bilibili-video", "bili-cli")

    def _read_youtube(self, original_url: str) -> ToolResult:
        ytdlp = self.tool_resolver("yt-dlp")
        if not ytdlp:
            return ToolResult(False, "youtube-metadata", "yt-dlp", error="yt-dlp is unavailable")
        return self._execute(
            [ytdlp, "--dump-single-json", "--skip-download", "--no-warnings",
             "--socket-timeout", "10", "--retries", "1", "--extractor-retries", "1", original_url],
            "youtube-metadata", "yt-dlp")

    def _read_web(self, original_url: str) -> ToolResult:
        curl = self.tool_resolver("curl") or self.tool_resolver("curl.exe")
        if not curl:
            return ToolResult(False, "web-reader", "Jina Reader", error="curl is unavailable")
        reader_url = "https://r.jina.ai/" + original_url
        return self._execute(
            [curl, "-fsSL", "--max-time", str(self.settings.timeout), reader_url],
            "web-reader", "Jina Reader")

    def _execute(self, args: list[str], route: str, backend: str) -> ToolResult:
        print(f"[AgentReach] route={route} backend={backend}")
        try:
            completed = self.process_runner(args, self.settings.timeout)
        except subprocess.TimeoutExpired:
            return ToolResult(False, route, backend,
                              error=f"{backend} timed out after {self.settings.timeout} seconds")
        except (OSError, subprocess.SubprocessError) as exc:
            return ToolResult(False, route, backend, error=sanitize_text(exc, 300))
        if completed.returncode != 0:
            return ToolResult(False, route, backend,
                              error=sanitize_text(completed.stderr or f"exit code {completed.returncode}", 300))
        output = sanitize_tool_output(completed.stdout)
        if not output:
            return ToolResult(False, route, backend, error="tool returned no public content")
        return ToolResult(True, route, backend, output=output)

    def _doctor_data(self) -> dict[str, Any]:
        if self._doctor is None:
            self.diagnose()
        return self._doctor or {}


def enrich_public_url(
        item: dict[str, Any],
        settings: AgentReachSettings | None = None,
        adapter: AgentReachAdapter | None = None) -> dict[str, Any]:
    return (adapter or AgentReachAdapter(settings)).enrich(item)


def resolve_tool(name: str) -> str | None:
    return shutil.which(name, path=tool_env().get("PATH"))


def run_process(args: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
    executable = args[0]
    suffix = os.path.splitext(executable)[1].lower()
    command = args
    if os.name == "nt" and suffix in {".bat", ".cmd"}:
        npm_command = resolve_windows_npm_shim(args)
        if npm_command:
            command = npm_command
        else:
            command_line = subprocess.list2cmdline(args)
            command = [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/s", "/c", command_line]
    completed = subprocess.run(
        command,
        capture_output=True,
        text=False,
        timeout=max(1, timeout),
        shell=False,
        env=tool_env(),
    )
    return subprocess.CompletedProcess(
        completed.args,
        completed.returncode,
        decode_process_bytes(completed.stdout),
        decode_process_bytes(completed.stderr),
    )


def resolve_windows_npm_shim(args: list[str]) -> list[str] | None:
    shim = Path(args[0])
    if shim.suffix.lower() != ".cmd" or not shim.is_file():
        return None
    try:
        content = shim.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    matches = re.findall(r'"%dp0%\\([^"\r\n]+?\.js)"', content, flags=re.IGNORECASE)
    if not matches:
        return None
    entry = shim.parent.joinpath(*matches[-1].replace("/", "\\").split("\\")).resolve()
    if not entry.is_file():
        return None
    bundled_node = shim.parent / "node.exe"
    node = str(bundled_node) if bundled_node.is_file() else shutil.which("node", path=tool_env().get("PATH"))
    if not node:
        return None
    return [node, str(entry), *args[1:]]


def decode_process_bytes(value: bytes | str | None) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    try:
        return value.decode("utf-8")
    except UnicodeDecodeError:
        # bilibili-cli emits the active Windows code-page even when the parent
        # requests UTF-8. GB18030 covers GBK without damaging normal ASCII.
        return value.decode("gb18030", errors="replace")


def tool_env() -> dict[str, str]:
    env = os.environ.copy()
    existing_path = env.get("PATH", "")
    extras = [str(path) for path in LOCAL_TOOL_DIRS if path.exists()]
    if extras:
        env["PATH"] = os.pathsep.join([*extras, existing_path])
    env.setdefault("PYTHONIOENCODING", "utf-8")
    return env


def sanitize_text(value: Any, limit: int = 500) -> str:
    text = "" if value is None else str(value)
    text = re.sub(
        r"(?i)(authorization\s*:\s*bearer\s+|bearer\s+|(?:cookie|token|session|password|secret)\s*[=:]\s*)[^\s,;&]+",
        r"\1[REDACTED]", text)
    text = re.sub(r"(?i)(xsec_token\s*[=:]\s*)[^\s,;&]+", r"\1[REDACTED]", text)
    text = "".join(ch for ch in text if ch >= " " or ch in "\r\n\t").strip()
    return text[:limit]


def sanitize_tool_output(value: Any) -> str:
    raw_text = "" if value is None else str(value).strip()
    try:
        decoded = json.loads(raw_text)
    except (json.JSONDecodeError, TypeError):
        decoded = None
    if decoded is not None:
        safe_json = sanitize_public_json(decoded)
        return json.dumps(safe_json, ensure_ascii=False)[:MAX_TOOL_OUTPUT]

    text = sanitize_text(raw_text, MAX_TOOL_OUTPUT)
    # OpenCLI browser state can contain the active page URL. Keep the public
    # note id visible for matching, but remove signed query parameters entirely
    # before the state or extracted text can reach Codex, logs, or persistence.
    text = re.sub(
        r"(?i)([?&])(?:xsec_token|xsec_source|token|session|authorization)=\[REDACTED\](?:&)?",
        lambda match: "?" if match.group(1) == "?" else "",
        text,
    )
    text = text.replace("?\n", "\n").replace("? ", " ").rstrip("?")
    safe_lines: list[str] = []
    for line in text.splitlines():
        lowered = line.lower()
        if any(re.search(rf"^\s*[\"']?{re.escape(key)}[\"']?\s*[:=]", lowered)
               for key in SENSITIVE_QUERY_KEYS):
            continue
        safe_lines.append(line)
    return "\n".join(safe_lines).strip()[:MAX_TOOL_OUTPUT]


def sanitize_public_json(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            str(key): sanitize_public_json(child)
            for key, child in list(value.items())[:100]
            if str(key).lower() not in SENSITIVE_QUERY_KEYS
        }
    if isinstance(value, list):
        return [sanitize_public_json(child) for child in value[:50]]
    if isinstance(value, str):
        if value.lower().startswith(("http://", "https://")):
            return safe_public_url(value)
        return sanitize_text(value, 1200)
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return sanitize_text(value, 300)


def safe_public_url(url: str) -> str:
    if not is_safe_public_url(url):
        return ""
    parsed = urllib.parse.urlsplit(url)
    safe_query = []
    for key, value in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True):
        if key.lower() in SENSITIVE_QUERY_KEYS or key.lower() in TRACKING_QUERY_KEYS:
            continue
        safe_query.append((key, value))
    return urllib.parse.urlunsplit((parsed.scheme.lower(), parsed.netloc, parsed.path,
                                    urllib.parse.urlencode(safe_query), ""))


def has_query_parameter(url: str, expected_key: str) -> bool:
    return any(key.lower() == expected_key.lower()
               for key, _value in urllib.parse.parse_qsl(
                   urllib.parse.urlsplit(url).query, keep_blank_values=True))


def is_safe_public_url(url: str) -> bool:
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme.lower() not in {"http", "https"} or not parsed.hostname:
            return False
        if parsed.username or parsed.password:
            return False
        host = parsed.hostname.lower()
        if host in {"localhost", "localhost.localdomain"} or host.endswith(".local"):
            return False
        try:
            address = ipaddress.ip_address(host)
            return not (address.is_private or address.is_loopback or address.is_link_local
                        or address.is_reserved or address.is_multicast)
        except ValueError:
            return True
    except ValueError:
        return False


def domain_from_url(url: str) -> str:
    try:
        return (urllib.parse.urlsplit(url).hostname or "").lower().removeprefix("www.")
    except ValueError:
        return ""


def platform_for_url(url: str, fallback: str = "web") -> str:
    domain = domain_from_url(url)
    for platform, domains in PLATFORM_DOMAINS.items():
        if any(domain == item or domain.endswith("." + item) for item in domains):
            return platform
    fallback_platform = sanitize_text(fallback or "web", 80).lower().replace("-", "_") or "web"
    return {"baidu_search": "baidu", "generic_web": "web", "browser": "web"}.get(
        fallback_platform, fallback_platform)


def content_type_for_platform(platform: str) -> str:
    if platform in {"youtube", "bilibili"}:
        return "video"
    if platform == "xiaohongshu":
        return "note"
    if platform == "github":
        return "repository-or-code-page"
    if platform in {"zhihu", "csdn", "juejin"}:
        return "article"
    if platform == "baidu":
        return "search-result"
    return "web-page"


def build_live_enrichment(item: dict[str, Any], platform: str, tool_result: ToolResult) -> dict[str, Any]:
    public = extract_public_fields(tool_result.output)
    url = safe_public_url(str(item.get("url") or ""))
    title = sanitize_text(public.get("title") or item.get("title") or url, 300)
    summary = sanitize_text(public.get("summary") or title, 1200)
    author = sanitize_text(public.get("author") or item.get("author") or "", 160)
    content_type = content_type_for_platform(platform)
    original_raw = safe_raw_metadata(item)
    tags = semantic_tags(title, summary, platform)
    occurred_at = sanitize_text(item.get("occurredAt") or datetime.now().replace(microsecond=0).isoformat(), 40)
    raw = {
        "browser": sanitize_text(original_raw.get("browser") or "", 80),
        "processName": sanitize_text(original_raw.get("processName") or "", 120),
        "windowTitle": sanitize_text(original_raw.get("windowTitle") or title, 240),
        "domain": domain_from_url(url),
        "visitCount": safe_int(original_raw.get("visitCount") or item.get("visitCount")),
        "adapter": "agent-reach",
        "adapterMode": "live",
        "agentReachStatus": "SUCCESS",
        "agentReachRoute": tool_result.route,
        "agentReachBackend": tool_result.backend,
        "originalSource": sanitize_text(item.get("source") or "", 120),
        "contentType": content_type,
    }
    event_type = "WATCH" if content_type == "video" else "VISIT"
    search_fallback = tool_result.route == "xiaohongshu-search-fallback"
    result = {
        "userId": sanitize_text(item.get("userId") or "", 120),
        "platform": platform,
        "source": "agent-reach-enrichment",
        "eventType": event_type,
        "type": event_type,
        "externalId": sanitize_text(item.get("externalId") or external_id_for(platform, url), 160),
        "title": title,
        "url": url,
        "author": author,
        "contentSnippet": summary,
        "summary": summary,
        "tags": tags,
        "contentType": content_type,
        "contentCategory": content_type,
        "intent": "public-url-enrichment",
        "summaryForProfile": summary,
        "recommendationHints": [],
        "confidence": "MEDIUM" if search_fallback else "HIGH",
        "dataLevel": "PAGE_VISIBLE_CONTENT",
        "detectionReason": "public_search_enrichment" if search_fallback else "public_url_enrichment",
        "matchedKeyword": domain_from_url(url),
        "occurredAt": occurred_at,
        "rawEvidence": raw,
        "rawMetadata": dict(raw),
    }
    print(f"[AgentReach] status=SUCCESS route={tool_result.route} platform={platform}")
    return result


def build_fallback(item: dict[str, Any], platform: str, tool_result: ToolResult) -> dict[str, Any]:
    result = dict(item)
    result["platform"] = platform
    result["url"] = safe_public_url(str(item.get("url") or ""))
    raw = safe_raw_metadata(item)
    raw.update({
        "adapter": "agent-reach",
        "adapterMode": "fallback",
        "agentReachStatus": "UNAVAILABLE" if "unavailable" in tool_result.error.lower() else "FAILED",
        "agentReachRoute": tool_result.route,
        "agentReachBackend": tool_result.backend,
        "agentReachError": sanitize_text(tool_result.error, 240),
    })
    result["rawEvidence"] = raw
    result["rawMetadata"] = dict(raw)
    print(f"[AgentReach] status={raw['agentReachStatus']} route={tool_result.route} platform={platform}")
    return result


def safe_raw_metadata(item: dict[str, Any]) -> dict[str, Any]:
    value = item.get("rawEvidence")
    if not isinstance(value, dict):
        value = item.get("rawMetadata")
    if not isinstance(value, dict):
        return {}
    allowed = {"browser", "processName", "windowTitle", "domain", "visitCount", "originalSource"}
    safe: dict[str, Any] = {}
    for key in allowed:
        if key not in value:
            continue
        safe[key] = safe_int(value[key]) if key == "visitCount" else sanitize_text(value[key], 240)
    return safe


def public_search_hint(item: dict[str, Any]) -> str:
    candidates = [
        item.get("title"),
        item.get("matchedKeyword"),
        (item.get("rawEvidence") or {}).get("windowTitle")
        if isinstance(item.get("rawEvidence"), dict) else "",
    ]
    for value in candidates:
        text = sanitize_text(value, 160)
        if not text or text.lower().startswith(("http://", "https://")):
            continue
        text = re.sub(r"\s*[|｜]\s*小红书.*$", "", text, flags=re.IGNORECASE).strip()
        text = re.sub(r"[&|<>^%!\"`]", " ", text)
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) >= 4:
            return text[:120]
    return ""


def select_xiaohongshu_search_result(output: str, note_id: str) -> str:
    if not output or not note_id:
        return ""
    try:
        decoded = json.loads(output)
    except json.JSONDecodeError:
        return ""

    def find(value: Any) -> dict[str, Any] | None:
        if isinstance(value, dict):
            candidate_id = sanitize_text(
                value.get("id") or value.get("note_id") or value.get("noteId") or "", 160)
            candidate_url = str(value.get("url") or "")
            if candidate_id.lower() == note_id.lower() or note_id.lower() in candidate_url.lower():
                return value
            for child in value.values():
                found = find(child)
                if found:
                    return found
        elif isinstance(value, list):
            for child in value:
                found = find(child)
                if found:
                    return found
        return None

    matched = find(decoded)
    return json.dumps(matched, ensure_ascii=False) if matched else ""


def extract_public_fields(output: str) -> dict[str, str]:
    decoded = decode_json_object(output)
    if decoded:
        flattened = flatten_public_scalars(decoded)
        title = first_value(flattened, ("title", "name"))
        summary = first_value(flattened, ("description", "summary", "content", "text", "body"))
        author = first_value(flattened, ("author", "uploader", "channel", "creator", "nickname"))
        return {"title": title, "summary": summary, "author": author}

    fields: dict[str, str] = {}
    aliases = {
        "title": ("title", "name", "标题"),
        "summary": ("description", "summary", "content", "text", "desc", "正文", "描述"),
        "author": ("author", "uploader", "channel", "creator", "nickname", "作者", "用户"),
    }
    for target, keys in aliases.items():
        for key in keys:
            match = re.search(rf"(?im)^\s*{re.escape(key)}\s*:\s*[\"']?(.*?)[\"']?\s*$", output)
            if match and match.group(1).strip():
                fields[target] = sanitize_text(match.group(1), 1200 if target == "summary" else 300)
                break
    if "author" not in fields:
        owner = re.search(
            r"(?ims)^\s*(?:owner|uploader|channel)\s*:\s*$.*?^\s*name\s*:\s*[\"']?(.*?)[\"']?\s*$",
            output,
        )
        if owner and owner.group(1).strip():
            fields["author"] = sanitize_text(owner.group(1), 160)
    if "summary" not in fields:
        if fields.get("title"):
            fields["summary"] = fields["title"]
        else:
            lines = [line.strip(" #-\t") for line in output.splitlines() if line.strip()]
            fields["summary"] = sanitize_text(" ".join(lines[:8]), 1200)
    return fields


def decode_json_object(value: str) -> dict[str, Any]:
    text = (value or "").strip()
    if not text:
        return {}
    try:
        decoded = json.loads(text)
        return decoded if isinstance(decoded, dict) else {}
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        if start >= 0 and end > start:
            try:
                decoded = json.loads(text[start:end + 1])
                return decoded if isinstance(decoded, dict) else {}
            except json.JSONDecodeError:
                return {}
    return {}


def flatten_public_scalars(value: Any, prefix: str = "") -> dict[str, str]:
    result: dict[str, str] = {}
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = str(key).lower()
            if normalized in SENSITIVE_QUERY_KEYS:
                continue
            result.update(flatten_public_scalars(child, normalized))
    elif isinstance(value, list):
        for child in value[:5]:
            child_values = flatten_public_scalars(child, prefix)
            for key, text in child_values.items():
                result.setdefault(key, text)
    elif prefix and value is not None:
        result[prefix] = sanitize_text(value, 1200)
    return result


def first_value(values: dict[str, str], keys: tuple[str, ...]) -> str:
    for key in keys:
        if values.get(key):
            return values[key]
    return ""


def semantic_tags(title: str, summary: str, platform: str) -> list[str]:
    text = f"{title} {summary}".lower()
    tags: list[str] = []
    rules = [
        ("Java后端", ("java", "spring", "redis", "mysql", "jvm")),
        ("AI工具", (" ai ", "codex", "agent", "llm", "chatgpt")),
        ("生活方式", ("生活", "穿搭", "旅行", "美食", "lifestyle")),
        ("视频内容", ("video", "视频", "教程")),
    ]
    for tag, words in rules:
        if any(word in f" {text} " for word in words):
            tags.append(tag)
    if platform == "xiaohongshu" and "生活方式" not in tags:
        tags.append("生活方式")
    return tags


def active_backend(value: Any, platform: str) -> str:
    if isinstance(value, dict):
        for key, child in value.items():
            if str(key).lower() == platform.lower() and isinstance(child, dict):
                backend = child.get("active_backend") or child.get("activeBackend")
                if backend:
                    return str(backend)
            found = active_backend(child, platform)
            if found:
                return found
    elif isinstance(value, list):
        for child in value:
            found = active_backend(child, platform)
            if found:
                return found
    return ""


def external_id_for(platform: str, url: str) -> str:
    if platform == "bilibili":
        match = re.search(r"/(BV[0-9A-Za-z]+)", urllib.parse.urlsplit(url).path, re.IGNORECASE)
        return match.group(1) if match else ""
    if platform == "youtube":
        parsed = urllib.parse.urlsplit(url)
        if domain_from_url(url) == "youtu.be":
            return parsed.path.strip("/").split("/")[0]
        return urllib.parse.parse_qs(parsed.query).get("v", [""])[0]
    if platform == "xiaohongshu":
        parts = [part for part in urllib.parse.urlsplit(url).path.split("/") if part]
        return parts[-1] if parts else ""
    return ""


def safe_int(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the privacy-safe Agent Reach URL bridge.")
    parser.add_argument("--doctor", action="store_true", help="Print available Agent Reach backends and exit.")
    parser.add_argument("--url", default="", help="Authorized public URL to enrich.")
    parser.add_argument("--platform", default="web", help="Optional platform hint.")
    parser.add_argument("--title", default="", help="Optional browser-history title.")
    parser.add_argument("--mode", choices=("auto", "live", "off"), default="auto")
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--allow-authenticated-browser", action="store_true",
                        help="Allow OpenCLI to reuse an existing browser login. Credentials are never exported.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    settings = AgentReachSettings(args.mode, max(1, args.timeout), args.allow_authenticated_browser)
    adapter = AgentReachAdapter(settings)
    if args.doctor:
        print(json.dumps(adapter.diagnose(), ensure_ascii=False, indent=2))
        return 0
    if not args.url:
        raise SystemExit("--url is required unless --doctor is used")
    item = {
        "platform": args.platform,
        "source": "public-url",
        "title": args.title,
        "url": args.url,
        "dataLevel": "PUBLIC_URL",
        "confidence": "MEDIUM",
    }
    print(json.dumps(adapter.enrich(item), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
