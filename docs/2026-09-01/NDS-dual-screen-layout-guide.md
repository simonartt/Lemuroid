# NDS 双屏独立布局 —— 实现方案解析 与 二次开发指南

> 版本：v1.5（2026-07-27）
> 适用代码：本仓库 `v8a` 分支当前状态
> 相关过程日志：`log/screen-layout-dev-log.md`

---

# 第一部分：实现方式 —— 上下屏单独可调是怎么做成的

## 1.1 先搞清楚问题的本质：为什么这件事"看起来简单，做起来全败"

NDS 的两块屏幕**不是两个独立的画面**。melonDS / DeSmuME 核心在内部把两块 256x192 的屏幕**打包进同一张 256x384 的帧纹理**（竖直堆叠：上半是上屏，下半是下屏），整帧一次性交给前端渲染。

而 App 侧（Kotlin/Compose）能控制的只有一个东西：

```
GLRetroView.viewport: RectF  →  LibretroDroid.setViewport(x, y, w, h)
```

**一个矩形，决定"整帧画在屏幕的哪里"。** 无论这个矩形怎么移动、缩放，上下两块屏在纹理内部是焊死的，永远一起动。

所以结论很直接：**在 Kotlin/Compose 层做任何努力都不可能实现上下屏独立控制**——之前没做成的根因就是在错误的层发力。

## 1.2 那些"注定失败"的做法（避坑记录）

| 做法 | 为什么不行 |
|------|-----------|
| 改 Compose 约束布局 / 移动锚点 | 只能整体移动"整帧"，两屏依旧绑定 |
| 两屏矩形取并集当 viewport（merge 补丁） | 渲染仍然是整帧入一个矩形，虚线框独立只是 UI 假象（v1.4 时实际发生过一次） |
| 调核心变量（`melonds_screen_layout1`、`desmume_screens_layout`） | 只有 top-bottom / bottom-top / left-right 等几种预设排布，不支持任意位置和缩放 |
| 双开 GLRetroView | 一个核心只有一帧输出，拿不到两份画面 |

## 1.3 唯一可行的方案：渲染层"拆帧"（Split Viewport）

核心思路一句话：**把"一帧 → 一个矩形"改成"半帧 → 一个矩形" × 2。**

帧纹理的上半部分是"上屏"内容、下半部分是"下屏"内容。在原生渲染管线的**最后一段 shader pass**，把原来的一次绘制拆成两次：

```
第 1 次绘制：纹理坐标裁剪 v ∈ [0, 0.5]（纹理上半 = 上屏）→ 画进位置矩形 R1
第 2 次绘制：纹理坐标裁剪 v ∈ [0.5, 1]（纹理下半 = 下屏）→ 画进位置矩形 R2
```

R1 和 R2 两个矩形互相独立，由 Kotlin 侧分别下发 —— 上下屏就此解耦。每块屏在各自矩形内仍做宽高比适配（每屏内容宽高比 = 整帧宽高比 × 2 = 256/192 ≈ 1.33）。

多段 shader（CRT/CUT 等滤镜）完全不受影响：它们在前面的 pass 里处理完整帧纹理，只有最后"上屏"这一步拆成两笔。

## 1.4 全链路数据流

```
用户拖滑杆/捏合手势
  ↓ (offsetX, offsetY, scale) 按屏存储
ScreenLayoutManager（SharedPreferences + JSON，方案持久化）
  ↓ StateFlow
MobileGameScreen.LaunchedEffect
  · computeNaturalScreenRects()   整帧按 256:384 适配锚点 → 切成上下两个"自然矩形"
  · applyScreenLayoutTransform()  对各自自然矩形做"中心缩放 + 像素平移"
  · normalizeToFullScreen()       归一化成 0..1 的 GL 视口坐标
  ↓ gameView.splitViewport = (R1, R2)
GLRetroView.splitViewport（libretrodroid-local，GL 线程）
  ↓ JNI: LibretroDroid.setSplitViewport(R1, R2)
libretrodroid.cpp → Video → VideoLayout
  · updateForegroundQuad(R1, aspect×2) → 顶点 quad1
  · updateForegroundQuad(R2, aspect×2) → 顶点 quad2
  · UV 数组：Top=[0..0.5]、Bottom=[0.5..1]
Video::renderFrame（末段 pass）
  · draw quad1（纹理上半）→ glDrawArrays
  · draw quad2（纹理下半）→ glDrawArrays
```

