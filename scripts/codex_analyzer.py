#!/usr/bin/env python3
"""Async Codex/LLM analyzer for public behavior-event semantics."""

from __future__ import annotations

import json
import re
import time
from concurrent.futures import Future, ThreadPoolExecutor
from typing import Any, Callable

from llm_gateway import LLMGateway


ALLOWED_CONFIDENCE = {"HIGH", "MEDIUM", "LOW"}


class CodexAnalyzer:
    def __init__(
            self,
            command: str = "codex",
            timeout: int = 60,
            max_workers: int = 4,
            env_provider: Callable[[], dict[str, str]] | None = None,
            sanitizer: Callable[[Any, int], str] | None = None):
        self.command = command or "codex"
        self.timeout = max(1, int(timeout or 60))
        self.env_provider = env_provider or (lambda: {})
        self.sanitize = sanitizer or default_sanitize
        self.gateway = LLMGateway(self.command, self.timeout, self.env_provider, self.sanitize)
        self.executor = ThreadPoolExecutor(max_workers=max(1, max_workers), thread_name_prefix="codex-analyzer")
        self.futures: list[Future[dict[str, Any]]] = []

    def should_analyze(self, item: dict[str, Any]) -> bool:
        event_type = self.sanitize(item.get("eventType") or item.get("type") or "", 40).upper()
        url = self.sanitize(item.get("url") or "", 600)
        return bool(url) and event_type in {"VISIT", "WATCH", "FAVORITE"}

    def mark_pending_or_failed(self, item: dict[str, Any]) -> None:
        raw = ensure_raw_metadata(item)
        if not self.gateway.available():
            raw["llm_status"] = "FAILED"
            raw["llm_error"] = f"Codex command not found: {self.command}"
            return
        raw["llm_status"] = "PENDING"

    def submit(self, item: dict[str, Any]) -> Future[dict[str, Any]] | None:
        if not self.should_analyze(item):
            return None
        self.mark_pending_or_failed(item)
        raw = ensure_raw_metadata(item)
        if raw.get("llm_status") == "FAILED":
            llm_input = self.llm_input(item)
            result = {
                "platform": llm_input["platform"],
                "url": llm_input["url"],
                "summary": "",
                "tags": [],
                "interestCategory": "",
                "intent": "",
                "confidence": "LOW",
                "llm_status": "FAILED",
                "error": raw.get("llm_error") or "Codex command not found",
                "latency_ms": 0,
                "token_usage": None,
            }
            print_structured_log("llm_input_log", llm_input)
            print_structured_log("llm_output_log", result)
            print_llm_result(result)
            return None
        future = self.executor.submit(self.analyze, item)
        future.add_done_callback(self._print_callback)
        self.futures.append(future)
        return future

    def submit_all(self, items: list[dict[str, Any]]) -> list[Future[dict[str, Any]]]:
        futures: list[Future[dict[str, Any]]] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            future = self.submit(item)
            if future is not None:
                futures.append(future)
        return futures

    def wait(self) -> None:
        for future in list(self.futures):
            try:
                future.result(timeout=self.timeout + 5)
            except Exception:
                pass

    def shutdown(self) -> None:
        self.executor.shutdown(wait=False, cancel_futures=False)

    def analyze(self, item: dict[str, Any]) -> dict[str, Any]:
        started = time.perf_counter()
        llm_input = self.llm_input(item)
        print_structured_log("llm_input_log", llm_input)
        prompt = build_prompt(llm_input)
        try:
            gateway_result = self.gateway.analyze_json(prompt)
            if not gateway_result.success:
                raise RuntimeError(gateway_result.error)
            decoded = gateway_result.output
            analysis = validate_analysis(decoded, self.sanitize)
            latency_ms = gateway_result.latency_ms
            analysis.update({
                "platform": llm_input["platform"],
                "url": llm_input["url"],
                "llm_status": "SUCCESS",
                "latency_ms": latency_ms,
                "token_usage": gateway_result.token_usage,
            })
            raw = ensure_raw_metadata(item)
            raw["llm_status"] = "SUCCESS"
            raw["llm_result"] = {
                "summary": analysis["summary"],
                "tags": analysis["tags"],
                "interestCategory": analysis["interestCategory"],
                "intent": analysis["intent"],
                "confidence": analysis["confidence"],
            }
            raw["llm_latency_ms"] = latency_ms
            apply_analysis_to_item(item, analysis)
            print_structured_log("llm_output_log", analysis)
            return analysis
        except Exception as exc:
            latency_ms = int((time.perf_counter() - started) * 1000)
            result = {
                "platform": llm_input["platform"],
                "url": llm_input["url"],
                "summary": "",
                "tags": [],
                "interestCategory": "",
                "intent": "",
                "confidence": "LOW",
                "llm_status": "FAILED",
                "error": self.sanitize(exc, 500),
                "latency_ms": latency_ms,
                "token_usage": None,
            }
            raw = ensure_raw_metadata(item)
            raw["llm_status"] = "FAILED"
            raw["llm_error"] = result["error"]
            raw["llm_latency_ms"] = latency_ms
            print_structured_log("llm_output_log", result)
            return result

    def llm_input(self, item: dict[str, Any]) -> dict[str, Any]:
        return {
            "platform": self.sanitize(item.get("platform") or "", 80),
            "url": self.sanitize(item.get("url") or "", 600),
            "title": self.sanitize(item.get("title") or "", 300),
            "contentSnippet": self.sanitize(item.get("contentSnippet") or item.get("summary") or "", 800),
            "eventType": self.sanitize(item.get("eventType") or item.get("type") or "", 40).upper(),
        }

    def _print_callback(self, future: Future[dict[str, Any]]) -> None:
        try:
            result = future.result()
        except Exception as exc:
            result = {
                "platform": "",
                "url": "",
                "summary": "",
                "tags": [],
                "interestCategory": "",
                "intent": "",
                "confidence": "LOW",
                "llm_status": "FAILED",
                "error": self.sanitize(exc, 500),
                "latency_ms": None,
                "token_usage": None,
            }
        print_llm_result(result)


