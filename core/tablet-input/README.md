# Tablet Input Core

该目录是 Android/GTK/Inkscape 之外的可执行输入规范。

它解决两个问题：

1. 谁拥有当前画布输入；
2. 当前对象交互是正常完成还是取消回滚。

## 为什么单独存在

直接在 GTK controller 或 Inkscape Select Tool 中编写 owner 逻辑，会让平台事件、视口手势、对象事务和工具状态互相耦合。这里先用纯 C++20 固定不变量，后续 adapter 只能把 GDK 事件转换为本模块事件，不得重新发明状态规则。

## 输入所有权

```text
None
 ├─ pen down ───────────────→ Pen
 └─ first finger down ──────→ TouchPending

TouchPending
 ├─ second finger down ─────→ TouchTransform
 └─ all fingers up ─────────→ None

TouchTransform
 └─ any tracked finger up ──→ BlockUntilAllTouchUp

BlockUntilAllTouchUp
 └─ all fingers up ─────────→ None
```

规则：

- `Pen` 期间出现的 finger 只登记和消费，不能抢占；
- touch 已获得 owner 后出现的整个 pen sequence 都忽略；
- 双指结束后，剩余单指不能继续平移，也不能变成对象点击；
- lifecycle cancel 清空所有 pointer registry；
- pen cancel 产生 `AbortInteraction`，normal up 产生 `ForwardPenUp`。

## InteractionSession

```text
Idle → Armed → Active → Committed
                 └────→ Aborted
```

点击可以从 `Armed` 直接 `Committed`；拖动先进入 `Active`。

不变量：

- `complete()` 和 `abort()` 只允许一次成功；
- `Aborted` 后迟到的 release 不能 commit；
- terminal session 必须显式 reset 才能复用；
- adapter 负责把 `AbortInteraction` 连接到工具 abort 和最小 rollback。

## 构建

```sh
bash scripts/dev core-test
```

该测试在 Linux 和 macOS CI 都运行。接入 GTK Interaction Lab 时，应复用此库或保持行为一致的薄 adapter，并用相同事件序列做回归。
