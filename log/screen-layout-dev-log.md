# NDS 双屏布局自定义 — 开发日志

> 需求文档：`/Users/simon/Documents/REQUIREMENTS-screen-layout-customizer.md`
> 开工时间：2026-07-26
> 实施人：WorkBuddy

## 实施方案（用户已确认）

1. 修复隐藏虚拟手柄时画面强制 TOP 对齐的 Bug
2. 新建 `ScreenLayoutManager`（JSON 存 SharedPreferences）
3. 新建 `GameViewModelScreenLayout` 并挂入 `BaseGameScreenViewModel`
4. 游戏菜单新增「画面布局」入口（仅 NDS 显示）
5. 实现布局编辑界面（滑杆 + 虚线预览框 + 方案管理）
6. 偏移/缩放应用到 viewport 计算（仅 NDS + 横屏生效）
7. 新增字符串资源

口径约定：
- 自定义布局只在**横屏**生效，竖屏维持原行为
- 编辑界面做成游戏内悬浮 Card（同 Edit Controls 模式）
- 不碰 C++/NDK、不重构 GameScreenLayout、不引数据库、不推 GitHub

---

## 步骤记录

### 步骤 1：修复强制 TOP 对齐 Bug ✅

- 位置：`MobileGameScreen.kt` 原第 176-177 行
- 改动：`verticalAlign` 不再随 `touchControlsVisibleState` 变化，恒为 `CENTER`
- `GameScreenLayout.kt` 中 `VerticalAlign.TOP` 与 `buildConstraintSetLandscapeTopAlign()` 保留不删（未再被引用）
- 涉及文件：`lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt`

### 步骤 2：新建 ScreenLayoutManager ✅

- 新建 `lemuroid-app/.../app/shared/game/screenlayout/ScreenLayoutManager.kt`
- 仿 `TouchControllerSettingsManager` 模式：JSON 字符串存 SharedPreferences + `MutableStateFlow` 内存缓存
- 数据结构：
  - `ScreenLayoutProfile(name, offsetX, offsetY, scale)` — 命名方案
  - `ScreenLayoutState(offsetX, offsetY, scale, profiles, activeProfileId)` — 工作值 + 方案表 + 当前方案
- 存储 key：`nds_screen_layout_settings`（硬编码在 companion，与 touchinput 模块风格一致）
- 方法：`updateTransform / saveAsNewProfile / overwriteActiveProfile(可改名) / selectProfile / deleteProfile / resetToDefault / suggestProfileName`
- JSON 损坏时自动清除并回退默认值（沿用 touchinput 的容错写法）
- 偏移单位：像素；缩放：围绕画面中心，范围 0.5~2.0

### 步骤 3：GameViewModelScreenLayout + ViewModel 挂载 ✅

- 新建 `lemuroid-app/.../app/shared/game/viewmodel/GameViewModelScreenLayout.kt`
  - 持有 `ScreenLayoutManager`，编辑器显隐 `MutableStateFlow`
  - **NDS 门控**：构造时传入 `isNdsSystem`，非 NDS 时 `toggleEditor` 直接忽略
- 修改 `BaseGameScreenViewModel.kt`
  - 新增 `private val screenLayout = GameViewModelScreenLayout(ScreenLayoutManager(sharedPreferences), system.id == SystemID.NDS, viewModelScope)`
  - 新增 12 个代理方法（isNdsSystem / isEditScreenLayoutShown / toggleEditScreenLayout / getScreenLayoutState / currentScreenLayoutState / updateScreenLayoutTransform / saveScreenLayoutAsNewProfile / overwriteActiveScreenLayoutProfile / selectScreenLayoutProfile / deleteScreenLayoutProfile / resetScreenLayoutToDefault / suggestScreenLayoutProfileName）

### 步骤 4：菜单入口 + 回传链路 ✅

- `GameMenuContract.kt`：新增 `RESULT_EDIT_SCREEN_LAYOUT`
- `GameMenuHomeScreen.kt`：在「Edit Controls」与「Virtual Controls」之间插入「画面布局」`LemuroidSettingsMenuLink`，仅 `game.systemId == SystemID.NDS.dbname`（"nds"）时显示；复用 `ic_menu_controls` 图标
- `BaseGameActivity.kt`：`onActivityResult` 中处理 `RESULT_EDIT_SCREEN_LAYOUT` → `toggleEditScreenLayout(true)`

