# 实现任务：修复 Lemuroid 游戏画面垂直对齐功能

## 目标

连接蓝牙手柄后 NDS 双屏居中显示（上下留黑边），用户希望画面靠上显示、下方留空。当前代码已写但功能不生效。

---

## 背景知识

### 项目结构

```
lemuroid-app/
  src/main/java/com/swordfish/lemuroid/
    app/
      mobile/feature/game/
        MobileGameScreen.kt    ← Compose 游戏画面
        GameScreenLayout.kt    ← 约束布局定义
        GameActivity.kt
      mobile/feature/settings/
        SettingsManager.kt     ← 设置读写
      shared/game/
        BaseGameScreenViewModel.kt  ← 核心 ViewModel
    res/values/
      keys.xml                 ← 设置 key 定义
      strings.xml              ← 字符串资源
```

### 核心渲染机制

游戏画面渲染分两层：

1. **`AndroidView`**（全屏 GLRetroView）：整个屏幕大小的 OpenGL 渲染面。位于 ConstraintLayout **外部**。
2. **`ConstraintLayout`**（全屏）：包含一个空 `Box(layoutId=GAME_VIEW)`，仅用于标记渲染视口位置。

```kotlin
// MobileGameScreen.kt 中的关键结构
PadKit(fillMaxSize) {
    AndroidView(fillMaxSize)          // ← GL 渲染面，全屏
    ConstraintLayout(fillMaxSize) {
        Box(layoutId=GAME_VIEW)       // ← 视口标记 Box，空的！
        leftPad(layoutId=LEFT_PAD)
        rightPad(layoutId=RIGHT_PAD)
        // ...
    }
}
```

**视口计算**：`GAME_VIEW` Box 的屏幕坐标被归一化为 0~1 的 RectF，传给 GLRetroView.viewport。GL 渲染器只在这个子区域内绘制，并在此区域内居中保持画面比例。

```
视口 = (boxLeft/屏幕宽, boxTop/屏幕高, boxRight/屏幕宽, boxBottom/屏幕高)
```

**这意味着**：通过调整 ConstraintLayout 中 GAME_VIEW Box 的位置和大小，就能控制游戏画面在屏幕上的位置。

### 当前行为

横屏 + 虚拟按键隐藏时，走 `buildConstraintSetLandscapeTopAlign()` 布局。手柄连接时自动触发 TOP 模式（代码在 `MobileGameScreen.kt` 168-170 行附近）。

---

## Bug 1（致命）：GAME_VIEW Box 高度为 0

**文件**：`GameScreenLayout.kt`

**位置**：`buildConstraintSetLandscapeTopAlign()` 方法（约第 187 行）

**错误**：

```kotlin
constrain(gameView) {
    width = Dimension.fillToConstraints
    height = Dimension.wrapContent   // ← BUG：空 Box 的 wrapContent = 0
    absoluteLeft.linkTo(leftPad.absoluteRight)
    absoluteRight.linkTo(rightPad.absoluteLeft)
    top.linkTo(parent.top)
    width = Dimension.fillToConstraints  // ← width 被重复设置
}
```

GAME_VIEW Box 是空的（无子元素），`Dimension.wrapContent` 计算结果为 0px。导致视口高度为 0，游戏画面不显示或位置错乱。另外 `width = Dimension.fillToConstraints` 写了两次，第二个会覆盖第一个。

**修复**：替换为 `Dimension.ratio("2:1")` + `verticalBias = 0f`

```kotlin
constrain(gameView) {
    width = Dimension.fillToConstraints
    height = Dimension.ratio("2:1")   // 高度 = 可用宽度 × 1/2
    absoluteLeft.linkTo(leftPad.absoluteRight)
    absoluteRight.linkTo(rightPad.absoluteLeft)
    top.linkTo(parent.top)
    bottom.linkTo(parent.bottom)
    verticalBias = 0f                 // 在 top-bottom 之间靠上对齐
}
```

**原理**：
- `ratio("2:1")` 含义：宽:高 = 2:1，即高度 = width ÷ 2
- `verticalBias = 0f`：在 top 和 bottom 约束间的可用空间中，靠顶部放置
- 典型手机上 gameView 约占屏幕上方 78%，下方 22% 留空
- GL 渲染器在该区域内居中渲染 → 画面自然靠上

**同时注意**：leftPad/rightPad 的 top/bottom 链接到 `gameView.top` / `gameView.bottom`，所以它们会自动跟随 gameView 的高度。这部分代码不变。

---

