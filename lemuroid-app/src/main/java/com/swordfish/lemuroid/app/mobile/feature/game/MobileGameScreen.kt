@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager.TouchButtonId
import com.swordfish.touchinput.radial.layouts.LocalButtonDrag
import com.swordfish.touchinput.radial.layouts.LocalButtonEdit
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.ids.Id
import gg.padkit.inputstate.InputState
import timber.log.Timber

/**
 * Every control id any touch layout can register (v1.20.7). While the touch-controls editor is
 * open, this whole set is pushed through PadKit's simulation channel with a neutral InputState.
 * Root cause it fixes: PadKit's root pointer loop (PadKit.kt) filters only `pressed` pointers
 * and NEVER checks consumption — so the same finger that our child-level edit gesture (select /
 * free-drag) consumes was also hit-tested by PadKit, flickering pressed highlights, vibrating,
 * and shattering cross/face-button groups under the dragging finger. The simulation override is
 * applied to `scope.inputState.value` AFTER hit-detection, and controls/haptics/events all read
 * that same state → everything goes inert while editing. Ids unused by the active pad are
 * harmless no-ops inside InputState (fold removes from empty sets).
 */
private val ALL_TOUCH_CONTROL_IDS: Set<Id> = buildSet {
    // v1.20.9 ROOT-CAUSE FIX (bug1): v1.20.7/1.20.8 enumerated only Id.Key(0..31), but every pad
    // button uses Android gamepad KEYCODES (BUTTON_MODE=82 … BUTTON_START=108) — NONE of them
    // were in the set, so only the D-pad (a DiscreteDirection) got neutralized and every button
    // still reacted to the editing finger. Enumerate the whole corridor any layout may use
    // (verified against `Id.Key(KeyEvent.*)` usages in lemuroid-touchinput). Unused ids are
    // harmless no-ops: InputState's fold just removes from an empty set.
    for (code in 0..127) add(Id.Key(code))
    // Direction controls — every MOTION_SOURCE value under both direction wrappers.
    for (src in 0..4) {
        add(Id.DiscreteDirection(src))
        add(Id.ContinuousDirection(src))
    }
}

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
            // v1.20.8: rotation NO LONGER touches the NDS screen layout. The layout is a manual
            // mode (layoutOrientation in ScreenLayoutState) switched from the game menu / editor
            // sub-menu — gravity-driven switching kept clobbering unsaved work between modes.
            // Touch-button settings DO stay per-physical-orientation (they follow how you hold
            // the phone, a separate concern from where the two screens render).
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        // NDS screen layout customization state
        val screenLayoutState = viewModel.getScreenLayoutState().collectAsState(null)
        val editScreenLayoutShown = viewModel.isEditScreenLayoutShown().collectAsState(false)
        // Editor sub-mode (v1.20.5): true = touch-controls editor, false = screen-layout editor
        val editControlsMode = viewModel.isEditControlsModeShown().collectAsState(false)

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // Touch-controls editor (v1.20.7): neutralize the WHOLE pad through PadKit's simulation
        // channel so its root pointer loop (which ignores consumed events) can no longer
        // highlight / vibrate / shatter buttons under the editing finger. See ALL_TOUCH_CONTROL_IDS.
        // NOTE: the .value reads must stay INSIDE the derivedStateOf closures — a captured local
        // val would freeze the first composition's value (stale closure).
        val padSimulatedIds =
            remember {
                derivedStateOf {
                    if (editScreenLayoutShown.value && editControlsMode.value) {
                        ALL_TOUCH_CONTROL_IDS + tiltSimulatedControls.value
                    } else {
                        tiltSimulatedControls.value
                    }
                }
            }
        val padSimulatedState =
            remember {
                derivedStateOf {
                    if (editScreenLayoutShown.value && editControlsMode.value) InputState()
                    else tiltSimulatedStates.value
                }
            }

        val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
        // Keyed by orientation: a rotation invalidates any previously frozen anchor rect
        val viewportPosition = remember(isLandscape) { mutableStateOf<Rect?>(null) }

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = padSimulatedState,
            simulatedControlIds = padSimulatedIds,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Hide the game picture while the SCREEN-LAYOUT editor is open: the user
                        // edits against the dashed frames only. In CONTROLS edit mode (v1.20.5) the
                        // picture stays visible so buttons can be placed over it. Alpha keeps the
                        // view alive so GLRetroView is not recreated and fullScreenPosition stays
                        // valid.
                        .alpha(if (editScreenLayoutShown.value && !editControlsMode.value) 0f else 1f)
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle)
                },
            )

            val fullPos = fullScreenPosition.value
            val viewPos = viewportPosition.value
            val screenLayout = screenLayoutState.value

            LaunchedEffect(fullPos, viewPos, screenLayout, isLandscape) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                // Custom NDS layout applies in both orientations; at default values the
                // transform is the identity so unconfigured behavior is unchanged.
                // NDS always renders in split-viewport mode — even at default values — so the
                // runtime picture and the editor's dashed frames share one geometry: each screen
                // sits on its natural rect (the anchor-fit size, full phone width in portrait /
                // full height side-by-side in landscape). There is no aspect-fit single-viewport
                // fallback for NDS anymore, which is what made the editor frames drift from the
                // actual runtime size.
                // v1.20.8: geometry follows the MANUAL layout mode (screenLayout.layoutOrientation),
                // NOT the physical rotation — editing one mode can never disturb the other.
                val applySplitLayout = viewModel.isNdsSystem() && screenLayout != null
                if (applySplitLayout) {
                    val layoutLandscape =
                        screenLayout!!.layoutOrientation == ScreenLayoutManager.Orientation.LANDSCAPE
                    val (naturalTop, naturalBottom) = computeNaturalScreenRects(viewPos, layoutLandscape)
                    val topRect =
                        applyScreenLayoutTransform(naturalTop, screenLayout.topScreen, gapSign = -1f, layoutLandscape, fullPos)
                    val bottomRect =
                        applyScreenLayoutTransform(naturalBottom, screenLayout.bottomScreen, gapSign = +1f, layoutLandscape, fullPos)
                    val topViewport = normalizeToFullScreen(topRect, fullPos)
                    val bottomViewport = normalizeToFullScreen(bottomRect, fullPos)
                    Timber.d("Setting split viewport: top=$topViewport bottom=$bottomViewport")
                    gameView.splitViewport = topViewport to bottomViewport
                    // Per-screen visibility (v1.20.4): a disabled screen's quad is skipped by the
                    // renderer and receives no touch. Both setters queue on the emulation thread
                    // in order, and splitViewport's own setter re-pushes the saved visibility, so
                    // the two calls converge regardless of order (also replays after Video rebuild).
                    gameView.splitScreenVisible =
                        screenLayout.topScreen.enabled to screenLayout.bottomScreen.enabled
                } else {
                    val viewport = normalizeToFullScreen(viewPos, fullPos)
                    Timber.d("Setting game viewport: $viewport (customLayout=false)")
                    gameView.splitViewport = null
                    gameView.viewport = viewport
                }
            }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape,
                        currentControllerConfig?.allowTouchOverlay ?: true,
                        verticalAlign = GameScreenLayout.VerticalAlign.CENTER,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                            // Freeze the anchor rect while touch controls are hidden: hiding the
                            // pads removes them from the constraint set which would otherwise move
                            // the anchor (and thus the picture). Keep the last visible-layout rect.
                            .onGloballyPositioned {
                                if (touchControlsVisibleState.value || viewportPosition.value == null) {
                                    viewportPosition.value = it.boundsInRoot()
                                }
                            },
                )

                val isVisible =
                    touchControllerSettings != null &&
                        currentControllerConfig != null &&
                        touchControlsVisibleState.value

                // Unified editor state (v1.20.5): the editor has two sub-modes —
                // screen layout (NDS dashed frames) and touch controls (button editor).
                val inControlsEdit = editScreenLayoutShown.value && editControlsMode.value

                if (isVisible) {
                    CompositionLocalProvider(LocalLemuroidPadTheme provides LemuroidPadTheme()) {
                        if (!isLandscape && !inControlsEdit) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                            )
                        } else if (!isLandscape && inControlsEdit) {
                            // Controls edit mode: hide the glass pad background so buttons can
                            // be judged against the game picture itself.
                        } else if (!currentControllerConfig.allowTouchOverlay && !inControlsEdit) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                            )
                        }

                        // In controls edit mode the pads receive select/drag callbacks instead of
                        // game input (input is blocked VM-side while editing).
                        CompositionLocalProvider(
                            LocalButtonEdit provides
                                if (inControlsEdit) ({ target: TouchButtonId -> viewModel.selectEditTarget(target) }) else null,
                            LocalButtonDrag provides
                                if (inControlsEdit) {
                                    ({ id: TouchButtonId, dx: Float, dy: Float -> viewModel.updateButtonFreeDrag(id, dx, dy) })
                                } else {
                                    null
                                },
                        ) {
                            leftGamePad?.invoke(
                                this,
                                Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                                touchControllerSettings,
                            )
                            rightGamePad?.invoke(
                                this,
                                Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                                touchControllerSettings,
                            )
                        }

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            controllerConfig = currentControllerConfig,
                            touchControllerSettings = touchControllerSettings,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        // Draggable floating menu button — shown when virtual controls are hidden.
        // Hidden while the NDS layout editor is open (v1.20.3): the editor's bottom-bar 菜单
        // sub-menu already offers 返回游戏菜单, so the top-right ☰ would only overlap it.
        if (!touchControlsVisibleState.value && !editScreenLayoutShown.value) {
            DraggableMenuButton(viewModel)
        }

        // NDS screen layout editor overlay (works with or without touch controls visible).
        // Composed after DraggableMenuButton so its touches reach the editor first.
        // v1.20.5: when the controls sub-mode is active, the touch-button editor takes over
        // (full-screen overlay with top A/B/C presets, bottom size slider, left visibility list).
        val layoutState = screenLayoutState.value
        if (editScreenLayoutShown.value && editControlsMode.value && touchControllerSettings != null) {
            TouchControlsEditorOverlay(
                viewModel = viewModel,
                touchControllerSettings = touchControllerSettings,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        } else if (editScreenLayoutShown.value && layoutState != null) {
            ScreenLayoutEditorOverlay(
                viewModel = viewModel,
                layoutState = layoutState,
                fullPos = fullScreenPosition.value,
                viewPos = viewportPosition.value,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            )
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    controllerConfig: ControllerConfig,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        LemuroidButtonPressFeedback(
            pressed = menuPressed.value,
            animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
            icon = R.drawable.button_menu,
        )
    }
}

