#!/usr/bin/env python3
"""
Local Codex-style app info proxy.

This script collects visible Windows application/process information and
converts it into the project's behavior event format. It can run once or expose
a small local HTTP proxy for a frontend/local client.

No third-party Python packages are required.
"""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_PROXY_PORT = 8765
DEFAULT_LIMIT = 80

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


@dataclass
class AppInfo:
    process_id: int
    process_name: str
    window_title: str
    platform: str
    tags: list[str]


class WindowInfo:
    def __init__(self, process_id: int, process_name: str, window_title: str):
        self.process_id = process_id
        self.process_name = process_name
        self.window_title = window_title


def now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def infer_platform(process_name: str, title: str) -> tuple[str, list[str]]:
    process = process_name.lower()
    title_text = title.lower()
    browser_rules = [
        ("browser-edge", ["msedge", "edge"], ["browser", "edge"]),
        ("browser-chrome", ["chrome"], ["browser", "chrome"]),
        ("browser", ["slbrowser", "sogouexplorer", "firefox"], ["browser"]),
    ]
    for platform, needles, tags in browser_rules:
        if any(needle in process for needle in needles):
            extra_tags = ["app-usage", *tags]
            if any(needle in title_text for needle in ["xiaohongshu", "小红书", "xhs"]):
                extra_tags.extend(["xiaohongshu", "lifestyle"])
            if any(needle in title_text for needle in ["bilibili", "哔哩", "b站"]):
                extra_tags.extend(["bilibili", "video"])
            if "youtube" in title_text:
                extra_tags.extend(["youtube", "video"])
            return platform, extra_tags

    rules = [
        ("xiaohongshu", ["xiaohongshu", "小红书", "xhs"], ["xiaohongshu", "lifestyle"]),
        ("bilibili", ["bilibili", "哔哩", "b站"], ["bilibili", "video"]),
        ("douyin", ["douyin", "抖音"], ["douyin", "short-video"]),
        ("wechat", ["wechat", "微信", "weixin"], ["wechat", "social"]),
        ("youtube", ["youtube"], ["youtube", "video"]),
    ]
    text = f"{process} {title_text}"
    for platform, needles, tags in rules:
        if any(needle in text for needle in needles):
            return platform, ["app-usage", *tags]
    return "desktop-app", ["app-usage", "desktop"]


def collect_windows_via_api(limit: int) -> list[WindowInfo]:
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32

    enum_windows = user32.EnumWindows
    enum_windows_proc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    is_window_visible = user32.IsWindowVisible
    get_window_text_length = user32.GetWindowTextLengthW
    get_window_text = user32.GetWindowTextW
    get_window_thread_process_id = user32.GetWindowThreadProcessId

    open_process = kernel32.OpenProcess
    query_full_process_image_name = kernel32.QueryFullProcessImageNameW
    close_handle = kernel32.CloseHandle
    PROCESS_QUERY_LIMITED_INFORMATION = 0x1000

    rows: list[WindowInfo] = []

    def process_name(pid: int) -> str:
        handle = open_process(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
        if not handle:
            return ""
        try:
            size = wintypes.DWORD(32768)
            buffer = ctypes.create_unicode_buffer(size.value)
            if query_full_process_image_name(handle, 0, buffer, ctypes.byref(size)):
                return os.path.splitext(os.path.basename(buffer.value))[0]
            return ""
        finally:
            close_handle(handle)

    def callback(hwnd: int, _lparam: int) -> bool:
        if len(rows) >= limit:
            return False
        if not is_window_visible(hwnd):
            return True
        length = get_window_text_length(hwnd)
        if length <= 0:
            return True
        buffer = ctypes.create_unicode_buffer(length + 1)
        get_window_text(hwnd, buffer, length + 1)
        title = buffer.value.strip()
        if not title:
            return True
        pid = wintypes.DWORD()
        get_window_thread_process_id(hwnd, ctypes.byref(pid))
        rows.append(WindowInfo(pid.value, process_name(pid.value), title))
        return True

    enum_windows(enum_windows_proc(callback), 0)
    return rows


def collect_apps(limit: int = DEFAULT_LIMIT) -> list[AppInfo]:
    if os.name != "nt":
        raise RuntimeError("This collector currently supports Windows only.")

    apps: list[AppInfo] = []
    for row in collect_windows_via_api(limit):
        process_name = row.process_name.strip()
        title = row.window_title.strip()
        if not process_name or not title:
            continue
        platform, tags = infer_platform(process_name, title)
        apps.append(
            AppInfo(
                process_id=row.process_id,
                process_name=process_name,
                window_title=title,
                platform=platform,
                tags=tags,
            )
        )
    return apps


def to_behavior_events(apps: list[AppInfo], user_id: str) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    occurred_at = now_iso()
    for app in apps:
        events.append(
            {
                "userId": user_id,
                "platform": app.platform,
                "source": "codex-app-proxy",
                "externalId": f"{app.process_name}-{app.process_id}",
                "type": "VISIT",
                "title": f"正在使用 {app.window_title}",
                "url": "",
                "summary": f"本地代理检测到窗口：{app.window_title}",
                "text": f"process={app.process_name} pid={app.process_id} title={app.window_title}",
                "occurredAt": occurred_at,
                "tags": app.tags,
            }
        )
    return events


def post_json(url: str, payload: dict[str, Any], timeout: int = 8) -> dict[str, Any]:
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
            return json.loads(response_body) if response_body else {}
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} from {url}: {error_body}") from exc


