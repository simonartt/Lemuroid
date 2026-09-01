# NDS 双屏布局自定义 — 功能需求文档

## 1. 概述

本项目是 Lemuroid（Android 多平台模拟器）的定制版 fork。源码基于 Swordfish90/Lemuroid，当前在 `v8a` 分支，位于 `/Users/simon/team-projects/Lemuroid/`。

本需求针对 **NDS 双屏模拟** 场景，增加屏幕位置/大小自定义功能，并支持多套布局方案保存和切换。

---

## 2. 问题描述

### 当前行为（有 Bug）

在 `MobileGameScreen.kt` 第 176-177 行：

```kotlin
verticalAlign = if (!touchControlsVisibleState.value && isLandscape)
    GameScreenLayout.VerticalAlign.TOP else GameScreenLayout.VerticalAlign.CENTER,
```

当用户在游戏菜单中关闭「虚拟手柄」（Virtual Controls）时：
- 横屏模式下 → `verticalAlign` 被强制设为 `TOP`
- 触发 `buildConstraintSetLandscapeTopAlign()` 布局
- NDS 双屏画面整体 **移到屏幕顶部**，下方留出大片空白
- 竖屏模式不受影响（一直 CENTER）

### 用户期望

1. **关闭虚拟手柄时，画面不要移动** — 保持原来的居中/用户设定的位置
2. **支持自定义双屏的位置和大小** — 用户可以手动调
3. **支持多套布局方案** — 保存/切换

---

## 3. 功能需求

### 3.1 修复：隐藏虚拟手柄时画面保持原位

**改动位置**: `MobileGameScreen.kt` 第 176-177 行

**要求**: 移除强制 TOP 对齐的逻辑。当虚拟手柄隐藏时，画面应使用与显示时相同的布局（CENTER 或用户自定义位置）。

**影响范围**:
- 可能需要废弃 `GameScreenLayout.VerticalAlign.TOP` 和 `buildConstraintSetLandscapeTopAlign()`
- 或者保留但改为用户可选（见 3.2）

### 3.2 NDS 双屏布局自定义

用户可以在游戏中的菜单里，对 NDS 双屏画面进行以下调整：

#### 3.2.1 可调参数

针对 **NDS 模拟核心**（melonDS / DeSmuME）全屏游戏画面，支持：

| 参数 | 范围 | 默认值 | 说明 |
|------|------|--------|------|
| 垂直偏移 (Y) | -屏幕高度 ~ +屏幕高度 | 0 (居中) | 负值上移，正值下移 |
| 水平偏移 (X) | -屏幕宽度 ~ +屏幕宽度 | 0 (居中) | 负值左移，正值右移 |
| 缩放 (Scale) | 0.5x ~ 2.0x | 1.0x | 画面整体缩放 |

#### 3.2.2 操作入口

在游戏菜单（`GameMenuHomeScreen.kt`）中新增一个菜单项：

```
「画面布局」 → 打开布局编辑界面
```

建议在现有的「Edit Controls」和「Virtual Controls」之间插入。

#### 3.2.3 布局编辑界面

新增一个 Compose 界面（可参考 `MenuEditTouchControls` 的样式），包含：

1. **实时预览** — 在背景显示当前游戏画面，叠加半透明虚线框表示屏幕边界
2. **位置调节** — 用 Slider 调节 X/Y 偏移和缩放（同触控按键编辑的 UI 风格）
3. **方案管理** — 见 3.3
4. **重置按钮** — 一键恢复默认居中

### 3.3 布局方案管理

#### 3.3.1 概念

用户可创建多套「布局方案」，每套方案包含一组（X偏移，Y偏移，缩放）参数。

#### 3.3.2 功能

| 功能 | 说明 |
|------|------|
| 保存当前布局为新方案 | 弹窗输入方案名称 |
| 保存覆盖已有方案 | 更新已保存的方案 |
| 切换方案 | 从方案列表中选择，实时生效 |
| 删除方案 | 删除不再需要的方案 |
| 方案默认名 | "方案 1", "方案 2" 等，可重命名 |

#### 3.3.3 数据存储

使用 `SharedPreferences` 存储（已有 `SettingsManager` 机制可参考）：

```kotlin
// 存储格式（JSON）
{
  "profiles": {
    "profile_1": { "name": "全屏", "offsetX": 0, "offsetY": 0, "scale": 1.5 },
    "profile_2": { "name": "底部偏左", "offsetX": -50, "offsetY": 100, "scale": 1.0 }
  },
  "activeProfile": "profile_1"
}
```

---

## 4. 技术上下文（给新开发者参考）

### 4.1 项目结构

