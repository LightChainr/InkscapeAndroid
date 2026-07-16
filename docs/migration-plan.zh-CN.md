# Inkscape Android 平板定向迁移方案

> 目标设备：小米平板 7 Ultra + 小米焦点触控笔  
> 当前范围：基础对象选择、移动、删除、撤销、保存副本  
> 开发操作端：Mac  
> 权威 native 构建：早期使用 Linux CI

## 1. 执行结论

项目不以“完整移植桌面 Inkscape”为第一目标，而以建立一条稳定的编辑闭环为目标：

```text
打开 SVG
  → pen 选择对象
  → pen 移动对象
  → complete 或 abort
  → Undo/Redo
  → Delete
  → Save Copy
  → 重载并验证
```

首版输入规则：

```text
pen：唯一可以修改对象的画布输入
single touch：画布无操作
two-finger touch：后续阶段负责 pan/zoom
pen 与 touch：一次序列只有一个 owner
cancel：显式 abort，不能等价为正常 release
```

## 2. 技术路线

首选路线为 GTK Android + Inkscape 现有 Canvas/工具链：

```text
Android MotionEvent
        ↓
GTK Android / GDK
        ↓
CanvasInputRouter
        ↓
InteractionSession
        ↓
Inkscape Canvas / Select Tool / DocumentUndo
        ↓
SVG 文档模型与序列化
```

必须复用：Canvas picking、Select Tool、Selection、DocumentUndo、文档模型、坐标变换和 SVG 保存。

禁止在 Android/Kotlin 层重新实现：对象命中、选择状态、Undo、SVG XML 修改或完整并行输入管线。

## 3. 仓库和上游代码

`LightChainr/InkscapeAndroid` 是集成仓库，保存：

- `sources.lock.toml`；
- 构建和诊断脚本；
- Android input probe、GTK Interaction Lab 和薄 Host；
- GTK/Inkscape 补丁队列；
- 测试数据与阶段证据；
- 方案、ADR 和已知限制。

完整 Inkscape、GTK 和 Pixiewood 源码拉取到 `.work/src/`，通过不可变 commit SHA 锁定。生成目录、sysroot、APK 和大型二进制不提交 Git。

## 4. 构建方案

### 4.1 早期权威路径

```text
Mac 编辑/触发 CI
        ↓
Linux 构建 GTK、gtkmm、依赖和 Inkscape Android target
        ↓
生成 arm64 APK、sysroot、symbols、build manifest
        ↓
Mac 下载产物
        ↓
ADB 安装到小米平板并测试
```

这条路径优先保证可重复性。Krita、GTK/Pixiewood、Collabora 等大型原生应用的 Android 构建也主要以 Linux native 构建为基线。

### 4.2 可选 Mac 加速

在 P2/P4 之后，可使用 Linux 生成的可重定位 Android sysroot，在 Mac 通过 NDK `darwin-x86_64` 工具链增量编译 Inkscape target。该路径必须与权威产物 ABI 和行为一致，不得成为前期阻塞项。

### 4.3 薄 Host

Inkscape 保持 CMake；Meson Host 只提供 GTK Android application entry：

```cpp
extern "C" int inkscape_android_run(int argc, char **argv);

int main(int argc, char **argv)
{
    configure_android_runtime_paths();
    return inkscape_android_run(argc, argv);
}
```

Host 不包含 SVG 编辑、选择、Undo 或手势业务。

## 5. 输入架构

### 5.1 PointerRegistry

记录 `pointer ID → tool type/device kind`，不把 pointer index 当作长期身份。设备类型至少区分 touch、stylus、eraser/inverted stylus 和 mouse。

### 5.2 InputOwner

```text
None
├─ pen down       → Pen
└─ first finger   → TouchPending

TouchPending + second finger → TouchTransform
TouchTransform + any finger up → BlockUntilAllTouchUp
```

owner 一旦确定，在同一序列中不移交。pen 编辑过程中手指输入被画布层忽略；双指导航期间落笔也不抢占 owner。

### 5.3 InteractionSession

对象移动必须是显式事务：

```text
Idle → Armed → Active → Committed
                    ↘ Aborted
```

