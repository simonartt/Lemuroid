# Lemuroid 定制版 - 修改日志

> 本文件记录对 Lemuroid 开源项目的所有修改，包括构建环境适配、功能增强和问题修复。

---

## v1.21 - 2026-09-05

### v1.20.10 用户实测 2 项调整：编辑手势与 graphicsLayer 解耦（根治拖动抖动/按键炸出屏幕）/ 删除编辑期蓝色选中描边（版本升至 1.20.10-v8b）

**分支 `v8b-nds-editor`，versionCode 275 / versionName 1.20.10 / suffix -v8b**（用户 v1.20.9 真机实测反馈）:

1. **重中之重：拖动虚拟按键仍不停抖动，ABXY/方向键偶尔剧烈抖动后"分裂"跑出设备屏幕彻底消失（只能全部复位恢复）— 前两轮修复（v1.20.7 置空 PadKit 命中、v1.20.8 松手才提交）都没打中的真正根因 = 手势坐标自激反馈回路**：
   - **根因**：`TweakableButton`/`TweakableButtonDesmume` 的编辑手势 `pointerInput` 与 `graphicsLayer(translationX=liveDx, scaleX=bs.scale)` 挂在**同一节点**上。Compose 的指针事件坐标会被该节点自身的 layer **逆映射**——手势每帧更新 `liveDx` 平移 layer，下一帧同一手指的**局部坐标**就反向跳变同量。形成自激振荡递推 `dx_k = ΔS_k − dx_{k−1}`（ΔS=手指真实位移）：单按钮表现为"半速+一顿一顿"跟手（不停抖动）；layer 带 scale≠1（ABXY/方向键组常被调大，横屏尤甚）时环路增益≠1，递推**发散**——`liveDx` 越滚越大、按钮高频乱跳（视觉"四分五裂"），松手提交的 accX/accY 直冲 ±4000px 夹取值写进 freeX/freeY → 按键组炸出屏幕、复位才能救回。
   - **证据链**：v1.20.5（每帧提交版，translation 异步落地延迟 1+ 帧，同样是负系数反馈）、v1.20.8（live 同步版，系数精确 -1）两版都抖——因为两版的**坐标通路完全没变**；PadKit 侧已由 v1.20.7/1.20.9 置空（按下高亮/震动/事件全哑），排除嫌疑。
   - **修法（架构）**：编辑手势挪到**不带 layer 的 wrapper Box** 上，`graphicsLayer` 只作用于 Box 内的 content——wrapper 的局部坐标是纯手指位移（子级 layer 变换不反馈到父级手势坐标），回路彻底断开，1:1 精确跟手。wrapper 用 `propagateMinConstraints = true`：LayoutRadial 对 primaryDial/secondaryDials 槽位是 `Constraints.fixed` 测量，透传 min 约束保证插入 wrapper 后布局零变化。**down 命中不受影响**：手指按在按钮绘制位置（freeX≠0 时=布局+平移），hit test 穿过 content 的 layer 逆映射命中子树，wrapper 作为命中链祖先照常收到 down；down 后 move/up 持续派发给原命中链（Compose 不重新 hit test），拖出原区域也不丢。
2. **删除编辑期按键蓝色选中描边（v1.20.7 引入的选中反馈环，用户要求去掉）**：删 `TweakableButton`/`TweakableButtonDesmume` 的 `ringMod`/`isSelected` 与 `LocalSelectedButton` compositionLocal 全链（MelonDS.kt 定义 + Desmume.kt 引用 + MobileGameScreen 的 import/provides）。VM 侧 `editingSelection` 状态**保留**——编辑卡片的"显示 ▾"网格点击选中、滑杆/复位的作用对象仍靠它（网格内仍有浅色高亮行，不影响）。

**修改文件**:
- `lemuroid-touchinput/.../radial/layouts/MelonDS.kt` — `TweakableButton` 重构：非编辑路径 `content(modifier.then(baseMod))` 原样；编辑路径改 wrapper `Box(modifier.pointerInput(id){手势}, propagateMinConstraints=true){ content(baseMod) }`（手势循环体逐行不变）；删 `LocalSelectedButton` 定义 + ring 逻辑 + border/RoundedCornerShape/Color/dp imports，加 Box import。GB/GBA/Nintendo3DS 布局共用此组件，一并修复。
- `lemuroid-touchinput/.../radial/layouts/Desmume.kt` — `TweakableButtonDesmume` 同款重构（wrapper Box + 删 ring）。
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — 删 `LocalSelectedButton` import / `editingSelection` collectAsState（仅 ring 用）/ `CompositionLocalProvider` 的 provides 项。
- `lemuroid-app/build.gradle.kts` — versionCode 274→275、versionName 1.20.9→1.20.10。

**避坑**：⚠️ **`pointerInput` 与 `graphicsLayer` 绝不能挂在同一节点再做"手势驱动本节点 translation"的事**——指针局部坐标会被自身 layer 逆映射，translation 每变一次，下一帧坐标反向跳一次，自激振荡/发散（scale≠1 时尤甚）。要"手势移动带 layer 的内容"，手势挂在外层无 layer 的 wrapper 上、layer 只包内容。⚠️ 在 LayoutRadial 的 fixed 约束槽位里插 wrapper Box 必须 `propagateMinConstraints = true`，否则 loose 测量会让 content 尺寸/对齐漂移。

---

## v1.21 - 2026-09-03

### v1.20.9 用户实测 4 bug+1 优化：编辑置空 keycode 全覆盖 / 隐形占位符摘除可编辑壳 / NDS 锁定物理方向到手动布局模式 / 触控编辑卡片改屏幕中心（版本升至 1.20.9-v8b）

**分支 `v8b-nds-editor`，versionCode 274 / versionName 1.20.9 / suffix -v8b**（用户 v1.20.8 真机实测反馈）:

1. **bug1：编辑期只有方向键不响应按下，其他按键依然响应** — 根因（实锤）：v1.20.7/1.20.8 的 `ALL_TOUCH_CONTROL_IDS` 枚举 `Id.Key(0..31)`，但**所有布局按键用的是 Android 游戏手柄 KEYCODE**（BUTTON_MODE=82、SELECT=91、A=96…START=108），一个都不在集合里；方向键是 `Id.DiscreteDirection(0)` 恰好在 0..4 枚举内——所以只有 D-pad 真被置空，其余全漏。修：`for (code in 0..127) add(Id.Key(code))`（覆盖全部布局 keycode + 余量；未用 id 对 InputState fold 是空集移除，无害）。
2. **bug2：选中菜单键时麦克风键也有蓝框，滑块同时缩放两个框** — 根因：左盘有个**隐形占位符** `SecondaryButtonMenuPlaceholder`（角度占位用），v1.20.5 起被 `TweakableButton(id=MENU)` 包裹 → 选中"全局菜单"时占位符也画蓝圈，而占位符 radialPosition(-120°) 正落在 MelonDS 麦克风键(L2,-120°) 位置（Desmume 则是关屏键），产生"双蓝框"；滑块写同一份 MENU 组设置 → 两个框同时缩放。修：占位符**摘掉 TweakableButton 壳、直接裸调用**（与 NES/SNES/PSX 等 15 个既有布局的用法一致），同步修 MelonDS/Desmume + GB/GBA/Nintendo3DS 三处同款隐患。占位符仍留在组内（子级数不变，径向分布不受影响）。
3. **bug3：横竖屏切换仍被重力感应响应** + 4. **bug4：屏幕显隐开关没起作用，上屏开启但运行时上屏不显示** — 同根：v1.20.8 手动模式后代码里已无任何重力→布局路径（grep 全库无 OrientationEventListener/onOrientationChanged 活调用），但**渲染锚点仍随物理旋转走**——`LaunchedEffect(fullPos, viewPos, screenLayout, isLandscape)` 里 isLandscape=物理方向，ConstraintLayout 按物理方向重排 → viewPos（游戏视图槽位）翻转 → `computeNaturalScreenRects` 拿着"竖屏几何"算"横屏锚点"（或反之），画面重排/错位——观感即"重力还在切布局"（bug3），错位后的矩形也让显隐开关表现异常（bug4；Kotlin→JNI→native 的 enabled 链路已逐层核查无断点，Video 重建重放也在）。修（架构级，用户"手动模式"语义的完成态）：**NDS 游戏运行期间把 Activity 物理方向锁定为手动布局模式**——`BaseGameActivity.followLayoutOrientation()`：`getScreenLayoutState().collect` → PORTRAIT→SCREEN_ORIENTATION_PORTRAIT / LANDSCAPE→LANDSCAPE（仅 `shouldFollowLayoutOrientation()`=true 的移动端 GameActivity 启用，TV 不动；非 NDS 直接 return 不锁）。锚点=布局模式恒一致，旋转手机**画面与布局完全静止**，显隐开关回到可预期语义。
5. **优化1：触控编辑悬浮卡片改屏幕中心出现** — `Alignment.BottomCenter`→`Alignment.Center`，拖动钳制改对称（maxVy=(屏高-卡高)/2，此前只许上拖 0..负值）。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `ALL_TOUCH_CONTROL_IDS` keycode 域 0..31→0..127；编辑卡片 align Center + 对称钳制。
- `lemuroid-touchinput/.../radial/layouts/MelonDS.kt` + `Desmume.kt` + `GB.kt` + `GBA.kt` + `Nintendo3DS.kt` — 左盘 MENU 占位符摘 TweakableButton 壳改裸调用。
- `lemuroid-app/.../shared/game/BaseGameActivity.kt` — 新增 `followLayoutOrientation()`（collect 布局状态→锁 requestedOrientation）+ open 钩子 `shouldFollowLayoutOrientation()=false`；import ActivityInfo。
- `lemuroid-app/.../mobile/feature/game/GameActivity.kt` — override 钩子=true（仅移动端 NDS 锁向）。
- `lemuroid-app/build.gradle.kts` — versionCode 273→274、versionName 1.20.8→1.20.9。