关键文件：

| 层 | 文件 | 作用 |
|----|------|------|
| Kotlin 应用 | `lemuroid-app/.../game/MobileGameScreen.kt` | 矩形计算、变换、编辑器 UI、手势 |
| Kotlin 桥 | `libretrodroid-local/.../GLRetroView.kt` | `splitViewport: Pair<RectF, RectF>?` 属性 |
| JNI | `libretrodroidjni.cpp` + `LibretroDroid.java` | `setSplitViewport` 导出 |
| 原生 | `videolayout.h/cpp` | 双 quad 顶点、纹理裁剪、触摸映射 |
| 原生 | `video.h/cpp` | 末段 pass 两次绘制 |
| 原生 | `libretrodroid.h/cpp` | 状态持有、Video 重建后重放 |

## 1.5 触摸映射同步（容易被忽略、但不做就会出怪 bug）

NDS 的触屏输入是**帧空间**坐标（核心期望拿到"点在整帧里的相对位置"）。整帧渲染时，原生用单个 quad 的边界做命中测试。分屏之后必须改成：

1. 分别对 quad1 / quad2 做命中测试；
2. 命中后把"quad 内相对坐标"**映射回帧空间**：上屏 quad → `(x, y/2)`，下屏 quad → `(x, 0.5 + y/2)`。

这样核心收到的坐标与整帧渲染时完全一致 —— **melonDS / DeSmuME 不需要任何改动**，触摸落点天然正确（实现位置：`VideoLayout::getRelativePosition`）。

## 1.6 工程上这次为什么能成（方法论记录）

1. **先验证依赖真相，再动手**：`dependencyInsight` 确认 `GLRetroView` 来自 Maven 成品（`com.github.Swordfish90:LibretroDroid:0.13.2`），而不是本地源码 —— 这正是 v1.4 第一次失败的原因（改了本地源码但没接进构建）。修复：`settings.gradle.kts` 注册模块 + `implementation(project(":libretrodroid"))`。
2. **补齐构建前提**：本地副本的 oboe / libretro-common 是空子模块，按上游 0.13.2 锁定的 commit 下载填入；NDK/CMake 缺失用 sdkmanager 补齐。
3. **每层交付前做产物验证**，不靠"理论上应该行"：
   - `:libretrodroid:externalNativeBuildDebug` 原生编译通过
   - `javap` 验证字节码里真有 `splitViewport` 调用
   - `nm` 验证 `.so` 里真有 `setSplitViewport` 导出符号
4. **不在错误的层做修补**：merge 并集补丁就是"在 Kotlin 层假装实现了分屏"，表面能编译、实际功能全假。

---

# 第二部分：需求调整指南 —— 以后改功能该动哪里

按"改动频率从高到低、风险从低到高"分四个板块。**改前两个板块就能覆盖绝大多数需求。**

## 2.1 交互 / UI 层（纯 Kotlin，最安全）

文件：`lemuroid-app/src/main/java/com/swordfish/lemuroid/app/mobile/feature/game/MobileGameScreen.kt`

| 需求 | 改哪里 |
|------|--------|
| 改编辑器样式、按钮、文案 | `MenuEditScreenLayout()`（底部 Card） |
| 改选屏方式 | `ScreenLayoutEditorOverlay()` 里的 `detectTapGestures` + 上屏/下屏按钮行 |
| 调手势（只拖不捏、加灵敏度系数、加双指旋转） | `detectTransformGestures { _, pan, zoom, rotation -> ... }` 回调体 |
| 改虚线框样式/颜色 | `drawScreenFrame()` |
| 菜单入口显隐规则 | `gamemenu/GameMenuHomeScreen.kt` 的 `if (game.systemId == SystemID.NDS.dbname)` |
| 缩放/偏移范围 | `ScreenLayoutManager` 的 `MIN_SCALE / MAX_SCALE` + 滑杆 `valueRange` |

## 2.2 数据 / 持久化层（Kotlin，低风险）

| 需求 | 改哪里 |
|------|--------|
| 方案存更多字段（如旋转、间距） | `ScreenLayoutManager.ScreenTransform` 加字段（注意写迁移） |
| 每个游戏独立方案 | 存储 key 加 gameId 维度（参考 `TouchControllerSettingsManager.getPreferenceString()` 的做法） |
| 改"启动时应用什么" | `loadState()` + `deriveWorkingValues()`（当前语义：未保存进方案的调整不跨会话） |
| 改方案默认名规则 | `suggestProfileName()` |
| ViewModel 接口 | `GameViewModelScreenLayout.kt`、`BaseGameScreenViewModel.kt`（纯转发，跟着 manager 改） |

