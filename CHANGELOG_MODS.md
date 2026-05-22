# Lemuroid 定制版 - 修改日志

> 本文件记录对 Lemuroid 开源项目的所有修改，包括构建环境适配、功能增强和问题修复。

---

## v1.1 - 2026-05-22

### 新增：游戏画面垂直对齐设置

**原因**: 连接蓝牙手柄后，触摸控件隐藏，NDS 双屏会居中显示在整个屏幕中间，上下留黑边。用户希望双屏靠上显示，下方留空。

**修改文件**:
- `lemuroid-app/src/main/res/values/keys.xml` — 新增 `pref_key_game_view_vertical_align`
- `lemuroid-app/src/main/res/values/strings.xml` — 新增字符串和选项数组（Center / Top）
- `lemuroid-app/src/main/java/.../settings/SettingsManager.kt` — 新增 `gameViewVerticalAlign()` 读取方法
- `lemuroid-app/src/main/java/.../game/GameScreenLayout.kt` — 新增 `VerticalAlign` 枚举和 `buildConstraintSetLandscapeTopAlign()` 约束布局
- `lemuroid-app/src/main/java/.../game/MobileGameScreen.kt` — 手柄连接时自动使用 Top 对齐
- `lemuroid-app/src/main/java/.../settings/general/SettingsScreen.kt` — 设置界面新增下拉选项

**效果**: 设置 → 常规 → "游戏画面垂直对齐"，可选"居中"或"靠上"。连接手柄时默认靠上。

---

## v1.0 - 2026-05-18

### 概述
首次定制修改，目标：使 Lemuroid 可在 macOS Intel + 阿里云 DashScope 编码环境下构建，并增强 NDS 存档兼容性。

### 1. 构建环境适配

#### 1.1 添加国内 Maven 镜像源
**原因**: 国内网络访问 Maven Central / Google Maven 不稳定，构建常超时。

**修改文件**:
- `build.gradle.kts` — 在 `allprojects.repositories` 中添加阿里云镜像：
  ```kotlin
  maven { url = uri("https://maven.aliyun.com/repository/google") }
  maven { url = uri("https://maven.aliyun.com/repository/public") }
  ```
- `settings.gradle.kts` — 在 `pluginManagement.repositories` 中添加阿里云镜像：
  ```kotlin
  maven { url = uri("https://maven.aliyun.com/repository/google") }
  maven { url = uri("https://maven.aliyun.com/repository/public") }
  maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
  ```

#### 1.2 注释掉 baselineprofile 模块
**原因**: baselineprofile 模块依赖解析失败，且对 APK 构建非必需。

**修改文件**:
- `build.gradle.kts` — 注释掉 `com.android.test` 和 `androidx.baselineprofile` 插件
- `lemuroid-app/build.gradle.kts` — 注释掉 `androidx.baselineprofile` 插件和 `baselineProfile` 依赖
- `settings.gradle.kts` — 注释掉 `:baselineprofile` 模块 include

#### 1.3 SDK 版本兼容
**原因**: 本地 Android SDK 安装了 compileSdk 35，原项目配置可能不支持。

**修改文件**:
- `gradle.properties` — 添加 `android.suppressUnsupportedCompileSdk=35`

### 2. NDS 存档兼容性增强 (.sav 支持)

#### 2.1 问题
原版 Lemuroid 只读取 `.srm` 格式的存档，无法读取烧录卡/DraStic 等模拟器生成的 `.sav` 文件。

#### 2.2 修改方案
**修改文件**: `retrograde-app-shared/src/main/java/com/swordfish/lemuroid/lib/saves/SavesManager.kt`

**核心改动**:
1. **多格式存档查找**: 对 NDS 游戏优先查找 `.sav` 再找 `.srm`，其他系统保持原有顺序
2. **超大存档自动裁剪**: 检测到 >1MB 的存档文件（通常是烧录卡填充到 512KB/1MB 的 oversized 存档），自动裁剪为 512KB，适配 MelonDS 模拟器
3. **保留迁移器回退**: 对旧版 `.dsv` 存档仍通过 migrator 兼容

**存档搜索逻辑**:
```kotlin
val extensions = if (game.systemId == "nds") 
    listOf("sav", "srm")  // NDS: 优先 .sav
else 
    listOf("srm", "sav")  // 其他: 优先 .srm
```

### 3. 构建产物

| 构建版本 | 文件名 | 大小 | 日期 |
|---------|--------|------|------|
| v1.0 debug | `lemuroid-app-free-dynamic-debug.apk` | 31.6 MB | 2026-05-18 |

**存档路径**: `Android/data/com.swordfish.lemuroid/files/saves/`

### 4. 构建环境

- **JDK**: Temurin 21.0.2 (`JAVA_HOME=/Users/simon/.hermes/android-env/jdk/Contents/Home`)
- **SDK Platforms**: 34, 35
- **Build Tools**: 34.0.0
- **Gradle**: Wrapper (项目自带)
- **macOS**: Intel

---

## 更新指南

每次代码更新后，请在此文件末尾追加新版本记录，格式：

```
## vX.X - YYYY-MM-DD

### 变更内容
- 修改了什么
- 解决了什么问题

### 修改文件
- 文件路径: 简述修改内容
```

---

## v1.1 - 2026-05-18

### NDS 双屏交换按钮

#### 新增功能
在 NDS 虚拟控制器右侧（X/Y/B/A 按键侧）添加了一个屏幕交换按钮，点击可切换上下屏幕的位置。按钮图标为双向箭头（↕），位于菜单按钮下方。

#### 实现方案
1. **新建按钮图标**: `button_swap_screens.xml` — 双向箭头矢量图标
2. **控制器布局修改**:
   - `MelonDS.kt` — 右侧 secondaryDials 添加 `KEYCODE_BUTTON_THUMBR` 映射的交换按钮
   - `Desmume.kt` — 同上
3. **事件处理**:
   - `GameViewModelTouchControls.kt` — 新增 `swapScreensCallback` 参数，拦截 `KEYCODE_BUTTON_THUMBR` 事件
   - `BaseGameScreenViewModel.kt` — 新增 `swapNdsScreens()` 方法，根据当前核心切换屏幕布局变量
4. **核心变量切换**:
   - MelonDS: `melonds_screen_layout1` 在 `top-bottom` ↔ `bottom-top` 之间切换
   - DeSmuME: `desmume_screens_layout` 在 `top/bottom` ↔ `bottom/top` 之间切换

#### 构建产物

| 版本 | 文件名 | 大小 | 备注 |
|------|--------|------|------|
| v1.1 debug | `lemuroid-app-free-dynamic-debug.apk` | 33.7 MB | 初始版本，按钮位置 `-60f`（与菜单键重叠） |
| v1.1 debug (fixed) | `lemuroid-v3-swap-button-fixed.apk` | 33.7 MB | 按钮位置调整为 `-120f` |

#### 按钮位置
- 左侧关闭按钮：`-60f`
- 右侧交换按钮：`-120f`（与左侧关闭按钮对称）

#### 修改文件
- `lemuroid-touchinput/src/main/res/drawable/button_swap_screens.xml`: 新增
- `lemuroid-touchinput/src/main/java/.../layouts/MelonDS.kt`: 添加交换按钮
- `lemuroid-touchinput/src/main/java/.../layouts/Desmume.kt`: 添加交换按钮
- `lemuroid-app/src/main/java/.../viewmodel/GameViewModelTouchControls.kt`: 新增回调参数 + 事件拦截
- `lemuroid-app/src/main/java/.../BaseGameScreenViewModel.kt`: 新增 `swapNdsScreens()` 方法
