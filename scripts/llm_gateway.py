#!/usr/bin/env python3
"""Unified LLM/Codex gateway for worker-side semantic analysis."""

from __future__ import annotations

import json
import os
import re
import shlex
import shutil
import subprocess
import time
from dataclasses import dataclass
from typing import Any, Callable


@dataclass
class LLMGatewayResult:
    success: bool
    output: dict[str, Any]
    latency_ms: int
    token_usage: Any = None
    error: str = ""


class LLMGateway:
    def __init__(
            self,
            command: str = "codex",
            timeout: int = 60,
            env_provider: Callable[[], dict[str, str]] | None = None,
            sanitizer: Callable[[Any, int], str] | None = None):
        self.command = command or "codex"
        self.timeout = max(1, int(timeout or 60))
        self.env_provider = env_provider or (lambda: os.environ.copy())
        self.sanitize = sanitizer or default_sanitize

    def available(self) -> bool:
        return self.resolve_command() is not None

    def analyze_json(self, prompt: str) -> LLMGatewayResult:
        started = time.perf_counter()
        resolved = self.resolve_command()
        if not resolved:
            return LLMGatewayResult(
                success=False,
                output={},
                latency_ms=0,
                error=f"Codex command not found: {self.command}",
            )
        args, shell = resolved
        try:
            completed = subprocess.run(
                args,
                input=prompt,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=self.timeout,
                env=self.env_provider(),
                shell=shell,
            )
            latency_ms = int((time.perf_counter() - started) * 1000)
            if completed.returncode != 0:
                return LLMGatewayResult(
                    success=False,
                    output={},
                    latency_ms=latency_ms,
                    error=self.sanitize(completed.stderr or f"Codex exited with {completed.returncode}", 500),
                )
            decoded = parse_json_output(completed.stdout or completed.stderr or "")
            return LLMGatewayResult(
                success=True,
                output=decoded,
                latency_ms=latency_ms,
                token_usage=token_usage(decoded),
            )
        except Exception as exc:
            latency_ms = int((time.perf_counter() - started) * 1000)
            return LLMGatewayResult(
                success=False,
                output={},
                latency_ms=latency_ms,
                error=self.sanitize(exc, 500),
            )

    def resolve_command(self) -> tuple[list[str] | str, bool] | None:
        parts = split_command(self.command)
        if not parts:
            return None
        executable = shutil.which(parts[0], path=self.env_provider().get("PATH"))
        if not executable:
            return None
        args = [executable, *parts[1:]]
        suffix = os.path.splitext(executable)[1].lower()
        if os.name == "nt" and suffix in {".bat", ".cmd"}:
            return subprocess.list2cmdline(args), True
        return args, False


def split_command(command: str) -> list[str]:
    command = (command or "codex").strip()
    if not command:
        return ["codex"]
    return shlex.split(command, posix=os.name != "nt")


def parse_json_output(output: str) -> dict[str, Any]:
    text = (output or "").strip()
    if not text:
        raise ValueError("empty LLM output")
    try:
        decoded = json.loads(text)
        if isinstance(decoded, dict):
            return decoded
    except json.JSONDecodeError:
        pass
    fenced = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.IGNORECASE | re.DOTALL)
    if fenced:
        decoded = json.loads(fenced.group(1))
        if isinstance(decoded, dict):
            return decoded
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        decoded = json.loads(text[start:end + 1])
        if isinstance(decoded, dict):
            return decoded
    raise ValueError("no JSON object found in LLM output")


def token_usage(decoded: dict[str, Any]) -> Any:
    for key in ("token_usage", "tokenUsage", "usage"):
        if key in decoded:
            return decoded[key]
    return None


def default_sanitize(value: Any, limit: int = 500) -> str:
    text = "" if value is None else str(value)
    text = re.sub(r"(?i)(cookie|token|session|password|authorization)\s*[=:]\s*[^\s,;&]+", r"\1=[REDACTED]", text)
    return text[:limit]