### 步骤 5：布局编辑界面 ✅（MobileGameScreen.kt）

- 新增 `MenuEditScreenLayout` composable，仿 `MenuEditTouchControls` 的 Card+Slider 风格：
  - 方案选择下拉框（无方案时显示"暂无已保存方案"；未选中方案时显示"自定义（未保存）"）
  - 三个滑杆：水平 X（-屏宽~+屏宽 px）、垂直 Y（-屏高~+屏高 px）、缩放（0.5~2.0），滑杆值实时写入 ViewModel → 实时生效
  - 按钮行：保存方案 / 删除方案（仅激活方案时显示）/ 重置 / 完成
  - 保存弹窗：名称输入框（默认"方案 N"或当前方案名）+「取消 / 覆盖当前（仅激活方案时）/ 存为新方案」→ 覆盖时改名即重命名
- 编辑层放置：PadKit 内容层、ConstraintLayout 之外（兄弟节点），因此**隐藏虚拟手柄时也能用**；z 序在 DraggableMenuButton 之前（其下层），点击事件编辑层优先
- 编辑打开时在画面当前生效位置画白色半透明虚线框（Canvas + dashPathEffect），仅横屏绘制（竖屏不应用变换，避免误导）
- 修复过的坑：`ifBlank { null }` 类型不匹配（ifBlank 要求非空返回值），改为 `trim().let { if (it.isEmpty()) null else it }` 形式

### 步骤 6：viewport 应用偏移/缩放 ✅（MobileGameScreen.kt）

- 原理：原逻辑把锚点 Box（`CONSTRAINTS_GAME_VIEW`）的 root 坐标边界归一化为 `RectF` 赋给 `GLRetroView.viewport`
- 改动：`LaunchedEffect(fullPos, viewPos, screenLayout, isLandscape)` 中，满足 `isNdsSystem && isLandscape && !isDefault` 时先用 `applyScreenLayoutTransform()` 对锚点矩形做"中心缩放 + 像素平移"，再归一化
- 新增私有函数 `applyScreenLayoutTransform(base: Rect, state): Rect`
- 生效条件严格限定 NDS + 横屏；竖屏、非 NDS 核心、未配置（默认 0,0,1.0）时走原始路径，行为零变化

### 步骤 7：字符串资源 ✅

- `strings.xml` 新增 `game_menu_edit_screen_layout` = "画面布局"（编辑界面内标签与现有触控编辑器一致，直接硬编码中文）

### 其他收尾

- ktlint 导入顺序修正：`GameMenuHomeScreen.kt`（SystemID 移到 lib 分组）、`BaseGameScreenViewModel.kt`（screenlayout 移到 viewmodel 之前）
- 构建由用户自行执行；未做编译验证

---

## 完成状态

全部 7 步完成，等待用户构建实测。构建命令见需求文档 4.5 节。

---

## 第二轮修复（2026-07-26 晚，用户实测反馈后）

### 用户反馈

1. 关闭虚拟手柄后画面"还是会自动居中"（仍移动）
2. 「画面布局」没有任何作用，拖动无法改变画面大小和位置

### 根因分析（3 个）

**根因 A：隐藏手柄时锚点矩形本身会变（横竖屏都存在）**
- DeSmuME / MelonDS 的 `ControllerConfig.allowTouchOverlay = false`（ControllerConfigs.kt:258,273）
- 横屏恒走 `buildConstraintSetLandscapeNoOverlay()`：显示手柄时 gameView 被约束在左右手柄之间的中间带；隐藏后手柄退出组合，约束引用坍缩为零，gameView 撑满全宽 → 画面横向"自动居中"
- 竖屏同理：gameView 底部约束在 leftPad.top，手柄消失后 gameView 撑满全高 → 画面纵向"自动居中"
- 第一版只改了 TOP 对齐，没处理锚点本身的变化 → 用户感知"没解决"

**根因 B：编辑层 z 序错误，触摸被全吞（滑杆拖不动的直接原因）**
- `DraggableMenuButton`（隐藏手柄时显示）内部有一层**全屏透明 clickable**，会拦截所有触摸（防止误触 NDS 触屏）
- 第一版把编辑层放在 PadKit 内，z 序在 DraggableMenuButton **之下** → 隐藏手柄打开编辑器时，滑杆根本收不到触摸事件 → "拖动无法改变"

