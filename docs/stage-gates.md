# 分阶段技术关卡

每个阶段只解决一个核心不确定性。前一阶段没有可复核证据，不进入下一阶段。

## P0：前期工程基线

P0 不再作为单一“仓库初始化”任务处理，而是拆成四个必须全部通过的子关卡。

| 子关卡 | 单一目标 | 最小通过证据 |
|---|---|---|
| P0-A | 仓库、Mac 操作和 CI 可复现 | `doctor`、scaffold check、PR 证据链、禁止提交生成物 |
| P0-B | 锁定输入所有权与事务不变量 | C++20 input core 在 Linux/macOS 测试通过 |
| P0-C | 锁定研究源码与目标依赖边界 | 官方远端 SHA 可达报告、依赖 manifest、sysroot fail-fast 工具 |
| P0-D | 建立可信的原始输入采集 | versioned schema、后台有界写盘、drop 记录、analyzer 测试、可安装 probe APK |

P0 完成标准：

```text
Scaffold check                         PASS
Pre-engineering / Ubuntu              PASS
Pre-engineering / macOS               PASS
Source candidate official reachability PASS
Input probe lint/test/APK build       PASS
```

P0 只证明“工程和测量工具可信”，不证明小米设备行为，也不证明 GTK/Inkscape 可移植。

## 主阶段

| 阶段 | 单一目标 | 最小通过证据 |
|---|---|---|
| P1 | 确认小米平板与焦点笔的原始事件 | schema-valid JSONL、设备能力矩阵、cancel/palm 序列、drop=0 |
| P2 | 建立 GTK Android 权威参考平台 | GTK demo APK、可重定位 arm64 sysroot、build manifest |
| P3 | 在 GTK Interaction Lab 验证输入与事务 | 同一 input core 的 GDK adapter、pen/touch 路由、显式 complete/abort、生命周期重置 |
| P4 | 启动薄 Host 与 Inkscape 主循环 | APK 进入主循环、资源路径可诊断、前后台不崩溃 |
| P5 | 只读显示 Inkscape Canvas | 内置 SVG 正确显示、density/坐标/Surface 恢复通过 |
| P6 | 仅选择对象 | pen 点击选择、空白取消、手指不能修改对象 |
| P7 | 正常移动与单次 Undo | 一次拖动一个 Undo，Undo/Redo 往返正确 |
| P8 | 显式 Abort 与系统取消回滚 | cancel、pause、focus loss、Surface loss 均不提交移动 |
| P9 | 删除、私有保存与重载 | Delete/Undo、保存到私有目录、重载结构一致（D0） |
| P10 | 单指屏蔽与双指导航 | 单指无操作、双指 pan/zoom、owner 不跳转 |
| P11 | 系统文件工作流与极简界面 | Open、Save Copy、错误/dirty 状态、原文件不受损 |
| P12 | 稳定性与可复现交付 | 回归集、长时间测试、锁文件可重建 APK（D1） |
| O1 | 可选 Mac 本地 native 增量加速 | 与 Linux 权威产物 ABI/行为一致，不影响主路径 |

## 阶段不变量

- P0-D 通过前，不使用探针日志推导采样率或延迟。
- P1 前不推测厂商笔能力。
- P2 的 `sources.lock.toml` 只有兼容构建通过后才能标记 `validated`。
- P3 前不接入 Inkscape。
- P3 必须复用或行为等价于 `core/tablet-input`，不能在 GTK adapter 中另写一套 owner 规则。
- P6 只允许选择，禁止拖动对象。
- P7 只验证正常完成，不宣称取消可靠。
- P8 通过前，不增加双指导航和外部文件写回。
- P11 默认 Save Copy，不覆盖原件。

## 每阶段证据包

至少包含：

```text
build-manifest.json
APK SHA-256
目标设备与系统版本
复现命令
关键日志或测试输出
已知限制
对应 schema / lock / patch-series hash
```

P1 证据包还必须包含：

```text
原始 events.jsonl
analyzer JSON summary
droppedRecords = 0
场景操作记录
输入设备 motion ranges
```

阶段完成不是“看起来能用”，而是另一台干净环境可以根据证据重复验证。