## 2.3 布局计算层（Kotlin，中风险）

文件：同上 `MobileGameScreen.kt`

| 需求 | 改哪里 |
|------|--------|
| 改"自然位置"（默认怎么摆） | `computeNaturalScreenRects()` —— 整帧适配 + 切半的逻辑 |
| 改帧宽高比（非 NDS 系统复用时） | `NDS_FRAME_ASPECT = 256f / 384f` |
| 改变换方式（比如非等比缩放） | `applyScreenLayoutTransform()`（当前：绕中心缩放 + 平移，矩形始终等比） |
| 隐藏手柄时的行为 | 锚点 `onGloballyPositioned` 里的冻结逻辑（当前：隐藏手柄时沿用最后可见布局的矩形） |
| 横竖屏区别对待 | `LaunchedEffect` 里 `applyCustomLayout` 的门控条件 |
| 想要"双屏联动"模式 | 编辑器加开关，开启时两屏写相同 transform 即可，不用动渲染层 |

## 2.4 原生渲染层（C++，高风险，谨慎动）

文件：`libretrodroid-local/libretrodroid/src/main/cpp/`
**改完必须等 CMake/NDK 重编；改前先在真机上验证默认路径无回归。**

| 需求 | 改哪里 | 注意 |
|------|--------|------|
| 支持 left/right 横排布局 | `videolayout.cpp` 的纹理裁剪改 u 方向（u∈[0,0.5]/[0.5,1]）；Kotlin 基准矩形同步改；每屏宽高比 = 帧/2；触摸映射改 x 方向 | 帧变为 512x192，宽高比要动态感知，不能硬编码 |
| 支持 3DS | 同上，但 3DS 上下屏**大小不同**（上 400x240、下 320x240，帧 400x480），分割比例不是 1/2，需要参数化裁剪比例 | 工作量大，建议单独立项 |
| 加每屏旋转 | 复用 `VideoLayout.rotation` 机制到 `updateForegroundQuad()`（结构已支持） | 触摸映射也要按旋转反算 |
| 调滤镜与分屏的兼容 | `video.cpp renderFrame()` 末段的 `splitLastPass` 分支 | 中间 pass 不要动，只动最后一笔 |
| 触屏映射规则 | `VideoLayout::getRelativePosition()` | 核心永远期望"帧空间"坐标，这是不变量 |

## 2.5 速查：一句话需求 → 落点

- "缩放想能放到 3 倍" → `ScreenLayoutManager.MAX_SCALE` + 滑杆 range（2.1）
- "想要拖动时吸附居中" → `detectTransformGestures` 回调里加 snap 逻辑（2.1）
- "想保存方案时截图预览" → 方案数据结构加字段 + 编辑器 UI（2.1 + 2.2）
- "想横屏竖屏各存一套" → 存储 key 加 orientation 维度（2.2）
- "想要左右并排双屏" → 原生裁剪方向 + Kotlin 基准矩形 + 触摸映射（2.3 + 2.4）
- "想彻底关掉这个功能" → 菜单入口删除 + `applyCustomLayout` 恒 false（2.1 + 2.3）

---

## 附：构建与验证命令

```bash
cd /Users/simon/team-projects/Lemuroid
export JAVA_HOME=/Users/simon/.hermes/android-env/jdk/Contents/Home
export HTTP_PROXY=http://127.0.0.1:7890 HTTPS_PROXY=http://127.0.0.1:7890

# 只验证原生改动编译（改 C++ 后必跑，最快反馈）
./gradlew :libretrodroid:externalNativeBuildDebug --offline

# 验证 Kotlin 编译
./gradlew :lemuroid-app:compileFreeDynamicDebugKotlin --offline

# 完整出包
./gradlew :lemuroid-app:assembleFreeDynamicDebug

# 验证原生符号（可选）
nm -D libretrodroid-local/libretrodroid/build/intermediates/cmake/debug/obj/arm64-v8a/liblibretrodroid.so | grep -i split

# 运行时日志
adb logcat | grep -E "Setting (split|game) viewport"
```
