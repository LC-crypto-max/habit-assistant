#!/usr/bin/env python3
"""
Import authorized Chrome/Edge browser history into Habit Assistant.

The script only reads the browser History SQLite database and only queries the
public browsing-history tables `urls` and `visits`. It never opens Cookies,
Login Data, Web Data, tokens, sessions, passwords, forms, or account data.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sqlite3
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


DEFAULT_BASE_URL = "http://localhost:8080"
CHROME_EPOCH = datetime(1601, 1, 1, tzinfo=timezone.utc)
PLATFORM_DOMAINS: dict[str, tuple[str, ...]] = {
    "xiaohongshu": ("xiaohongshu.com", "xhslink.com"),
    "youtube": ("youtube.com", "youtu.be"),
    "bilibili": ("bilibili.com", "b23.tv"),
    "zhihu": ("zhihu.com",),
    "github": ("github.com",),
    "csdn": ("csdn.net",),
    "juejin": ("juejin.cn",),
}


@dataclass
class HistoryRow:
    browser: str
    platform: str
    url_id: int
    url: str
    title: str
    visit_count: int
    visit_time: int
    domain: str


def sanitize_text(value: Any, limit: int = 500) -> str:
    text = "" if value is None else str(value)
    text = "".join(ch for ch in text if ch >= " " or ch in "\r\n\t").strip()
    return text[:limit]


def domain_from_url(url: str) -> str:
    try:
        host = urllib.parse.urlparse(url).hostname or ""
        return host.lower().removeprefix("www.")
    except ValueError:
        return ""


def platform_for_domain(domain: str) -> str:
    for platform, domains in PLATFORM_DOMAINS.items():
        if any(domain == item or domain.endswith("." + item) for item in domains):
            return platform
    return "browser"


def domains_for_platform(platform: str) -> tuple[str, ...]:
    platform = platform.lower().strip()
    if platform == "all":
        domains: list[str] = []
        for items in PLATFORM_DOMAINS.values():
            domains.extend(items)
        return tuple(domains)
    return PLATFORM_DOMAINS.get(platform, ())


def locate_history_files(browser: str) -> list[Path]:
    local_app_data = Path(os.environ.get("LOCALAPPDATA", ""))
    if not local_app_data:
        return []
    browser = browser.lower().strip()
    if browser == "edge":
        root = local_app_data / "Microsoft" / "Edge" / "User Data"
    elif browser == "chrome":
        root = local_app_data / "Google" / "Chrome" / "User Data"
    else:
        raise ValueError("--browser must be edge or chrome")
    candidates = [root / "Default" / "History"]
    candidates.extend(sorted(root.glob("Profile */History")))
    return [path for path in candidates if path.exists() and path.name == "History"]


def copy_history_to_temp(history_path: Path) -> Path:
    if history_path.name != "History":
        raise ValueError("Only a browser History SQLite file can be imported.")
    temp = tempfile.NamedTemporaryFile(prefix="habit_history_", suffix=".sqlite", delete=False)
    temp.close()
    temp_path = Path(temp.name)
    shutil.copy2(history_path, temp_path)
    return temp_path


def chrome_time_to_local(value: int) -> str:
    try:
        if value <= 0:
            return datetime.now().replace(microsecond=0).isoformat(timespec="seconds")
        utc_time = CHROME_EPOCH + timedelta(microseconds=int(value))
        return utc_time.astimezone().replace(tzinfo=None, microsecond=0).isoformat(timespec="seconds")
    except (OverflowError, ValueError):
        return datetime.now().replace(microsecond=0).isoformat(timespec="seconds")


def read_history(history_path: Path, browser: str, platform: str, limit: int) -> list[HistoryRow]:
    temp_path = copy_history_to_temp(history_path)
    try:
        with sqlite3.connect(str(temp_path)) as conn:
            return query_history(conn, browser, platform, limit)
    finally:
        try:
            temp_path.unlink(missing_ok=True)
        except OSError:
            pass


def query_history(conn: sqlite3.Connection, browser: str, platform: str, limit: int) -> list[HistoryRow]:
    domains = domains_for_platform(platform)
    if platform != "all" and not domains:
        raise ValueError(f"Unsupported platform: {platform}")
    where = ""
    params: list[Any] = []
    if domains:
        clauses = []
        for domain in domains:
            clauses.append("u.url LIKE ?")
            params.append(f"%{domain}%")
        where = "WHERE " + " OR ".join(clauses)
    params.append(max(1, int(limit)))
    query = f"""
        SELECT
            u.id,
            u.url,
            u.title,
            u.visit_count,
            MAX(COALESCE(v.visit_time, u.last_visit_time, 0)) AS visit_time
        FROM urls u
        LEFT JOIN visits v ON v.url = u.id
        {where}
        GROUP BY u.id, u.url, u.title, u.visit_count
        ORDER BY visit_time DESC
        LIMIT ?
    """
    rows = conn.execute(query, params).fetchall()
    results: list[HistoryRow] = []
    requested = platform.lower().strip()
    for url_id, url, title, visit_count, visit_time in rows:
        clean_url = sanitize_text(url, 1200)
        domain = domain_from_url(clean_url)
        resolved_platform = platform_for_domain(domain)
        if requested != "all" and resolved_platform != requested:
            continue
        if requested == "all" and resolved_platform == "browser":
            continue
        results.append(HistoryRow(
            browser=browser.lower().strip(),
            platform=resolved_platform,
            url_id=int(url_id),
            url=clean_url,
            title=sanitize_text(title or clean_url, 300),
            visit_count=max(0, int(visit_count or 0)),
            visit_time=int(visit_time or 0),
            domain=domain,
        ))
    return results


def rows_to_events(rows: list[HistoryRow], user_id: str) -> list[dict[str, Any]]:
    events: list[dict[str, Any]] = []
    for row in rows:
        matched = next((domain for domain in PLATFORM_DOMAINS[row.platform]
                        if row.domain == domain or row.domain.endswith("." + domain)), row.domain)
        browser_label = "Edge" if row.browser == "edge" else "Chrome"
        events.append({
            "userId": user_id,
            "platform": row.platform,
            "source": "browser-history",
            "type": "VISIT",
            "externalId": f"{row.browser}-history-{row.url_id}",
            "title": row.title,
            "url": row.url,
            "author": "",
            "summary": f"来自 {browser_label} 浏览器历史的访问记录，visitCount={row.visit_count}",
            "tags": [row.platform, "browser-history"],
            "occurredAt": chrome_time_to_local(row.visit_time),
            "confidence": "MEDIUM",
            "dataLevel": "BROWSER_HISTORY",
            "detectionReason": "browser_history",
            "matchedKeyword": matched,
            "rawEvidence": {
                "browser": row.browser,
                "domain": row.domain,
                "visitCount": row.visit_count,
            },
        })
    return events


def post_json(url: str, payload: dict[str, Any], timeout: int = 10) -> dict[str, Any]:
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
            text = response.read().decode("utf-8", errors="replace")
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} from {url}: {error_body}") from exc


def import_history(args: argparse.Namespace) -> dict[str, Any]:
    history_files = [Path(args.history_path)] if args.history_path else locate_history_files(args.browser)
    if not history_files:
        raise RuntimeError(f"No {args.browser} History file found. Close the browser or pass --history-path.")
    all_rows: list[HistoryRow] = []
    remaining = max(1, int(args.limit))
    for history_path in history_files:
        if remaining <= 0:
            break
        rows = read_history(history_path, args.browser, args.platform, remaining)
        all_rows.extend(rows)
        remaining = max(0, int(args.limit) - len(all_rows))
    events = rows_to_events(all_rows[:args.limit], args.user_id)
    payload = {"events": events}
    if args.print_payload or args.dry_run:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    if args.dry_run:
        return {"dryRun": True, "imported": len(events), "events": events}
    if not events:
        return {"imported": 0, "skipped": 0, "messagesPublished": 0, "activities": []}
    endpoint = args.base_url.rstrip("/") + "/api/v1/behavior-events/batch"
    return post_json(endpoint, payload, timeout=args.timeout)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import authorized Chrome/Edge History records.")
    parser.add_argument("--user-id", default="me")
    parser.add_argument("--browser", choices=["edge", "chrome"], required=True)
    parser.add_argument("--platform", default="all",
                        choices=["all", *sorted(PLATFORM_DOMAINS.keys())])
    parser.add_argument("--limit", type=int, default=200)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--timeout", type=int, default=10)
    parser.add_argument("--history-path", help="Test/debug only: explicit History SQLite path.")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--print-payload", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        result = import_history(args)
    except Exception as exc:
        print(f"import failed: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
