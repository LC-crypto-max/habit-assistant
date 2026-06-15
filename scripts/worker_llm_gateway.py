#!/usr/bin/env python3
"""Worker-facing gateway for real-time LLM analysis."""

from __future__ import annotations

from concurrent.futures import Future
from typing import Any, Callable

from codex_analyzer import CodexAnalyzer


class WorkerLLMGateway:
    """Small facade so the worker does not call analyzer internals directly."""

    def __init__(
            self,
            command: str,
            timeout: int,
            env_provider: Callable[[], dict[str, str]],
            sanitizer: Callable[[Any, int], str]):
        self.analyzer = CodexAnalyzer(
            command=command,
            timeout=timeout,
            env_provider=env_provider,
            sanitizer=sanitizer,
        )

    def submit_events(self, items: list[dict[str, Any]]) -> list[Future[dict[str, Any]]]:
        return self.analyzer.submit_all(items)

    def wait(self) -> None:
        self.analyzer.wait()

    def shutdown(self) -> None:
        self.analyzer.shutdown()
