# Lemuroid 定制版 - 修改日志

> 本文件记录对 Lemuroid 开源项目的所有修改，包括构建环境适配、功能增强和问题修复。

---

## v1.8 - 2026-09-01

### 新增：NDS 屏幕编辑器全新 UI（浮动词条工具箱）+ 应用更名

**需求**: 将自设计的「NDS 屏幕编辑器」交互 UI 套入模拟器，替代旧的滑杆式布局编辑器。两个屏幕分开独立调整——通过触摸点击画面上的虚线框选择屏幕，再用 UI 按钮精调。

**实现**（分支 `v8b-nds-editor`，版本 1.18.0-v8b，应用名改为 **Lemuroid NDS**）:

1. **数据模型扩展**（`ScreenLayoutManager.kt`）:
   - `ScreenTransform` 新增 `scaleY`（纵向缩放）与 `gap`（两屏间距），旧 JSON 反序列化向后兼容
   - 新增 `adjustTransform` / `setVerticalScale` / `setGap`，`MAX_SCALE` 提升至 7.0
2. **渲染逻辑**（`MobileGameScreen.kt`）:
   - `applyScreenLayoutTransform` 支持 scaleY 与 gap（gapSign：上屏 -1、下屏 +1）
   - 删除旧 `MenuEditScreenLayout` 编辑器，底部改为浮动词条工具箱 + 底部操作栏
3. **新 UI**（`ScreenLayoutEditorToolbox.kt`，新增）:
   - `ScreenLayoutEditorToolbox`：居中浮动词条，4×3 工具网格 + 缩放面板（横屏 1x-7x / 竖屏 1x-5x，当前档高亮）
   - `ScreenLayoutBottomBar`：底部操作栏（菜单/重设回默认/关闭工具箱/调整屏幕大小）
   - 工具映射：纵向缩放50%/100%、上下左右移动、水平/垂直间距(+gap)、间距100%(gap=0)、原始尺寸(重置本屏)
4. **ViewModel 转发**（`GameViewModelScreenLayout.kt` / `BaseGameScreenViewModel.kt`）: 新增 `updateTransform`(对象重载)、`nudgeScreenLayout`、`setScreenLayoutVerticalScale`、`setScreenLayoutGap`、`setScreenLayoutScale`

**说明**: 副标题（桌面显示名）由 `Lemuroid V8A` 改为 `Lemuroid NDS`。版本号 `versionCode 254 / versionName 1.18.0 / suffix -v8b`。本次由 GitHub Actions 在 `v8b-nds-editor` 分支触发自动编译，产出 `Lemuroid-1.18.0-v8b.apk` 发布至 latest Release。

---

## v1.9 - 2026-09-01

### NDS 屏幕编辑器语义修正 + 三处优化

**按钮语义修正**（对照 UI-元素文档 §4.1，纠正上版误实现）:
- R2C2「自由移动」→ **居中**（选中屏幕移到屏幕中心）
- R1C2「上移」→ **顶部对齐**
- R3C2「下移」→ **底部对齐**
- R2C1「左移」→ **左对齐**
- R2C3「右移」→ **右对齐**
- 对齐/居中需要几何信息，由 overlay 层计算目标 offset 后下发（新增 `setOffset` / `setScreenLayoutOffset`）

**优化**:
1. **编辑时隐藏虚拟按键**：`toggleEditScreenLayout` 进入编辑器时隐藏虚拟按键并记住原状态，退出时恢复
2. **缩放基准改为 NDS 原始分辨率**：`computeNaturalScreenRects` 以 256×192（×density）为基准，缩放面板 1x=256×192 原始分辨率、其他倍数按倍数计算
3. **工具箱默认折叠**：编辑器进入时工具箱默认关闭，屏幕中间显示「工具箱」按钮，点击后展开；底部栏改为 菜单/重设回默认/关闭工具箱/完成

---

## v1.6 - 2026-08-29

### 新增：游戏菜单「载入本地存档」（.sav 直接载入）

**需求**: 在游戏菜单中加入载入本地存档入口，可直接载入本地 `.sav` 格式存档（兼容烧录卡 / DraStic 等生成的 SRAM 存档）。

**实现**:

1. `GameMenuContract.kt` — 新增 `RESULT_LOAD_LOCAL_SAVE` 回传常量
2. `GameMenuHomeScreen.kt` — 在「载入」菜单项下方新增「载入本地存档」入口（仅 `statesSupported` 核心显示，跟随现有载入菜单位置）
3. `BaseGameActivity.kt`:
   - 菜单回传收到 `RESULT_LOAD_LOCAL_SAVE` 后，用 `ACTION_OPEN_DOCUMENT` 打开系统文件选择器（新 requestCode 101，与 DIALOG_REQUEST 区分）
   - 选择器返回 `.sav` Uri → IO 线程读字节 → 超大存档（>1MB，烧录卡填充）自动裁剪至 512KB → **按 NDS 游戏文件名（`game.fileName`）写入存档目录的 `游戏名.srm`**（melonDS 自读的正是这个；上传的 `.sav` 自身文件名与该文件名不一致也没关系，写入始终对齐 ROM 名；不再额外写 `.sav`，避免残留误导文件）
   - **立即应用（关键）**：写盘后重启游戏会话（`restartGameToApplySave()`）—— 新 Activity 会创建全新核心，`retro_load_game` 时 melonDS 从磁盘重读 `*.srm`，从而带上刚导入的存档。当前 Activity 直接 `finish()`（不写回旧 SRAM，避免覆盖新存档）
   - 成功/失败均以 Toast 提示（新增 `game_toast_load_local_save_success/failed`）