**根因 C：自定义变换门控为"仅横屏"**
- 第一版按验收标准"不影响竖屏模式"把变换限制在横屏；竖屏下拖滑杆零反馈
- 实际上"不影响竖屏"应理解为"竖屏**默认行为**不变"（默认参数恒等变换即可保证），而不是竖屏禁用该功能

### 修复内容（均在 MobileGameScreen.kt）

1. **锚点冻结**：`onGloballyPositioned` 中仅当手柄可见（或尚无记录）时才更新 `viewportPosition`；隐藏手柄期间忽略锚点变化，沿用最后一次可见布局的矩形 → 隐藏手柄画面不再移动（横竖屏都生效）。`viewportPosition` 改为 `remember(isLandscape)`，旋转后自动失效重采
2. **编辑层挪到最顶层**：从 PadKit 内部移到 `DraggableMenuButton` 之后（BoxWithConstraints 根部），`fullScreenPosition`/`viewportPosition` 声明随之提升到根部作用域 → 滑杆恢复触摸
3. **取消横屏门控**：`applyCustomLayout` 不再要求 `isLandscape`，默认参数下是恒等变换，未配置用户行为零变化；虚线预览框同步取消横屏限制
4. 加 `Timber.d("Setting game viewport: ...")` 日志，可用 logcat 验证 viewport 是否按预期变化

### 待用户验证

- 重新构建后：① 隐藏手柄画面应原地不动；② 打开「画面布局」拖滑杆画面应实时移动/缩放（横竖屏均可）
- 若仍无效果，抓 logcat 过滤 `Setting game viewport` 看 viewport 值是否随滑杆变化

---

## 第三轮改造（2026-07-26 深夜，v1.4）：上下屏独立控制 + 手势 + 触屏修复

### 用户需求

1. 上屏/下屏独立调整大小和位置（点选屏幕 → 调整 → 换另一屏）
2. 支持单指拖动移动、双指捏合缩放
3. Bug：关闭虚拟手柄后 NDS 触屏（下屏触摸输入）也失效

### 关键决策：必须动 C++ 渲染层（原需求"不要碰"被新需求覆盖）

- NDS 双屏打包在同一张 256x384 纹理里，单 viewport 无法独立控制 → 原生末段 shader pass 改为**绘制两次**：上半屏纹理 → 矩形 1，下半屏纹理 → 矩形 2
- 触摸映射 `getRelativePosition` 同步分屏：命中哪个 quad 就映射回帧空间对应半区（核心拿到的坐标与整帧渲染时完全一致，melonDS/DeSmuME 无需改动）
- 原生层会随 Gradle 构建自动重编（CMake），用户首次构建会变慢

### 原生层改动（libretrodroid-local）

- `videolayout.h/cpp`：新增 splitScreenEnabled、viewportRect2、foregroundVertices2、textureCoordinatesTop/Bottom（v 方向 0~0.5 / 0.5~1 裁剪）；`updateForegroundVertices` 重构为 `updateForegroundQuad(rect, aspect, out)`；分屏时每屏内容宽高比 = 整帧 × 2；`updateSplitViewportSize`；`getRelativePositionInQuad` 抽取 + 分屏帧空间重映射；`updateRelativeForegroundBounds` 取两 quad 并集
- `video.h/cpp`：`updateSplitViewportSize` 直通；`renderFrame` 末段 pass 分屏时两次 draw
- `libretrodroid.h/cpp`：`setSplitViewport(r1, r2)`，持有 splitViewportEnabled 并在 Video 重建后重放
- `libretrodroidjni.cpp`：JNI `setSplitViewport` 导出
- `LibretroDroid.java`：native 声明
- `GLRetroView.kt`：`splitViewport: Pair<RectF, RectF>?` 属性；置 null 回退单 viewport

### Kotlin 层改动