**遗留已知问题（本次未动，如实记录）**：换屏（THUMBR）写的 core 变量值 `melonds_screen_layout1="bottom-top"`/`desmume_screens_layout="bottom/top"` 不在本仓库暴露的合法选项列表里（melonds=top-bottom/left-right，desmume=top-bottom/left-right），core 可能直接忽略→换屏可能一直无效；稳妥做法是 app 侧对调两 quad 矩形（不碰 core 变量），另立新版本验证处理。

**避坑**：⚠️ PadKit 置空集合必须覆盖**实际使用的 id 空间**——本项目布局按键全是 Android KEYCODE(82..108)，不是 0..31；⚠️ `SecondaryButtonMenuPlaceholder` 是几何占位，绝不能包进可编辑按钮（隐形蓝圈+双份设置）；⚠️ 手动布局模式下，几何=layoutOrientation 的函数，锚点必须同模式——用锁向消除错配，别再往"错位后钳补救"方向走。

---

### v1.20.8 用户实测 4 点修正：显隐面板 4 列网格 / NDS 按键标签纠错 / 拖动改本地实时+松手一次提交（修重影）/ 画面布局改手动方向模式（彻底脱离重力感应）（版本升至 1.20.8-v8b）

**分支 `v8b-nds-editor`，versionCode 273 / versionName 1.20.8 / suffix -v8b**（用户 v1.20.7 真机实测 4 点反馈，"问题不小 你仔细思考"）:

1. **显隐面板改网格（UI）** — v1.20.6 的展开显隐列表是单竖排 10 行，把悬浮卡片撑得极长。改 `allButtons.chunked(4)` 每行 4 格（最多 3 行）：每格=上方 MiniToggle + 下方 10sp 标签（垂直堆叠保证 4 列可读），`Column weight(1f)` 等宽对齐，末行不足用 `Box(weight(1f))` 补位；最大高 240dp 内竖滚。
2. **bug1：按键标签纠错（"左摇杆其实是关屏按钮"）** — 根因：NDS 布局把摇杆格位挪用成快捷键——MelonDS 的 THUMBL 格渲染 `button_close_screen`（关屏）、THUMBR 渲染 `button_swap_screens`（换屏）；且 **L2 格也错位**（MelonDS=麦克风图标、Desmume=关屏图标），但枚举 label 仍写死"左摇杆/右摇杆/菜单"。修：新增 `buttonEditorLabel(id, isNds)`——NDS 下 THUMBL→"关屏"、THUMBR→"换屏"、L2→"L2"（因核而异不硬编中文语义），非 NDS 保留枚举默认。注意：图标/事件本身没变（仍发 KEYCODE_BUTTON_THUMBL 等，GameShortcut 的 L3+R3=菜单组合不受影响），只是标签说谎。
3. **bug2：拖动重影/四分五裂仍在（横屏更严重）——v1.20.7 只治了 PadKit 命中检测一路，真正主因是写路径积压** — 根因：旧实现每个 pointer 事件（~120Hz）都回调 `updateButtonFreeDrag` → VM scope.launch → 全量 Settings+预设 JSON 编码 → SharedPreferences 写 → StateFlow 回流 → 整棵 pad 树重组。事件速率远超重组吞吐，积压成批爆发=按钮"追手指"乱跳的鬼影；横屏按钮大、graphicsLayer 重组更贵，故更严重。修：**拖动期间完全不碰存储**——`TweakableButton`/`TweakableButtonDesmume` 本地 `liveDx/liveDy`（`remember { mutableFloatStateOf }`）实时驱动 graphicsLayer，手势内累加 accX/accY；松手才把整段增量经 `dragCommitLatest` 提交**一次**。`absorbed = bs.freeX != startFreeX || bs.freeY != startFreeY` 检查让提交落地帧无缝接管（live 增量清零由 bs 值接管，无 snap-back 帧、无双位移帧）。⚠️ 设计取舍：闭包内通过 `committedFree = rememberUpdatedState(bs.freeX to bs.freeY)` 读最新已提交值；**不结转**未吸收的 live 增量到新一次按下（会双重累加），代价是极端快连按下最多丢一帧连续性，远小于双计跳位的代价。
4. **bug3：竖屏布局调好→旋横屏→回来把竖屏也带跑（互相污染）→ 用户拍板"直接把这两个模式分开，游戏菜单里按钮切换，不再被重力感应切换"** — 根因：v1.20.5 起虽按方向分 3+3 槽，但**工作值（未保存的编辑）是全局单份**，`LaunchedEffect(isLandscape)` 每次旋转调 `onOrientationChanged` 装载/清空活动槽，未保存的调校在切换瞬间被另一方向的几何覆盖；且 scale/offset 是相对**各自方向 natural rect** 的百分比语义，跨方向搬运必然爆钳制。修（手动模式，架构级）：
   - `ScreenLayoutState` 新增 `layoutOrientation: Orientation = PORTRAIT`（@Serializable，老档默认竖屏布局）+ `workByOrientation: Map<String, OrientationWork>`（离开的模式的**未保存工作值+活动槽**停车表）。
   - `switchLayoutOrientation(newMode)`：停车离开模式的当前值 → 进入模式优先恢复其 parked 值，否则载入其最后使用槽，否则 DEFAULT。**绝不把另一模式的工作值搬运过去**（v1.20.5 教训）。
   - `onOrientationChanged` 标 `@Deprecated` 改 no-op；MobileGameScreen `LaunchedEffect(isLandscape)` 删除对布局方向的调用（**触控按键** settings 仍按物理方向分桶——那是"怎么拿手机"的问题，与"画面往哪摆"解耦）。
   - **渲染与编辑器几何全部改读 `layoutOrientation`**：渲染 LaunchedEffect 里 `layoutLandscape` 传入 computeNaturalScreenRects/applyScreenLayoutTransform；编辑器 `val isLandscape = layoutState.layoutOrientation == LANDSCAPE`（替代 `screenWidthPx > screenHeightPx`）——虚线框是运行时 split-viewport 的 WYSIWYG 预览，两者必须锚同一模式。旋转手机画面**纹丝不动**。
   - 两个切换入口：①游戏菜单新增"画面布局方向（NDS）"列表（竖屏布局/横屏布局，仅 NDS 显示），经 Intent extras（EXTRA_SCREEN_LAYOUT_ORIENTATION 传 ordinal + EXTRA_SCREEN_LAYOUT_IS_NDS 门控 / RESULT_SCREEN_LAYOUT_ORIENTATION 回传）→ BaseGameActivity.onActivityResult → `switchScreenLayoutOrientation`；②编辑器"菜单"子菜单新增"切换到横/竖屏布局"按钮（子菜单删掉 isLandscape 参数，改从 layoutState 推导），切换即时无需退出。

**修改文件**:
- `lemuroid-app/.../shared/game/screenlayout/ScreenLayoutManager.kt` — 新增 `@Serializable OrientationWork`；`ScreenLayoutState` 增 `layoutOrientation`/`workByOrientation`；新增 `switchLayoutOrientation()`（停车/恢复三分支）与 `currentLayoutOrientation()`；`onOrientationChanged` 改 @Deprecated no-op。
- `lemuroid-app/.../shared/game/viewmodel/GameViewModelScreenLayout.kt` + `BaseGameScreenViewModel.kt` — 转发 `switchLayoutOrientation`/`currentLayoutOrientation`；旧 `onOrientationChanged` 加 @Suppress("DEPRECATION")。
- `lemuroid-app/.../shared/GameMenuContract.kt` — 增 3 个 extra 常量。
- `lemuroid-app/.../shared/game/BaseGameActivity.kt` — 菜单 Intent 塞当前模式 ordinal + isNds；onActivityResult 消费 RESULT_SCREEN_LAYOUT_ORIENTATION（`Orientation.values().getOrElse` 防越界）。
- `lemuroid-app/.../mobile/feature/gamemenu/GameMenuActivity.kt` — GameMenuRequest 增 `isNdsGame`/`screenLayoutOrientation` 字段并解析。
- `lemuroid-app/.../mobile/feature/gamemenu/GameMenuHomeScreen.kt` — isNdsGame 时渲染"画面布局方向（NDS）" LemuroidSettingsList（rememberMemoryIntSettingState(coerceIn(0,1))，onItemSelected 回传 index）。
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — ①显隐面板 chunked(4) 网格；②`buttonEditorLabel()`；③渲染 LaunchedEffect 用 layoutLandscape、编辑器 isLandscape 改读 layoutOrientation；④删除旋转触发布局切换；⑤`ALL_TOUCH_CONTROL_IDS` 改 `buildSet { for(code in 0..31) Id.Key(code); for(src in 0..4) Discrete/ContinuousDirection }`（删 android.view.KeyEvent / ComposeTouchLayouts import，0..4 兜底覆盖所有 MOTION_SOURCE，多余 id 对 InputState fold 无害）；⑥ScreenLayoutSubmenu 去 isLandscape 参数 + 新增模式切换按钮。
- `lemuroid-touchinput/.../radial/layouts/MelonDS.kt` + `Desmume.kt` — TweakableButton/TweakableButtonDesmume 拖动改本地 live 增量+松手一次提交（见上 3）；import 补 `getValue/setValue/mutableFloatStateOf/remember`。
- `lemuroid-app/build.gradle.kts` — versionCode 272→273、versionName 1.20.7→1.20.8

