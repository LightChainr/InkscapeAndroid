# Input probe usage

## Build

The GitHub Actions workflow `Input probe build` produces a debug APK. A local build with the locked probe toolchain is:

```sh
cd android/input-probe
gradle :app:assembleDebug
```

## Install and launch

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start \
  -n io.github.lightchainr.inkscapeandroid.inputprobe/.MainActivity
```

## Collect JSONL

The debug application writes to its private files directory. Export with `run-as`:

```sh
PACKAGE=io.github.lightchainr.inkscapeandroid.inputprobe
adb shell run-as "$PACKAGE" ls files/input-probe
adb exec-out run-as "$PACKAGE" \
  cat files/input-probe/events.jsonl > events.jsonl
```

Clear the previous capture before a controlled test session:

```sh
adb shell run-as "$PACKAGE" rm -f files/input-probe/events.jsonl
```

## Required capture metadata

Store beside the JSONL file:

```text
device model
HyperOS version
Android API level
pen model
probe commit SHA
capture scenario
whether USB or wireless ADB was used
```

Do not commit raw captures until they have been checked for sensitive device or path information.
