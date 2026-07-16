# Android GTK Host

薄宿主只负责 GTK Android 应用入口、运行时路径、生命周期桥、资源初始化和调用 `inkscape_android_run()`。

宿主不得包含：

- SVG 解析或序列化；
- 对象命中和选择；
- Undo；
- Canvas 手势业务；
- Inkscape 工具状态。

建议入口：

```cpp
extern "C" int inkscape_android_run(int argc, char **argv);

int main(int argc, char **argv)
{
    configure_android_runtime_paths();
    return inkscape_android_run(argc, argv);
}
```

Inkscape 继续由 CMake 构建；Meson Host 只满足 GTK Android application target 和打包入口要求。