**避坑**：⚠️ 手动模式下**任何"NDS 几何"代码一律读 `layoutState.layoutOrientation`，禁止再用 `screenWidthPx > screenHeightPx` / 物理 isLandscape**——渲染、编辑器虚线框、toolbox 网格、gap 轴都锚它；物理旋转只影响触控按键分桶。⚠️ 以后给拖动类交互加持久化时，先问"每事件都写存储会不会引发重组风暴"——本项目的 Settings 是全量 JSON 编解码，高频路径必须本地渲染+提交一次。

---

### 触控编辑模式抖动/按键四分五裂修复：编辑期整盘走 PadKit 模拟通道置空 + 选中按键蓝色描边（版本升至 1.20.7-v8b）

**分支 `v8b-nds-editor`，versionCode 272 / versionName 1.20.7 / suffix -v8b**（用户 v1.20.6 真机实测 BUG）:

1. **编辑触控按键时按键仍响应按下 → 拖动剧烈抖动、方向键/ABXY 四分五裂消失（BUG 根因）** — 逐层翻 PadKit 源码定位：Lemuroid 整棵虚拟按键树挂在 `PadKit(...)` 根容器下，PadKit 在根 Box 上跑自己的全局 `pointerInput` 循环（PadKit.kt），每帧 `event.changes.filter { it.pressed }` **完全不检查 isConsumed**。v1.20.5 给每个按键加的编辑手势（选中+自由拖动）在子节点上 `consume()` 了事件，但 PadKit 根循环无视消费标记，拿**同一根手指**照常做命中检测 → 按钮被算成 pressed（高亮/震动都从它内部走），且拖动时 graphicsLayer 移动按键与 PadKit 记录的 rect 错位、FaceButtons/Cross 的 `trackPointers` 锁人 → 高频翻转 = 抖动；ABXY/方向键是一个 handler 里 4 个锚点，整组随图层移动时归一化距离 >1 → 4 前景乱跳甚至集体不渲染 = "四分五裂"。
2. **修复：编辑期用 PadKit 官方模拟通道把整盘置空** — PadKit 命中检测之后会把 `simulatedControlIds` 列出的控件状态**强制覆盖**为 `simulatedState`（官方给倾斜/测试用的通道，本项目 tilt 已在用）。进入触控编辑子模式时，把全部控件 id（`ALL_TOUCH_CONTROL_IDS`，覆盖所有系统布局的按键+方向 id 并集）塞进覆盖列表、覆盖值=空 `InputState()`（全松开）→ 该状态下：控件 pressed 恒 false（不高亮）、`InputHapticGenerator` 不再震动、`InputEventsGenerator` 不再产事件，而**编辑手势在子节点独立工作完全不受影响**。退出编辑自动回落 tilt 通道，正常游戏路径零改动。⚠️ 副作用（用户已确认接受）：编辑期所有按钮**不再有任何按下高亮**。多余 id 对当前 pad 无害（`InputState` 的 fold 是 remove from empty，不抛）。
3. **选中按键加蓝色描边作为选中反馈** — 因副作用②编辑期完全无高亮，无法分辨当前滑杆/复位作用于哪个按键组。新增 `LocalSelectedButton` compositionLocal（MelonDS.kt 定义，Desmume.kt 同包直接引用），`TweakableButton`/`TweakableButtonDesmume` 里选中组叠 `Modifier.border(2.dp, #35B5E8, RoundedCornerShape(16dp))`；MobileGameScreen 用 `editingSelection.collectAsState` 提供值。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — 顶层新增 `ALL_TOUCH_CONTROL_IDS: Set<Id>` 常量；PadKit 调用点 `simulatedState`/`simulatedControlIds` 由 tilt 直传改为 `derivedStateOf` 包装（编辑中→全量 id+空状态，否则→原 tilt）；`CompositionLocalProvider` 增 `LocalSelectedButton provides editingSelection.value`；新增 `val editingSelection = viewModel.getEditingSelection().collectAsState(null)`；import 补 `gg.padkit.ids.Id`/`android.view.KeyEvent`/`LocalSelectedButton`/`ComposeTouchLayouts`。⚠️ 三个 `derivedStateOf` 闭包内直接读 `.value`（不能用外部捕获的 `controlsEditing` 局部 val，会闭包陈旧——但此处 derivedStateOf 会追踪读到的 state，故已删除冗余局部）。
- `lemuroid-touchinput/.../radial/layouts/MelonDS.kt` — 新增 `LocalSelectedButton` compositionLocal；`TweakableButton` 读选中态、`content` 尾叠 `ringMod`；import 补 `border`/`RoundedCornerShape`/`Color`/`dp`。
- `lemuroid-touchinput/.../radial/layouts/Desmume.kt` — `TweakableButtonDesmume` 同步加选中环（同包，无需 import Local）；import 补 `border`/`RoundedCornerShape`/`Color`/`dp`。
- `lemuroid-app/build.gradle.kts` — versionCode 271→272、versionName 1.20.6→1.20.7

**验证依据**：对照项目实际锁定版本 `1.0.0-beta1`（buildSrc/deps.kt）的 PadKit tag 源码逐行确认 `simulatedControlIds`/`simulatedState` 签名、`handleSimulatedInputEvents` 覆盖点、`LaunchedEffect(simulatedState.value)` 重放、pointer tracking 松手自清理均成立；与最新 main 一致。

---

### 触控编辑器交互修正：预设改圆形+纯长按存 / 显隐面板并入可拖动悬浮卡片 / 底部卡片固定宽可拖+滑杆去文本（版本升至 1.20.6-v8b）

**分支 `v8b-nds-editor`，versionCode 271 / versionName 1.20.6 / suffix -v8b**（用户 v1.20.5 真机实测 3 点反馈）:

1. **预设 A/B/C 改圆形 + 修点击误存（BUG）** — 旧交互 `onClick = if(saved) load else save`：点空槽会直接保存，所以表现为"点击就显示已存"；长按虽已实现但重复保存无反馈显得"没反应"。现三个圆形按钮（52dp）字母居中，**点按=仅载入**（空槽点按无动作），**长按=保存当前布局进该槽**，激活态高亮、已存显示"已存"、空槽显示"空"。下方保留"点按载入/长按保存"提示。
2. **左侧常驻显隐面板 → 底部悬浮卡片的可展开区（防拥挤）** — 旧版左侧竖排面板列出**所有按键**的开关，遮挡按键、位置拥挤。删除该常驻面板，改为底部卡片里"显示 ▾"入口点击**下拉展开**显隐列表（每项 MiniToggle+名称，超高 220dp 内竖滚），默认收起、按需查看，不挡屏幕。（用户在"底部菜单入口"与"左侧可拖动下拉"两案间让 agent 定夺，采用二者融合：显隐做进可拖动的底部卡片。）
3. **底部调节栏：固定宽度 + 悬浮可拖动 + 滑杆行去文本** — 由通栏 `fillMaxWidth` 改**固定 360dp** 悬浮卡片，**按住卡片背景可拖动**到屏幕任意位置（`detectDragGestures`；子控件各自消费点击，按按钮/滑杆不触发拖动）。拖动钳制用 `rememberUpdatedState` 读最新边界（避免首帧 `cardSize=0` 闭包陈旧）。滑杆行去掉"X·大小"标签文本，结构=**左复位按钮 + 右滑杆**。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `TouchControlsEditorOverlay` 整体重写：A/B/C 圆形 `combinedClickable`（onClick 仅 saved 才 load）、`cardOffset`/`cardSize`/`visibilityExpanded` 状态、卡片 `.align(BottomCenter).offset{cardOffset}.width(360.dp).onSizeChanged{}` + `pointerInput(Unit){detectDragGestures}` 拖背景、滑杆行 `[复位][Slider weight1]`、展开式显隐列表；新增 import `detectDragGestures`/`rememberScrollState`/`verticalScroll`/`heightIn`/`getValue`/`setValue`/`IntSize`/`onSizeChanged`（`rememberUpdatedState` 已存在）。⚠️ TextButton `contentPadding` 是 `PaddingValues`（非 `Padding`），全限定名写死避 import。
- `lemuroid-app/build.gradle.kts` — versionCode 270→271、versionName 1.20.5→1.20.6

