# Lemuroid — 定制版

> ⚠️ 这是一个 **VibeCoding** 项目，由 AI 辅助开发，非官方原版。

基于 [Lemuroid](https://github.com/Swordfish90/Lemuroid) 开源项目的定制修改版本，主要面向 NDS 模拟器（melonDS / DeSmuME）的增强和中文本地化支持。

原版 Lemuroid 是一个基于 Libretro 的 Android 多平台模拟器，支持 NES、SNES、GBA、NDS、PS1、PSP 等数十种游戏机。

---

## 定制改动记录

详见 [CHANGELOG_MODS.md](CHANGELOG_MODS.md)

### v1.1 — 2026-05-22

#### NDS 双屏交换按钮
- 右侧触摸按键区新增屏幕交换按钮（↕），可一键切换上下屏布局
- 支持 melonDS 和 DeSmuME 双核心
- 通过切换模拟器配置变量实现（`melonds_screen_layout1` / `desmume_screens_layout`）

#### 虚拟按键手动开关
- 游戏菜单新增「虚拟按键」开关，可随时隐藏/显示触摸控制器
- 隐藏后右上角出现悬浮菜单按钮，点击可打开游戏菜单
- 解决了连接蓝牙手柄后触摸控制器自动隐藏但无法手动恢复的问题

### v1.0 — 2026-05-18

#### 构建环境适配
- 添加阿里云 Maven 镜像，解决国内网络构建超时问题
- 注释掉 baselineprofile 模块，解决依赖解析失败
- 添加 SDK compileSdk 35 兼容支持

#### NDS 存档兼容性增强（.sav 支持）
- 原版只支持 `.srm` 存档，现优先读取 `.sav` 格式（烧录卡 / DraStic 存档）
- 自动裁剪超大存档文件（>1MB），适配 melonDS 模拟器

### 尚未完成的功能

- **游戏画面垂直对齐**：代码已写入（设置界面 + 布局逻辑），但实际对齐效果尚未正确实现，待后续修复。设置项可见但不会真正改变画面位置。

---

## 支持的系统

| 系统 | 核心 |
|------|------|
| Nintendo DS (NDS) | melonDS（主力）/ DeSmuME |
| Game Boy Advance (GBA) | mgba |
| Nintendo (NES) | fceumm |
| Super Nintendo (SNES) | snes9x |
| PlayStation (PSX) | PCSX-ReARMed |
| PlayStation Portable (PSP) | ppsspp |
| Nintendo 64 (N64) | mupen64plus |
| Sega Genesis / CD / Master System / Game Gear | genesis_plus_gx |
| 其他 | GB/GBC, N64, 3DS, Arcade(FBNEO), PCE, NGP/NGC, WS/WSC, Atari 等 |

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

---

## 原版项目

- [Lemuroid (GitHub)](https://github.com/Swordfish90/Lemuroid)
- [F-Droid](https://f-droid.org/packages/com.swordfish.lemuroid/)
- [Google Play](https://play.google.com/store/apps/details?id=com.swordfish.lemuroid)
