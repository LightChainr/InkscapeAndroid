# 开源项目参考评估

没有发现可直接 fork 后快速变成 Inkscape Android 的成熟项目。可行方案是分别借鉴平台构建、输入路由、交互事务和文件工作流。

## 高价值参考

### GTK Android / Pixiewood

用途：Android Surface、GDK pen/touch、文件选择、content URI、GTK 运行时和 APK 壳层。

可借鉴：上游 Android backend、Java glue、cross files、Gradle 模板和 Linux CI 路线。

限制：Pixiewood要求 Meson application target，而 Inkscape 使用 CMake；当前实现还有 Linux host 假设。因此它适合构建 GTK 依赖和薄 Host，不适合直接替代 Inkscape 构建系统。

### Krita

用途：大型桌面 C++ 绘图应用迁移 Android 的整体组织方式。

可借鉴：集中式 Input Manager、tablet/touch 区分、移动事件压缩、Android 包装与 Linux native 构建基线。

限制：Qt 和位图绘画架构不能直接复制到 GTK/Inkscape；代码许可证需单独审查。

### Godot Android Editor

用途：原生编辑器核心与 Android Activity/生命周期分层。

可借鉴：薄 Android 编辑器宿主、生命周期和 Intent 处理、Android UI 仅承担平台职责。

限制：Godot 有自己的渲染和窗口体系，不能提供 Inkscape Canvas 代码。

### Collabora Online Android

用途：大型 Linux/C++ 应用的 Android native core 与应用工程分离。

可借鉴：Linux 构建 native 核心、Android Studio 侧调试和打包；Mac 作为操作端而非所有依赖的唯一构建环境。

### Butterfly

用途：跨平台画布应用的设备路由。

可借鉴：维护 pointer ID 到 device kind 的映射；只有活动 pointer 都是 touch 时才进入视口移动；独立处理 pointer cancel；应用离开 resumed 时重置输入。

限制：Flutter/Dart 实现不可直接移植；AGPL 项目默认只做清洁室架构参考。

### Graphite

用途：消息驱动的矢量工具状态机。

可借鉴：Select Tool 使用明确 FSM；Abort、DragStart、DragStop、PointerMove 是不同消息；工具状态不依赖平台 release 猜测取消语义。

### tldraw

用途：对象移动事务与取消。

可借鉴：拖动开始时建立 history mark 和对象 snapshot；正常 complete 与 cancel 分离；cancel 回退到历史锚点。

限制：当前采用定制许可证，不能作为可自由复制的实现来源。

### SVG-Edit

用途：SVG 编辑命令和历史记录的可测试封装。

可借鉴：start/end batch command 将连续变化合并为一个 Undo；历史服务可注入并易于单元测试。

限制：不能取代 Inkscape DocumentUndo，只能作为事务 API 设计参考。

### Google Ink / Android Ink API

用途：未来的自由笔迹低延迟渲染、平滑和预测。

限制：不解决对象 picking、选择、变换或 SVG Undo；不纳入对象编辑 MVP。

## 汇总决策

```text
平台与打包       → GTK Android / Pixiewood
大型移植组织     → Krita / Godot / Collabora
设备路由         → Butterfly / Krita
工具 FSM         → Graphite
事务与取消       → tldraw / SVG-Edit
未来自由笔迹     → Google Ink
SVG 编辑核心     → 继续使用 Inkscape
```

## 由调研产生的方案调整

1. 在接入 Inkscape 前增加 GTK Interaction Lab，先验证输入路由和 complete/abort。
2. 将 Linux 全 native 构建设为早期权威路径；Mac 负责操作、CI、Android Studio、ADB 和真机测试。
3. 将通用 `cancel_interaction()` 收紧为显式 `InteractionSession.abort()`。
4. 在拖动开始建立最小对象 snapshot 或历史锚点，取消时从该锚点恢复。
5. 输入策略集中管理 pointer kind 和 owner，不散落到每个 Inkscape 工具。
6. 许可证不兼容或定制许可证项目只借鉴设计，不复制代码。