### v1.20.5：旋转铺满修复（方案A）+ 启用开关挪框内右上角 + 触控按键绝对定位/预设 A|B|C + 编辑入口全系统统一（版本升至 1.20.5-v8b）

**分支 `v8b-nds-editor`，versionCode 270 / versionName 1.20.5 / suffix -v8b**（用户 v1.20.4 实测反馈 + 触控编辑大改，经 A/B/C 三点确认）:

1. **旋转到无保存槽的方向 → 重置默认布局（BUG 修复，方案 A）** — 根因：`onOrientationChanged` 在无槽方向直接继承旧方向工作值，而 scale/offset 是**相对各方向自然框**的，竖屏调好的值带到横屏成脏数据 + 全屏钳制把框拍满设备屏。现 else 分支改 `topScreen/bottomScreen = ScreenTransform.DEFAULT, activeSlot = null`。想跨方向复用布局请用槽位保存/载入。
2. **每屏启用开关位置：虚线框内左上角 → 内部右上角（4dp 内缩）** — 用户指定；`ScreenEnableToggle` 定位改 `top-right + 4dp`。
3. **触控按键绝对自由定位** — `ButtonGroupSettings` 新增 `freeX/freeY`（像素，叠加在 legacy offset 之上，±4000px 夹取），编辑中按住按钮拖动写 freeX/freeY → 按钮可拖离径向锚点区、**浮到游戏画面上方任意位置**（默认布局不变，只是"可搬走"，用户 C 确认）。`graphicsLayer` translation 应用之。
4. **触控编辑新交互（全系统一套 UI）** — 进入"编辑触控按键"模式后：按住按钮=选中+拖动；设备底部滑杆=当前按钮大小 0.5–2x（+复位）；左侧固定竖排面板=每按钮显示/隐藏开关（MiniToggle）+名称；顶部 A/B/C 三 chips=**点按载入预设 / 长按把当前布局存入该槽**，任何编辑自动镜像进当前激活方案（`storeSettings` 内 sync），手动编辑后 `activePreset=null`。预设按 (controller, orientation) 分桶持久化（`Settings.presets`）。
5. **编辑入口统一化** — 游戏菜单删独立"编辑触控按键"项：NDS/GBA/3DS 一律走布局编辑器 → 底栏"菜单"子菜单 →"编辑触控按键"；非 NDS 系统点"编辑布局"直接进触控编辑模式（`toggleEditScreenLayout` 双模式）。`BaseGameActivity` 旧 `RESULT_EDIT_TOUCH_CONTROLS` 通道移除。

**修改文件**:
- `lemuroid-app/.../shared/game/screenlayout/ScreenLayoutManager.kt` — `onOrientationChanged` else→DEFAULT（方案A）
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `ScreenEnableToggle` 右上角；`TouchControlsEditorOverlay`（左侧显隐面板/顶部 A|B|C chips/底部大小滑杆/返回屏幕布局/退出编辑）；pads 树注入 `LocalButtonEdit/LocalButtonDrag`
- `lemuroid-app/.../shared/game/viewmodel/GameViewModelTouchControls.kt` — `updateButtonFreeDrag/loadTouchPreset/saveTouchPreset/reportSettings`（非挂起 latestSettings 缓存）
- `lemuroid-app/.../shared/game/BaseGameScreenViewModel.kt` — `toggleEditScreenLayout` 双模式、`setEditControlsMode`、`exitLayoutEditor`
- `lemuroid-app/.../mobile/feature/gamemenu/GameMenuHomeScreen.kt` + `shared/game/BaseGameActivity.kt` — 删旧触控编辑入口
- `lemuroid-touchinput/.../settings/TouchControllerSettingsManager.kt` — `freeX/freeY`、`Preset`、`presets`+`activePreset`、storeSettings 自动镜像、`currentSettings` 非挂起读
- `lemuroid-touchinput/.../layouts/MelonDS.kt / Desmume.kt / GBA.kt / GB.kt` — `TweakableButton` 重写（按下选中+拖动 onEditDrag），各布局按钮接入
- `lemuroid-app/build.gradle.kts` — versionCode 269→270、versionName 1.20.4→1.20.5

### NDS 编辑器：宽度按钮改相对语义 + 横屏左右对齐修复 + 移除双指捏合 + 全屏钳制 + 每屏启用开关（版本升至 1.20.4-v8b）

**分支 `v8b-nds-editor`，versionCode 269 / versionName 1.20.4 / suffix -v8b**（用户 v1.20.3 实测反馈 + 新功能）:

1. **"宽度50%"=当前宽度减半、"宽度100%"=还原宽度（语义变更）** — 旧实现是"缩放到设备屏宽 50%/100%"（反算等比 scale 的 `setWidthPercent`，已删除）。现直接操作**宽度轴系数 scaleX**：R2C4 每次点按 `scaleX ×= 0.5`（下限 `MIN_AXIS_SCALE=0.1`，可重复逐次减半）；R1C4 `scaleX=1.0` 还原自然宽度。高度/位置不受影响。
2. **横屏左/右对齐贴设备边（BUG 修复）** — 旧 `alignToEdge` LEFT/RIGHT 锚定 `viewPos`（游戏视图矩形），横屏两侧被虚拟按键占据 → 贴不到设备左边/右边。现与上/下对齐一致锚定 `fullPos`（物理屏边）。同时对齐用的半宽高改从**钳制后可见矩形**取，保证"看到哪贴哪"。
3. **移除双指捏合缩放（用户未要求过的功能）** — `dragInsideFrame` 里遗留的 `changes.size>=2 → zoomChange` 逻辑删除；改为主指（第一根按下手指）独占平移、其余手指忽略——双指并拢/张开不再改变框大小。缩放唯一入口=缩放模式把手（工具箱按钮/1x–7x 档不受影响）。`onTransform` 签名从 4 参改 3 参。
4. **缩放/移动限制在设备屏内（钳制）** — `applyScreenLayoutTransform` 新增可选 `deviceBounds`：半宽/半高各封顶设备屏宽高一半、中心钳制使整框不出屏。编辑框、运行时 viewport、把手命中测试**全部经过此钳制**（单一钳制点，任何工具都无法越界）。把手 `dragResizeHandle` 改为**以钳制后的可见框为冻结基准**（k=1 恒等于当前可见尺寸；之前存储值与可见值在触边钳制后会脱节），emit 时双轴尺寸钳制 + 中心钳制回设备矩形内；正常模式拖动同样按可见框中心增量钳制。
5. **新功能：每屏启用开关（横竖屏通用）** — 选中虚线框后其**左上角**出现 44×22dp 小滑动开关（自绘 `ScreenEnableToggle`，非 m3 Switch，子级 clickable 稳定消费点击）。关闭 → 该屏**真实渲染隐藏**且**不接收触摸**；虚线框仍可点选、开关仍可再开。开关状态存入 `ScreenTransform.enabled`（随槽位保存/载入，默认 true）。全链路：Kotlin `gameView.splitScreenVisible` → JNI `setSplitViewportVisibility` → `LibretroDroid::setSplitViewportVisibility`（状态持有，Video 重建后重放）→ `VideoLayout` 跳过隐藏 quad 的 `glDrawArrays` + `getRelativePosition` 不命中隐藏屏。**子模块 libretrodroid-local 需先推 dualscreen 分支**。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `applyScreenLayoutTransform(+deviceBounds 钳制)`；`dragInsideFrame`（删捏合、主指平移、可见框增量钳制）；`dragResizeHandle`（可见框基准、双轴钳制、中心回算）；`alignToEdge`（LEFT/RIGHT→fullPos、可见半宽高）；`ScreenEnableToggle` 新组件 + 选中框左上角挂载；LaunchedEffect 下发 `splitScreenVisible`；两处 rect 计算传 `fullPos`
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — width100/width50 改 scaleX 语义（删 `setWidthPercent`/`remember`，新增 `MIN_AXIS_SCALE`）
- `lemuroid-app/.../shared/game/screenlayout/ScreenLayoutManager.kt` — `ScreenTransform.enabled` 字段 + `setEnabled()`；`isDefault` 计入 enabled
- `lemuroid-app/.../shared/game/BaseGameScreenViewModel.kt` + `viewmodel/GameViewModelScreenLayout.kt` — `setScreenLayoutEnabled` 转发
- `libretrodroid-local`（子模块）— `videolayout.h/cpp`（top/bottomScreenVisible + 触摸过滤）、`video.h/cpp`（跳过隐藏 quad 绘制）、`libretrodroid.h/cpp`（状态持有+Video 重建重放）、`libretrodroidjni.cpp` + `LibretroDroid.java`（`setSplitViewportVisibility` 导出）、`GLRetroView.kt`（`splitScreenVisible` 属性）
- `lemuroid-app/build.gradle.kts` — versionCode 268→269、versionName 1.20.3→1.20.4