def build_prompt(llm_input: dict[str, Any]) -> str:
    return (
        "You are analyzing a user's browsing behavior. Extract semantic meaning.\n\n"
        "INPUT JSON:\n"
        f"{json.dumps(llm_input, ensure_ascii=False, indent=2)}\n\n"
        "OUTPUT JSON ONLY:\n"
        "{\n"
        '  "summary": "...",\n'
        '  "tags": ["..."],\n'
        '  "interestCategory": "...",\n'
        '  "intent": "...",\n'
        '  "confidence": "HIGH|MEDIUM|LOW"\n'
        "}\n\n"
        "RULES:\n"
        "- tags must reflect content meaning, not system labels\n"
        "- summary must describe semantic meaning of the behavior\n"
        "- do NOT include sensitive inference such as health, identity, or private status\n"
        "- max 5 tags\n"
        "- output must be strict JSON\n"
    )


def validate_analysis(decoded: dict[str, Any], sanitize: Callable[[Any, int], str]) -> dict[str, Any]:
    summary = sanitize(decoded.get("summary") or "", 420)
    tags_value = decoded.get("tags")
    tags = []
    if isinstance(tags_value, list):
        for tag in tags_value:
            text = sanitize(tag, 80)
            if text and text.lower() not in {"youtube", "bilibili", "xiaohongshu", "web", "public-url", "agent-reach"}:
                tags.append(text)
    confidence = sanitize(decoded.get("confidence") or "MEDIUM", 20).upper()
    if confidence not in ALLOWED_CONFIDENCE:
        confidence = "MEDIUM"
    return {
        "summary": summary,
        "tags": dedupe(tags)[:5],
        "interestCategory": sanitize(decoded.get("interestCategory") or "other", 120),
        "intent": sanitize(decoded.get("intent") or "", 120),
        "confidence": confidence,
    }


def ensure_raw_metadata(item: dict[str, Any]) -> dict[str, Any]:
    raw = item.get("rawMetadata")
    if not isinstance(raw, dict):
        raw = item.get("rawEvidence")
    if not isinstance(raw, dict):
        raw = {}
    item["rawMetadata"] = raw
    item["rawEvidence"] = raw
    return raw


def apply_analysis_to_item(item: dict[str, Any], analysis: dict[str, Any]) -> None:
    item["summary"] = analysis.get("summary") or item.get("summary") or ""
    item["summaryForProfile"] = item["summary"]
    item["tags"] = list(analysis.get("tags") or [])
    item["interestCategory"] = analysis.get("interestCategory") or item.get("interestCategory") or "other"
    item["contentCategory"] = item["interestCategory"]
    item["intent"] = analysis.get("intent") or item.get("intent") or ""
    item["confidence"] = analysis.get("confidence") or item.get("confidence") or "MEDIUM"
    item["source"] = "codex-cli-analysis"


def print_llm_result(result: dict[str, Any]) -> None:
    print("=====================================")
    print("[LLM ANALYSIS RESULT]")
    print(f"Platform: {result.get('platform') or ''}")
    print(f"URL: {result.get('url') or ''}")
    if result.get("llm_status") == "FAILED":
        print(f"Summary: LLM analysis failed: {result.get('error') or 'unknown error'}")
    else:
        print(f"Summary: {result.get('summary') or ''}")
    print(f"Tags: {', '.join(result.get('tags') or [])}")
    print(f"Category: {result.get('interestCategory') or ''}")
    print(f"Confidence: {result.get('confidence') or ''}")
    print("=====================================")


def print_structured_log(name: str, payload: dict[str, Any]) -> None:
    print(f"{name} {json.dumps(payload, ensure_ascii=False, sort_keys=True)}")


def default_sanitize(value: Any, limit: int = 500) -> str:
    text = "" if value is None else str(value)
    text = re.sub(r"(?i)(cookie|token|session|password|authorization)\s*[=:]\s*[^\s,;&]+", r"\1=[REDACTED]", text)
    return text[:limit]


def dedupe(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        key = value.lower()
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result