```
lemuroid-app/
  src/main/java/com/swordfish/lemuroid/app/
    mobile/feature/game/
      MobileGameScreen.kt           ← 游戏主界面（Compose），关键修改点
      GameScreenLayout.kt           ← 约束布局定义，关键修改点
    mobile/feature/gamemenu/
      GameMenuHomeScreen.kt         ← 游戏菜单，需添加新入口
    shared/game/
      BaseGameScreenViewModel.kt    ← ViewModel，新增状态和方法
      viewmodel/
        GameViewModelTouchControls.kt  ← 触控控制状态，参考其模式
    mobile/feature/settings/
      SettingsManager.kt            ← SharedPreferences 管理器，参考其模式
  src/main/res/values/
    keys.xml                        ← Preference key 定义
    strings.xml                     ← 字符串资源
```

### 4.2 核心类说明

#### MobileGameScreen.kt
- Compose 可组合函数，游戏主界面
- 使用 `ConstraintLayout` 布局（`GameScreenLayout.buildConstraintSet()`）
- 第 176-177 行：当前强制 TOP 对齐的逻辑 → **需修改**
- 第 101-102 行：`touchControlsVisibleState` 控制虚拟手柄显隐
- 第 230-233 行：隐藏手柄时显示悬浮菜单按钮

#### GameScreenLayout.kt
- 定义 4 种约束布局：
  - `buildConstraintSetPortrait()` — 竖屏
  - `buildConstraintSetLandscape()` — 横屏 + 有触控覆盖
  - `buildConstraintSetLandscapeNoOverlay()` — 横屏 + 无覆盖（手柄接入时）
  - `buildConstraintSetLandscapeTopAlign()` — 横屏 + 顶部对齐（当前隐藏手柄时使用）
- `VerticalAlign` 枚举：`CENTER`, `TOP`
- 修改思路：移除强制 TOP，改为透传用户自定义偏移量

#### GameMenuHomeScreen.kt
- 第 144-156 行：现有「虚拟手柄」开关
- 第 131-142 行：现有「Edit Controls」入口
- 需要在此新增「画面布局」菜单项

#### SettingsManager.kt
- 基于 `FlowSharedPreferences` 的键值存储
- 参考 `gameViewVerticalAlign()` 方法（第 51-52 行）的模式的模式写新的持久化方法
- 已有 `pref_key_game_view_vertical_align` key 在 keys.xml 第 23 行

### 4.3 数据持久化方案

有两种方式可选：

**方案 A：使用 SharedPreferences（推荐）**
- 参考 `SettingsManager` 的模式
- 新增一个 JSON 字符串字段存储所有方案数据
- 优点：简单、不需要数据库

**方案 B：使用 Proto DataStore**
- 本项目当前没有使用 DataStore，引入会增加依赖
- 不推荐，除非你有充分理由

### 4.4 需要注意的规则

1. **只针对 NDS 核心生效** — melonDS 和 DeSmuME
2. **不改变默认行为** — 如果用户从未进入布局编辑，画面应保持原来默认的居中行为
3. **不要改原版文件结构** — 新增文件放在对应包下，不要重构已有文件
4. **编码规范** — 使用 Kotlin + Compose，与项目现有风格一致

### 4.5 构建和测试

```bash
cd /Users/simon/team-projects/Lemuroid
export JAVA_HOME=/Users/simon/.hermes/android-env/jdk/Contents/Home
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
./gradlew :lemuroid-app:assembleFreeDynamicDebug
```

APK 输出：`lemuroid-app/build/outputs/apk/freeDynamic/debug/lemuroid-app-free-dynamic-debug.apk`

⚠ 注意：macOS 需要走 FlClash 代理（127.0.0.1:7890）才能下载 Gradle 依赖。代理已配置在 `gradle.properties` 的 `org.gradle.jvmargs` 中。

---

## 5. 验收标准

- [ ] 关闭虚拟手柄后，NDS 双屏保持原位置不动
- [ ] 游戏中可通过菜单进入「画面布局」编辑界面
- [ ] 可调节 X/Y 偏移和缩放，实时生效
- [ ] 可保存多套布局方案，命名、切换、删除
- [ ] 方案数据持久化，重启 app 后保留
- [ ] 不影响非 NDS 核心（GBA、PSP 等）
- [ ] 不影响竖屏模式
- [ ] 不改动默认未配置时的居中行为

---

## 6. 不建议做的

- ❌ 不要碰 C++/NDK 层（libretro 核心代码）
- ❌ 不要改 DualScreenView 相关的渲染代码（如果有的话）
- ❌ 不要引入 Room 或其他数据库
- ❌ 不要重构现有布局系统（GameScreenLayout）
- ❌ 不要修改调试签名配置
- ❌ 不要推送到 GitHub（用户先测试确认后再推）