### NDS 编辑器：把手箭头短粗 + 编辑界面隐藏右上☰ + 缩放中可双指平移 + 子菜单固定宽左对齐（版本升至 1.20.3-v8b）

**分支 `v8b-nds-editor`，versionCode 268 / versionName 1.20.3 / suffix -v8b**（用户 v1.20.2 实测反馈）:

1. **把手双头箭头短一点、粗一点（需求①）** — 对角线端点留白 `m: 10f→17f`（50dp 框内箭头明显变短），全部线宽 `strokeWidth 3f→5f`，箭头人字头 8f→7f。
2. **取消编辑界面右上角三横条按钮（需求②）** — `DraggableMenuButton`（☰，原本仅在虚拟按键隐藏时显示）在编辑器打开时**一并隐藏**：条件加 `&& !editScreenLayoutShown.value`。出口已有底栏"菜单▸返回游戏菜单"，☰ 与其重复。非编辑态行为不变。
3. **缩放把手时仍可平移虚线框（需求③）** — `dragResizeHandle` 重写为**多指双角色**手势：一根手指按住把手=等比缩放（左上角锚定，语义同 v1.20.2）；期间**另一根手指按住任意框体即可拖动该屏位置**（增量累加 `panDX/panDY`，与缩放位移**可加**：`offset = 冻结基准 + hw0·(k−1) + panD`）。单指语义兜底：无缩放进行时按框体=选中+平移（等同普通模式）；每根手指按 `PointerId` 绑定角色，抬手不中断另一指；把手**每次按下都从实时 transform 重冻结基准**（k=1 恒等于"当前尺寸"，不会跳回旧值）。`.pointerInput` key 增加 `isLandscape`（旋转后把手命中区随布局刷新）。新增 import：`PointerId`。
4. **子菜单固定宽度 + 左对齐（需求④）** — `ScreenLayoutSubmenu` Surface 从 `fillMaxWidth()` 改 `widthIn(min=300.dp, max=300.dp)`（固定 300dp），调用点对齐 `Alignment.BottomCenter→BottomStart`（左下角，紧贴底栏"菜单"按钮上方）。横屏不再被拉成通栏、行内文字不再两头散开。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `dragResizeHandle`（双角色多指循环）；把手 Canvas 箭头参数；`DraggableMenuButton` 显示条件；`ScreenLayoutSubmenu` Surface 宽度 + 调用点 align；import `PointerId`
- `lemuroid-app/build.gradle.kts` — versionCode 267→268、versionName 1.20.2→1.20.3

### NDS 编辑器：缩放把手四处修正（框内/虚线/双向箭头/灵敏度+宽度上限）（版本升至 1.20.2-v8b）

**分支 `v8b-nds-editor`，versionCode 267 / versionName 1.20.2 / suffix -v8b**（用户 v1.20.1 实测反馈）:

1. **把手位置：从框外挪进框内（BUG）** — 原把手定位到选中框右下角顶点处、整体溢出框外。现偏移减把手尺寸（`right-50dp / bottom-50dp`），50dp 方块**完整落在虚线框内**贴右下角。
2. **把手样式（需求）** — 原白底实色填充+实线蓝边 → 改为**无填充 + `#35b5e8` 虚线圆角方框**（与编辑器虚线框设计语言一致）；箭头从 ↘ 单向 → **↖↘ 对角双端箭头**（两端各两个人字头）。整个把手改单 Canvas 绘制（`drawRoundRect` + `Stroke(pathEffect=dashPathEffect)`，CornerRadius 用全限定名避免加 import）。
3. **缩放"一下子变巨大"（BUG，根因=复合缩放）** — 旧实现每帧 `newScale = live(t.scale) × k`，而 `k` 又是相对**按下基准**的绝对比值 → 同一手势内事件级联指数放大（k 的幂），手指稍快一甩就爆大。现**按下瞬间冻结基准**（t0/hw0/hh0/TL/ref），每个 move 事件都算 `newScale = t0.scale × (d/ref)`——手指位移与框边位移 1:1 线性对应，无累积；k 夹 0.05..20、scale 仍夹 MIN/MAX。
4. **宽度无上限（BUG）** — 缩放后有效宽度超过设备屏宽没有约束。现每次应用前算 `effW = 2·baseW1·newScale`（baseW1=hw0/t0.scale），若 `effW > fp.width`（GLRetroView 全屏矩形=物理屏宽）则按比例回缩 `newScale ×= fp.width/effW`，**宽度硬上限=设备屏宽**（左上角锚定语义不变，等比故高度随宽度联动）。
5. **把手命中区收窄（配套）** — 缩放模式下现在**只有按下把手方块区域**才启动缩放；按下框内其他位置仅**切换选中屏**（旧版按框内任意点即拖缩放，与"拖把手"的设计不符，也是误触发爆大的入口）。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `dragResizeHandle`（冻结基准+inHandle 命中+宽度钳制）；overlay 把手渲染（offset 减 handlePx、Canvas 虚线框+双端箭头、`with(LocalDensity.current){50.dp.toPx()}`）
- `lemuroid-app/build.gradle.kts` — versionCode 266→267、versionName 1.20.1→1.20.2

---

## v1.20 - 2026-09-03

### NDS 编辑器：底栏 4 项 + 等比缩放把手（左上角锚定）+ 菜单子菜单 + 竖/横各 3 槽位存储（版本升至 1.20.1-v8b）

**分支 `v8b-nds-editor`，versionCode 266 / versionName 1.20.1 / suffix -v8b**:

1. **删除灰色"编辑全局布局"禁用按钮（需求①）** — 底栏无功能的预留占位项删除，5 项变 4 项：菜单 / 重设回默认 / 关闭·打开工具箱 / 调整屏幕大小。
2. **"调整屏幕大小"= 等比缩放模式开关（需求②）** — 原为"退出编辑器"，现改为模式开关：单击进入**缩放模式**（按钮文字变"返回"，再点退出回普通编辑），选中虚线框**右下角**出现 **50dp 白底蓝边圆角把手**（内 Canvas 手绘 ↘ 双向箭头，不用 `Icons.Default.SouthEast`——compose-icons core 1.6.1 无此图标）。拖动把手**等比缩放**该屏，**左上角固定不动**（用户拍板 2b）。缩放数学：锚点=框当前实际左上角 TL（由 `applyScreenLayoutTransform` 得到，含 offset/gap）；参考距离 `ref`=按下点沿主轴（竖屏 x / 横屏 y）到 TL 距离；每帧 `k=(d/ref)`、`newScale=clamp(scale·k)`，再按实际应用系数 `kApplied=newScale/scale` 平移 offset（`ox'=offsetX+hw·(kApplied−1)`，`oy'=offsetY+hh·(kApplied−1)`）→ TL 纹丝不动、宽高同步。首帧 k≈1 无跳变。缩放模式下点按框内**只选中不平移**（`dragInsideFrame` 不挂），双指捏合不生效（预期）。
3. **编辑器出口并入"菜单"子菜单（用户拍板 2a）** — 底栏"菜单"（第 1 项）点击弹**子菜单** `ScreenLayoutSubmenu`（不再直开模拟器主菜单）：标题"保存 / 载入布局（横/竖版 · 全局）"；只读行"**现在布局**：{槽位名 | 默认（未保存）}"；**当前方向** 3 个槽位各一行（已占用显 ●，当前激活槽浅色高亮+名称蓝 #35b5e8），行右侧"保存""载入"（空槽载入置灰）；底部"**返回游戏菜单**"（= `toggleEditScreenLayout(false)` + `showGameMenu()`，即编辑器出口）。子菜单 Surface 带 no-op clickable 吞点按，防止底下编辑器手势误拖。
4. **布局存储改为竖/横各 3 槽位（方案 A，用户拍板）** — 原数据层有一套无 UI 引用的死代码 `profiles`/`activeProfileId`，本次删除，换成 `slots: Map<String, Slot>`（key=`portrait_1`…`landscape_3`，`SLOTS_PER_ORIENTATION=3`）+ `activeSlot`。**全局、跨 NDS 游戏共用**，竖版横版分开各 3 槽。旋转屏幕自动切到对应方向最后使用的槽（`onOrientationChanged`：目标方向有槽→载入其最后使用槽；activeSlot 已属该方向→不动；**无槽→保留当前工作值但清空 activeSlot**，UI 显示"默认（未保存）"，避免横竖串标）。旧 profiles 数据不迁移（无 UI 用过，加载时直接忽略回默认）。将来若要"每游戏独立"，只需 slot key 加 gameId 维度，架构预留不返工。

