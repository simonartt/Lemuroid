# Lemuroid V8 — 定制版

> ⚠️ 本项目基于 [Lemuroid](https://github.com/Swordfish90/Lemuroid) 开源项目定制修改，非官方原版。

原版 Lemuroid 是一个基于 Libretro 的 Android 多平台模拟器，支持 NES、SNES、GBA、NDS、PS1、PSP 等数十种游戏机。

**V8 定制版**在原版基础上增加了 NDS 专属增强功能，包括双屏交换、触控编辑、存档兼容等。

---

## 定制功能一览

### 触控按键独立编辑系统 ⭐

游戏内长按编辑模式，可对 **10 个虚拟按键**进行独立操作：

| 功能 | 说明 |
|------|------|
| **拖拽位移** | 点选按键后拖动，调整任意位置 |
| **滑块调大小** | 底部滑块实时调节按键缩放比例 |
| **独立偏移** | 每个按键的偏移量独立存储，互不影响 |
| **按键显隐** | 每个按键都有"显示此按钮"开关，可隐藏不需要的按键 |
| **下拉选键** | 编辑菜单通过下拉选择要调节的按键 |
| **全局复位** | 一键恢复所有按键到默认布局 |

### NDS 增强

| 功能 | 说明 |
|------|------|
| **双屏交换** | 双击左摇杆切换上下屏布局（top-bottom ↔ bottom-top） |
| **存档兼容 (.sav)** | 优先读取 `.sav` 格式存档（烧录卡/DraStic），原版只支持 `.srm` |
| **超大存档裁剪** | 自动裁剪 >1MB 的烧录卡存档，适配 melonDS |
| **画面垂直对齐** | 连接蓝牙手柄后可选"居中"或"靠上"显示双屏画面 |

### 虚拟按键开关

- 游戏菜单新增「虚拟按键」开关，可随时隐藏/显示触摸控制器
- 隐藏后右上角出现悬浮菜单按钮，点击可打开游戏菜单
- 解决连接蓝牙手柄后触摸控制器自动隐藏但无法手动恢复的问题

### 构建适配

- 阿里云 Maven 镜像，国内网络构建不超时
- JDK 21 (Temurin) + compileSdk 35 兼容

---

## 版本区分

| 版本 | applicationId | 图标背景 | 说明 |
|------|--------------|---------|------|
| **V8** (master) | `com.swordfish.lemuroid.v8` | 🟢 绿色 | NDS 定制主版本 |
| **V8A** (v8a) | `com.swordfish.lemuroid.v8a` | 🔴 红色 | 3DS 定制版本，可与 V8 共存安装 |

两个版本 `applicationId` 不同，可同时安装在同一台设备上，数据互不干扰。

---

## 支持的系统

| 系统 | 核心 |
|------|------|
| Nintendo DS (NDS) | melonDS（主力）/ DeSmuME |
| Nintendo 3DS | Citra |
| Game Boy Advance (GBA) | mgba |
| Nintendo (NES) | fceumm |
| Super Nintendo (SNES) | snes9x |
| PlayStation (PSX) | PCSX-ReARMed |
| PlayStation Portable (PSP) | ppsspp |
| Nintendo 64 (N64) | mupen64plus |
| 其他 | GB/GBC, Genesis, Arcade(FBNEO), PCE, NGP/NGC, WS/WSC, Atari 等 |

---

## 构建

```bash
export JAVA_HOME=/Users/simon/.hermes/android-env/jdk/Contents/Home
./gradlew :lemuroid-app:assembleFreeDynamicRelease
```

APK 输出：`lemuroid-app/build/outputs/apk/freeDynamic/release/lemuroid-app-free-dynamic-release.apk`

---

## 存档路径

```
Android/data/com.swordfish.lemuroid/files/saves/
```

NDS 游戏优先读取 `.sav` 格式，兼容烧录卡和 DraStic 存档。

---

## 修改日志

详见 [CHANGELOG_MODS.md](CHANGELOG_MODS.md)

---

## 原版项目

- [Lemuroid (GitHub)](https://github.com/Swordfish90/Lemuroid)
- [F-Droid](https://f-droid.org/packages/com.swordfish.lemuroid/)
- [Google Play](https://play.google.com/store/apps/details?id=com.swordfish.lemuroid)