/**
 * Touch-button editor overlay (v1.20.5, reworked in v1.20.6 per user feedback). The buttons
 * themselves are dragged directly on the game picture (via LocalButtonDrag); this overlay only
 * supplies the surrounding controls:
 *   • TOP: three CIRCULAR A / B / C preset buttons, letter centered. Tap = LOAD the preset (no-op
 *     while empty), long-press = SAVE current layout into it. Active preset highlighted, "已存"
 *     under the letter when saved. (v1.20.6 fix: tap no longer auto-saves empty slots.)
 *   • FLOATING CARD (bottom center initially): FIXED width, draggable anywhere by holding its
 *     background (children consume their own touches). Contains: selected-button row with 复位
 *     button on the LEFT + size slider on the RIGHT (no label text — v1.20.6), action row
 *     (显示 panel toggle / 全部复位 / 返回屏幕布局 NDS-only / 退出编辑), and an EXPANDABLE
 *     visibility section (one row per button = MiniToggle + label). The old fixed LEFT panel is
 *     gone — it covered too many buttons (v1.20.6).
 * Hidden buttons still render (dimmed) in edit mode so they can be re-enabled.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TouchControlsEditorOverlay(
    viewModel: BaseGameScreenViewModel,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    screenWidthPx: Float,
    screenHeightPx: Float,
) {
    val selectedButton = viewModel.getEditingSelection().collectAsState(null)
    val allButtons = TouchButtonId.values().toList()
    val isNds = viewModel.isNdsSystem()

    // Floating editor card (v1.20.6): fixed width, free position. Offset is in PIXELS from the
    // box's natural bottom-center spot; dragging is done by holding the card's BACKGROUND
    // (pointerInput below the children, so button/slider taps never move it).
    var cardOffset by remember { mutableStateOf(IntOffset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var visibilityExpanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // v1.20.9: card anchors at the SCREEN CENTER, so the drag clamp is symmetric on both axes
    // (was bottom-anchored with a vertical-only-upward clamp).
    val maxVx = with(density) { (screenWidthPx / 2f - 180.dp.toPx()).toInt().coerceAtLeast(0) }
    val maxVy = ((screenHeightPx - cardSize.height) / 2f).toInt().coerceAtLeast(0)
    // The drag closure lives in pointerInput(Unit) (built once) — read the LATEST clamp bounds
    // through rememberUpdatedState, otherwise cardSize.height=0 from first composition sticks.
    val curMaxVx = rememberUpdatedState(maxVx)
    val curMaxVy = rememberUpdatedState(maxVy)

    Box(modifier = Modifier.fillMaxSize()) {
        // (A) TOP — three CIRCULAR preset buttons, letter centered.
        //   Tap    → load  (no-op when the slot is empty: v1.20.6, previously an empty-slot tap
        //            silently SAVED, which is what the user hit).
        //   Hold   → save the current layout into the slot.
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            for (name in listOf("A", "B", "C")) {
                val saved = touchControllerSettings.presets.containsKey(name)
                val active = touchControllerSettings.activePreset == name
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .size(52.dp)
                                .background(
                                    if (active) Color(0xFF35b5e8) else Color(0xE6252528),
                                    CircleShape,
                                )
                                .border(
                                    1.5.dp,
                                    if (active) Color.White else Color(0xFF35b5e8),
                                    CircleShape,
                                )
                                .combinedClickable(
                                    onClick = { if (saved) viewModel.loadTouchPreset(name) },
                                    onLongClick = { viewModel.saveTouchPreset(name) },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name,
                            color = if (active) Color.White else Color(0xFF35b5e8),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = if (saved) "已存" else "空",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        // Hint for the preset interaction, right under the circles.
        Text(
            text = "点按载入 / 长按保存",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 108.dp),
        )

        // (B) FLOATING editor card (v1.20.9, user request): starts at the SCREEN CENTER —
        //     bottom-anchored cards covered the face buttons the user is trying to drag.
        //     Fixed 360dp width, draggable in all directions, contains size row + actions
        //     + expandable visibility panel.
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset { cardOffset }
                    .width(360.dp)
                    .onSizeChanged { cardSize = it }
                    .background(Color(0xE6252528), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        // Drag the card by its background: detectDragGestures only fires once a
                        // touch actually MOVES, and children (buttons, slider, toggles) consume
                        // their own events, so taps/presses on controls never drag the card.
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            cardOffset =
                                IntOffset(
                                    (cardOffset.x + dragAmount.x).toInt().coerceIn(-curMaxVx.value, curMaxVx.value),
                                    (cardOffset.y + dragAmount.y).toInt().coerceIn(-curMaxVy.value, curMaxVy.value),
                                )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val id = selectedButton.value
            if (id != null) {
                val bs = touchControllerSettings.getButtonSettings(id)
                // v1.20.6: 复位 button LEFT, size slider RIGHT, no label text.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { viewModel.resetButtonSettings(id) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("复位", color = Color.White, fontSize = 12.sp)
                    }
                    Slider(
                        value = bs.scale,
                        onValueChange = { viewModel.updateButtonScale(id, it) },
                        valueRange = 0.5f..2f,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = "按住屏幕上的按键可拖动位置，点选后可调大小",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { visibilityExpanded = !visibilityExpanded },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("显示 ▾", color = Color(0xFF35b5e8), fontSize = 12.sp)
                }
                TextButton(
                    onClick = { viewModel.resetTouchControls() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("全部复位", color = Color.White, fontSize = 12.sp)
                }
                if (isNds) {
                    TextButton(
                        onClick = { viewModel.setEditControlsMode(false) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("返回屏幕布局", color = Color(0xFF35b5e8), fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = { viewModel.exitLayoutEditor() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(if (isNds) "退出编辑" else "完成", color = Color.White, fontSize = 12.sp)
                }
            }
            // Expandable visibility panel (v1.20.6): replaces the old always-visible LEFT column.
            // v1.20.8: laid out as a GRID (4 per row) instead of one long column — the single
            // column made the floating card extremely tall with 10 buttons. Each cell = toggle on
            // top + short label below (vertical keeps the 4 columns readable); tapping the cell
            // selects that button.
            if (visibilityExpanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (rowBtns in allButtons.chunked(4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (btn in rowBtns) {
                                val visible = !touchControllerSettings.isButtonHidden(btn)
                                val active = selectedButton.value == btn
                                Column(
                                    modifier =
                                        Modifier
                                            // weight(1f) keeps the 4 columns aligned even when the
                                            // last row has fewer cells.
                                            .weight(1f)
                                            .then(
                                                if (active) Modifier.background(Color(0x2235b5e8), RoundedCornerShape(6.dp))
                                                else Modifier,
                                            )
                                            .clickable { viewModel.selectEditTarget(btn) }
                                            .padding(horizontal = 2.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    MiniToggle(
                                        on = visible,
                                        onToggle = { nv -> viewModel.toggleButtonVisibility(btn, !nv) },
                                    )
                                    Text(
                                        text = buttonEditorLabel(btn, isNds),
                                        color = if (visible) Color.White else Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                            // Pad a short last row so the columns stay aligned.
                            repeat(4 - rowBtns.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Editor-facing label for a button GROUP (v1.20.8). On NDS the left-stick / right-stick slots
 * are repurposed: MelonDS renders 关屏 (close a screen) and 换屏 (swap screens) there, and the
 * L2 slot is the mic — the generic enum labels (左摇杆/右摇杆/菜单) were actively confusing
 * (user-reported "左摇杆其实是关屏按钮"). Non-NDS keeps the enum defaults.
 */