**修改文件**:
- `lemuroid-app/.../shared/game/screenlayout/ScreenLayoutManager.kt` — 删 profiles 整套；新增 `Slot`/`slotKey()`/`saveToSlot`/`loadFromSlot`/`onOrientationChanged`/`activeSlotLabel`；`ScreenLayoutState` 增 `slots`/`activeSlot`；`loadState` 按 activeSlot 恢复工作值
- `lemuroid-app/.../shared/game/viewmodel/GameViewModelScreenLayout.kt` + `BaseGameScreenViewModel.kt` — profile facade 方法换成 `saveScreenLayoutToSlot`/`loadScreenLayoutFromSlot`/`onScreenLayoutOrientationChanged`/`currentScreenLayoutSlotLabel`（`updateScreenLayoutTransform` 语义不变：只覆盖 offsetX/Y+scale、保留 scaleX/scaleY/gap——缩放手势依赖此点）
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — `ScreenLayoutBottomBar` 新签名（`resizeMode`/`onToggleResizeMode`/`onMenu`），删"编辑全局布局"禁用项
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — overlay 新增 `resizeMode`/`menuOpen` 状态；`.pointerInput(resizeMode.value)` 按模式二选一挂 `dragInsideFrame`/`dragResizeHandle`（**单一 handler 原则**，见 MEMORY 避坑）；新增 `dragResizeHandle` 手势 + 右下角 50dp 把手渲染 + `ScreenLayoutSubmenu` 子菜单；`LaunchedEffect(isLandscape)` 追加 `onScreenLayoutOrientationChanged` 自动切方向槽位
- `lemuroid-app/build.gradle.kts` — versionCode 265→266、versionName 1.20.0→1.20.1

---

## v1.19 - 2026-09-03

### NDS 编辑器：图标-功能按"现状格位"归位 + 四向箭头环绕居中 + 唯一间距按钮（版本升至 1.20.0-v8b）

**分支 `v8b-nds-editor`，versionCode 265 / versionName 1.20.0 / suffix -v8b**:

1. **图标与功能张冠李戴（BUG）** — v1.17/v1.18 把功能挪进用户钉死的格位时，功能仍带着它在**原始设计稿格位**的图标（`nds_tile_*` drawable 的文件名=原始稿格位，非现状格位），导致宽度100%按钮显示↑、宽度50%显示→、间距按钮显示↔百分比。本次按**现状格位**逐一核对图标视觉并修正：宽度100%→↔100% 图标（`nds_tile_r2c4`）、宽度50%→↔50% 图标（`nds_tile_r1c3`）。所有讨论坐标系统一改为**现状布局**（用户明确要求）。
2. **四个方向箭头对齐按钮应环绕居中按钮（设计修正）** — ↑↓←→ 四张箭头瓦片此前散落（↑挂在宽度100%上、→挂在宽度50%上），逻辑上应包围居中按钮。竖屏现状：↑=R2C2（**新增上对齐按钮**，`AlignEdge.TOP` 贴设备屏顶 `fullPos.top`）、←=R3C1、→=R3C3、↓=R4C2、✛=R3C2。横屏同理重排为四向环绕 R2C2 居中。
3. **间距按钮从两个归为一个（设计修正）** — 原"间距−(↔50%图标)"与"间距+(↔100%图标)"两按钮删除；全工具箱**只有一个间距按钮**，图标为设计稿原始 R3C3 格位的 `tiles/R3C3.svg`（仓内即 `nds_tile_r3c3.xml`，两横线夹竖向 10px 箭头），竖屏放 **R4C3**。行为：点击循环 缝隙 0→16→32→48→0 px（`cycleGap`，步长/回绕常量 `GAP_CYCLE_STEP/GAP_CYCLE_MAX`）。
4. **竖屏最终网格** — R1[高度100%(↕100) | 宽度100%(↔100) | 空位] / R2[高度50%(↕50) | 上对齐(↑) | 宽度50%(↔50)] / R3[左对齐(←) | 居中(✛) | 右对齐(→)] / R4[原始尺寸 | 底部对齐(↓贴设备底) | 间距(10px图标)]。横屏：R1[高50|上对齐↑|高100|宽100] / R2[左←|居中✛|右→|宽50] / R3[原始|下对齐↓|间距|空]。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — width100/width50 换图标 drawable；新增 `CELL_ALIGN_TOP`（`nds_tile_r1c2` ↑）与 `CELL_GAP`（`nds_tile_r3c3`）；删除 `CELL_GAP_MINUS/CELL_GAP_PLUS`；`CELL_ALIGN_BOTTOM` 改用 `AlignEdge.BOTTOM_DEVICE`（横竖屏底部对齐统一贴设备屏底）；`toolGridPortrait/toolGridLandscape` 重排；`nudgeGap`→`cycleGap`
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `alignToEdge` 的 `AlignEdge.TOP` 锚点由 `viewPos.top` 改为 `fullPos.top`（贴设备屏顶）
- `lemuroid-app/build.gradle.kts` — versionCode 264→265、versionName 1.19.9→1.20.0

---

## v1.18 - 2026-09-03

### NDS 编辑器：修复框内拖不动 + 工具箱默认关闭 + 竖屏网格格位归位（版本升至 1.19.9-v8b）

**分支 `v8b-nds-editor`，versionCode 264 / versionName 1.19.9 / suffix -v8b**:

1. **修复"无论触摸在哪都拖不动"（BUG）** — v1.17 在同一个全屏 Box 上挂了**两个** `.pointerInput`：`detectTapGestures`（选屏）与 `dragInsideFrame`（拖动）。两个 handler 竞争同一条事件流：`detectTapGestures` 先消费 `down`/`move`，`dragInsideFrame` 拿不到干净的按下点，**框内框外全都拖不动**。现合并为**单一** `dragInsideFrame`：按下命中某框 → 立即选中该屏并进入拖拽（单指 pan、双指 pinch-zoom）；按下在所有框外 → 外层 `while(true)+continue` 忽略本次、不消费、不移动并继续监听下一次按下。删除 `detectTapGestures` 及其导入。子级（工具箱瓦片/底栏按钮）的 clickable 先消费事件，故按下检测加 `!c.isConsumed` 守卫，避免点按钮时误拖其下虚线框。
2. **工具箱默认关闭（BUG）** — 进入编辑界面时工具箱原默认展开。用户要求：默认关闭，底栏按钮此时显示"打开工具箱"。`ScreenLayoutEditorOverlay` 的 `toolboxVisible` 初值 `true` → `false`。
3. **竖屏网格格位归位（BUG）** — 用户多次钉死格位：R1C2=宽度100%、R2C3=宽度50%、R3C3=右对齐、R4C2=底部对齐(贴设备屏底)、**R4C3 必须留空**。v1.17 竖屏实现却排成 R2C2=宽度100%、R3C3=宽度50%、R4C3=右对齐（即"莫名多出 R4C3 右对齐按钮"）。`toolGridPortrait` 按上述格位重排；横屏网格本就正确，仅修正其过时注释（R1C2/R2C3 实为宽度 100%/50%，非"上移/右移"）。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `dragInsideFrame` 合并选屏逻辑成唯一手势（外层 `while(true)`+`continue` 忽略框外按下、`!isConsumed` 守卫子级点击）；移除 Box 上竞争的 `detectTapGestures` pointerInput 与导入；`toolboxVisible` 初值改 `false`
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — `toolGridPortrait` 竖屏网格按钉死格位重排（R4C3 留空）；横屏网格文档注释更正
- `lemuroid-app/build.gradle.kts` — versionCode 263→264、versionName 1.19.8→1.19.9

---

## v1.17 - 2026-09-01

### NDS 编辑器：拖动仅限框内 + R3C3 右对齐 + R4C2 设备底对齐（版本升至 1.19.8-v8b）

**分支 `v8b-nds-editor`，versionCode 263 / versionName 1.19.8 / suffix -v8b**:

1. **拖动只在虚线框内响应（BUG，回退 v1.15 的全屏可拖）** — v1.15 把 overlay 合并为单个 full-screen Box、手势挂在容器上，导致手指落在**任意位置**都能拖动选中屏。用户明确要求：手指必须**先按在虚线框范围内**才响应移动，框外触摸移动不响应。现新增 `PointerInputScope.dragInsideFrame`：用 `awaitFirstDown(requireConsumption=false)` 拿到按下点，把 top/bottom 帧矩形从 root 坐标平移到本 Box 局部坐标（与 tap-to-select 同一套 `translate(-fp.left,-fp.top)` 命中算法）做命中测试——命中哪块框就选中并拖动它；**完全落在所有框外则直接 return，不消费、不动任何屏**。单指 pan、双指以中点为基准 pinch-zoom（`previousPosition`/`position` 距离比），循环 `awaitPointerEvent` 直到所有手指抬起。移除了不再使用的 `detectTransformGestures` 导入。
2. **R3C3 改为右对齐（BUG）** — R3C3（nds_tile_r3c3，原"Screen Gap"）改为**右对齐**：选中屏贴靠显示区右边缘（`AlignEdge.RIGHT`）。新建图标 `nds_tile_align_right.xml`（镜像 r2c1 左对齐瓦片：右箭头+右侧竖条），横屏/竖屏网格的 R3C3 格位均替换为 `CELL_ALIGN_RIGHT`。
3. **R4C2 底部对齐到设备屏幕底（BUG）** — 竖屏工具箱 R4C2（下移，nds_tile_r3c2）此前对齐到游戏视图锚点底边（`viewPos.bottom`）。用户要求"底部=设备屏幕底部"。新增 `AlignEdge.BOTTOM_DEVICE`：`alignToEdge` 对其用 `fullPos.bottom`（全屏 GLRetroView 即物理屏底）而非 `viewPos.bottom`；竖屏 R4C2 改为运行时 cell `bottomDevice`（`remember{}`，action=align(BOTTOM_DEVICE)），横屏"下移"保持对齐游戏视图锚点不变。
4. **宽度工具确认** — R1C2=宽度 100%、R2C3=宽度 50%（v1.15/v1.16 已实现）经本次核对无误，保持不变。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — 新增 `dragInsideFrame` 手势（仅框内响应）、`alignToEdge` 增加 `BOTTOM_DEVICE` 分支（`fullPos.bottom`）、移除 `detectTransformGestures`、新增相关导入
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — `AlignEdge` 增 `BOTTOM_DEVICE`；R3C3 → `CELL_ALIGN_RIGHT`（右对齐）；竖屏 R4C2 → `bottomDevice`（设备底对齐）；grid 工厂函数签名更新
- `lemuroid-app/.../res/drawable/nds_tile_align_right.xml` — 新增右对齐瓦片图标
- `lemuroid-app/build.gradle.kts` — versionCode 262→263、versionName 1.19.7→1.19.8

