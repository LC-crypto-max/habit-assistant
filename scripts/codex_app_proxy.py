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
import json
import os
import subprocess
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


@dataclass
class AppInfo:
    process_id: int
    process_name: str
    window_title: str
    platform: str
    tags: list[str]


def now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def infer_platform(process_name: str, title: str) -> tuple[str, list[str]]:
    text = f"{process_name} {title}".lower()
    rules = [
        ("bilibili", ["bilibili", "哔哩", "b站"], ["bilibili", "video"]),
        ("douyin", ["douyin", "抖音"], ["douyin", "short-video"]),
        ("xiaohongshu", ["xiaohongshu", "小红书"], ["xiaohongshu", "lifestyle"]),
        ("wechat", ["wechat", "微信", "weixin"], ["wechat", "social"]),
        ("youtube", ["youtube"], ["youtube", "video"]),
        ("browser-edge", ["msedge", "edge"], ["browser", "edge"]),
        ("browser-chrome", ["chrome"], ["browser", "chrome"]),
    ]
    for platform, needles, tags in rules:
        if any(needle in text for needle in needles):
            return platform, ["app-usage", *tags]
    return "desktop-app", ["app-usage", "desktop"]


def collect_apps(limit: int = DEFAULT_LIMIT) -> list[AppInfo]:
    if os.name != "nt":
        raise RuntimeError("This collector currently supports Windows only.")

    command = [
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        (
            "Get-Process | "
            "Where-Object { $_.MainWindowTitle -and $_.MainWindowTitle.Trim().Length -gt 0 } | "
            "Select-Object -First %d Id,ProcessName,MainWindowTitle | "
            "ConvertTo-Json -Depth 3"
        )
        % limit,
    ]
    completed = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=10,
    )
    raw = completed.stdout.strip()
    if not raw:
        return []
    decoded = json.loads(raw)
    rows = decoded if isinstance(decoded, list) else [decoded]
    apps: list[AppInfo] = []
    for row in rows:
        process_name = str(row.get("ProcessName") or "").strip()
        title = str(row.get("MainWindowTitle") or "").strip()
        if not process_name or not title:
            continue
        platform, tags = infer_platform(process_name, title)
        apps.append(
            AppInfo(
                process_id=int(row.get("Id") or 0),
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
        headers={"Content-Type": "application/json; charset=utf-8"},
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