private fun buttonEditorLabel(
    id: TouchButtonId,
    isNds: Boolean,
): String =
    when {
        isNds && id == TouchButtonId.THUMBL -> "关屏"
        isNds && id == TouchButtonId.THUMBR -> "换屏"
        // MelonDS renders L2 as a mic and DeSmuME as a close-screen — core-dependent, so just
        // show the key itself instead of the misleading generic "菜单".
        isNds && id == TouchButtonId.L2 -> "L2"
        else -> id.label
    }

/** Small pill slide switch for the left visibility panel (same look as ScreenEnableToggle). */
@Composable
private fun MiniToggle(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp, 18.dp)
                .background(
                    if (on) Color(0xFF35b5e8) else Color(0xFF606066),
                    RoundedCornerShape(9.dp),
                )
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                .clickable { onToggle(!on) },
    ) {
        Box(
            modifier =
                Modifier
                    .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(2.dp)
                    .size(14.dp)
                    .background(Color.White, CircleShape),
        )
    }
}
/** NDS single-screen resolution in native pixels — the 1x zoom button's target size. */
private const val NDS_SCREEN_WIDTH = 256f
private const val NDS_SCREEN_HEIGHT = 192f

/**
 * Computes the natural (untouched) rects of the top and bottom screens at scale=1.0 —
 * i.e. the DEFAULT look: both screens together fit the game-view anchor.
 *
 * Portrait: the pair stacks vertically, each screen as wide as the anchor (the phone's full
 * width), height = 0.75 × width to keep the 4:3 screen ratio; if that overflows the anchor
 * height the pair scales down proportionally. Landscape: the pair sits side by side, each
 * screen as tall as the anchor (full height), width = 1.333 × height, scaling down if it
 * overflows the anchor width.
 *
 * The zoom panel's 1x..7x are then NATIVE-RESOLUTION multiples: pressing Nx sets scale to
 * nativeWidth / naturalWidth (e.g. ≈0.23 on a 1080px phone), so 1x renders exactly 256×192
 * device px. The default (scale=1.0) never equals an integer step, which is why no zoom
 * button is highlighted until one is pressed.
 */
private fun computeNaturalScreenRects(anchor: Rect, isLandscape: Boolean): Pair<Rect, Rect> {
    val anchorW = anchor.width
    val anchorH = anchor.height
    if (anchorW <= 0f || anchorH <= 0f) return anchor to anchor

    // Per-screen size that fits the pair inside the anchor.
    var w: Float
    var h: Float
    if (isLandscape) {
        // Side by side: each screen full anchor height, width from the 4:3 ratio;
        // the pair is twice as wide, so it scales down when that overflows.
        h = anchorH
        w = anchorH * NDS_SCREEN_WIDTH / NDS_SCREEN_HEIGHT
        val pairW = 2f * w
        if (pairW > anchorW) {
            val k = anchorW / pairW
            w *= k
            h *= k
        }
    } else {
        // Stacked: each screen full anchor width, height from the 4:3 ratio;
        // the pair is 1.5× the single height, so it scales down when that overflows.
        w = anchorW
        h = anchorW * NDS_SCREEN_HEIGHT / NDS_SCREEN_WIDTH
        val pairH = 2f * h
        if (pairH > anchorH) {
            val k = anchorH / pairH
            w *= k
            h *= k
        }
    }

    val cx = anchor.center.x
    val cy = anchor.center.y
    return if (isLandscape) {
        // Top screen on the left, bottom screen on the right, flush at the center line.
        Rect(cx - w, cy - h / 2f, cx, cy + h / 2f) to
            Rect(cx, cy - h / 2f, cx + w, cy + h / 2f)
    } else {
        // Top screen on top, bottom screen below, flush at the center line.
        Rect(cx - w / 2f, cy - h, cx + w / 2f, cy) to
            Rect(cx - w / 2f, cy, cx + w / 2f, cy + h)
    }
}

