#!/usr/bin/env python3
"""Validate immutable upstream candidate SHAs, optionally fetching them from their remotes."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
import tomllib
from pathlib import Path
from typing import Any

SHA1 = re.compile(r"^[0-9a-f]{40}$")


def run(command: list[str], cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)


def verify_remote(name: str, source: dict[str, Any]) -> dict[str, Any]:
    result: dict[str, Any] = {"url": source["url"], "commit": source["commit"]}
    with tempfile.TemporaryDirectory(prefix=f"source-{name}-") as directory:
        work = Path(directory)
        initialized = run(["git", "init", "--quiet"], cwd=work)
        if initialized.returncode != 0:
            result["error"] = initialized.stderr.strip()
            return result
        fetched = run(
            ["git", "fetch", "--quiet", "--depth=1", source["url"], source["commit"]],
            cwd=work,
        )
        if fetched.returncode != 0:
            result["error"] = fetched.stderr.strip() or fetched.stdout.strip()
            return result
        resolved = run(["git", "rev-parse", "FETCH_HEAD^{commit}"], cwd=work)
        if resolved.returncode != 0:
            result["error"] = resolved.stderr.strip()
            return result
        result["resolvedCommit"] = resolved.stdout.strip()
        result["reachable"] = result["resolvedCommit"] == source["commit"]
        if not result["reachable"]:
            result["error"] = "FETCH_HEAD did not resolve to requested commit"
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("sources.candidates.toml"))
    parser.add_argument("--network", action="store_true", help="fetch each SHA from its official remote")
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args(argv)

    with args.manifest.open("rb") as handle:
        manifest = tomllib.load(handle)

    report: dict[str, Any] = {
        "manifest": str(args.manifest),
        "status": manifest.get("status"),
        "network": args.network,
        "sources": {},
        "errors": [],
    }

    for name in ("inkscape", "gtk", "pixiewood"):
        source = manifest.get(name)
        if not isinstance(source, dict):
            report["errors"].append(f"missing source table: {name}")
            continue
        url = source.get("url")
        commit = source.get("commit")
        if not isinstance(url, str) or not url.startswith("https://"):
            report["errors"].append(f"invalid URL for {name}")
            continue
        if not isinstance(commit, str) or not SHA1.fullmatch(commit):
            report["errors"].append(f"invalid immutable SHA for {name}")
            continue
        if args.network:
            entry = verify_remote(name, source)
            if not entry.get("reachable"):
                report["errors"].append(f"{name} SHA is not reachable from official remote")
        else:
            entry = {"url": url, "commit": commit, "syntaxValid": True}
        report["sources"][name] = entry

    encoded = json.dumps(report, indent=2, sort_keys=True)
    print(encoded)
    if args.json_out:
        args.json_out.write_text(encoded + "\n", encoding="utf-8")
    return 2 if report["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
