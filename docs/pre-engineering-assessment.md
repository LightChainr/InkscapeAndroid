# 前期工程评估

本文记录在进入 GTK Interaction Lab 和 Inkscape Android 集成前，已经核实的源码事实、工程约束和验证顺序。

## 1. 当前结论

项目不应立即修改 Inkscape 的选择工具。正确顺序是：

```text
平台无关输入/事务核心
        ↓
小米设备原始输入数据
        ↓
GTK Android Interaction Lab
        ↓
Inkscape 启动、资源和只读 Canvas
        ↓
选择、移动、Undo、显式 Abort
```

在进入下一层之前，上一层必须有自动测试或真机日志证据。

## 2. 已核实的 Inkscape 源码事实

研究候选：`85e98c11b8eb9c6cdbd46df509f99f6100056166`。最终使用前必须确认该 SHA 可从 Inkscape 官方 GitLab 获取。

### 2.1 构建与语言

- CMake 最低版本为 3.24。
- C++ 标准为 C++20。
- 当前源码版本标识为 1.5-dev。
- Inkscape 已经存在 `if(ANDROID)` 分支：桌面端创建 executable，Android 创建 library。
- `inkscape_base` 已经包含 Document、Selection、SelTrans、DocumentUndo、Canvas/UI 等大部分核心源文件。

这意味着不需要从零发明一个“核心 SDK”，但需要处理以下边界：

- `inkscape-main.cpp` 仍定义普通 `main()`；薄 Host 不能同时定义另一个冲突的 `main()`。
- Android 路径需要将入口改成可调用函数，例如 `inkscape_android_run()`，或用一个不冲突的 wrapper target。
- 当前启动代码设置 PATH、PYTHONPATH 和 XDG_DATA_DIRS；Android MVP 必须禁用扩展环境设置，并使用 asset/resource resolver。

### 2.2 C++ GTK 绑定是硬前置

当前 Inkscape 要求：

- GTK 4.14+
- gtkmm 4.13.3+
- glibmm 2.78.1+
- cairomm 1.16
- pangomm 2.48

若系统找不到 glibmm/gtkmm，Inkscape 会用 CMake `ExternalProject` 下载并构建它们。该回退命令没有 Android cross-file，也依赖 host 端生成工具，不适用于本项目。

因此：

> Android sysroot 必须在 Inkscape CMake configure 之前提供完整 gtkmm/glibmm；找不到时必须 fail fast，禁止进入上游 ExternalProject 回退。

对应机器检查：`scripts/check-inkscape-cross-preconditions.py`。

### 2.3 MVP 目标依赖面仍然较大

即使关闭 PDF、CDR、Visio、拼写检查和脚本扩展，基础 SVG 编辑仍依赖 GTK/gtkmm、Pango、HarfBuzz、Fontconfig、Graphene、Cairo、GLib/GIO、ICU、GSL、Boehm GC、LCMS2、double-conversion、LibXML2、LibXSLT、PNG、Potrace、Boost 和 2Geom 等。

依赖分层记录在 `deps/android-target-dependencies.toml`。P2 的主要工作不是“编译 Inkscape”，而是生成一个可重定位、无 host 污染、ABI 一致的目标 sysroot。

## 3. 已核实的 GTK Android 约束

研究候选：`939826ae53d9b3029270546d6f3a4fb7988a7f85`。

GTK Android 主线已经提供：

- Android GDK backend 和 runtime；
- SurfaceView/GTK Surface 桥接；
- finger touch sequence；
- stylus、eraser、hover、pressure、distance 和 tilt；
- Android 文件选择器和 content URI GFile。

仍需补齐：

1. Pointer-source stylus `ACTION_CANCEL` 当前会退化为 button release，不能表达“回滚”。
2. Android 13+ `ACTION_POINTER_UP + FLAG_CANCELED` 需要保留为取消语义。
3. Java glue 使用 `MotionEvent.obtainNoHistory()`，未来自由笔迹需要重新评估；基础对象选择和移动暂不依赖 history。
4. 所有取消通知必须早于工具的正常 release 提交。

## 4. 输入探针的测量设计

现有探针 v0.1 可以验证事件是否出现，但在 UI 线程上构造 JSON 并同步写盘，会扰动高频事件和延迟测量。

探针 v1 必须采用：

```text
MotionEvent callback
  ↓ 只复制 primitive snapshot
有界内存队列
  ↓
后台线程 JSON 编码和写盘
```

