# Inkscape Android Tablet MVP

将 Inkscape 的基础 SVG 对象编辑能力迁移到 Android 平板的实验性集成仓库。

当前目标设备：**小米平板 7 Ultra + 小米焦点触控笔**。当前阶段只验证基础闭环，不以完整桌面功能或应用商店发布为目标。

## 当前状态

本仓库处于 **P0：框架初始化**，并已加入 P1 输入探针工程。

已定义的首个编辑闭环：

```text
打开 SVG → 触控笔选择 → 移动 → 完成/取消 → 撤销 → 删除 → 保存副本 → 重载验证
```

输入策略：

```text
触控笔：唯一可修改对象的画布输入
单指触摸：画布无操作
双指触摸：后续阶段用于平移和缩放
系统取消：必须走显式 abort，不能当成正常抬笔
```

## 架构方向

```text
Android / GTK Runtime
        ↓
CanvasInputRouter + InteractionSession
        ↓
Inkscape Canvas / Select Tool / DocumentUndo
        ↓
SVG 文档模型与序列化
```

本仓库是轻量集成仓库，负责：

- 上游源码版本锁定；
- Android/GTK/Inkscape 补丁队列；
- 输入探针和 GTK Interaction Lab；
- 构建脚本与 CI；
- 测试资产、诊断和方案文档。

本仓库不直接提交完整 Inkscape/GTK 源码，也不提交 APK、sysroot 或其他大型二进制产物。

## 文档

- [完整迁移方案](docs/migration-plan.zh-CN.md)
- [开源项目参考评估](docs/open-source-reference-assessment.zh-CN.md)
- [分阶段技术关卡](docs/stage-gates.md)
- [Mac 操作基线](docs/build-macos.md)
- [架构边界](docs/architecture.md)

## 从 Mac 开始

```sh
git clone https://github.com/LightChainr/InkscapeAndroid.git
cd InkscapeAndroid
bash scripts/dev doctor
```

`doctor` 只检查环境，不修改系统。

## P1 输入探针

输入探针是独立 Android 工程，不依赖 GTK 或 Inkscape：

```sh
cd android/input-probe
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions 工作流 `Input probe build` 会使用 JDK 17、AGP 9.2.0 和 Gradle 9.4.1 构建 debug APK。探针日志写入应用私有目录的 `input-probe/events.jsonl`。

## 开发原则

1. 每个阶段只引入一种新能力。
2. `complete()` 与 `abort()` 必须是不同路径，且只能发生一次。
3. 不在 Kotlin/Java 层复制 Inkscape 的选择、文档模型或 Undo。
4. 正常输入沿用 GTK/GDK；只为丢失的取消语义增加窄补丁。
5. 所有目标依赖必须来自 Android sysroot，禁止误链接 Homebrew/macOS 动态库。
6. 第三方代码复制前必须完成许可证审查；默认只借鉴架构，不直接复制实现。

## 许可证

仓库尚未确定统一许可证。提交第三方代码、补丁或可分发产物前，请先阅读 `LICENSES/README.md`。
