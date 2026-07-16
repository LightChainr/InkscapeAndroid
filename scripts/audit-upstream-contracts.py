#!/usr/bin/env python3
"""Audit the upstream source assumptions that the migration architecture depends on."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Check:
    name: str
    relative_path: str
    needles: tuple[str, ...]
    rationale: str


CHECKS: dict[str, tuple[Check, ...]] = {
    "inkscape": (
        Check(
            "cmake-and-cxx-baseline",
            "CMakeLists.txt",
            ("cmake_minimum_required(VERSION 3.24.0)", "set(CMAKE_CXX_STANDARD 20)"),
            "The Android build overlay and host compiler matrix depend on the current CMake/C++ baseline.",
        ),
        Check(
            "android-library-target",
            "src/CMakeLists.txt",
            ("if(NOT ANDROID)", "add_library(inkscape ${main_SRC})", "add_library(inkscape_base)"),
            "Inkscape already changes its application target shape on Android; the port should extend this rather than rebuild a second core.",
        ),
        Check(
            "gtk-binding-minimums",
            "CMakeScripts/DefineDependsandFlags.cmake",
            ("gtk4>=4.14.0", "gtkmm-4.0>=4.13.3", "glibmm-2.68>=2.78.1", "pangomm-2.48", "cairomm-1.16"),
            "The Android sysroot must provide the same C and C++ GTK stack expected by Inkscape.",
        ),
        Check(
            "unsafe-native-binding-fallback",
            "CMakeScripts/DefineDependsandFlags.cmake",
            ("ExternalProject_Add(glibmm", "ExternalProject_Add(gtkmm", "meson setup --libdir lib"),
            "These native fallback builds do not carry the Android cross files and must be blocked by preflight.",
        ),
        Check(
            "main-entry-and-desktop-environment",
            "src/inkscape-main.cpp",
            ("int main(int argc, char *argv[])", "set_extensions_env()", "PYTHONPATH", "XDG_DATA_DIRS"),
            "A thin Android host needs a non-conflicting callable entry and an Android resource policy.",
        ),
    ),
    "gtk": (
        Check(
            "android-backend-options",
            "meson.options",
            ("option('android-backend'", "option('android-runtime'"),
            "The primary architecture requires GTK's mainline Android backend and native runtime.",
        ),
        Check(
            "touch-cancel-preserved",
            "gdk/android/gdkandroidevents.c",
            ("AMOTION_EVENT_ACTION_CANCEL", "GDK_TOUCH_CANCEL"),
            "Finger touch sequences already expose explicit cancellation and should remain the reference path.",
        ),
        Check(
            "stylus-cancel-release-gap",
            "gdk/android/gdkandroidevents.c",
            ("treat cancel like a", "button up event", "GDK does not", "provide a cancel mechanism"),
            "The port needs a narrow stylus cancellation side channel or translator hook.",
        ),
        Check(
            "history-currently-discarded",
            "gdk/android/glue/java/org/gtk/android/ToplevelActivity.java",
            ("MotionEvent.obtainNoHistory(event)",),
            "Basic object editing can proceed without history, but future freehand input cannot assume it is preserved.",
        ),
        Check(
            "android-document-picker",
            "gtk/gtkfilechoosernativeandroid.c",
            ("action_open_document", "action_create_document", "action_open_document_tree"),
            "Open/Save Copy should first validate the existing GTK Android document picker bridge.",
        ),
    ),
    "pixiewood": (
        Check(
            "meson-application-contract",
            "README.md",
            ("android_exe_type: 'application'", "g_application_run"),
            "Pixiewood cannot directly replace Inkscape's CMake build; a thin Meson host is required.",
        ),
        Check(
            "linux-host-assumptions",
            "pixiewood",
            ("linux-x86_64", "nproc"),
            "The authoritative dependency build should remain on Linux unless host portability is fixed upstream.",
        ),
    ),
}


def audit_source(root: Path, checks: tuple[Check, ...]) -> tuple[list[dict[str, Any]], list[str]]:
    results: list[dict[str, Any]] = []
    errors: list[str] = []
    for check in checks:
        path = root / check.relative_path
        entry: dict[str, Any] = {
            "name": check.name,
            "path": str(path),
            "rationale": check.rationale,
            "needles": list(check.needles),
        }
        if not path.is_file():
            entry["passed"] = False
            entry["missingFile"] = True
            errors.append(f"{root.name}:{check.name}: missing {check.relative_path}")
            results.append(entry)
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        missing = [needle for needle in check.needles if needle not in text]
        entry["missingNeedles"] = missing
        entry["passed"] = not missing
        if missing:
            errors.append(
                f"{root.name}:{check.name}: expected source contracts disappeared: {missing}"
            )
        results.append(entry)
    return results, errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workspace", type=Path, default=Path(".out/upstream"))
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args(argv)

    report: dict[str, Any] = {"workspace": str(args.workspace), "sources": {}, "errors": []}
    for source, checks in CHECKS.items():
        results, errors = audit_source(args.workspace / source, checks)
        report["sources"][source] = results
        report["errors"].extend(errors)

    encoded = json.dumps(report, indent=2, sort_keys=True)
    print(encoded)
    if args.json_out:
        args.json_out.write_text(encoded + "\n", encoding="utf-8")
    return 2 if report["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