4. `SavesManager.kt` — 新增 `getSaveRAMDirectory()` 公开方法
5. `strings.xml` + `values-zh-rCN/strings.xml` — 新增菜单与提示字符串

**说明**: 载入的是 SRAM 存档（.sav），与「保存/载入」菜单项（状态槽位快照）不同。已载入的存档会写回存档目录。
**重要实测结论**: melonDS（NDS）只在**全新启动（`retro_load_game`）时从磁盘重读 `*.srm`**。`GLRetroView.unserializeSRAM()` 内存注入与 `retro_reset` 软重置均不能让它重读档——实测需"杀后台 → 替换 *.srm → 重开"才能生效。因此改为「写盘 + 重启游戏会话」，等效于自动完成上述手动操作。若核心支持内存注入（部分非 NDS 核心），重启同样安全（新核心也会读新档）。TV 版菜单（`TVGameMenuActivity`）未同步此入口。

---

## v1.5 - 2026-07-27（凌晨）

### 修复 v1.4 上下屏独立调节失效 + 启动画面位置异常

**根因**: v1.4 的原生分屏渲染改动写在 `libretrodroid-local/` 源码里，但 app 实际依赖的是 Maven 产物 `com.github.Swordfish90:LibretroDroid:0.13.2`（本地模块从未接入 `settings.gradle.kts`），`splitViewport` 无法编译。编译失败后被临时改成"两屏矩形取并集当单 viewport"的补丁 —— 整帧仍一体渲染，导致"两屏一起动"；并集矩形叠加历史残留偏移，导致"启动画面在屏幕边缘"。

**修复内容**:

1. **本地模块接入构建**（关键）:
   - `settings.gradle.kts` — 增加 `:libretrodroid`（projectDir → `libretrodroid-local/libretrodroid`）
   - `lemuroid-app/build.gradle.kts` — 依赖改为 `implementation(project(":libretrodroid"))`
   - 补齐本地副本缺失的子模块源码：oboe（google/oboe @ b15f5e3）、libretro-common（libretro/libretro-common @ b0c348e），与上游 0.13.2 锁定版本一致
   - 构建环境补齐：sdkmanager 安装 `cmake;3.22.1` 与 `ndk;26.1.10909125`（原 NDK 目录为空壳）
2. **恢复真分屏渲染**：删除"并集 viewport"补丁，`MobileGameScreen.kt` 恢复 `gameView.splitViewport` 调用。已验证：Kotlin 编译通过、字节码含 splitViewport 调用、原生 .so 含 `setSplitViewport` 导出符号
3. **启动位置语义修正**：未保存进方案的调整改为**会话级**（重启不保留）；启动时只应用「显式选中的方案」。编辑器新增「全部重置」按钮

**验证记录**: `:libretrodroid:externalNativeBuildDebug` 原生编译通过（仅上游既有警告）；`:lemuroid-app:compileFreeDynamicDebugKotlin` 通过；`nm` 验证 `liblibretrodroid.so` 含 `Java_..._setSplitViewport` 等 4 个新符号

**注意**: 首次完整构建需编译 oboe + libretrodroid 全部 C++，耗时明显增加属正常

---

## v1.4 - 2026-07-26（深夜）

### NDS 上下屏独立布局 + 手势操作 + 修复触屏失效

**改动内容**:

1. **上下屏独立控制**:「画面布局」编辑器中，点按画面上的虚线框（或 Card 上的"上屏/下屏"按钮）选择屏幕，独立调整其位置和大小。实现上修改了**原生渲染层**：末段 shader pass 由单次绘制改为两次（上半帧纹理/下半帧纹理各入其矩形），触摸坐标映射同步分屏适配（核心收到的坐标与整帧渲染一致，melonDS/DeSmuME 核心无需改动）。旧版整体布局数据自动迁移为双屏相同参数。
2. **手势操作**:编辑器打开后可直接在画面上单指拖动移动选中屏、双指捏合缩放（0.5~2.0x），滑杆保留用于微调。
3. **修复触屏失效**:删除悬浮菜单按钮的全屏透明点击层 —— 它此前吞掉所有触摸，导致关闭虚拟手柄后 NDS 触屏（下屏点击）完全失效。