/**
 * The uniform scale that renders one screen at its NDS native resolution (256×192 device px),
 * given the natural rect width produced by [computeNaturalScreenRects]. Used to map the zoom
 * panel's Nx buttons onto [ScreenLayoutManager.ScreenTransform.scale] values: pressing Nx sets
 * scale = N × this value, so 1x always means exactly 256 px wide regardless of device.
 */
internal fun nativeResolutionScale(naturalWidthPx: Float): Float {
    return if (naturalWidthPx > 0f) NDS_SCREEN_WIDTH / naturalWidthPx else 1f
}

/**
 * Applies a per-screen transform (scale around own center + pixel translation + gap).
 * The gap runs along the stack axis: vertical in portrait (top screen up, bottom down),
 * horizontal in landscape (left screen left, right screen right).
 *
 * When [deviceBounds] is non-null the resulting rect is CLAMPED (v1.20.4, user-pinned) so the
 * frame can never exceed the device screen: each axis' size is capped at the device dimension and
 * the position is shifted inward so the whole rect stays inside. This is the single choke point
 * both the editor's dashed frames and the runtime split-viewport pass through, so every tool
 * (resize handle, width/height, zoom, align) obeys the "stay inside the device screen" rule and
 * the visible frame always matches the rendered picture.
 */
private fun applyScreenLayoutTransform(
    base: Rect,
    transform: ScreenLayoutManager.ScreenTransform,
    gapSign: Float = 0f,
    isLandscape: Boolean = false,
    deviceBounds: Rect? = null,
): Rect {
    val centerX = (base.left + base.right) / 2f
    val centerY = (base.top + base.bottom) / 2f
    // Effective width = uniform scale × horizontal (width-axis) scale.
    var halfWidth = (base.right - base.left) * transform.scale * transform.scaleX / 2f
    // Effective height = uniform scale × vertical (height-axis) scale.
    var halfHeight = (base.bottom - base.top) * transform.scale * transform.scaleY / 2f
    var cx = centerX + transform.offsetX
    var cy = centerY + transform.offsetY
    // The gap pushes along the stack axis.
    if (isLandscape) cx += transform.gap * gapSign else cy += transform.gap * gapSign
    if (deviceBounds != null) {
        val maxHalfW = deviceBounds.width / 2f
        val maxHalfH = deviceBounds.height / 2f
        if (halfWidth > maxHalfW) halfWidth = maxHalfW
        if (halfHeight > maxHalfH) halfHeight = maxHalfH
        cx = cx.coerceIn(deviceBounds.left + halfWidth, deviceBounds.right - halfWidth)
        cy = cy.coerceIn(deviceBounds.top + halfHeight, deviceBounds.bottom - halfHeight)
    }
    return Rect(
        left = cx - halfWidth,
        top = cy - halfHeight,
        right = cx + halfWidth,
        bottom = cy + halfHeight,
    )
}

/** Normalizes a root-coordinate rect into the GLRetroView's 0..1 viewport space. */
private fun normalizeToFullScreen(
    rect: Rect,
    fullPos: Rect,
): RectF {
    return RectF(
        (rect.left - fullPos.left) / fullPos.width,
        (rect.top - fullPos.top) / fullPos.height,
        (rect.right - fullPos.left) / fullPos.width,
        (rect.bottom - fullPos.top) / fullPos.height,
    )
}

/** Euclidean distance between two [Offset]s (Compose's Offset has no built-in helper). */
@Suppress("unused")
private fun Offset.distanceTo(other: Offset): Float = kotlin.math.hypot(x - other.x, y - other.y)

/**
 * Unified tap + drag gesture for the editor overlay (normal mode). A single pointer handler owns
 * ALL touches on the full-screen Box (no competing detectTapGestures — two handlers fighting over
 * the same events made drags inside a frame stop working):
 *
 * - Press INSIDE a dashed frame → that screen is selected immediately; if the finger then moves,
 *   it PANS the frame. Pinch-zoom was REMOVED (v1.20.4, user-pinned): the ONLY way to resize a
 *   frame is the corner handle in resize mode. Any additional fingers beyond the first are ignored
 *   for movement so a two-finger touch simply drags with the dominant (first) finger.
 * - Press OUTSIDE every frame → nothing happens (no selection change, no move).
 */
private suspend fun PointerInputScope.dragInsideFrame(
    topRectLatest: State<Rect>,
    bottomRectLatest: State<Rect>,
    fullPosLatest: State<Rect?>,
    selectedScreen: MutableState<ScreenLayoutManager.ScreenId>,
    onTransform: (ScreenLayoutManager.ScreenId, Float, Float) -> Unit,
) {
    awaitPointerEventScope {
        // Outer loop: after each gesture (or an ignored outside press) keep listening for the
        // next finger-down instead of completing and depending on pointerInput restart.
        while (true) {
            // Compose 1.6 has no awaitFirstDown — wait for the first finger-down manually.
            // Skip already-consumed downs: those belong to children (toolbox tiles, bottom
            // bar buttons) and must not select or drag the frame underneath them.
            var press: Offset? = null
            while (press == null) {
                val ev = awaitPointerEvent()
                for (c in ev.changes) {
                    if (c.changedToDown() && !c.isConsumed) {
                        press = c.position
                        break
                    }
                }
            }
            val pressPos = press
            val fp = fullPosLatest.value
            // Frame rects are in root coords; the press is local to this full-screen Box, so
            // shift them into the same space before the hit test.
            val target =
                if (fp == null) {
                    null
                } else {
                    val topLocal = topRectLatest.value.translate(-fp.left, -fp.top)
                    val bottomLocal = bottomRectLatest.value.translate(-fp.left, -fp.top)
                    when {
                        topLocal.contains(pressPos) -> ScreenLayoutManager.ScreenId.TOP
                        bottomLocal.contains(pressPos) -> ScreenLayoutManager.ScreenId.BOTTOM
                        else -> null // outside every frame: ignore this gesture
                    }
                }
            if (target == null) continue
            selectedScreen.value = target

            // Track ONLY the dominant (first) finger for panning. No zoom is computed.
            var dragId: PointerId? = null
            var lastX = pressPos.x
            var lastY = pressPos.y
            while (true) {
                val event = awaitPointerEvent()
                // Lock the drag to whichever finger is the first still-pressed one; later fingers
                // (a pinch attempt) are ignored entirely, so a two-finger touch pans as one finger.
                val drag =
                    if (dragId == null) {
                        event.changes.firstOrNull { it.pressed }?.also {
                            dragId = it.id
                            lastX = it.position.x
                            lastY = it.position.y
                        }
                    } else {
                        event.changes.firstOrNull { it.id == dragId }
                    }
                if (drag == null || !drag.pressed) break // dominant finger lifted → end gesture
                onTransform(target, drag.position.x - lastX, drag.position.y - lastY)
                lastX = drag.position.x
                lastY = drag.position.y
            }
        }
    }
}