---

## v1.16 - 2026-09-03

### NDS 编辑器：去掉工具箱整块深色底板（版本升至 1.19.7-v8b）

**分支 `v8b-nds-editor`，versionCode 262 / versionName 1.19.7 / suffix -v8b**:

1. **工具箱不再有整块深色面板背景（优化）** — v1.15 只去掉了阴影，但用户指出的是"按钮集下面一整块黑底框"：工具箱 `Surface(color=0xCC1C1C20)` 包裹 grid+缩放面板，竖屏时左侧 grid（4 行）比右侧缩放面板（3 行）高，顶部对齐后面板背景在缩放按钮下方露出一大块黑色区域。现把 Surface 背景改为 **`Color.Transparent`**（阴影 v1.15 已为 0）。每个工具瓦片自带白底圆角方块+#48DAFF 描边、缩放按钮自带白底+虚线框（设计文档 §4.1/§4.2），去掉深底后按钮直接浮在游戏画面上，依然清晰。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — 工具箱 Surface `color` `0xCC1C1C20` → `Color.Transparent`

---

## v1.15 - 2026-09-01

### NDS 编辑器：倍率按钮互斥 + 全屏可拖 + 宽度百分比工具 + 去工具箱阴影（版本升至 1.19.6-v8b）

**分支 `v8b-nds-editor`，versionCode 261 / versionName 1.19.6 / suffix -v8b**:

1. **倍率按钮不再同时激活（BUG）** — 竖屏 1080px 手机上 `baseScale = 256/1080 ≈ 0.237`，点 1x→0.237、2x→0.474 都低于旧的 `MIN_SCALE=0.5f`，两者都被钳到 0.5 导致 1x/2x 按钮同时高亮。现把 `ScreenLayoutManager.MIN_SCALE` 从 `0.5f` 降到 `0.15f`，各档位值互不重叠、按下哪个就只高亮哪个。
2. **虚线框全屏任意位置可拖动（BUG）** — overlay 此前是两个同级 full-screen Box：第一个装 pointer handlers + Canvas，第二个装工具箱+底部栏；后者盖在前者上，空区域的触摸被它拦截后向上传播、永远到不了第一个 Box 的手势，导致手指必须落在虚线框内才能拖。现**合并为单个 full-screen Box**，pointer handlers 挂在容器上（拖动全屏任意位置生效），工具箱与底部栏作为子节点，其自身的 clickable 会先消费点击、不干扰变换手势。
3. **R1C2/R2C3 改为宽度百分比工具（功能）** — R1C2（nds_tile_r1c2，左右箭头+横条）从"上移"改为**宽度 100%**：把选中屏渲染宽度设为设备屏幕宽；R2C3（nds_tile_r2c3，右箭头+竖条）从"右移"改为**宽度 50%**：设为设备屏幕宽的 50%。实现为 `setWidthPercent`：`targetScale = percent × displayWidth / naturalWidth`（统一缩放保持 4:3）。因该逻辑需要运行时几何参数，这两个 cell 在 composable 内用 `remember(几何值)` 构建并注入 grid（grid 改为工厂函数 `toolGridLandscape/Portrait(width100, width50)`），其余 cell 仍为 top-level 常量。
4. **去掉工具箱底部黑色装饰框（优化）** — 工具箱 Surface 的 `shadowElevation=8.dp` 在 Android 上 elevation 阴影偏底部，看起来像工具箱下沿多了一条黑框。现改为 `shadowElevation=0.dp`，保留半透明深色底板与圆角。

**修改文件**:
- `lemuroid-app/.../shared/game/screenlayout/ScreenLayoutManager.kt` — `MIN_SCALE` 0.5f → 0.15f
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — overlay 两个 full-screen Box 合并为一个（pointer handlers 挂容器，工具箱/底部栏为子节点）
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — R1C2/R2C3 cell 改为宽度 100%/50%；新增 `setWidthPercent()`；grid 数组改工厂函数并注入运行时构建的 width cell；工具箱 Surface `shadowElevation` 8dp→0dp

---

## v1.14 - 2026-09-01

### NDS 编辑器：默认全宽 fit + 横屏左右并排 + 倍率按钮原生基准（版本升至 1.19.5-v8b）

**分支 `v8b-nds-editor`，versionCode 260 / versionName 1.19.5 / suffix -v8b**:

1. **默认打开 = 双屏整体 fit 游戏区（设计纠正）** — v1.19.4 把 natural rect 定为固定 256×192 device px，导致默认打开时每块屏只有手机屏幕宽度的 ~1/4。现改为：**竖屏**每块屏宽 = 锚点全宽（手机全宽）、高 = 0.75×宽（保持 4:3），上下叠放、高度不够时等比缩；**横屏**双屏左右并排，每块屏高 = 锚点全高、宽 = 1.333×高，宽度不够时等比缩。运行时画面与编辑器虚线框仍共用同一套几何（v1.19.4 的 BUG1 修复保留）。
2. **横屏双屏左右并排** — `applyScreenLayoutTransform` 的 gap 轴随方向切换：竖屏沿 Y（上屏上移/下屏下移），横屏沿 X（左屏左移/右屏右移）；对齐工具（上下左右/回中）逻辑不变，天然适配两种排布。
3. **倍率按钮改为原生分辨率基准 + 默认不高亮** — 此前 scale=1.0 恰好等于 1x 档位导致编辑器打开时 1x 按钮默认高亮。新语义：**Nx = N × 256×192 device px**（按 `nativeResolutionScale = 256 / naturalWidth` 映射到 scale 值，如 1080px 宽手机上 1x ≈ scale 0.237），默认 fit 观感（scale=1.0）不等于任何整数档位，**倍率按钮默认全部不激活、手动按下才高亮**；点 1x 缩回 256px 原生小图。`maxOnScreenScale` 动态上限删除（默认已是全宽，zoom-in 必须允许超过 fit），统一用 MIN_SCALE..MAX_SCALE 区间。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — `computeNaturalScreenRects(anchor, isLandscape)` 重写为锚点 fit（竖屏全宽叠放/横屏并排）；新增 `nativeResolutionScale()`；删除 `maxOnScreenScale()`；`applyScreenLayoutTransform` gap 轴随方向切换
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — ZoomPanel 档位改为原生基准映射（Nx → N×256px），默认态无高亮

---

## v1.13 - 2026-09-01

### NDS 编辑器：运行时与虚线框同源 + 1x 对应原始分辨率 + 竖屏工具箱紧凑居中（版本升至 1.19.4-v8b）

**分支 `v8b-nds-editor`，versionCode 259 / versionName 1.19.4 / suffix -v8b**:

1. **编辑虚线框与运行时屏幕同源（BUG）** — 此前 NDS 默认态走 core 的 aspect-fit 单 viewport（整帧 256×384 塞进锚点），而编辑器虚线框走 split viewport，两套几何不一致导致虚线框大小/位置对不上运行画面，且"重设回默认"的值也不对。现改为 **NDS 始终走 split-viewport 渲染**（含默认态）：每块屏落在自己的 natural rect 上、围绕锚点居中堆叠，运行时画面与编辑器虚线框共享同一套几何计算，三者（运行画面 / 虚线框 / 重置默认）天然一致。
2. **1x 对应 256×192 原始分辨率（BUG）** — 此前 natural rect 用 `256 × density`（逻辑像素→物理像素，如 704~768px），导致点 1x 后虚线框远大于 256×192、后面大倍率按钮尺寸全错。现 **natural rect = 256×192 device px（不乘 density）**：1x = NDS 原生分辨率 1:1，一个模拟器像素对应一个设备像素；`maxOnScreenScale` 同步去掉 density 参数。
3. **竖屏工具箱紧凑居中（BUG）** — R1C4/R2C4 上移后原横屏第 4 列全空，此前保留为空槽造成中间大间隙。现**直接删除该空列**，改为 3×4 紧凑布局：整体居中、与屏幕两边留均匀间距、与倍数放大面板保持既有间距。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — NDS 始终 split viewport（删除 aspect-fit fallback）；`computeNaturalScreenRects` / `maxOnScreenScale` 去掉 density（natural rect = 256×192 device px）；overlay/toolbox 签名清理 density
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — TOOL_GRID_PORTRAIT 改为 3 列紧凑布局（删除全空第 4 列）；composable/ZoomPanel 签名去掉 density

