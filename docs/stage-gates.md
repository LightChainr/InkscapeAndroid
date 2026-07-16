# 分阶段技术关卡

每个阶段只解决一个核心不确定性。前一阶段没有可复核证据，不进入下一阶段。

| 阶段 | 单一目标 | 最小通过证据 |
|---|---|---|
| P0 | 建立 Mac 操作环境和仓库框架 | `doctor` 输出、CI scaffold check、可安装空白或探针 APK |
| P1 | 确认小米平板与焦点笔的原始事件 | JSONL 日志、设备能力矩阵、cancel/palm 序列 |
| P2 | 建立 GTK Android 权威参考平台 | GTK demo APK、可重定位 arm64 sysroot、build manifest |
| P3 | 在 GTK Interaction Lab 验证输入与事务 | pen/touch 路由、显式 complete/abort、生命周期重置 |
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

- P1 前不推测厂商笔能力。
- P3 前不接入 Inkscape。
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
```

阶段完成不是“看起来能用”，而是另一台干净环境可以根据证据重复验证。
