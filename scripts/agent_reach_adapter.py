#!/usr/bin/env python3
"""
Privacy-safe Agent Reach adapter for public URL enrichment.

Phase 1 intentionally uses mock/dry-run enrichment. It never reads cookies,
tokens, sessions, passwords, forms, private messages, contacts, or payment
records. The input URL must already come from an authorized browser-history
item or a user-provided public URL.
"""

from __future__ import annotations

import re
import urllib.parse
from datetime import datetime
from typing import Any


PLATFORM_DOMAINS: dict[str, tuple[str, ...]] = {
    "xiaohongshu": ("xiaohongshu.com", "xhslink.com"),
    "youtube": ("youtube.com", "youtu.be"),
    "bilibili": ("bilibili.com", "b23.tv"),
    "github": ("github.com",),
    "zhihu": ("zhihu.com",),
    "csdn": ("csdn.net",),
    "juejin": ("juejin.cn",),
}


def sanitize_text(value: Any, limit: int = 500) -> str:
    text = "" if value is None else str(value)
    text = re.sub(r"(?i)authorization\s*:\s*bearer\s+\S+", "[REDACTED_AUTHORIZATION]", text)
    text = re.sub(r"(?i)(cookie|token|session|password)\s*[=:]\s*[^\s,;&]+", r"\1=[REDACTED]", text)
    text = "".join(ch for ch in text if ch >= " " or ch in "\r\n\t").strip()
    return text[:limit]


def domain_from_url(url: str) -> str:
    try:
        host = urllib.parse.urlparse(url).hostname or ""
        return host.lower().removeprefix("www.")
    except ValueError:
        return ""


def platform_for_url(url: str, fallback: str = "web") -> str:
    domain = domain_from_url(url)
    for platform, domains in PLATFORM_DOMAINS.items():
        if any(domain == item or domain.endswith("." + item) for item in domains):
            return platform
    return sanitize_text(fallback or "web", 80).lower() or "web"


def content_type_for_platform(platform: str, url: str) -> str:
    if platform in {"youtube", "bilibili"}:
        return "video"
    if platform == "xiaohongshu":
        return "note"
    if platform == "github":
        return "repository-or-code-page"
    if platform in {"zhihu", "csdn", "juejin"}:
        return "article"
    return "web-page"


def default_tags(platform: str, content_type: str, title: str, url: str) -> list[str]:
    text = f"{platform} {content_type} {title} {url}".lower()
    tags = [platform, "agent-reach", "public-url", content_type]
    if any(word in text for word in ["java", "spring", "redis", "mysql", "jvm"]):
        tags.append("Java后端")
    if any(word in text for word in ["ai", "codex", "agent", "llm", "chatgpt"]):
        tags.append("AI工具")
    if platform == "xiaohongshu":
        tags.append("生活方式")
    if platform in {"youtube", "bilibili"}:
        tags.append("视频内容")
    return list(dict.fromkeys(tag for tag in tags if tag))


def enrich_public_url(item: dict[str, Any]) -> dict[str, Any]:
    """Return a structured enrichment object for a public URL item.

    This mock adapter deliberately does not perform network access. Later
    versions can replace read_* functions with Agent Reach/OpenCLI/yt-dlp
    calls while keeping the output contract stable.
    """
    url = sanitize_text(item.get("url") or "", 1200)
    if not url.startswith(("http://", "https://")):
        return dict(item)

    platform = platform_for_url(url, str(item.get("platform") or "web"))
    if platform == "bilibili":
        return read_bilibili(item, platform)
    if platform == "youtube":
        return read_youtube(item, platform)
    if platform == "xiaohongshu":
        return read_xiaohongshu_public(item, platform)
    return read_web_page(item, platform)


def read_bilibili(item: dict[str, Any], platform: str = "bilibili") -> dict[str, Any]:
    return build_mock_enrichment(item, platform, "video", "Bilibili public video metadata placeholder")


def read_youtube(item: dict[str, Any], platform: str = "youtube") -> dict[str, Any]:
    return build_mock_enrichment(item, platform, "video", "YouTube public video metadata placeholder")


def read_xiaohongshu_public(item: dict[str, Any], platform: str = "xiaohongshu") -> dict[str, Any]:
    return build_mock_enrichment(item, platform, "note", "Xiaohongshu public note metadata placeholder")


def read_web_page(item: dict[str, Any], platform: str = "web") -> dict[str, Any]:
    return build_mock_enrichment(item, platform, content_type_for_platform(platform, str(item.get("url") or "")),
                                 "Public web page metadata placeholder")


def build_mock_enrichment(item: dict[str, Any], platform: str, content_type: str, summary_prefix: str) -> dict[str, Any]:
    url = sanitize_text(item.get("url") or "", 1200)
    title = sanitize_text(item.get("title") or url, 300)
    domain = domain_from_url(url)
    original_raw = item.get("rawEvidence") if isinstance(item.get("rawEvidence"), dict) else {}
    visit_count = original_raw.get("visitCount") or item.get("visitCount") or 0
    tags = default_tags(platform, content_type, title, url)
    occurred_at = sanitize_text(item.get("occurredAt") or datetime.now().replace(microsecond=0).isoformat(), 40)
    return {
        "userId": sanitize_text(item.get("userId") or "", 120),
        "platform": platform,
        "source": "agent-reach-mock",
        "type": "WATCH" if content_type == "video" else "VISIT",
        "externalId": sanitize_text(item.get("externalId") or "", 160),
        "title": title,
        "url": url,
        "author": sanitize_text(item.get("author") or "", 160),
        "summary": f"{summary_prefix}: {title}",
        "tags": tags,
        "contentType": content_type,
        "contentCategory": content_type,
        "intent": "public-url-enrichment",
        "summaryForProfile": f"用户访问过 {platform} 的公开{content_type}内容：{title}",
        "recommendationHints": [f"继续推荐与 {title[:40]} 相关的公开内容"],
        "confidence": "MEDIUM",
        "dataLevel": "PUBLIC_URL",
        "detectionReason": "public_url_enrichment",
        "matchedKeyword": domain,
        "occurredAt": occurred_at,
        "rawEvidence": {
            "browser": sanitize_text(original_raw.get("browser") or "", 80),
            "processName": sanitize_text(original_raw.get("processName") or "", 120),
            "windowTitle": sanitize_text(original_raw.get("windowTitle") or title, 240),
            "domain": domain,
            "visitCount": safe_int(visit_count),
            "adapter": "agent-reach",
            "adapterMode": "mock",
            "contentType": content_type,
            "contentCategory": content_type,
            "intent": "public-url-enrichment",
        },
    }


def safe_int(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0
