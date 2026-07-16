# Android Input Probe

P1 的独立 Gradle 应用。只记录 `MotionEvent`，不依赖 GTK 或 Inkscape。

最低日志字段：event time、arrival time、action、pointer ID、tool type、source、坐标、pressure、tilt、orientation、distance、buttons、flags、history size、device ID。

输出为 JSON Lines，并附设备型号、系统版本和应用 build ID。探针不得推断手掌或触控笔语义，原始事件和解释报告分开保存。

必须覆盖：

- 轻点、慢拖、快拖；
- hover、压力和侧键；
- 一指、两指、两指变一指；
- pen 与手掌同时接触；
- `ACTION_CANCEL`；
- Android 13+ `FLAG_CANCELED`；
- 切后台、系统面板、锁屏和 Surface 变化。
