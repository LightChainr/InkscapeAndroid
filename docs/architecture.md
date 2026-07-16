# 架构边界

## 运行层次

```text
GTK Android Activity / Surface
        ↓
CanvasInputRouter
  ├─ PointerRegistry
  ├─ TabletInputPolicy
  └─ InputOwner
        ↓
InteractionSession
  ├─ begin
  ├─ update
  ├─ complete
  └─ abort
        ↓
Inkscape Canvas / Select Tool / DocumentUndo
```

## 必须复用

- Inkscape Canvas picking；
- Select Tool；
- Selection model；
- DocumentUndo；
- SVG 文档模型和序列化；
- GTK Android Surface、GDK pen/touch 与文件接口。

## 不在 Android 层实现

- SVG hit testing；
- 第二套对象选择；
- 第二套 Undo；
- Kotlin/Java 直接修改 SVG XML；
- 与 GDK 并行的完整 MotionEvent 管线。

## 输入所有权

```text
None
├─ pen down       → Pen
└─ first finger   → TouchPending

TouchPending + second finger → TouchTransform
Pen + finger                 → finger ignored on canvas
TouchTransform + pen         → pen ignored until touch sequence ends
```

输入 owner 在同一序列中不动态移交。双指少于两根后进入阻塞状态，剩余单指不能继续平移或转成对象编辑。

## 交互会话

```cpp
struct InteractionSession {
    enum class State { Idle, Armed, Active, Committed, Aborted };

    void begin(...);
    void update(...);
    void complete();
    void abort(CancelReason reason);
};
```

不变量：

- `complete()` 与 `abort()` 互斥且幂等；
- cancel 后到达的 release 不得提交；
- 正常拖动只产生一个 Undo；
- abort 恢复对象变换、Undo 状态、grab、pointer registry 和 input owner；
- 生命周期暂停、失焦和 Surface 丢失进入同一 abort 路径。

## 构建边界

```text
Linux 权威构建
  GTK / gtkmm / target dependencies / sysroot / reference APK

Mac 操作端
  补丁编辑 / CI 触发与下载 / Gradle / ADB / LLDB / 真机验证

可选 Mac 加速
  使用同一锁文件和 sysroot 的 NDK 增量编译
```

Inkscape 保持 CMake 构建；薄 Meson Host 只提供 GTK Android application entry 和 `inkscape_android_run()` 调用，不复制编辑业务。