/**
 * Resize-mode gesture: dragging the 50dp handle at the selected frame's bottom-right scales that
 * frame PROPORTIONALLY with its TOP-LEFT corner pinned. A second finger holding the frame body
 * pans the SAME frame while the resize finger keeps scaling — the two contributions are additive:
 * offset = frozen base + resize delta + pan delta.
 *
 * Roles: at most one resize finger (must land on the handle) and one pan finger (any frame body).
 * A frame-body press with no resize running behaves like normal mode: select + pan. Presses
 * outside every frame are ignored.
 *
 * Sensitivity: everything is measured against a base FROZEN at first press (t0, hw0, hh0, baseW1)
 * — newScale = t0.scale · (d/ref), 1:1 with finger travel, no per-event compounding. Clamped
 * (v1.20.4, user-pinned): BOTH the effective width and height are capped so the frame never
 * exceeds the DEVICE screen, and the final offset is clamped so the whole frame stays inside the
 * device bounds. When the resize finger lands after a pan started, the existing frozen base is
 * REUSED (no re-freeze) so the scale continues seamlessly.
 */
private suspend fun PointerInputScope.dragResizeHandle(
    selectedScreen: MutableState<ScreenLayoutManager.ScreenId>,
    naturalTopLatest: State<Rect>,
    naturalBottomLatest: State<Rect>,
    fullPosLatest: State<Rect?>,
    layoutStateLatest: State<ScreenLayoutManager.ScreenLayoutState>,
    isLandscape: Boolean,
    handleSizePx: Float,
    onResize: (ScreenLayoutManager.ScreenId, Float, Float, Float) -> Unit,
) {
    val dominantX = !isLandscape
    awaitPointerEventScope {
        while (true) {
            var resizeId: PointerId? = null
            var panId: PointerId? = null
            var target: ScreenLayoutManager.ScreenId? = null
            var t0: ScreenLayoutManager.ScreenTransform? = null
            var hw0 = 0f
            var hh0 = 0f
            var baseW1 = 0f
            var cx0 = 0f
            var cy0 = 0f
            var natCx0 = 0f
            var natCy0 = 0f
            var gapX0 = 0f
            var gapY0 = 0f
            var kApplied = 1f
            var tlMainAxis = 0f
            var ref = 1f
            var panDX = 0f
            var panDY = 0f
            var panLastX = 0f
            var panLastY = 0f

            var running = true
            while (running) {
                val event = awaitPointerEvent()
                val fp = fullPosLatest.value ?: break
                val layout = layoutStateLatest.value
                // Hit-test frames are the CLAMPED, visible ones (same deviceBounds the dashed
                // frames are drawn with), so handle taps land where the frame actually is.
                val topLocal = applyScreenLayoutTransform(
                    naturalTopLatest.value, layout.topScreen, gapSign = -1f, isLandscape, fp,
                ).translate(-fp.left, -fp.top)
                val bottomLocal = applyScreenLayoutTransform(
                    naturalBottomLatest.value, layout.bottomScreen, gapSign = +1f, isLandscape, fp,
                ).translate(-fp.left, -fp.top)

                // --- assign new presses to roles ---
                for (c in event.changes) {
                    if (!c.changedToDown() || c.isConsumed) continue
                    val p = c.position
                    val selFrame =
                        if (selectedScreen.value == ScreenLayoutManager.ScreenId.TOP) topLocal else bottomLocal
                    val onHandle =
                        p.x >= selFrame.right - handleSizePx && p.x <= selFrame.right &&
                            p.y >= selFrame.bottom - handleSizePx && p.y <= selFrame.bottom
                    if (resizeId == null && onHandle) {
                        resizeId = c.id
                        // (Re-)freeze the base from the LIVE, CLAMPED (visible) frame on EVERY
                        // handle press, so k=1 always means "current visible size" (a fresh
                        // press after a pan+resize must not jump back to the original).
                        val s = selectedScreen.value
                        val nat =
                            if (s == ScreenLayoutManager.ScreenId.TOP) naturalTopLatest.value else naturalBottomLatest.value
                        val t = layout.transformOf(s)
                        // selFrame is the clamped visible rect in LOCAL coords; centre it back
                        // to root coords for the freeze math.
                        val selRoot = selFrame.translate(fp.left, fp.top)
                        val w = selRoot.width / 2f
                        val h = selRoot.height / 2f
                        if (t.scale <= 0f || w <= 0f || h <= 0f) { resizeId = null; continue }
                        target = s
                        t0 = t; hw0 = w; hh0 = h
                        // Unclamped natural half width per uniform scale — used to invert the
                        // desired visible width back into a stored scale value.
                        baseW1 = nat.width * t.scaleX / 2f
                        cx0 = selRoot.center.x
                        cy0 = selRoot.center.y
                        natCx0 = nat.center.x
                        natCy0 = nat.center.y
                        gapX0 = if (isLandscape) t.gap * (if (s == ScreenLayoutManager.ScreenId.TOP) -1f else 1f) else 0f
                        gapY0 = if (isLandscape) 0f else t.gap * (if (s == ScreenLayoutManager.ScreenId.TOP) -1f else 1f)
                        kApplied = 1f; panDX = 0f; panDY = 0f
                        // ref/TL measured live from the press, so the first move of a still finger
                        // gives k = 1 (no jump).
                        tlMainAxis = if (dominantX) selFrame.left else selFrame.top
                        ref = ((if (dominantX) p.x else p.y) - tlMainAxis).coerceAtLeast(1f)
                    } else if (panId == null && (topLocal.contains(p) || bottomLocal.contains(p))) {
                        panId = c.id
                        panLastX = p.x
                        panLastY = p.y
                        if (t0 == null) {
                            // Standalone pan: select the pressed frame and freeze its VISIBLE
                            // (clamped) rect as the base.
                            val s =
                                if (topLocal.contains(p)) ScreenLayoutManager.ScreenId.TOP else ScreenLayoutManager.ScreenId.BOTTOM
                            selectedScreen.value = s
                            val nat =
                                if (s == ScreenLayoutManager.ScreenId.TOP) naturalTopLatest.value else naturalBottomLatest.value
                            val t = layout.transformOf(s)
                            val frameLocal = if (s == ScreenLayoutManager.ScreenId.TOP) topLocal else bottomLocal
                            val rootRect = frameLocal.translate(fp.left, fp.top)
                            val w = rootRect.width / 2f
                            val h = rootRect.height / 2f
                            if (t.scale <= 0f || w <= 0f || h <= 0f) { panId = null; continue }
                            target = s
                            t0 = t; hw0 = w; hh0 = h
                            baseW1 = nat.width * t.scaleX / 2f
                            cx0 = rootRect.center.x
                            cy0 = rootRect.center.y
                            natCx0 = nat.center.x
                            natCy0 = nat.center.y
                            gapX0 = if (isLandscape) t.gap * (if (s == ScreenLayoutManager.ScreenId.TOP) -1f else 1f) else 0f
                            gapY0 = if (isLandscape) 0f else t.gap * (if (s == ScreenLayoutManager.ScreenId.TOP) -1f else 1f)
                            kApplied = 1f; panDX = 0f; panDY = 0f
                        }
                        // If a resize base already exists, the pan just adds to it (same target).
                    }
                }

                // --- apply per-role movement ---
                val rr = resizeId?.let { id -> event.changes.firstOrNull { it.id == id } }
                if (rr != null) {
                    if (rr.pressed) {
                        val d = (if (dominantX) rr.position.x else rr.position.y) - tlMainAxis
                        if (d > 0f) kApplied = (d / ref).coerceIn(0.05f, 20f)
                    } else {
                        resizeId = null // lift keeps kApplied frozen — frame stays scaled
                    }
                }
                val pp = panId?.let { id -> event.changes.firstOrNull { it.id == id } }
                if (pp != null) {
                    if (pp.pressed) {
                        panDX += pp.position.x - panLastX
                        panDY += pp.position.y - panLastY
                        panLastX = pp.position.x
                        panLastY = pp.position.y
                    } else {
                        panId = null // lift keeps accumulated panDX/panDY
                    }
                }
                if (resizeId == null && panId == null) { running = false; break }

                // --- ONE additive emit: frozen VISIBLE base + resize delta + pan delta ---
                val t = t0
                val s = target
                if (t != null && s != null) {
                    // k is 1:1 with finger travel from the frozen visible size, capped so the
                    // frame never exceeds the DEVICE screen in either axis (v1.20.4).
                    val kMax = minOf(
                        if (hw0 > 0f) fp.width / (2f * hw0) else Float.MAX_VALUE,
                        if (hh0 > 0f) fp.height / (2f * hh0) else Float.MAX_VALUE,
                    )
                    val kNew = kApplied.coerceAtMost(kMax).coerceAtLeast(0.01f)
                    val hwN = hw0 * kNew
                    val hhN = hh0 * kNew
                    // TL stays pinned while resizing; accumulated pan shifts the whole frame.
                    var c1x = (cx0 - hw0) + hwN + panDX
                    var c1y = (cy0 - hh0) + hhN + panDY
                    // The whole frame must stay inside the device rect.
                    c1x = c1x.coerceIn(fp.left + hwN, fp.right - hwN)
                    c1y = c1y.coerceIn(fp.top + hhN, fp.bottom - hhN)
                    // Invert the desired visible size/position back into stored values:
                    // visible half = base × scale (scaleX/scaleY are untouched here), and
                    // center = natural center + offset + gap.
                    val newScale =
                        if (baseW1 > 0f) (hwN / baseW1).coerceIn(ScreenLayoutManager.MIN_SCALE, ScreenLayoutManager.MAX_SCALE)
                        else t.scale
                    onResize(
                        s,
                        c1x - natCx0 - gapX0,
                        c1y - natCy0 - gapY0,
                        newScale,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen editor overlay for the NDS dual-screen layout customizer.
 * Shows a dashed frame per screen; tap a frame to select it, drag to move. Resizing happens ONLY
 * through the corner handle in resize mode (pinch-zoom was removed in v1.20.4).
 * The bottom bar carries 菜单(槽位子菜单) / 重设回默认 / 工具箱开关 / 调整屏幕大小(等比缩放模式).
 */
@Composable
private fun ScreenLayoutEditorOverlay(
    viewModel: BaseGameScreenViewModel,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
    fullPos: Rect?,
    viewPos: Rect?,
    screenWidthPx: Float,
    screenHeightPx: Float,
) {
    val selectedScreen = remember { mutableStateOf(ScreenLayoutManager.ScreenId.TOP) }
    // The toolbox starts HIDDEN when the editor opens; the "打开工具箱" item in the bottom
    // bar shows it, and "关闭工具箱" hides it again (design §7.5).
    val toolboxVisible = remember { mutableStateOf(false) }
    // Proportional-resize mode: toggled by the bottom-bar "调整屏幕大小/返回" item. When on, a
    // 50dp handle appears at the selected frame's BOTTOM-RIGHT corner; dragging it scales the
    // frame proportionally with its TOP-LEFT corner pinned (user-pinned anchor).
    val resizeMode = remember { mutableStateOf(false) }
    // The "菜单" sub-menu (save/load slots + return to game menu) is opened from the bottom bar.
    val menuOpen = remember { mutableStateOf(false) }

    if (fullPos != null && viewPos != null) {
        // v1.20.8: the editor's dashed frames, gap axis, toolbox grid and zoom steps ALL follow
        // the MANUAL layout mode (layoutState.layoutOrientation) — not how the phone is held.
        // The dashed frames are the WYSIWYG preview of the runtime split-viewport, so both must
        // anchor on the same orientation or the frames would lie while editing in the other mode.
        val isLandscape =
            layoutState.layoutOrientation == ScreenLayoutManager.Orientation.LANDSCAPE

        // Natural rects are needed both for display and for the align/center tools.
        val (naturalTop, naturalBottom) = computeNaturalScreenRects(viewPos, isLandscape)

        // Dashed frames are drawn from the same natural-rect math as the zoom semantics:
        // scale=1.0 = the anchor-fit default (full phone width in portrait / full height
        // side-by-side in landscape); 1x..7x map to native-resolution multiples via
        // nativeResolutionScale(). The game picture is hidden while editing, so the frames
        // are the single source of truth — no letterbox/aspect-fit indirection that could drift.
        val topRect = applyScreenLayoutTransform(naturalTop, layoutState.topScreen, gapSign = -1f, isLandscape, fullPos)
        val bottomRect = applyScreenLayoutTransform(naturalBottom, layoutState.bottomScreen, gapSign = +1f, isLandscape, fullPos)

        // Stable references for the pointer handlers: keying pointerInput on rects/layoutState
        // (which change every frame while dragging) restarts the gesture detector on each
        // recomposition and makes drags stutter one-frame-at-a-time. Instead the blocks are
        // registered once and always read the latest values through these state holders.
        val topRectLatest = rememberUpdatedState(topRect)
        val bottomRectLatest = rememberUpdatedState(bottomRect)
        val fullPosLatest = rememberUpdatedState(fullPos)
        val layoutStateLatest = rememberUpdatedState(layoutState)
        val viewPosLatest = rememberUpdatedState(viewPos)
        // The natural rects are needed by the proportional-resize handler; read live through state.
        val naturalTopLatest = rememberUpdatedState(naturalTop)
        val naturalBottomLatest = rememberUpdatedState(naturalBottom)

        // Align/center tools need geometry, so compute the target offset here and push it down.
        // Offsets are ABSOLUTE in the stored space (center = natural center + offset + gap), so
        // the math anchors on the natural center; the half sizes come from the CLAMPED visible
        // rects so aligning matches what the user sees. LEFT/RIGHT now anchor to the PHYSICAL
        // device edges (fullPos) like TOP/BOTTOM_DEVICE — viewPos is narrower in landscape
        // (virtual pads take side space), which made 左/右对齐 look broken (fixed v1.20.4).
        val alignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit = { screen, edge ->
            val isTopScreen = screen == ScreenLayoutManager.ScreenId.TOP
            val visible = if (isTopScreen) topRect else bottomRect
            val natural = if (isTopScreen) naturalTop else naturalBottom
            val transform = layoutState.transformOf(screen)
            val halfW = visible.width / 2f
            val halfH = visible.height / 2f
            val gapSignX = if (isTopScreen) -1f else 1f
            val gapX = if (isLandscape) transform.gap * gapSignX else 0f
            val gapY = if (isLandscape) 0f else transform.gap * gapSignX
            val natCx = natural.center.x
            val natCy = natural.center.y
            val (ox, oy) =
                when (edge) {
                    AlignEdge.TOP -> transform.offsetX to (fullPos.top + halfH - natCy - gapY)
                    AlignEdge.BOTTOM -> transform.offsetX to (viewPos.bottom - halfH - natCy - gapY)
                    AlignEdge.BOTTOM_DEVICE -> transform.offsetX to (fullPos.bottom - halfH - natCy - gapY)
                    AlignEdge.LEFT -> (fullPos.left + halfW - natCx - gapX) to transform.offsetY
                    AlignEdge.RIGHT -> (fullPos.right - halfW - natCx - gapX) to transform.offsetY
                    AlignEdge.CENTER -> 0f to 0f
                }
            viewModel.setScreenLayoutOffset(screen, ox, oy)
        }

        // The resize handle is a 50dp square; compute its pixel size once here (composable scope
        // has LocalDensity) and share it between the gesture hit-test and the drawing below.
        val handlePx = with(LocalDensity.current) { 50.dp.toPx() }

        // Single full-screen Box with ONE pointer handler. The handler is chosen by mode and the
        // block re-runs when [resizeMode] flips (keyed on it) so only one gesture loop is ever live:
        // - normal mode → dragInsideFrame (tap-to-select + single-finger drag; no pinch zoom);
        // - resize mode → dragResizeHandle (drag the corner handle to scale proportionally).
        // A second detectTapGestures here used to compete for the same events and broke drags — removed.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(resizeMode.value, isLandscape) {
                        if (resizeMode.value) {
                            dragResizeHandle(
                                selectedScreen = selectedScreen,
                                naturalTopLatest = naturalTopLatest,
                                naturalBottomLatest = naturalBottomLatest,
                                fullPosLatest = fullPosLatest,
                                layoutStateLatest = layoutStateLatest,
                                isLandscape = isLandscape,
                                handleSizePx = handlePx,
                                onResize = { screen, ox, oy, scale ->
                                    viewModel.updateScreenLayoutTransform(screen, ox, oy, scale)
                                },
                            )
                        } else {
                            dragInsideFrame(
                                topRectLatest = topRectLatest,
                                bottomRectLatest = bottomRectLatest,
                                fullPosLatest = fullPosLatest,
                                selectedScreen = selectedScreen,
                                onTransform = { screen, dx, dy ->
                                    val current = layoutStateLatest.value.transformOf(screen)
                                    // Pan the VISIBLE (clamped) frame and stay inside the device
                                    // rect (v1.20.4): map the finger delta onto the clamped center,
                                    // then convert back to a stored-offset delta so the frame never
                                    // sticks at an edge while its stored offset drifts off-screen.
                                    val fp = fullPosLatest.value
                                    val vis =
                                        if (screen == ScreenLayoutManager.ScreenId.TOP) topRectLatest.value
                                        else bottomRectLatest.value
                                    var ddx = dx
                                    var ddy = dy
                                    if (fp != null) {
                                        val hw = vis.width / 2f
                                        val hh = vis.height / 2f
                                        val c1x = (vis.center.x + dx).coerceIn(fp.left + hw, fp.right - hw)
                                        val c1y = (vis.center.y + dy).coerceIn(fp.top + hh, fp.bottom - hh)
                                        ddx = c1x - vis.center.x
                                        ddy = c1y - vis.center.y
                                    }
                                    viewModel.updateScreenLayoutTransform(
                                        screen,
                                        current.offsetX + ddx,
                                        current.offsetY + ddy,
                                        current.scale,
                                    )
                                },
                            )
                        }
                    },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawScreenFrame(
                    topRect,
                    fullPos,
                    selected = selectedScreen.value == ScreenLayoutManager.ScreenId.TOP,
                    fillColor = NDS_FRAME_FILL_TOP,
                )
                drawScreenFrame(
                    bottomRect,
                    fullPos,
                    selected = selectedScreen.value == ScreenLayoutManager.ScreenId.BOTTOM,
                    fillColor = NDS_FRAME_FILL_BOTTOM,
                )
            }

            // Per-screen enable switch (v1.20.4): a small slide toggle at the TOP-LEFT CORNER of
            // the SELECTED frame. Off = that screen is not rendered and receives no touch (the
            // hidden content can still come back via the screen-position swap). The frame stays
            // selectable while off so the switch can be turned back on.
            run {
                val selRect =
                    if (selectedScreen.value == ScreenLayoutManager.ScreenId.TOP) topRect else bottomRect
                val selEnabled = layoutState.transformOf(selectedScreen.value).enabled
                // Pinned INSIDE the frame's top-right corner (v1.20.5) — never escapes the
                // dashed frame or the device screen, even when the frame hugs an edge.
                val toggleInset = with(LocalDensity.current) { 4.dp.toPx() }
                val toggleW = with(LocalDensity.current) { 44.dp.toPx() }
                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    (selRect.right - fullPos.left - toggleW - toggleInset).toInt(),
                                    (selRect.top - fullPos.top + toggleInset).toInt(),
                                )
                            }
                            .padding(2.dp),
                ) {
                    ScreenEnableToggle(
                        enabled = selEnabled,
                        onToggle = { viewModel.setScreenLayoutEnabled(selectedScreen.value, it) },
                    )
                }
            }

            // Proportional-resize handle: in resize mode a 50dp square sits INSIDE the selected
            // frame's bottom-right corner (inset by its own size), drawn as a DASHED blue outline
            // with NO fill and a ↖↘ double-headed arrow. Dragging it scales the frame with its
            // top-left corner pinned; presses on the frame body elsewhere just switch selection.
            if (resizeMode.value) {
                val selRect =
                    if (selectedScreen.value == ScreenLayoutManager.ScreenId.TOP) topRect else bottomRect
                Canvas(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    (selRect.right - fullPos.left - handlePx).toInt(),
                                    (selRect.bottom - fullPos.top - handlePx).toInt(),
                                )
                            }
                            .size(50.dp),
                ) {
                    val lineColor = Color(0xFF35b5e8)
                    // Dashed rounded square, no fill — matches the editor's dashed-frame language.
                    drawRoundRect(
                        color = lineColor,
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                    // ↖↘ double-headed arrow along the diagonal (heads on BOTH ends).
                    // Short & thick (user v1.20.3): margins 17f keep the diagonal compact,
                    // strokeWidth 5f makes it readable inside the small handle.
                    val m = 17f
                    val c0 = Offset(m, m)
                    val c1 = Offset(size.width - m, size.height - m)
                    drawLine(lineColor, c0, c1, strokeWidth = 5f)
                    val head = 7f
                    // Bottom-right head.
                    drawLine(lineColor, Offset(c1.x - head, c1.y), c1, strokeWidth = 5f)
                    drawLine(lineColor, Offset(c1.x, c1.y - head), c1, strokeWidth = 5f)
                    // Top-left head.
                    drawLine(lineColor, Offset(c0.x + head, c0.y), c0, strokeWidth = 5f)
                    drawLine(lineColor, Offset(c0.x, c0.y + head), c0, strokeWidth = 5f)
                }
            }

            if (toolboxVisible.value) {
                ScreenLayoutEditorToolbox(
                    modifier = Modifier.align(Alignment.Center),
                    viewModel = viewModel,
                    layoutState = layoutState,
                    selectedScreen = selectedScreen.value,
                    onScreenSelected = { selectedScreen.value = it },
                    isLandscape = isLandscape,
                    displayWidthPx = screenWidthPx,
                    displayHeightPx = screenHeightPx,
                    naturalTopWidthPx = naturalTop.width,
                    naturalBottomWidthPx = naturalBottom.width,
                    onAlignToEdge = alignToEdge,
                    onClose = { toolboxVisible.value = false },
                )
            }
            // Bottom bar is ALWAYS visible — the 关闭/打开工具箱 item toggles the panel.
            ScreenLayoutBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                viewModel = viewModel,
                isLandscape = isLandscape,
                toolboxVisible = toolboxVisible.value,
                onToggleToolbox = { toolboxVisible.value = !toolboxVisible.value },
                resizeMode = resizeMode.value,
                onToggleResizeMode = {
                    // Entering resize mode hides the toolbox (the handle replaces it); leaving
                    // restores whatever state the toolbox was in.
                    val next = !resizeMode.value
                    resizeMode.value = next
                    if (next) toolboxVisible.value = false
                },
                onMenu = { menuOpen.value = true },
            )

            // "菜单" sub-menu: save/load the current layout into a slot + return to game menu.
            if (menuOpen.value) {
                ScreenLayoutSubmenu(
                    modifier = Modifier.align(Alignment.BottomStart),
                    viewModel = viewModel,
                    layoutState = layoutState,
                    onDismiss = { menuOpen.value = false },
                    onEditTouchControls = {
                        menuOpen.value = false
                        viewModel.setEditControlsMode(true)
                    },
                    onReturnToGameMenu = {
                        menuOpen.value = false
                        viewModel.toggleEditScreenLayout(false)
                        viewModel.showGameMenu()
                    },
                )
            }
        }
    }
}

