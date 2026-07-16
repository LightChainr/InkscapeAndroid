#!/usr/bin/env python3
"""Fail fast before configuring Inkscape for Android.

This script intentionally checks the target pkg-config environment before CMake can
fall back to native ExternalProject builds of glibmm/gtkmm.
"""

from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any

FORBIDDEN_HOST_PATHS = (
    "/opt/homebrew",
    "/usr/local/Cellar",
    "/System/Library/Frameworks",
    "/Library/Frameworks",
)


def run(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, text=True, capture_output=True, check=False)


def package_name(requirement: str) -> str:
    return requirement.split()[0]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("deps/android-target-dependencies.toml"),
    )
    parser.add_argument("--pkg-config", default=os.environ.get("PKG_CONFIG", "pkg-config"))
    parser.add_argument("--strict-env", action="store_true")
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args(argv)

    with args.manifest.open("rb") as handle:
        manifest = tomllib.load(handle)

    requirements: list[str] = manifest["required_pkg_config"]["packages"]
    result: dict[str, Any] = {
        "manifest": str(args.manifest),
        "pkgConfig": args.pkg_config,
        "environment": {
            "PKG_CONFIG_SYSROOT_DIR": os.environ.get("PKG_CONFIG_SYSROOT_DIR", ""),
            "PKG_CONFIG_LIBDIR": os.environ.get("PKG_CONFIG_LIBDIR", ""),
            "PKG_CONFIG_PATH": os.environ.get("PKG_CONFIG_PATH", ""),
        },
        "packages": {},
        "errors": [],
        "warnings": [],
    }

    if args.strict_env:
        for variable in ("PKG_CONFIG_SYSROOT_DIR", "PKG_CONFIG_LIBDIR"):
            if not result["environment"][variable]:
                result["errors"].append(f"{variable} is not set")
        if result["environment"]["PKG_CONFIG_PATH"]:
            result["warnings"].append(
                "PKG_CONFIG_PATH is set; target builds should normally rely on PKG_CONFIG_LIBDIR"
            )

    version = run([args.pkg_config, "--version"])
    if version.returncode != 0:
        result["errors"].append(
            f"cannot execute pkg-config wrapper: {version.stderr.strip() or version.stdout.strip()}"
        )
    else:
        result["pkgConfigVersion"] = version.stdout.strip()

    for requirement in requirements:
        name = package_name(requirement)
        exists = run([args.pkg_config, "--exists", requirement])
        entry: dict[str, Any] = {"requirement": requirement, "found": exists.returncode == 0}
        if exists.returncode == 0:
            modversion = run([args.pkg_config, "--modversion", name])
            entry["version"] = modversion.stdout.strip() if modversion.returncode == 0 else None
            flags = run([args.pkg_config, "--cflags", "--libs", name])
            entry["flags"] = flags.stdout.strip() if flags.returncode == 0 else ""
            contaminated = [path for path in FORBIDDEN_HOST_PATHS if path in entry["flags"]]
            if contaminated:
                entry["hostPathContamination"] = contaminated
                result["errors"].append(
                    f"{name} contains host paths: {', '.join(contaminated)}"
                )
        else:
            diagnostic = (exists.stderr or exists.stdout).strip()
            entry["diagnostic"] = diagnostic
            result["errors"].append(f"missing target package: {requirement}")
        result["packages"][name] = entry

    # Inspect the complete link surface too, because a dependency can inject a host path indirectly.
    names = [package_name(item) for item in requirements]
    aggregate = run([args.pkg_config, "--cflags", "--libs", *names])
    if aggregate.returncode == 0:
        result["aggregateFlags"] = aggregate.stdout.strip()
        for forbidden in FORBIDDEN_HOST_PATHS:
            if forbidden in result["aggregateFlags"]:
                result["errors"].append(f"aggregate flags contain host path: {forbidden}")
    else:
        result["aggregateDiagnostic"] = (aggregate.stderr or aggregate.stdout).strip()

    encoded = json.dumps(result, indent=2, sort_keys=True)
    print(encoded)
    if args.json_out:
        args.json_out.write_text(encoded + "\n", encoding="utf-8")

    if result["errors"]:
        print("Android target preflight failed", file=sys.stderr)
        return 2
    print("Android target preflight passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