必须记录：

- session/build/device/display/input-device 元数据；
- current 与 historical sample；
- pointer ID、tool type、source、button、flags；
- event time 与 capture time，且明确时钟来源；
- queue drop 记录；
- schemaVersion 和严格递增 sequence。

数据契约：`schemas/input-probe-v1.schema.json`。
离线检查：`tools/analyze_input_probe.py`。

重要限制：`System.nanoTime()` 和 MotionEvent event time 不应在未确认时钟基准前直接相减。日志同时记录 monotonic capture time 和 uptime-based time，分析器只使用明确可比较的字段。

## 5. 输入与事务核心

`core/tablet-input` 是平台无关的 C++20 参考核心，不依赖 GTK、Android 或 Inkscape。它提前锁定以下不变量：

- 笔拥有对象编辑权时，finger 不能抢占；
- 第一根 finger 只进入 pending，不执行操作；
- 第二根 finger 才开始 viewport transform；
- 双指中任一 finger 离开即结束手势，剩余单指被阻塞；
- touch 已获得 owner 时，新 pen sequence 整体忽略；
- cancel 与 normal up 是不同动作；
- abort 后迟到的 up 不能提交；
- complete/abort 均只能成功一次。

该核心不是最终 GTK adapter，而是 GTK Interaction Lab 和 Inkscape adapter 的可执行规范。

## 6. 构建拓扑评估

### 权威路径

Linux x86_64 CI 负责：

- GTK/Pixiewood 和目标依赖；
- 可重定位 arm64 sysroot；
- GTK Interaction Lab APK；
- 最终 Inkscape native/Host APK 的可重复构建。

Mac 负责：

- 代码、补丁和文档操作；
- Android Studio；
- ADB/LLDB/Perfetto；
- 真机测试和日志采集；
- 可选的 NDK 增量编译加速。

Mac 本地完整 native 构建不是 P0/P1 的前置条件。

### 为什么不直接套 Pixiewood

Pixiewood要求应用是 Meson `android_exe_type: 'application'` target，而 Inkscape 使用 CMake。它还包含 Linux host 假设。因此合理边界是：

- Pixiewood/GTK CI 负责 runtime、sysroot 和 package template；
- Inkscape继续由 CMake构建；
- 薄 Meson Host 仅提供 GTK Android application entry，并调用不冲突的 Inkscape Android 入口。

## 7. 技术关卡

### Gate A：前期核心

必须同时通过：

- C++ input core 在 Linux/macOS 编译并通过测试；
- source candidates 格式校验；
- dependency manifest 格式校验；
- input log analyzer 测试；
- input probe debug APK 编译通过。

### Gate B：设备数据

必须获得小米平板上的原始 JSONL，至少覆盖：

- pen down/move/up；
- hover；
- 一指、双指和两指变一指；
- pen + palm；
- touch 后落笔；
- 应用暂停和系统面板中断；
- canceled pointer 或 ACTION_CANCEL（若设备产生）。

### Gate C：GTK Interaction Lab

必须证明：

- 同一个 input core 可由 GDK adapter 驱动；
- pen drag 可 complete；
- Android cancel 可 abort；
- cancel 后 late release 不提交；
- 双指只修改 viewport；
- 生命周期中断后 owner、grab 和 interaction 全部清空。

只有 Gate C 通过后，才允许修改 Inkscape Select Tool 的 Android 适配。

## 8. 当前不应实施

- 不创建 Kotlin SVG 文档模型；
- 不复制 Inkscape hit testing；
- 不实现节点编辑、文本编辑或压感绘制；
- 不绑定小米专有按钮；
- 不把 gtkmm 的本机构建回退当作 Android 方案；
- 不把候选 SHA 直接标记为 validated；
- 不在没有 drop 计数的数据上声称采样率或延迟结论。

## 9. 参考源码

- Inkscape CMake: https://gitlab.com/inkscape/inkscape
- GTK Android: https://gitlab.gnome.org/GNOME/gtk
- Pixiewood: https://github.com/sp1ritCS/gtk-android-builder
- Krita input manager: https://github.com/KDE/krita
- Butterfly viewport input: https://github.com/LinwoodDev/Butterfly
- Graphite select tool FSM: https://github.com/GraphiteEditor/Graphite
- SVG-Edit history recording: https://github.com/SVG-Edit/svgedit

第三方项目默认仅作为架构参考；复制代码前必须单独检查许可证。