def print_payload(payload: dict[str, Any]) -> None:
    print("Final request payload:")
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def upload_apps(
    base_url: str,
    user_id: str,
    limit: int,
    dry_run: bool = False,
    print_payload_before_upload: bool = False,
) -> dict[str, Any]:
    apps = collect_apps(limit)
    events = to_behavior_events(apps, user_id)
    payload = {"events": events}
    if dry_run:
        return {"dryRun": True, "apps": [asdict(app) for app in apps], "payload": payload}
    if not events:
        return {"imported": 0, "skipped": 0, "messagesPublished": 0, "activities": []}
    if print_payload_before_upload:
        print_payload(payload)
    endpoint = base_url.rstrip("/") + "/api/v1/behavior-events/batch"
    return post_json(endpoint, payload)


def upload_events(base_url: str, events: list[dict[str, Any]], print_payload_before_upload: bool = False) -> dict[str, Any]:
    if not events:
        return {"imported": 0, "skipped": 0, "messagesPublished": 0, "activities": []}
    endpoint = base_url.rstrip("/") + "/api/v1/behavior-events/batch"
    payload = {"events": events}
    if print_payload_before_upload:
        print_payload(payload)
    return post_json(endpoint, payload)


def print_app_preview(apps: list[AppInfo]) -> None:
    if not apps:
        print("No visible application windows were collected.")
        return
    print("Visible application windows to upload:")
    for index, app in enumerate(apps, start=1):
        print(f"{index:>2}. [{app.platform}] {app.process_name} pid={app.process_id} title={app.window_title}")


def confirm_upload() -> bool:
    answer = input("Upload these app usage events to backend? [y/N] ").strip().lower()
    return answer in {"y", "yes"}


class ProxyHandler(BaseHTTPRequestHandler):
    base_url = DEFAULT_BASE_URL
    user_id = "me"
    limit = DEFAULT_LIMIT

    def do_GET(self) -> None:
        if self.path == "/health":
            self.send_json({"status": "UP", "service": "codex-app-proxy"})
            return
        if self.path.startswith("/apps"):
            apps = [asdict(app) for app in collect_apps(self.limit)]
            self.send_json({"apps": apps})
            return
        self.send_error(404, "Not Found")

    def do_POST(self) -> None:
        if self.path == "/collect/apps":
            body = self.read_json()
            user_id = str(body.get("userId") or self.user_id)
            limit = int(body.get("limit") or self.limit)
            dry_run = bool(body.get("dryRun") or False)
            self.send_json(upload_apps(self.base_url, user_id, limit, dry_run))
            return
        if self.path == "/proxy/behavior-events/batch":
            body = self.read_json()
            endpoint = self.base_url.rstrip("/") + "/api/v1/behavior-events/batch"
            self.send_json(post_json(endpoint, body))
            return
        self.send_error(404, "Not Found")

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        data = self.rfile.read(length).decode("utf-8", errors="replace")
        return json.loads(data) if data.strip() else {}

    def send_json(self, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("[%s] %s\n" % (time.strftime("%Y-%m-%d %H:%M:%S"), fmt % args))


def serve(base_url: str, user_id: str, host: str, port: int, limit: int) -> None:
    ProxyHandler.base_url = base_url
    ProxyHandler.user_id = user_id
    ProxyHandler.limit = limit
    server = ThreadingHTTPServer((host, port), ProxyHandler)
    print(f"codex-app-proxy listening on http://{host}:{port}")
    print(f"forwarding behavior events to {base_url.rstrip()}/api/v1/behavior-events/batch")
    server.serve_forever()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect local app usage and proxy it to Habit Assistant.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Spring Boot backend base URL.")
    parser.add_argument("--user-id", default="me", help="User id written into behavior events.")
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT, help="Maximum visible apps to inspect.")
    parser.add_argument("--once", action="store_true", help="Collect once and upload to backend.")
    parser.add_argument("--dry-run", action="store_true", help="Print collected payload without uploading.")
    parser.add_argument("--print-payload", action="store_true", help="Print final request body before upload.")
    parser.add_argument("--yes", action="store_true", help="Skip interactive confirmation when uploading once.")
    parser.add_argument("--serve", action="store_true", help="Start local HTTP proxy server.")
    parser.add_argument("--host", default="127.0.0.1", help="Proxy bind host.")
    parser.add_argument("--port", type=int, default=DEFAULT_PROXY_PORT, help="Proxy bind port.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.serve:
        serve(args.base_url, args.user_id, args.host, args.port, args.limit)
        return 0
    if args.once and not args.dry_run:
        apps = collect_apps(args.limit)
        print_app_preview(apps)
        if not args.yes and not confirm_upload():
            print(json.dumps({"cancelled": True, "apps": [asdict(app) for app in apps]}, ensure_ascii=False, indent=2))
            return 0
        result = upload_events(args.base_url, to_behavior_events(apps, args.user_id), args.print_payload)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    result = upload_apps(args.base_url, args.user_id, args.limit, True)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
