#!/usr/bin/env python3
"""
Small Ollama API smoke test using only Python's standard library.

Usage examples:
  set OLLAMA_API_KEY=...
  python scripts/ollama_api_smoke_test.py --list-tags
  python scripts/ollama_api_smoke_test.py --model qwen3.5:397b-cloud --prompt "oi" --stream false --think false
  python scripts/ollama_api_smoke_test.py --model qwen3.5:397b-cloud --prompt "oi" --stream true --think true
"""

from __future__ import annotations

import argparse
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.request
from typing import Any


DEFAULT_HOST = "https://ollama.com"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Test Ollama Cloud without the Ollama Python library.")
    parser.add_argument("--host", default=DEFAULT_HOST, help="Base host, for example https://ollama.com")
    parser.add_argument("--api-key", default=os.environ.get("OLLAMA_API_KEY", ""), help="Ollama API key")
    parser.add_argument("--model", default="qwen3.5:397b-cloud", help="Model name to test")
    parser.add_argument("--prompt", default="Oi. Responda com uma frase curta em portugues.", help="Prompt to send")
    parser.add_argument("--stream", choices=["true", "false"], default="false", help="Use streaming mode")
    parser.add_argument(
        "--think",
        default="omit",
        help='Thinking mode: "true", "false", "omit", or a string level such as "low", "medium", "high"',
    )
    parser.add_argument("--list-tags", action="store_true", help="List models from /api/tags and exit")
    parser.add_argument("--timeout", type=int, default=120, help="HTTP timeout in seconds")
    return parser.parse_args()


def build_headers(api_key: str) -> dict[str, str]:
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    return headers


def normalize_host(host: str) -> str:
    return host.rstrip("/")


def decode_json_response(response: Any) -> Any:
    raw = response.read()
    encoding = response.headers.get_content_charset() or "utf-8"
    return json.loads(raw.decode(encoding, errors="replace"))


def request_json(url: str, headers: dict[str, str], body: dict[str, Any] | None, timeout: int) -> tuple[int, str, Any]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url=url, data=data, headers=headers, method="GET" if body is None else "POST")
    context = ssl.create_default_context()
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            content_type = response.headers.get("Content-Type", "")
            return response.status, content_type, decode_json_response(response)
    except urllib.error.HTTPError as error:
        payload = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code} for {url}\n{payload}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Network error for {url}: {error}") from error


def request_stream(url: str, headers: dict[str, str], body: dict[str, Any], timeout: int) -> None:
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url=url, data=data, headers=headers, method="POST")
    context = ssl.create_default_context()

    try:
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            content_type = response.headers.get("Content-Type", "")
            print(f"status={response.status}")
            print(f"content_type={content_type}")
            print("stream_mode=ndjson" if "ndjson" in content_type else "stream_mode=unknown")

            full_content: list[str] = []
            full_thinking: list[str] = []
            line_count = 0

            while True:
                raw_line = response.readline()
                if not raw_line:
                    break

                line = raw_line.decode("utf-8", errors="replace").strip()
                if not line:
                    continue

                line_count += 1
                try:
                    chunk = json.loads(line)
                except json.JSONDecodeError:
                    print(f"chunk[{line_count}] parse_error={line[:180]}")
                    continue

                message = chunk.get("message") or {}
                thinking = message.get("thinking") or chunk.get("thinking") or ""
                content = message.get("content") or chunk.get("content") or ""
                done = bool(chunk.get("done"))

                if thinking:
                    full_thinking.append(thinking)
                if content:
                    full_content.append(content)

                print(
                    f"chunk[{line_count}] done={done} "
                    f"thinking_len={len(thinking)} content_len={len(content)}"
                )
                if thinking:
                    print(f"  thinking={preview(thinking)}")
                if content:
                    print(f"  content={preview(content)}")

            joined_thinking = "".join(full_thinking)
            joined_content = "".join(full_content)
            print("--- final ---")
            print(f"thinking_total_len={len(joined_thinking)}")
            print(f"content_total_len={len(joined_content)}")
            print(f"thinking_preview={preview(joined_thinking)}")
            print(f"content_preview={preview(joined_content)}")
    except urllib.error.HTTPError as error:
        payload = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code} for {url}\n{payload}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Network error for {url}: {error}") from error


def preview(text: str, limit: int = 180) -> str:
    compact = " ".join(text.split())
    if len(compact) <= limit:
        return compact
    return compact[:limit] + "..."


def normalize_think(raw_value: str) -> Any:
    value = raw_value.strip().lower()
    if value == "omit":
        return None
    if value == "true":
        return True
    if value == "false":
        return False
    return raw_value


def list_tags(host: str, headers: dict[str, str], timeout: int) -> int:
    url = f"{host}/api/tags"
    started = time.time()
    status, content_type, payload = request_json(url, headers, None, timeout)
    elapsed_ms = int((time.time() - started) * 1000)

    models = payload.get("models") or []
    print(f"status={status}")
    print(f"content_type={content_type}")
    print(f"elapsed_ms={elapsed_ms}")
    print(f"model_count={len(models)}")

    for model in models[:50]:
        name = model.get("name", "")
        size = model.get("size", "")
        print(f"- {name} size={size}")
    return 0


def run_chat(host: str, headers: dict[str, str], model: str, prompt: str, stream: bool, think: Any, timeout: int) -> int:
    url = f"{host}/api/chat"
    body: dict[str, Any] = {
        "model": model,
        "messages": [
            {
                "role": "user",
                "content": prompt,
            }
        ],
        "stream": stream,
    }
    if think is not None:
        body["think"] = think

    print(f"url={url}")
    print(f"model={model}")
    print(f"stream={stream}")
    print(f"think={think if think is not None else 'omitted'}")
    print(f"prompt={prompt}")

    started = time.time()
    if stream:
        request_stream(url, headers, body, timeout)
    else:
        status, content_type, payload = request_json(url, headers, body, timeout)
        elapsed_ms = int((time.time() - started) * 1000)
        message = payload.get("message") or {}
        thinking = message.get("thinking") or payload.get("thinking") or ""
        content = message.get("content") or payload.get("content") or ""
        print(f"status={status}")
        print(f"content_type={content_type}")
        print(f"elapsed_ms={elapsed_ms}")
        print(f"done={payload.get('done')}")
        print(f"thinking_len={len(thinking)}")
        print(f"content_len={len(content)}")
        print(f"thinking_preview={preview(thinking)}")
        print(f"content_preview={preview(content)}")
        print("--- raw json ---")
        print(json.dumps(payload, indent=2, ensure_ascii=False)[:5000])
    return 0


def main() -> int:
    args = parse_args()
    if not args.api_key:
        print("Missing API key. Pass --api-key or set OLLAMA_API_KEY.", file=sys.stderr)
        return 2

    host = normalize_host(args.host)
    headers = build_headers(args.api_key)
    think = normalize_think(args.think)
    stream = args.stream == "true"

    if args.list_tags:
        return list_tags(host, headers, args.timeout)
    return run_chat(host, headers, args.model, args.prompt, stream, think, args.timeout)


if __name__ == "__main__":
    raise SystemExit(main())
