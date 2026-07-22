#!/usr/bin/env python3
"""Unified LLM/Codex gateway for worker-side semantic analysis."""

from __future__ import annotations

import json
import os
import re
import shlex
import shutil
import subprocess
import tempfile
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
            sanitizer: Callable[[Any, int], str] | None = None,
            provider: str = "codex",
            max_steps: int = 8):
        self.command = command or "codex"
        self.provider = (provider or "codex").strip().lower()
        if self.provider not in {"codex", "trae"}:
            raise ValueError(f"Unsupported LLM provider: {self.provider}")
        self.timeout = max(1, int(timeout or 60))
        self.max_steps = max(1, min(int(max_steps or 8), 50))
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
                error=f"{self.provider.upper()} command not found: {self.command}",
            )
        args, shell = resolved
        try:
            # Run the analyzer away from the repository so the model only sees
            # the supplied public context, not unrelated local project files.
            with tempfile.TemporaryDirectory(prefix=f"habit-assistant-{self.provider}-") as isolated_dir:
                task_file = None
                result_file = None
                trajectory_file = None
                if self.provider == "trae":
                    input_file = os.path.join(isolated_dir, "ANALYSIS_INPUT.md")
                    result_file = os.path.join(isolated_dir, "analysis-result.json")
                    trajectory_file = os.path.join(isolated_dir, "trae-trajectory.json")
                    with open(input_file, "w", encoding="utf-8", newline="\n") as handle:
                        handle.write(prompt)
                    with open(result_file, "w", encoding="utf-8", newline="\n") as handle:
                        handle.write(json.dumps({
                            "summary": "",
                            "tags": [],
                            "interestCategory": "",
                            "intent": "",
                            "confidence": "",
                        }, ensure_ascii=False, indent=2))
                    task_file = os.path.join(isolated_dir, "analysis-task.txt")
                    with open(task_file, "w", encoding="utf-8", newline="\n") as handle:
                        handle.write(build_trae_file_task())
                invocation = self.invocation(args, isolated_dir, task_file, trajectory_file)
                completed = subprocess.run(
                    invocation,
                    input=None if self.provider == "trae" else prompt,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    timeout=self.timeout,
                    env=self.child_env(),
                    shell=shell,
                    cwd=isolated_dir,
                )
                decoded = None
                failure_detail = ""
                if self.provider == "trae" and result_file:
                    decoded = parse_trae_result_file(result_file)
                    if decoded is None and trajectory_file:
                        decoded = parse_trae_trajectory(trajectory_file)
                        failure_detail = trae_failure_detail(trajectory_file)
                if decoded is None and completed.returncode == 0:
                    if self.provider == "trae":
                        try:
                            candidate = parse_json_output(completed.stdout or completed.stderr or "")
                        except (ValueError, json.JSONDecodeError):
                            candidate = None
                        decoded = candidate if is_analysis_payload(candidate) else None
                    else:
                        decoded = parse_json_output(completed.stdout or completed.stderr or "")
            latency_ms = int((time.perf_counter() - started) * 1000)
            if completed.returncode != 0 and decoded is None:
                process_detail = completed.stderr or failure_detail or tail_console_text(completed.stdout)
                return LLMGatewayResult(
                    success=False,
                    output={},
                    latency_ms=latency_ms,
                    error=self.sanitize(
                        process_detail or f"{self.provider.upper()} exited with {completed.returncode}", 500),
                )
            if decoded is None:
                raise ValueError(
                    "TRAE completed without writing a valid analysis-result.json"
                    if self.provider == "trae" else "no JSON object found in LLM output")
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

    def invocation(
            self,
            resolved_args: list[str] | str,
            isolated_dir: str,
            task_file: str | None = None,
            trajectory_file: str | None = None) -> list[str] | str:
        if self.provider != "trae":
            return resolved_args
        if isinstance(resolved_args, str):
            raise ValueError("TRAE CLI requires a shell-free argument list")
        if not task_file:
            raise ValueError("TRAE CLI requires a task file")
        args = list(resolved_args)
        extra = []
        command_text = str(args[-1]) if args else ""
        if not re.search(r"(^|\s)run($|\s)", command_text, flags=re.IGNORECASE):
            extra.append("run")
        extra.extend([
            "--file", task_file,
            "--working-dir", isolated_dir,
            "--max-steps", str(self.max_steps),
            "--console-type", "simple",
        ])
        if trajectory_file:
            extra.extend(["--trajectory-file", trajectory_file])
        if len(args) >= 5 and str(args[1:4]).lower().find("/c") >= 0:
            args[-1] = f"{args[-1]} {subprocess.list2cmdline(extra)}"
            return args
        args.extend(extra)
        return args

    def child_env(self) -> dict[str, str]:
        env = dict(self.env_provider())
        if self.provider == "trae":
            # TRAE and its tools emit Unicode status symbols. A Windows child
            # process otherwise inherits cp936/GBK even when our pipe decoder
            # is UTF-8, causing the CLI to abort before it writes the result.
            env["PYTHONIOENCODING"] = "utf-8"
            env["PYTHONUTF8"] = "1"
            env["PYTHONLEGACYWINDOWSSTDIO"] = "0"
        return env

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
            command_line = subprocess.list2cmdline(args)
            return [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/s", "/c", command_line], False
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
    decoder = json.JSONDecoder()
    candidates: list[dict[str, Any]] = []
    for match in re.finditer(r"\{", text):
        try:
            decoded, _ = decoder.raw_decode(text[match.start():])
            if isinstance(decoded, dict):
                candidates.append(decoded)
        except json.JSONDecodeError:
            continue
    if candidates:
        return candidates[-1]
    raise ValueError("no JSON object found in LLM output")


def build_trae_file_task() -> str:
    """Adapt a data-analysis request to TRAE 0.1.0's code-task workflow."""
    return (
        "This disposable directory is a single bounded JSON transformation fixture.\n"
        "Resolve the issue by reading ANALYSIS_INPUT.md and replacing the placeholder values in "
        "analysis-result.json with the requested semantic analysis.\n"
        "Do not inspect files outside this directory. Do not modify ANALYSIS_INPUT.md. "
        "Do not create code, tests, patches, or additional files.\n"
        "Use the file editing tool directly; shell exploration is unnecessary.\n"
        "analysis-result.json must contain exactly one strict JSON object with non-empty summary, "
        "interestCategory, intent, confidence, and a tags array. Do not put Markdown or prose in it.\n"
        "After saving the valid JSON file, immediately call task_done."
    )


def is_analysis_payload(value: Any) -> bool:
    if not isinstance(value, dict):
        return False
    required_text = ("summary", "interestCategory", "intent", "confidence")
    return all(isinstance(value.get(key), str) and value.get(key).strip() for key in required_text) \
        and isinstance(value.get("tags"), list)


def parse_trae_result_file(path: str) -> dict[str, Any] | None:
    try:
        with open(path, encoding="utf-8") as handle:
            decoded = json.load(handle)
        return decoded if is_analysis_payload(decoded) else None
    except (OSError, UnicodeError, json.JSONDecodeError):
        return None


def parse_trae_trajectory(path: str) -> dict[str, Any] | None:
    """Recover a strict result when a TRAE version returns it through task_done only."""
    try:
        with open(path, encoding="utf-8") as handle:
            trajectory = json.load(handle)
    except (OSError, UnicodeError, json.JSONDecodeError):
        return None
    values = [trajectory.get("final_result")]
    for step in trajectory.get("agent_steps") or []:
        if not isinstance(step, dict):
            continue
        response = step.get("llm_response")
        if isinstance(response, dict):
            values.append(response.get("content"))
        for call in step.get("tool_calls") or []:
            if isinstance(call, dict) and str(call.get("name") or "").lower() == "task_done":
                values.append(call.get("arguments"))
    for value in reversed(values):
        if is_analysis_payload(value):
            return value
        if isinstance(value, dict):
            for key in ("result", "response", "output"):
                nested = value.get(key)
                if is_analysis_payload(nested):
                    return nested
                if isinstance(nested, str):
                    try:
                        decoded = parse_json_output(nested)
                    except (ValueError, json.JSONDecodeError):
                        continue
                    if is_analysis_payload(decoded):
                        return decoded
        if isinstance(value, str):
            try:
                decoded = parse_json_output(value)
            except (ValueError, json.JSONDecodeError):
                continue
            if is_analysis_payload(decoded):
                return decoded
    return None


def trae_failure_detail(path: str) -> str:
    try:
        with open(path, encoding="utf-8") as handle:
            trajectory = json.load(handle)
    except (OSError, UnicodeError, json.JSONDecodeError):
        return ""
    steps = trajectory.get("agent_steps") or []
    for step in reversed(steps):
        if not isinstance(step, dict):
            continue
        if step.get("error"):
            return f"TRAE step error: {step['error']}"
        for result in reversed(step.get("tool_results") or []):
            if isinstance(result, dict) and result.get("error"):
                return f"TRAE tool error: {result['error']}"
    return f"TRAE trajectory ended without a valid result after {len(steps)} step(s)"


def tail_console_text(value: str, limit: int = 1200) -> str:
    text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", value or "")
    text = text.strip()
    return text[-limit:] if text else ""


def token_usage(decoded: dict[str, Any]) -> Any:
    for key in ("token_usage", "tokenUsage", "usage"):
        if key in decoded:
            return decoded[key]
    return None


def default_sanitize(value: Any, limit: int = 500) -> str:
    text = "" if value is None else str(value)
    text = re.sub(r"(?i)(cookie|token|session|password|authorization)\s*[=:]\s*[^\s,;&]+", r"\1=[REDACTED]", text)
    return text[:limit]