- 超过 drag slop 后才进入 Active；
- `complete()` 只处理正常结束；
- `abort()` 处理 Android cancel、palm rejection、pause、focus loss、Surface loss 和工具切换；
- canceled sequence 后迟到的 release 不得提交；
- 一次正常拖动只产生一个 Undo；
- abort 恢复变换、Undo、grab、pointer 和 owner 状态。

GTK Android 当前能表达 touch cancel，但 stylus cancel 可能被转换成普通 release，因此只为丢失的 cancel/`FLAG_CANCELED` 增加窄桥，不复制所有 move 事件。

## 6. GTK Interaction Lab

在接入 Inkscape 前，建立一个不依赖 Inkscape 的 GTK Android 实验应用：

- 两个可选/可拖动测试矩形；
- pen 与 touch 路由；
- single touch 无操作；
- two-finger pan/zoom；
- explicit complete/abort；
- lifecycle/cancel 重置；
- owner 和事务日志。

只有实验场通过，才将相同的 input core 接入 Inkscape。这样可以区分平台输入错误和 Inkscape 工具集成错误。

## 7. 对象编辑范围

D0/D1 支持：

- rect、ellipse/circle、普通 path；
- group、image、text 作为整体对象；
- clone/use、带 transform 的对象；
- 点击对象选择；
- 点击空白取消选择；
- 拖动移动；
- 删除、Undo、Redo；
- 应用私有保存与 Save Copy。

暂不支持：节点编辑、对象控制柄缩放/旋转、多选、文本内容编辑、图层面板、压感绘制、Python 扩展、复杂格式导入、画布旋转和笔/双指并发。

## 8. 文件工作流

D0 先保存到应用私有目录并重新打开验证。D1 使用系统文件选择器：

```text
OPEN      → ACTION_OPEN_DOCUMENT
SAVE COPY → ACTION_CREATE_DOCUMENT
```

Save Copy 流程：

1. Inkscape 序列化到私有临时文件；
2. 重新解析临时 SVG；
3. 结构化验证对象与引用；
4. 写入目标文档；
5. flush/close；
6. 原始输入文件不修改。

首版不默认覆盖原件。不同 document provider 的 seek/truncate/权限行为必须单独验证。

## 9. 分阶段目标

详细标准见 `docs/stage-gates.md`。核心顺序：

```text
P0 仓库与环境
P1 小米输入探针
P2 GTK Android 参考平台
P3 GTK Interaction Lab
P4 Inkscape Host/主循环
P5 Canvas 只读渲染
P6 只选择
P7 正常移动与 Undo
P8 Abort/Cancel 回滚
P9 Delete/私有保存（D0）
P10 双指导航
P11 Open/Save Copy/UI
P12 稳定性与复现（D1）
```

## 10. 测试与证据

必须建立三层测试：

- host 单元测试：owner、pointer registry、centroid/span、complete/abort；
- native 集成测试：Select Tool、Undo、tool abort、坐标、资源和 GFile；
- 真机测试：stylus、palm、Surface、锁屏、系统面板、文件 provider。

关键不变量：

```text
single touch 永不修改对象
双指导航永不移动对象
cancel 永不提交对象移动
下一次输入不继承旧 grab/owner
Save Copy 失败不损坏原文件
```

每阶段提交 APK checksum、build manifest、设备/系统版本、复现命令、关键日志和已知限制。

## 11. 开源项目参考

- GTK Android/Pixiewood：平台、输入、文件和 APK 壳层；
- Krita/Godot/Collabora：大型原生编辑器的 Android 宿主与构建分层；
- Butterfly：pointer device 路由、cancel 和 lifecycle input reset；
- Graphite/tldraw：显式工具状态机、history mark、complete/abort；
- SVG-Edit：单次交互的 batch history 设计。

只借鉴架构模式。AGPL 或定制许可证项目的实现代码不得未经审查直接复制。

## 12. 当前下一步

当前仓库处于 P0。接下来应依次完成：

1. 运行并修正 `scripts/doctor-macos.sh`；
2. 建立 input-probe Gradle 工程；
3. 在目标平板采集原始事件并形成设备报告；
4. 将上游候选版本替换为验证后的 commit SHA；
5. 建立 GTK demo/Interaction Lab 权威构建。