/** Top-screen frame fill — design spec: #5D71E4 at 50% opacity. */
private val NDS_FRAME_FILL_TOP = Color(0x805D71E4)

/** Bottom-screen frame fill — design spec: #5DE45D at 50% opacity. */
private val NDS_FRAME_FILL_BOTTOM = Color(0x805DE45D)

/**
 * Draws one dashed editor frame. Line thickness is halved vs the old editor (1.5dp/1dp),
 * and each screen gets its own semi-transparent fill so top/bottom are distinguishable at a
 * glance while editing without the game picture.
 */
private fun DrawScope.drawScreenFrame(
    rect: Rect,
    fullPos: Rect,
    selected: Boolean,
    fillColor: Color,
) {
    val topLeft = Offset(rect.left - fullPos.left, rect.top - fullPos.top)
    val size = Size(rect.width, rect.height)
    drawRect(color = fillColor, topLeft = topLeft, size = size)
    drawRect(
        color = if (selected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f),
        topLeft = topLeft,
        size = size,
        style =
            Stroke(
                width = (if (selected) 1.5.dp else 1.dp).toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f)),
            ),
    )
}

/**
 * Tiny pill slide-switch drawn over a selected editor frame's top-left corner (v1.20.4).
 * ON = the screen renders; OFF = the screen's quad is skipped natively (hidden, untouchable).
 * Custom (not Material3 Switch) so it stays small and its tap is consumed reliably — a child
 * composable with `.clickable` wins the pointer contest over the parent's raw pointerInput loop.
 */
