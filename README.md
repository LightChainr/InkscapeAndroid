# Inkscape Android Tablet MVP

将 Inkscape 的基础 SVG 对象编辑能力迁移到 Android 平板的实验性集成仓库。

当前目标设备：**小米平板 7 Ultra + 小米焦点触控笔**。当前阶段只验证基础闭环，不以完整桌面功能或应用商店发布为目标。

## 当前状态

本仓库处于 **P0：前期工程基线**。P0 被拆成仓库/CI、输入事务核心、源码与依赖边界、可信输入采集四个子关卡；全部通过后才进入小米真机事件采集。

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

- [前期工程评估](docs/pre-engineering-assessment.md)
- [完整迁移方案](docs/migration-plan.zh-CN.md)
- [开源项目参考评估](docs/open-source-reference-assessment.zh-CN.md)
- [分阶段技术关卡](docs/stage-gates.md)
- [Mac 操作基线](docs/build-macos.md)
- [架构边界](docs/architecture.md)
- [输入核心契约](core/tablet-input/README.md)

## 从 Mac 开始

```sh
git clone https://github.com/LightChainr/InkscapeAndroid.git
cd InkscapeAndroid
bash scripts/dev doctor
bash scripts/dev test
```

`doctor` 只检查环境，不修改系统。`test` 运行锁文件、候选 SHA、Python analyzer、C++ input core 和仓库策略检查。

## P1 输入探针

输入探针是独立 Android 工程，不依赖 GTK 或 Inkscape：

```sh
cd android/input-probe
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

探针采用有界后台写盘，队列溢出会产生显式 `drop` 记录。日志写入应用私有目录：

```text
files/input-probe/events.jsonl
```

导出并验证：

```sh
PACKAGE=io.github.lightchainr.inkscapeandroid.inputprobe
adb exec-out run-as "$PACKAGE" \
  cat files/input-probe/events.jsonl > events.jsonl
bash scripts/dev probe-analyze events.jsonl
```

存在 schema 错误或 dropped records 的日志不能作为完整事件序列证据。

## 研究版本

- `sources.candidates.toml`：可从官方远端取得的研究候选 SHA；
- `sources.lock.toml`：只有兼容构建和证据包完成后才能标记 `validated`；
- `deps/android-target-dependencies.toml`：目标 sysroot 的依赖分层和硬性约束。

## 开发原则

1. 每个阶段只引入一种新能力。
2. `complete()` 与 `abort()` 必须是不同路径，且只能发生一次。
3. 不在 Kotlin/Java 层复制 Inkscape 的选择、文档模型或 Undo。
4. 正常输入沿用 GTK/GDK；只为丢失的取消语义增加窄补丁。
5. 所有目标依赖必须来自 Android sysroot，禁止误链接 Homebrew/macOS 动态库。
6. 第三方代码复制前必须完成许可证审查；默认只借鉴架构，不直接复制实现。

## 许可证

仓库尚未确定统一许可证。提交第三方代码、补丁或可分发产物前，请先阅读 `LICENSES/README.md`。
