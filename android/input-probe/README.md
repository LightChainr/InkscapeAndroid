# Android Input Probe

P1 的独立 Android 应用。它只记录 `MotionEvent`，不依赖 GTK 或 Inkscape。

## 设计目标

探针用于回答三个问题：

1. 小米焦点触控笔向第三方应用实际暴露哪些标准 Android 字段；
2. pen、finger、palm 和 canceled pointer 的事件顺序是什么；
3. GTK adapter 后续必须保留哪些原始语义。

探针不负责推断“这是手掌”或“这是点击”。原始日志与解释报告必须分开保存。

## 数据路径

```text
MotionEvent callback
  ↓ 复制 primitive snapshot
有界队列（8192 records）
  ↓
后台线程 JSON 编码与 UTF-8 JSONL 写盘
```

若队列溢出，日志会写入 `recordType=drop`，并记录丢失数量。存在 drop 的日志不能用于采样率或完整序列结论。

数据契约：[`schemas/input-probe-v1.schema.json`](../../schemas/input-probe-v1.schema.json)。

每次启动写入一条 session 记录，包括：

- 应用版本和 build type；
- 设备型号、Android/HyperOS fingerprint；
- 屏幕密度与刷新率；
- 已连接 input devices；
- 每个 input device 的 motion ranges。

每个 event sample 至少包括：

- current/historical 标记；
- event time、capture monotonic time、capture uptime；
- action、action pointer、pointer ID/index/count；
- tool type、source、device ID、display ID；
- pressure、tilt、orientation、distance；
- size、touch/tool major/minor；
- button、meta、flags、classification；
- raw/local coordinates 和 precision。

`System.nanoTime()` 与 MotionEvent event time 不应直接相减，除非先确认时钟基准。分析器默认只对明确可比较的数据做统计。

## 构建

CI 使用 JDK 17、AGP 9.2.0、Gradle 9.4.1 和 Android 36 SDK：

```sh
cd android/input-probe
gradle :app:assembleDebug --stacktrace
```

安装：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start \
  -n io.github.lightchainr.inkscapeandroid.inputprobe/.MainActivity
```

## 导出

```sh
PACKAGE=io.github.lightchainr.inkscapeandroid.inputprobe
adb exec-out run-as "$PACKAGE" \
  cat files/input-probe/events.jsonl > events.jsonl
```

验证：

```sh
bash scripts/dev probe-analyze events.jsonl
```

## 必测场景

- 轻点、慢拖、快拖；
- hover、压力和侧键；
- 一指、两指、两指变一指；
- pen 先落下后放手掌；
- finger 先落下后落笔；
- 双指过程中落笔；
- `ACTION_CANCEL`；
- Android 13+ `FLAG_CANCELED`；
- 切后台、系统面板、锁屏和窗口失焦。

每个场景开始前记录操作说明和时间，原始 JSONL 不做人工编辑。
