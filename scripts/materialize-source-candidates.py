#!/usr/bin/env python3
"""Fetch immutable source candidates into a disposable workspace."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any


def run(command: list[str], cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)


def materialize(name: str, source: dict[str, Any], output: Path) -> dict[str, Any]:
    target = output / name
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)

    result: dict[str, Any] = {
        "name": name,
        "url": source["url"],
        "requestedCommit": source["commit"],
        "path": str(target),
    }

    commands = [
        ["git", "init", "--quiet"],
        ["git", "remote", "add", "origin", source["url"]],
        ["git", "fetch", "--quiet", "--depth=1", "origin", source["commit"]],
        ["git", "checkout", "--quiet", "--detach", "FETCH_HEAD"],
    ]
    for command in commands:
        completed = run(command, cwd=target)
        if completed.returncode != 0:
            result["error"] = (completed.stderr or completed.stdout).strip()
            result["failedCommand"] = command
            return result

    resolved = run(["git", "rev-parse", "HEAD"], cwd=target)
    if resolved.returncode != 0:
        result["error"] = resolved.stderr.strip()
        return result
    result["resolvedCommit"] = resolved.stdout.strip()
    result["ok"] = result["resolvedCommit"] == source["commit"]
    if not result["ok"]:
        result["error"] = "resolved commit does not match requested commit"
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("sources.candidates.toml"))
    parser.add_argument("--output", type=Path, default=Path(".out/upstream"))
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args(argv)

    with args.manifest.open("rb") as handle:
        manifest = tomllib.load(handle)

    args.output.mkdir(parents=True, exist_ok=True)
    report: dict[str, Any] = {"output": str(args.output), "sources": {}, "errors": []}
    for name in ("inkscape", "gtk", "pixiewood"):
        source = manifest[name]
        entry = materialize(name, source, args.output)
        report["sources"][name] = entry
        if not entry.get("ok"):
            report["errors"].append(f"failed to materialize {name}")

    encoded = json.dumps(report, indent=2, sort_keys=True)
    print(encoded)
    if args.json_out:
        args.json_out.write_text(encoded + "\n", encoding="utf-8")
    return 2 if report["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
