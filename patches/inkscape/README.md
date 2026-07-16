# Inkscape patch queue

按数字顺序保存可独立重放的补丁。候选范围：

- Android MVP 构建开关；
- 可调用的 `inkscape_android_run()`；
- 平板工作区；
- Canvas 输入策略；
- 显式工具 abort；
- Android 资源解析；
- 诊断和阶段测试接口。

禁止在此重写 SVG hit testing、文档模型或 Undo。每个补丁必须记录对应上游 commit SHA，并保持桌面构建可用。