## Bug 2（摆设）：设置项未接入 UI

设置 key `pref_key_game_view_vertical_align`（值 "center" / "top"）已定义，`SettingsManager.gameViewVerticalAlign()` 已写，但 **MobileGameScreen 从未调用它**。目前是硬编码逻辑：

```kotlin
verticalAlign = if (!touchControlsVisibleState.value && isLandscape)
    TOP else CENTER
```

### 修复步骤

#### Step 1：SettingsManager 新增 Flow 方法

**文件**：`SettingsManager.kt`

**位置**：`gameViewVerticalAlign()` 方法后面

```kotlin
// 已有（不变）
suspend fun gameViewVerticalAlign() =
    stringPreference(R.string.pref_key_game_view_vertical_align, "center")

// 新增：Flow 版本，供 Compose collectAsState 订阅
fun gameViewVerticalAlignFlow(): Flow<String> {
    return sharedPreferences.getString(
        getString(R.string.pref_key_game_view_vertical_align), "center"
    ).asFlow()
}
```

需要在文件顶部添加 `import kotlinx.coroutines.flow.Flow`。

#### Step 2：ViewModel 新增方法

**文件**：`BaseGameScreenViewModel.kt`

**位置**：构造函数参数 + 方法区

```kotlin
// 改构造函数，将 settingsManager 改为 private val（原来是普通参数不存字段）
class BaseGameScreenViewModel(
    private val appContext: Context,
    game: Game,
    private val settingsManager: SettingsManager,  // ← 加 private val
    // ... 其余不变
)

// 新增方法（放在 getTouchHapticFeedbackMode() 后面）
fun getGameViewVerticalAlign(): Flow<GameScreenLayout.VerticalAlign> {
    return settingsManager.gameViewVerticalAlignFlow().map { value ->
        when (value) {
            "top" -> GameScreenLayout.VerticalAlign.TOP
            else -> GameScreenLayout.VerticalAlign.CENTER
        }
    }
}
```

需要在文件顶部添加：
```kotlin
import com.swordfish.lemuroid.app.mobile.feature.game.GameScreenLayout
import kotlinx.coroutines.flow.map
```

#### Step 3：MobileGameScreen 接入设置

**文件**：`MobileGameScreen.kt`

**位置**：`hapticFeedbackMode` collectAsState 后面，ConstraintLayout 的 constraintSet 参数处

```kotlin
// 新增：收集设置值
val verticalAlignState =
    viewModel
        .getGameViewVerticalAlign()
        .collectAsState(GameScreenLayout.VerticalAlign.CENTER)

// 修改 constraintSet 调用
constraintSet = GameScreenLayout.buildConstraintSet(
    isLandscape,
    currentControllerConfig?.allowTouchOverlay ?: true,
    verticalAlign = when {
        // 手柄连接（触摸控件隐藏）+ 横屏 → 强制靠上
        !touchControlsVisibleState.value && isLandscape ->
            GameScreenLayout.VerticalAlign.TOP
        // 其他情况 → 使用用户设置
        else -> verticalAlignState.value
    },
),
```

---

## 最终行为

| 条件 | 对齐方式 |
|------|---------|
| 横屏 + 触摸控件隐藏（手柄连接） | **强制 TOP**（靠上） |
| 竖屏 | CENTER（居中，竖屏布局不区分） |
| 横屏 + 触摸控件可见 | 使用设置项 |
| 横屏 + 触摸 overlay 模式 | 使用设置项 |

用户可在 设置 → 常规 → "Game screen vertical alignment" 选择 Center / Top。

---

## 改动清单

| 文件 | 改动类型 | 关键变更 |
|------|---------|---------|
| `GameScreenLayout.kt` | 修复 | `buildConstraintSetLandscapeTopAlign()` 中 gameView 约束重写 |
| `SettingsManager.kt` | 新增 | `gameViewVerticalAlignFlow()` |
| `BaseGameScreenViewModel.kt` | 新增 | `getGameViewVerticalAlign()` + settingsManager 改 private val |
| `MobileGameScreen.kt` | 修改 | 收集 verticalAlign Flow，替换硬编码逻辑 |
| `CHANGELOG_MODS.md` | 记录 | v1.1.1 条目 |
| `README.md` | 更新 | 移除"尚未完成"标记 |

---

## 验证方式

构建 debug APK：
```bash
./gradlew :lemuroid-app:assembleFreeDynamicDebug
```

安装后在 NDS 游戏中连接蓝牙手柄，横屏下确认画面靠上显示而非居中。
