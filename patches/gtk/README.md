# GTK patch queue

只接受由目标设备证据证明必要的窄补丁，例如：

- stylus `ACTION_CANCEL` 与正常 release 的区分；
- Android 13+ `FLAG_CANCELED` pointer 通知；
- cancel 在工具提交前到达的时序保证；
- 目标设备上可复现的 renderer/input 修复。

禁止复制完整 MotionEvent 流或建立与 GDK 并行的输入系统。优先将通用修复整理为可上游提交的独立补丁。
