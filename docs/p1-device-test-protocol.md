# P1 小米平板输入测试协议

目标设备：小米平板 7 Ultra + 小米焦点触控笔。

本协议用于产生可复核的原始输入证据。测试者不得根据屏幕表现修改 JSONL，也不得把“没有观察到事件”直接解释为设备不支持。

## 1. 测试前记录

每轮测试记录：

```text
测试编号
日期与时区
测试者
平板型号与序列标识（可匿名化）
HyperOS / Android 版本
Build fingerprint
输入探针 APK SHA-256
输入探针应用版本
焦点触控笔型号/固件/电量（系统可见时）
屏幕刷新率设置
横屏/竖屏
是否连接键盘、鼠标、USB、充电器
是否开启悬浮窗、分屏、游戏加速或省电模式
```

建议：

- 固定横屏；
- 使用 USB ADB；
- 关闭自动旋转、分屏和悬浮窗；
- 保持屏幕刷新率配置不变；
- 测试前重启探针应用；
- 不同时运行 Mi Canvas 或其他触控笔应用。

## 2. 获取 APK

从 PR 的 `Input probe build` 工作流下载 `input-probe-debug` artifact。

记录 APK SHA-256：

```sh
shasum -a 256 app-debug.apk
adb install -r app-debug.apk
```

## 3. 日志隔离

探针目前把多个 session 追加到同一个文件。每轮正式采集前清空旧日志：

```sh
PACKAGE=io.github.lightchainr.inkscapeandroid.inputprobe
adb shell am force-stop "$PACKAGE"
adb shell run-as "$PACKAGE" rm -f files/input-probe/events.jsonl
adb shell am start -n "$PACKAGE/.MainActivity"
```

看到屏幕上的新 session UUID 后再开始动作。

## 4. 场景序列

每个场景开始前静止两秒，完成后静止两秒。操作记录模板见 `tests/input-fixtures/p1-session-notes-template.csv`。

### P1-01 笔悬停

1. 笔从屏幕外进入悬停范围；
2. 沿水平线慢速悬停；
3. 离开悬停范围；
4. 重复三次。

观察字段：hover enter/move/exit、toolType、distance、tilt、orientation、buttons。

### P1-02 笔轻点

在五个不同位置轻点，每次完全离开屏幕。

观察字段：DOWN/UP、pressure 最小值、pointer ID 复用、历史采样。

### P1-03 慢速直线拖动

笔接触后用约两秒横向拖动，再正常抬笔，重复三次。

观察字段：MOVE 间隔、historySize、pressure、tilt、坐标单调性。

### P1-04 快速拖动

执行短距离快速拖动，重复五次。

观察字段：historical samples、队列 drop、事件间隔尾部。

### P1-05 压力变化

保持位置基本不变，从轻压逐渐加压再减压。不得使用压力触发点击逻辑。

观察字段：pressure 范围、噪声、是否大于 1、是否有明显死区。

### P1-06 侧键与橡皮端

分别在悬停和接触状态按每个可用按钮；若有橡皮端则测试。

观察字段：buttonState、ACTION_BUTTON_PRESS/RELEASE、toolType=ERASER。

### P1-07 单指

单指点按、慢拖、长按后抬起。

观察字段：FINGER tool type、touch major/minor、classification、flags。

### P1-08 双指

两指同时落下，依次执行：

- 同向平移；
- pinch in/out；
- 小角度旋转；
- 正常全部抬起。

观察字段：pointer IDs、POINTER_DOWN/UP action pointer、历史采样。

### P1-09 两指变一指

双指移动过程中先抬起一根，剩余一根继续移动一秒再抬起。

该场景用于验证后续 `BlockUntilAllTouchUp`，探针本身不做策略判断。

### P1-10 笔先落下，再放手掌

1. 笔开始慢速拖动；
2. 保持笔接触，把书写手掌自然放到屏幕；
3. 继续拖动；
4. 抬笔和手掌。

重复五次，改变手掌接触位置。

观察字段：新增 finger pointer、ACTION_CANCEL、FLAG_CANCELED、pointer up 顺序。

### P1-11 手指先落下，再落笔

一根手指保持接触，然后笔落下并移动，最后分别抬起。

用于确认混合 tool type 的事件分发和 pointer sequence。

### P1-12 双指期间落笔

双指移动时落笔、移动、抬笔，再结束双指。

用于确认笔是否进入同一 MotionEvent、独立 pointer source，或被系统抑制。

### P1-13 生命周期中断

笔拖动期间分别触发：

- 下拉系统面板；
- Home/切后台；
- 锁屏；
- Activity 失去焦点。

每种中断单独测试并重新启动应用。

观察字段：最后一个 MotionEvent 是否为 CANCEL/UP、应用暂停前日志是否 flush。

### P1-14 长时间压力测试

连续快速笔移动与双指操作至少五分钟。

唯一目标：确认 `drop` 记录是否出现、文件是否持续可解析、应用是否卡死。

## 5. 导出证据

```sh
PACKAGE=io.github.lightchainr.inkscapeandroid.inputprobe
adb shell am force-stop "$PACKAGE"
adb exec-out run-as "$PACKAGE" \
  cat files/input-probe/events.jsonl > events.jsonl

shasum -a 256 events.jsonl > events.jsonl.sha256
python3 tools/analyze_input_probe.py events.jsonl \
  --strict \
  --json-out events.summary.json
```

同时保存：

- `adb shell getprop > device-getprop.txt`；
- `adb shell dumpsys input > dumpsys-input.txt`；
- `adb shell dumpsys display > dumpsys-display.txt`；
- 场景记录 CSV；
- APK SHA-256；
- analyzer summary。

涉及隐私或设备序列号的字段可在设备报告中脱敏，但原始事件时间和输入字段不得修改。

## 6. P1 通过条件

必须满足：

- JSONL schema/analyzer 无错误；
- `droppedRecords = 0`；
- 有且仅有本轮预期 session；
- 关键场景能根据时间和 action 序列定位；
- pen 和 finger 的 tool/source 行为有原始记录；
- cancel/palm 行为以“观察到/未观察到/不确定”描述，不猜测；
- 报告列出设备未暴露的字段和零值轴；
- 证据包有 SHA-256 和复现命令。

P1 通过不代表输入策略已经实现。只有 P3 GTK Interaction Lab 才验证 owner 和 abort 行为。