---

## v1.12 - 2026-09-01

### NDS 编辑器：编辑时隐藏画面 + 虚线框样式改造 + 缩放语义修正（版本升至 1.19.3-v8b）

**分支 `v8b-nds-editor`，versionCode 258 / versionName 1.19.3 / suffix -v8b**:

1. **编辑时隐藏游戏画面** — 进入「调整屏幕大小」后游戏画面 alpha 置 0（视图保持存活、不重建），画面上只剩虚线框作为唯一参照，直接对虚线框进行拖动/缩放/对齐。此前多轮"让画面跟随虚线框"的尝试因 core 渲染时序问题无法稳定同步，改为隐藏画面从根上消除不同步。
2. **虚线框样式改造** — 虚线粗细减半（选中 1.5dp / 未选中 1dp）；上屏框填充 `#5D71E4`、下屏框填充 `#5DE45D`，均为 50% 透明度，先填充后描边。
3. **缩放倍率语义修正（BUG）** — 此前 overlay 走 `aspectFitRect()` letterbox 分支导致 1x 不等于原始分辨率。删除该间接层：虚线框与缩放面板统一以 natural rect（256×192 逻辑像素 × density）为基准，**1x = NDS 屏幕原始分辨率 256×192**，2x/3x… 按倍数递增；`maxOnScreenScale` 上限从锚点矩形改为整屏尺寸（编辑时画面可放在屏幕任意位置）。
4. **竖屏编辑模式按钮布局调整** — 仅竖屏：R1C4（高度100%）移到 R1C1（高度50%）正上方、R2C4（间距+）移到 R1C2（上移）正上方，其余格子保持原列位置，形成 4×4 网格；横屏布局不变。

**修改文件**:
- `lemuroid-app/.../mobile/feature/game/MobileGameScreen.kt` — AndroidView 加 `.alpha(0f)` 编辑隐藏；overlay 删除 aspectFitRect 分支改用 natural rect；drawScreenFrame 加填充色+线宽减半；maxOnScreenScale 改全屏上限
- `lemuroid-app/.../mobile/feature/game/ScreenLayoutEditorToolbox.kt` — ToolCell 重构（cell 自带 drawable）；新增 TOOL_GRID_PORTRAIT 4×4 布局；ZoomPanel 改收 displayWidthPx/displayHeightPx

---

## v1.11 - 2026-09-01

### 修复：NDS 编辑器四个 BUG（版本升至 1.19.2-v8b）

**分支 `v8b-nds-editor`，versionCode 257 / versionName 1.19.2 / suffix -v8b**:

1. **虚线框与游戏画面不同步** — v1.10 引入的「动态读取 core 宽高比」存在时序问题：编辑器打开时 core 可能尚未报告 geometry，`getAspectRatio()` 返回默认值 1.0（而非 256/384≈0.667），导致虚线框整体错位。回退为硬编码常量：默认布局用整帧 `256/384`、自定义布局每屏用单屏 `256/192`（= 原生 `aspectRatio * 2`，与 `videolayout.cpp` letterbox 数学一致），删除 `LaunchedEffect` 中的动态读取。
2. **工具箱无法重新打开** — 底栏按钮原先固定显示「关闭工具箱」且只能关。现改为状态切换：面板可见时显示「关闭工具箱」，隐藏后变为「打开工具箱」，点击即开/关（`ScreenLayoutBottomBar` 新增 `toolboxVisible` / `onToggleToolbox` 参数）。
3. **瓦片图标不完整** — R1C1、R1C3、R1C4、R2C1、R2C3、R2C4、R3C2、R3C3 八个图标缺少表示对齐基准的横条/竖条，按设计稿补齐。
4. **倍数放大按钮选中态错误 + 排序** — 原 `active = currentScale == clampedScale`：当上限低于某些档位时（如 1x 屏上限 1.5），3x/4x/5x… 全部被 clamp 到同一值、同时高亮变蓝。改为 `stepFloat <= maxScale && currentScale == stepFloat`，只有自身值等于当前倍数的按钮才高亮；档位顺序由列优先（1,5,2,6,…）改为顺序排列（横屏 1~7x、竖屏 1~5x，从左到右从上到下）。

---

## v1.10 - 2026-09-01

### 修复：NDS 编辑器工具箱 UI 还原 + 虚线框与渲染严格同源（版本升至 1.19.1-v8b）

**分支 `v8b-nds-editor`，versionCode 256 / versionName 1.19.1 / suffix -v8b**:

1. **工具箱 UI 还原设计稿** — R1C1~R3C3 九个工具按钮改用设计稿原始 SVG 瓦片图标（新增 `nds_tile_r1c1`…`nds_tile_r3c3` 共 11 个 VectorDrawable），不再自绘近似图形；底部操作栏按设计稿还原为「关闭工具箱」等固定项，删除此前自创的居中「工具箱」打开按钮（面板打开即默认可见）。
2. **虚线框与实际渲染严格同源** — 根因：overlay 硬编码 `256/384` 整帧宽高比，而原生 `videolayout.cpp` 用 core 报告的 `aspectRatio * 2`（melonDS 与 desmume 报告值不同），必然对某一个 core 错位。修复：新增 JNI 桥接 `LibretroDroid.getAspectRatio()`（C++ → Java native → `GLRetroView.getAspectRatio()`，在 emulation 线程同步读取），编辑器打开时读一次真实宽高比并缓存——自定义布局用 `coreAspect × 2`、默认布局用 `coreAspect`，与原生 letterbox 数学完全一致；读取失败时回退到 256/384 安全值。
3. **拖动卡顿** — 指针手势检测器改用稳定 key（`pointerInput(Unit)` + `rememberUpdatedState`），避免每帧重组重启手势导致一帧一顿。

---

## v1.9 - 2026-09-02

### 修复：NDS 屏幕编辑器三个问题（版本升至 1.19.0-v8b）

**分支 `v8b-nds-editor`，versionCode 255 / versionName 1.19.0 / suffix -v8b**:

1. **进入编辑模式时游戏不暂停、画面不冻结** — 根因是生命周期竞态：游戏菜单为独立 Activity，返回时 `onActivityResult()`（执行 `pauseEmulation()`）先于 `onResume()` 触发，后者经 `RenderLifecycleObserver.resume()` 把 `isEmulationReady` 重新置 true 导致自动解冻。修复：`GLRetroView.kt` 新增 `isEditorMode` 标志位，编辑期间跳过 ON_RESUME 的自动 resume；显式 `resumeEmulation()` 时才清除标志恢复。
2. **虚线框与实际画面位置/大小不匹配** — overlay 原先按固定自然尺寸画框，未复刻 native `updateForegroundQuad` 的 aspect-fit letterbox。修复：`MobileGameScreen.kt` 新增 `aspectFitRect()`，默认布局（整帧 256×384 fit）与自定义布局（每屏单独 fit）分别计算框的几何。
3. **底部菜单栏被隐藏** — `ScreenLayoutBottomBar` 原在工具箱 else 分支内，现移出使其始终可见；仅中间「工具箱」按钮随面板开合切换。

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

## v1.10 - 2026-09-02

### 缩放倍率动态 clamp（不超手机可用区）

**需求**: 缩放面板与捏合缩放的倍率上限不应写死为 7x/5x，而应动态取「当前手机可用区的最大不超屏倍率」，保证放大后的单块屏始终完整显示在屏幕内。

**实现**:
1. `MobileGameScreen.kt` — 新增 `maxOnScreenScale(viewPos, density, scaleX, scaleY)`：以 256×192×density 为基准，最大倍率 = min(可用宽/(基准宽×scaleX), 可用高/(基准高×scaleY))，并 coerce 到 [1, MAX_SCALE]。可用区取 GLRetroView 的锚点矩形 `viewPos`。
2. 捏合缩放（`detectTransformGestures`）与缩放面板（`ZoomPanel`）的 scale 上限从写死的 `MAX_SCALE` 改为动态 `maxOnScreenScale`；缩放面板中超过上限的档位会被 clamp 到上限值（例如小屏上 7x 档位实际只放大到 5.6x）。

**说明**: clamp 作用于「单块屏」——保证单屏不超屏；双屏纵向叠加时上下屏仍可能各自完整但整体超过可用区（属于布局自由摆放，不在此约束内）。本地无 JDK/SDK，验证依赖 GitHub Actions 自动编译。

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