**修改文件**:
- 原生（`libretrodroid-local/`）:`videolayout.h/cpp`（分屏 quad/纹理裁剪/触摸映射）、`video.h/cpp`（末段双次绘制）、`libretrodroid.h/cpp` + `libretrodroidjni.cpp` + `LibretroDroid.java`（`setSplitViewport` JNI）、`GLRetroView.kt`（`splitViewport` 属性）
- `lemuroid-app/.../screenlayout/ScreenLayoutManager.kt` — 数据模型改为 per-screen transform + 旧数据迁移
- `lemuroid-app/.../viewmodel/GameViewModelScreenLayout.kt`、`BaseGameScreenViewModel.kt` — 按屏更新接口
- `lemuroid-app/.../game/MobileGameScreen.kt` — 分屏 viewport 应用、`ScreenLayoutEditorOverlay`（选屏/手势/双虚线框）、编辑器重写、删除触摸吞没层

**注意**: 首次构建需重编 C++（CMake 自动进行），耗时明显增加属正常。分屏渲染按竖直堆叠（top-bottom/bottom-top）实现，暂不支持核心选项中的 left/right 横排布局。

---

## v1.3 - 2026-07-26（晚）

### 修复 v1.2 实测问题：画面仍移动 + 布局编辑器失效

**实测反馈**: ① 关闭虚拟手柄画面仍"自动居中"；② 「画面布局」滑杆拖动无效。

**根因与修复**（均在 `MobileGameScreen.kt`）:

1. **画面移动根因**: NDS 两个核心 `allowTouchOverlay = false`，隐藏手柄后虚拟按键退出约束布局，gameView 锚点从"手柄间区域"坍缩/撑满全屏 → 画面必然移动（横竖屏同因）。第一版只改了 TOP 对齐未处理锚点变化。**修复**: 锚点冻结 —— 隐藏手柄期间忽略锚点位置更新，沿用最后一次手柄可见时的矩形；旋转后自动重采。
2. **滑杆拖不动根因**: 隐藏手柄时显示的 `DraggableMenuButton` 内含全屏透明点击层拦截所有触摸，而编辑层 z 序在其下方。**修复**: 编辑层（虚线预览 + 滑杆 Card）移到 `DraggableMenuButton` 之后，位于最顶层。
3. **竖屏零反馈**: 自定义变换原限制"仅横屏"。**修复**: 取消横屏门控，横竖屏均可调；默认参数为恒等变换，未配置用户行为不变。

另：viewport 应用处新增 `Timber.d` 日志（tag 过滤 `Setting game viewport`），便于实测验证。

---

## v1.2 - 2026-07-26

### NDS 双屏布局自定义 + 修复隐藏手柄时画面移位

**原因**: 横屏下关闭虚拟手柄时 NDS 双屏被强制移到屏幕顶部（`MobileGameScreen` 强制 TOP 对齐），用户期望画面保持原位，并希望能手动调整双屏位置/大小、保存多套布局方案。

**修复**: 移除隐藏虚拟手柄时强制 `VerticalAlign.TOP` 的逻辑，画面恒保持居中（`GameScreenLayout` 中 TOP 布局代码保留未删）。

**新增功能（仅 NDS 核心 + 横屏生效）**:
- 游戏菜单新增「画面布局」入口（位于 Edit Controls 与 Virtual Controls 之间，仅 NDS 游戏显示）
- 游戏内悬浮编辑 Card：水平/垂直偏移（像素）与缩放（0.5~2.0x）滑杆，实时生效，画面位置以虚线框预览
- 布局方案管理：保存为新方案（默认名"方案 N"）、覆盖保存/重命名、切换、删除、一键重置
- 数据持久化：JSON 存 SharedPreferences（key: `nds_screen_layout_settings`），重启保留
- 竖屏、非 NDS 核心、未配置时的默认居中行为完全不受影响

**实现原理**: 不改动渲染层。游戏画面位置由空锚点 Box 的边界归一化为 `RectF` 赋给 `GLRetroView.viewport` 决定；自定义布局在归一化前对锚点矩形做"中心缩放 + 像素平移"。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — 修复 TOP 对齐；viewport 计算应用自定义变换；新增 `MenuEditScreenLayout` 编辑界面与虚线预览
- `lemuroid-app/.../shared/game/screenlayout/ScreenLayoutManager.kt` — **新增**，方案数据持久化管理器（仿 TouchControllerSettingsManager 模式）
- `lemuroid-app/.../shared/game/viewmodel/GameViewModelScreenLayout.kt` — **新增**，编辑态与方案操作的 ViewModel 封装（NDS 门控）
- `lemuroid-app/.../shared/game/BaseGameScreenViewModel.kt` — 挂载 screenLayout，新增代理方法
- `lemuroid-app/.../shared/GameMenuContract.kt` — 新增 `RESULT_EDIT_SCREEN_LAYOUT`
- `lemuroid-app/.../mobile/feature/gamemenu/GameMenuHomeScreen.kt` — 新增「画面布局」菜单项（仅 NDS）
- `lemuroid-app/.../shared/game/BaseGameActivity.kt` — 处理菜单回传，打开编辑器
- `lemuroid-app/src/main/res/values/strings.xml` — 新增 `game_menu_edit_screen_layout`

**开发日志**: `log/screen-layout-dev-log.md`

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
