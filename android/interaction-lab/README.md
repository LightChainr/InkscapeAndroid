# GTK Interaction Lab

P3 的最小 GTK Android 应用，用两个简单对象验证：

- pen 与 touch 路由；
- pointer registry；
- 单指无操作；
- 双指视口变换；
- `InteractionSession.complete()`；
- `InteractionSession.abort()`；
- cancel、pause、失焦和 Surface 重建；
- owner 与事务事件日志。

该实验场不能链接 Inkscape。只有实验场的输入和事务不变量通过后，才把同一 input core 接入 Inkscape。

建议内部模块：

```text
pointer-registry
input-owner
canvas-input-router
interaction-session
viewport-transform
trace-buffer
```
