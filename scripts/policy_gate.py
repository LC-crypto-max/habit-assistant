#!/usr/bin/env python3
"""Mandatory policy gate for worker event ingestion and LLM routing."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable


CONTENT_EVENT_TYPES = {"VISIT", "WATCH", "SEARCH", "FAVORITE"}
CONTENT_PLATFORMS = {
    "youtube",
    "bilibili",
    "baidu",
    "baidu_search",
    "xiaohongshu",
    "web",
    "generic_web",
    "github",
}
APP_USAGE_TYPES = {"APP_USAGE"}
APP_USAGE_LEVELS = {"APP_USAGE_SNAPSHOT"}


@dataclass
class PolicyDecision:
    item: dict[str, Any]
    platform: str
    event_type: str
    llm_required: bool
    reason: str
    gateway_path: str
    final_ingestion_path: str


class PolicyGate:
    """Every event must pass here before LLM analysis or ingestion."""

    def __init__(self, sanitizer: Callable[[Any, int], str] | None = None):
        self.sanitize = sanitizer or default_sanitize

    def evaluate(self, item: dict[str, Any]) -> PolicyDecision:
        platform = self.sanitize(item.get("platform") or "", 80).lower()
        event_type = self.sanitize(item.get("eventType") or item.get("type") or "", 40).upper()
        data_level = self.sanitize(item.get("dataLevel") or "", 80).upper()

        if event_type in APP_USAGE_TYPES or data_level in APP_USAGE_LEVELS:
            decision = self._decision(
                item=item,
                platform=platform,
                event_type=event_type,
                llm_required=False,
                reason="app_usage_excluded",
                final_ingestion_path="PolicyGate -> direct ingestion",
            )
            self._attach_trace(decision)
            return decision

        content_related = event_type in CONTENT_EVENT_TYPES or platform in CONTENT_PLATFORMS
        if content_related:
            decision = self._decision(
                item=item,
                platform=platform,
                event_type=event_type,
                llm_required=True,
                reason="content_event_requires_llm",
                final_ingestion_path="PolicyGate -> WorkerLLMGateway -> ingestion",
            )
            self._attach_trace(decision)
            return decision

        decision = self._decision(
            item=item,
            platform=platform,
            event_type=event_type,
            llm_required=False,
            reason="non_content_event",
            final_ingestion_path="PolicyGate -> direct ingestion",
        )
        self._attach_trace(decision)
        return decision

    def evaluate_all(self, items: list[dict[str, Any]]) -> list[PolicyDecision]:
        decisions: list[PolicyDecision] = []
        for item in items:
            if isinstance(item, dict):
                decisions.append(self.evaluate(item))
        return decisions

    def print_trace(self, decision: PolicyDecision) -> None:
        print("[PIPELINE TRACE]")
        print(f"- PolicyGate decision: {decision.reason}")
        print(f"- LLM triggered: {str(decision.llm_required).lower()}")
        print(f"- LLM gateway path: {decision.gateway_path}")
        print(f"- Final ingestion path: {decision.final_ingestion_path}")

    def print_traces(self, decisions: list[PolicyDecision]) -> None:
        for decision in decisions:
            self.print_trace(decision)

    def _decision(
            self,
            item: dict[str, Any],
            platform: str,
            event_type: str,
            llm_required: bool,
            reason: str,
            final_ingestion_path: str) -> PolicyDecision:
        return PolicyDecision(
            item=item,
            platform=platform,
            event_type=event_type,
            llm_required=llm_required,
            reason=reason,
            gateway_path=(
                "PolicyGate -> WorkerLLMGateway -> LLMGateway -> Analyzer"
                if llm_required else "PolicyGate -> no LLM"
            ),
            final_ingestion_path=final_ingestion_path,
        )

    def _attach_trace(self, decision: PolicyDecision) -> None:
        raw = decision.item.get("rawMetadata")
        if not isinstance(raw, dict):
            raw = decision.item.get("rawEvidence")
        if not isinstance(raw, dict):
            raw = {}
        raw["policyGate"] = {
            "decision": decision.reason,
            "llmTriggered": decision.llm_required,
            "gatewayPath": decision.gateway_path,
            "finalIngestionPath": decision.final_ingestion_path,
        }
        decision.item["rawMetadata"] = raw
        decision.item["rawEvidence"] = raw


def default_sanitize(value: Any, limit: int = 500) -> str:
    return ("" if value is None else str(value))[:limit]