- `ScreenLayoutManager`：数据模型改为 `topScreen`/`bottomScreen` 两个 `ScreenTransform`；`ScreenId` 枚举；v1.2 旧数据（含 profiles 内）加载时自动迁移（旧组合值复制到两屏后清零）
- `GameViewModelScreenLayout` / `BaseGameScreenViewModel`：`updateTransform(screen, ...)`、`resetScreen(screen)`
- `MobileGameScreen.kt`：
  - `computeNaturalScreenRects()`：整帧按 256/384 宽高比适配锚点后切上下两半，作为每屏基准矩形
  - LaunchedEffect：非默认布局 → `splitViewport = (top, bottom)`；默认 → 单 viewport（`splitViewport = null`）
  - `ScreenLayoutEditorOverlay`：双虚线框预览（选中高亮）、`detectTapGestures` 点按选屏、`detectTransformGestures` 单指拖/双指捏合作用于选中屏（pointerInput 以 rect/state 为 key 保证拿到最新值）
  - `MenuEditScreenLayout` 重写：上屏/下屏切换按钮（Button/OutlinedButton）、滑杆绑定选中屏、「重置本屏」；方案保存/覆盖/切换/删除保留（方案含两屏参数）
  - **触屏修复**：删除 `DraggableMenuButton` 的全屏透明 clickable 层（它吞掉所有触摸导致 NDS 触屏失效），悬浮按钮本身保留

### 注意事项 / 已知边界

- 分屏按**竖直堆叠**（top-bottom / bottom-top）假设实现，即本 fork 换屏功能使用的两种布局；若用户在核心选项里改用 left/right 横排，分屏渲染会不正确（未做支持）
- Kotlin 侧基准矩形按 256x384 帧计算；原生侧用核心上报的真实宽高比做每屏适配，二者一致
- 首次构建需重编 C++，耗时明显增加属正常

---

## 第四轮修复（2026-07-27 凌晨，v1.5）：本地模块未接入 + 紧急补丁反噬

### 用户反馈

1. 打开游戏后双屏不在初始位置，跑到屏幕边缘
2. 上下屏独立调节失败：选中上屏调整，实际渲染仍是两屏一起动（只有虚线框独立变化）

### 根因（本次最关键的发现）

1. **v1.4 的原生改动从未进入构建**：app 的 `GLRetroView` 依赖的是 Maven 产物 `com.github.Swordfish90:LibretroDroid:0.13.2`（deps.kt:169），`libretrodroid-local/` 模块（untracked）根本不在 `settings.gradle.kts` 的 include 里。我 v1.4 改了本地源码但没接线 —— `gameView.splitViewport` 编译必然失败
2. **用户侧自行打了"合并补丁"**：编译失败后 MobileGameScreen.kt 被改成了 `mergeToSingleViewport()`（取两屏矩形的并集当单 viewport）使编译通过 → 整帧仍在一个 viewport 里渲染，两屏自然"一起动"（Bug 2）；并集矩形叠加 v1.3 残留的测试偏移（迁移到双屏）→ 启动时画面在边缘（Bug 1）
3. 证据链：dependencyInsight 显示 compile classpath = maven 0.13.2；javap 该 AAR 的 GLRetroView 仅有 getViewport/setViewport；编译产物 MobileGameScreenKt.class 里没有 splitViewport 调用、却有 mergeToSingleViewport

### 修复内容

1. **接入本地模块**：`settings.gradle.kts` 增加 `:libretrodroid`（projectDir → `libretrodroid-local/libretrodroid`）；`lemuroid-app/build.gradle.kts` 依赖从 `deps.libs.libretrodroid` 改为 `project(":libretrodroid")`
2. **补齐缺失子模块**：本地副本的 `cpp/oboe/`、`cpp/libretro/libretro-common/` 是空目录（上游 submodule）。按上游 0.13.2 锁定的 commit 下载填入：
   - oboe @ b15f5e39c01a7ada306d959e5129620b145fb8b4
   - libretro-common @ b0c348ea5543c4d7fb0bc479258aa6988b20c0c9
3. **补齐构建工具链**：SDK 里 NDK 目录是空壳（无 source.properties）、无 CMake。用 sdkmanager 安装 `cmake;3.22.1` 和 `ndk;26.1.10909125`
4. **恢复 split 渲染调用**：MobileGameScreen.kt 删除 mergeToSingleViewport 补丁，恢复 `gameView.splitViewport = top to bottom`（非默认时）/ `splitViewport = null`（默认时）
5. **启动位置语义修正（Bug 1 的根治）**：`ScreenLayoutManager.loadState()` 新增 `deriveWorkingValues()` —— 工作值改为**会话级**，启动时只从「显式选中的方案」派生；未保存进方案的拖动一律不带入下次会话。编辑器新增「全部重置」按钮

### 用户须知

- 首次构建会完整编译 C++（oboe + libretrodroid），耗时显著增加
- 安装后如果画面仍在非默认位置：打开「画面布局」→「全部重置」一次即可（此后未保存的调整重启自动清空）