@Composable
private fun ScreenEnableToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp, 22.dp)
                .background(
                    if (enabled) Color(0xFF35b5e8) else Color(0xFF606066),
                    RoundedCornerShape(11.dp),
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(11.dp))
                .clickable { onToggle(!enabled) },
    ) {
        Box(
            modifier =
                Modifier
                    .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .background(Color.White, CircleShape),
        )
    }
}

/**
 * Floating menu button shown when virtual controls are hidden.
 * Simple fixed position at top-right, tap to open game menu.
 * NOTE: the previous full-screen invisible click layer was removed — it swallowed every
 * touch and killed the NDS touchscreen input when virtual controls were hidden.
 */
@Composable
private fun DraggableMenuButton(viewModel: BaseGameScreenViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clickable { viewModel.showGameMenu() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.4f),
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Game Menu",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                )
            }
        }
    }
}

/**
 * "菜单" sub-menu of the layout editor (bottom-bar first item). A dark card listing:
 *  - a read-only "现在布局：横·槽2 / 默认（未保存）" line showing where the current values came from;
 *  - for each slot of the CURRENT orientation: 保存为 槽N (overwrite) and 载入 槽N
 *    (disabled while the slot is empty); the active slot row is highlighted;
 *  - 返回游戏菜单 (close the editor and open the game menu).
 * Layouts are GLOBAL across NDS games and split by orientation (Plan A: 竖/横各 3 槽).
 */
