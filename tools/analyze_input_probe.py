#!/usr/bin/env python3
"""Validate and summarize input-probe JSONL logs using only the Python standard library."""

from __future__ import annotations

import argparse
import collections
import json
import math
import statistics
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

SCHEMA_VERSION = 1
ACTION_CANCEL = 3
ACTION_POINTER_UP = 6
FLAG_CANCELED = 0x20

BASE_REQUIRED = {"schemaVersion", "recordType", "sessionId"}
SESSION_REQUIRED = {"createdWallTimeMillis", "app", "device", "display", "inputDevices"}
EVENT_REQUIRED = {
    "sequence",
    "dispatchPath",
    "sampleKind",
    "captureMonotonicNanos",
    "captureUptimeMillis",
    "eventTimeMillis",
    "downTimeMillis",
    "actionMasked",
    "actionIndex",
    "pointerCount",
    "pointerIndex",
    "pointerId",
    "isActionPointer",
    "toolType",
    "source",
    "deviceId",
    "buttonState",
    "metaState",
    "flags",
    "historySize",
    "x",
    "y",
    "pressure",
    "orientation",
    "tilt",
    "distance",
}
DROP_REQUIRED = {"afterSequence", "droppedRecords", "queueCapacity"}


@dataclass
class Report:
    source: str
    line_count: int = 0
    record_counts: collections.Counter[str] = field(default_factory=collections.Counter)
    action_counts: collections.Counter[int] = field(default_factory=collections.Counter)
    tool_counts: collections.Counter[int] = field(default_factory=collections.Counter)
    dispatch_counts: collections.Counter[str] = field(default_factory=collections.Counter)
    flag_counts: collections.Counter[int] = field(default_factory=collections.Counter)
    sessions: set[str] = field(default_factory=set)
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    dropped_records: int = 0
    cancel_records: int = 0
    canceled_pointer_records: int = 0
    pointer_up_with_other_flags: int = 0
    current_event_times: dict[tuple[str, int, int, int, str], list[int]] = field(
        default_factory=lambda: collections.defaultdict(list)
    )
    last_sequence: dict[str, int] = field(default_factory=dict)

    def add_record(self, line_number: int, record: dict[str, Any]) -> None:
        self.line_count += 1
        missing_base = BASE_REQUIRED - record.keys()
        if missing_base:
            self.errors.append(f"line {line_number}: missing base fields {sorted(missing_base)}")
            return
        if record["schemaVersion"] != SCHEMA_VERSION:
            self.errors.append(
                f"line {line_number}: unsupported schemaVersion={record['schemaVersion']}"
            )
            return

        record_type = record["recordType"]
        session_id = record["sessionId"]
        if not isinstance(session_id, str) or not session_id:
            self.errors.append(f"line {line_number}: invalid sessionId")
            return
        self.sessions.add(session_id)
        self.record_counts[record_type] += 1

        if record_type == "session":
            self._require(line_number, record, SESSION_REQUIRED)
        elif record_type == "event":
            if not self._require(line_number, record, EVENT_REQUIRED):
                return
            self._add_event(line_number, record)
        elif record_type == "drop":
            if self._require(line_number, record, DROP_REQUIRED):
                dropped = record["droppedRecords"]
                if isinstance(dropped, int) and dropped > 0:
                    self.dropped_records += dropped
                else:
                    self.errors.append(f"line {line_number}: invalid droppedRecords")
        else:
            self.errors.append(f"line {line_number}: unknown recordType={record_type!r}")

    def _require(self, line_number: int, record: dict[str, Any], required: set[str]) -> bool:
        missing = required - record.keys()
        if missing:
            self.errors.append(f"line {line_number}: missing fields {sorted(missing)}")
            return False
        return True

    def _add_event(self, line_number: int, record: dict[str, Any]) -> None:
        sequence = record["sequence"]
        session_id = record["sessionId"]
        if not isinstance(sequence, int) or sequence < 0:
            self.errors.append(f"line {line_number}: invalid sequence")
            return
        previous = self.last_sequence.get(session_id)
        if previous is not None and sequence <= previous:
            self.errors.append(
                f"line {line_number}: sequence {sequence} is not greater than {previous}"
            )
        self.last_sequence[session_id] = sequence

        sample_kind = record["sampleKind"]
        if sample_kind not in {"current", "historical"}:
            self.errors.append(f"line {line_number}: invalid sampleKind={sample_kind!r}")

        action = record["actionMasked"]
        tool = record["toolType"]
        flags = record["flags"]
        dispatch = record["dispatchPath"]
        self.action_counts[action] += 1
        self.tool_counts[tool] += 1
        self.flag_counts[flags] += 1
        self.dispatch_counts[dispatch] += 1

        if action == ACTION_CANCEL:
            self.cancel_records += 1
        if action in {ACTION_CANCEL, ACTION_POINTER_UP} and flags & FLAG_CANCELED:
            self.canceled_pointer_records += 1
        elif action == ACTION_POINTER_UP and flags != 0:
            self.pointer_up_with_other_flags += 1

        pointer_count = record["pointerCount"]
        pointer_index = record["pointerIndex"]
        if not isinstance(pointer_count, int) or pointer_count < 1:
            self.errors.append(f"line {line_number}: invalid pointerCount")
        if not isinstance(pointer_index, int) or not 0 <= pointer_index < pointer_count:
            self.errors.append(f"line {line_number}: pointerIndex outside pointerCount")

        numeric_fields = (
            "captureMonotonicNanos",
            "captureUptimeMillis",
            "eventTimeMillis",
            "downTimeMillis",
            "x",
            "y",
            "pressure",
            "orientation",
            "tilt",
            "distance",
        )
        for field_name in numeric_fields:
            value = record[field_name]
            if not isinstance(value, (int, float)) or not math.isfinite(value):
                self.errors.append(f"line {line_number}: invalid numeric field {field_name}")

        if sample_kind == "current":
            key = (
                session_id,
                record["deviceId"],
                record["pointerId"],
                record["downTimeMillis"],
                dispatch,
            )
            self.current_event_times[key].append(record["eventTimeMillis"])

    def interval_summary(self) -> dict[str, float | int | None]:
        deltas: list[int] = []
        for times in self.current_event_times.values():
            for previous, current in zip(times, times[1:]):
                if current > previous:
                    deltas.append(current - previous)
        if not deltas:
            return {"count": 0, "medianMs": None, "p95Ms": None, "maxMs": None}
        ordered = sorted(deltas)
        p95_index = min(len(ordered) - 1, math.ceil(len(ordered) * 0.95) - 1)
        return {
            "count": len(ordered),
            "medianMs": statistics.median(ordered),
            "p95Ms": ordered[p95_index],
            "maxMs": ordered[-1],
        }

    def as_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "lineCount": self.line_count,
            "sessions": sorted(self.sessions),
            "recordCounts": dict(sorted(self.record_counts.items())),
            "actionCounts": {str(k): v for k, v in sorted(self.action_counts.items())},
            "toolCounts": {str(k): v for k, v in sorted(self.tool_counts.items())},
            "dispatchCounts": dict(sorted(self.dispatch_counts.items())),
            "flagCounts": {str(k): v for k, v in sorted(self.flag_counts.items())},
            "cancelRecords": self.cancel_records,
            "canceledPointerRecords": self.canceled_pointer_records,
            "pointerUpWithOtherFlags": self.pointer_up_with_other_flags,
            "droppedRecords": self.dropped_records,
            "sampleIntervals": self.interval_summary(),
            "errors": self.errors,
            "warnings": self.warnings,
        }


def iter_jsonl(path: Path) -> Iterable[tuple[int, dict[str, Any]]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            line = raw_line.strip()
            if not line:
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                yield line_number, {"__parse_error__": str(error)}
                continue
            if not isinstance(value, dict):
                yield line_number, {"__parse_error__": "record is not a JSON object"}
                continue
            yield line_number, value


def analyze(path: Path) -> Report:
    report = Report(str(path))
    for line_number, record in iter_jsonl(path):
        parse_error = record.get("__parse_error__")
        if parse_error is not None:
            report.errors.append(f"line {line_number}: {parse_error}")
            continue
        report.add_record(line_number, record)
    if report.record_counts["session"] == 0:
        report.warnings.append("no session metadata record found")
    if report.record_counts["event"] == 0:
        report.warnings.append("no event records found")
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--strict", action="store_true", help="fail on schema errors or dropped records")
    args = parser.parse_args(argv)

    report = analyze(args.log)
    encoded = json.dumps(report.as_dict(), ensure_ascii=False, indent=2, sort_keys=True)
    print(encoded)
    if args.json_out:
        args.json_out.write_text(encoded + "\n", encoding="utf-8")

    if report.errors:
        return 2
    if args.strict and report.dropped_records:
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
