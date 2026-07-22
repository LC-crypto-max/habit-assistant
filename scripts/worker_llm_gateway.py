#!/usr/bin/env python3
"""Worker-facing gateway for real-time LLM analysis."""

from __future__ import annotations

from concurrent.futures import Future
from typing import Any, Callable

from codex_analyzer import CodexAnalyzer
from policy_gate import PolicyDecision


class WorkerLLMGateway:
    """Small facade so the worker does not call analyzer internals directly."""

    def __init__(
            self,
            command: str,
            timeout: int,
            env_provider: Callable[[], dict[str, str]],
            sanitizer: Callable[[Any, int], str],
            provider: str = "codex",
            max_steps: int = 8):
        self.analyzer = CodexAnalyzer(
            command=command,
            timeout=timeout,
            env_provider=env_provider,
            sanitizer=sanitizer,
            provider=provider,
            max_steps=max_steps,
        )

    def submit_policy_decisions(self, decisions: list[PolicyDecision]) -> list[Future[dict[str, Any]]]:
        futures: list[Future[dict[str, Any]]] = []
        for decision in decisions:
            if not decision.llm_required:
                continue
            # PolicyGate decides whether analysis is required; CodexAnalyzer
            # still owns the final URL/event validation before scheduling it.
            future = self.analyzer.submit(decision.item)
            if future is not None:
                futures.append(future)
        return futures

    def wait(self) -> None:
        self.analyzer.wait()

    def shutdown(self) -> None:
        self.analyzer.shutdown()