/**
 * The editor's "菜单" sub-menu (v1.20.5). Since v1.20.8 it also hosts the MANUAL layout-mode
 * toggle: orientation is no longer driven by gravity, so the sub-menu derives the mode from
 * [ScreenLayoutManager.ScreenLayoutState.layoutOrientation] and can switch it in place — the
 * dashed frames / toolbox / slots all re-anchor on the new mode instantly, and each mode's
 * unsaved work values are parked and restored when you switch back.
 */
@Composable
private fun ScreenLayoutSubmenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
    onDismiss: () -> Unit,
    onEditTouchControls: () -> Unit,
    onReturnToGameMenu: () -> Unit,
) {
    val orientation = layoutState.layoutOrientation
    val isLandscapeMode = orientation == ScreenLayoutManager.Orientation.LANDSCAPE
    val dir = if (isLandscapeMode) "横" else "竖"
    val otherDir = if (isLandscapeMode) "竖屏" else "横屏"
    val activeLabel = viewModel.currentScreenLayoutSlotLabel()
    Surface(
        // FIXED width, bottom-LEFT aligned (user v1.20.3): in landscape a fillMaxWidth card
        // stretches across the whole screen and every row's text gets pulled apart.
        modifier = modifier.widthIn(min = 300.dp, max = 300.dp).clickable { /* swallow taps so the editor underneath never drags */ },
        color = Color(0xFF252528),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "保存 / 载入布局（${dir}版 · 全局）",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = Color(0xFF35b5e8), fontSize = 12.sp)
                }
            }
            Text(
                text = "现在布局：${activeLabel ?: "默认（未保存）"}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            for (n in 1..ScreenLayoutManager.SLOTS_PER_ORIENTATION) {
                val key = ScreenLayoutManager.slotKey(orientation, n)
                val occupied = layoutState.slots.containsKey(key)
                val active = layoutState.activeSlot == key
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (active) {
                                    Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (occupied) "槽$n ●" else "槽$n",
                        color = if (active) Color(0xFF35b5e8) else Color.White,
                        fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { viewModel.saveScreenLayoutToSlot(orientation, n) }) {
                            Text("保存", color = Color.White, fontSize = 12.sp)
                        }
                        TextButton(
                            enabled = occupied,
                            onClick = { viewModel.loadScreenLayoutFromSlot(orientation, n) },
                        ) {
                            Text(
                                "载入",
                                color = if (occupied) Color.White else Color.White.copy(alpha = 0.35f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            // v1.20.8: MANUAL layout-mode toggle. Switching parks the current mode's unsaved
            // work and restores the other mode's — the two orientations never contaminate each
            // other, and gravity is no longer involved at all.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = {
                        viewModel.switchScreenLayoutOrientation(
                            if (isLandscapeMode) {
                                ScreenLayoutManager.Orientation.PORTRAIT
                            } else {
                                ScreenLayoutManager.Orientation.LANDSCAPE
                            },
                        )
                    },
                ) {
                    Text("切换到${otherDir}布局", color = Color(0xFF35b5e8), fontSize = 13.sp)
                }
            }
            // v1.20.5: the touch-button editor moved from the game menu into this sub-menu.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onEditTouchControls) {
                    Text("编辑触控按键", color = Color(0xFF35b5e8), fontSize = 13.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onReturnToGameMenu) {
                    Text("返回游戏菜单", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
