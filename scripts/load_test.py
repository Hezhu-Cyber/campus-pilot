"""Concurrent read-only assistant load test."""

from __future__ import annotations

import argparse
import json
import os
import statistics
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.request import Request, urlopen

BASE_URL = os.getenv("LOAD_BASE_URL", "http://127.0.0.1:8081").rstrip("/")
TOKEN = os.getenv("LOAD_AUTH_TOKEN", "")
REPORT = Path(__file__).resolve().parents[1] / "reports" / "load-latest.json"


def one_request(index: int) -> dict:
    started = time.perf_counter()
    payload = json.dumps({
        "threadId": f"load-{uuid.uuid4()}",
        "message": "本周有哪些活动？",
        "pagePath": "/load-test",
    }).encode("utf-8")
    request = Request(
        f"{BASE_URL}/assistant/chat",
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json", "authorization": TOKEN},
    )
    try:
        with urlopen(request, timeout=60) as response:
            body = json.loads(response.read().decode("utf-8"))
        return {
            "index": index,
            "ok": response.status == 200 and bool(body.get("success")),
            "latency": time.perf_counter() - started,
        }
    except Exception as exc:
        return {
            "index": index,
            "ok": False,
            "latency": time.perf_counter() - started,
            "error": str(exc),
        }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--requests", type=int, default=50)
    parser.add_argument("--concurrency", type=int, default=10)
    args = parser.parse_args()
    if not TOKEN:
        raise RuntimeError("LOAD_AUTH_TOKEN is required")
    if args.requests < 1 or args.concurrency < 1:
        raise ValueError("requests and concurrency must be positive")

    started = time.time()
    results = []
    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(one_request, index) for index in range(args.requests)]
        for future in as_completed(futures):
            results.append(future.result())
    elapsed = time.time() - started
    latencies = sorted(item["latency"] for item in results)
    success = sum(1 for item in results if item["ok"])
    p95_index = min(len(latencies) - 1, max(0, int(len(latencies) * 0.95) - 1))
    report = {
        "requests": args.requests,
        "concurrency": args.concurrency,
        "success": success,
        "successRate": success / args.requests,
        "elapsedSeconds": elapsed,
        "throughputRps": args.requests / elapsed if elapsed else 0,
        "latencySeconds": {
            "avg": statistics.mean(latencies),
            "p50": latencies[len(latencies) // 2],
            "p95": latencies[p95_index],
            "max": max(latencies),
        },
        "errors": [item for item in results if not item["ok"]][:10],
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["successRate"] >= 0.99 else 1


if __name__ == "__main__":
    raise SystemExit(main())
