import json
import tempfile
import unittest
from pathlib import Path

from tools.analyze_input_probe import analyze


class AnalyzeInputProbeTest(unittest.TestCase):
    def write_records(self, records):
        temp = tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", delete=False)
        with temp:
            for record in records:
                temp.write(json.dumps(record) + "\n")
        return Path(temp.name)

    def session(self):
        return {
            "schemaVersion": 1,
            "recordType": "session",
            "sessionId": "session-a",
            "createdWallTimeMillis": 1,
            "app": {"versionName": "test", "versionCode": 1, "buildType": "debug"},
            "device": {
                "manufacturer": "Xiaomi",
                "model": "test",
                "sdkInt": 36,
                "release": "test",
                "fingerprint": "test",
            },
            "display": {
                "widthPixels": 100,
                "heightPixels": 100,
                "densityDpi": 320,
                "refreshRateHz": 120,
            },
            "inputDevices": [],
        }

    def event(self, sequence, event_time, action=2, flags=0):
        return {
            "schemaVersion": 1,
            "recordType": "event",
            "sessionId": "session-a",
            "sequence": sequence,
            "dispatchPath": "touch",
            "sampleKind": "current",
            "captureMonotonicNanos": event_time * 1_000_000,
            "captureUptimeMillis": event_time,
            "eventTimeMillis": event_time,
            "eventTimeNanos": None,
            "actionMasked": action,
            "actionIndex": 0,
            "pointerCount": 1,
            "pointerIndex": 0,
            "pointerId": 7,
            "isActionPointer": True,
            "toolType": 2,
            "source": 0x4002,
            "deviceId": 3,
            "buttonState": 0,
            "metaState": 0,
            "flags": flags,
            "historySize": 0,
            "x": 10.0,
            "y": 20.0,
            "pressure": 0.5,
            "orientation": 0.0,
            "tilt": 0.1,
            "distance": 0.0,
        }

    def test_valid_log_summary(self):
        path = self.write_records([self.session(), self.event(1, 100), self.event(2, 108)])
        report = analyze(path)
        self.assertEqual(report.errors, [])
        self.assertEqual(report.record_counts["event"], 2)
        self.assertEqual(report.interval_summary()["medianMs"], 8)

    def test_non_monotonic_sequence_is_rejected(self):
        path = self.write_records([self.session(), self.event(2, 100), self.event(2, 108)])
        report = analyze(path)
        self.assertTrue(any("not greater" in error for error in report.errors))

    def test_missing_field_is_rejected(self):
        broken = self.event(1, 100)
        del broken["pressure"]
        path = self.write_records([self.session(), broken])
        report = analyze(path)
        self.assertTrue(any("pressure" in error for error in report.errors))

    def test_drop_records_are_counted(self):
        drop = {
            "schemaVersion": 1,
            "recordType": "drop",
            "sessionId": "session-a",
            "afterSequence": 9,
            "droppedRecords": 4,
            "queueCapacity": 1024,
        }
        path = self.write_records([self.session(), drop])
        report = analyze(path)
        self.assertEqual(report.dropped_records, 4)


if __name__ == "__main__":
    unittest.main()
