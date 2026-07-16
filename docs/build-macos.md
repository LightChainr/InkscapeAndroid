# Mac 操作基线

Mac 是主要操作、调试与真机控制端。Linux 构建环境是早期 native/GTK 权威产物来源。

## 本机职责

- 编辑集成仓库和补丁；
- 运行环境诊断；
- 触发和下载 CI 产物；
- Gradle/Android Studio 操作；
- ADB 安装、日志、截图和真机测试；
- 后续可选的 NDK 增量编译。

## 基础检查

```sh
bash scripts/dev doctor
```

检查范围包括 Xcode CLI、JDK、Android SDK/NDK、ADB、CMake、Meson、Ninja、Git 和潜在的 Homebrew 目标库污染。

## 推荐主机工具

```text
Xcode Command Line Tools
git / git-lfs
JDK 17
Android command-line tools / Android Studio
cmake
meson
ninja
pkg-config
python3
```

## Android 基线候选

```text
ABI: arm64-v8a
minSdk/native API: 31
compileSdk: 36
targetSdk: 36
NDK: 27.2.12479018
build-tools: 35.0.0
C++ runtime: libc++_shared
```

这些值当前处于 `unvalidated` 状态；P2 的 GTK 参考 APK 成功后才改为已验证锁定。

## 关键约束

- Android ABI 仅 `arm64-v8a`。
- NDK host 目录仍名为 `darwin-x86_64`，不要自行拼出 `darwin-arm64`。
- Native Android 依赖必须来自已校验 sysroot。
- `PKG_CONFIG_PATH` 和 `CMAKE_PREFIX_PATH` 不得把 `/opt/homebrew` 或 `/usr/local/Cellar` 库带入目标链接。
- Mac 本地 native 编译是可选加速，不是 P0–P3 的必要前提。

## 真机循环

```sh
adb devices -l
adb install -r <apk>
adb logcat
```

输入、palm rejection、GPU 和生命周期问题必须保留 USB 复现路径。无线 ADB 可用于日常安装，但网络抖动不能被误判为应用延迟。
