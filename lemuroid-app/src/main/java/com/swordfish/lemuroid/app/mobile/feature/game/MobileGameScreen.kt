@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import gg.padkit.inputstate.InputState
import timber.log.Timber

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
            // NDS layouts are stored per-orientation (3 slots each): on rotation, auto-load the
            // new orientation's last-used slot if one exists (no-op otherwise).
            viewModel.onScreenLayoutOrientationChanged(
                if (isLandscape) {
                    ScreenLayoutManager.Orientation.LANDSCAPE
                } else {
                    ScreenLayoutManager.Orientation.PORTRAIT
                },
            )
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

        val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
        // Keyed by orientation: a rotation invalidates any previously frozen anchor rect
        val viewportPosition = remember(isLandscape) { mutableStateOf<Rect?>(null) }

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // Hide the game picture while the layout editor is open: the user edits
                        // against the dashed frames only (the frozen frame would drift out of
                        // sync with them). Alpha keeps the view alive so GLRetroView is not
                        // recreated and fullScreenPosition stays valid.
                        .alpha(if (editScreenLayoutShown.value) 0f else 1f)
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
                val applySplitLayout = viewModel.isNdsSystem() && screenLayout != null
                if (applySplitLayout) {
                    val (naturalTop, naturalBottom) = computeNaturalScreenRects(viewPos, isLandscape)
                    val topRect =
                        applyScreenLayoutTransform(naturalTop, screenLayout!!.topScreen, gapSign = -1f, isLandscape)
                    val bottomRect =
                        applyScreenLayoutTransform(naturalBottom, screenLayout.bottomScreen, gapSign = +1f, isLandscape)
                    val topViewport = normalizeToFullScreen(topRect, fullPos)
                    val bottomViewport = normalizeToFullScreen(bottomRect, fullPos)
                    Timber.d("Setting split viewport: top=$topViewport bottom=$bottomViewport")
                    gameView.splitViewport = topViewport to bottomViewport
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

                if (isVisible) {
                    CompositionLocalProvider(LocalLemuroidPadTheme provides LemuroidPadTheme()) {
                        if (!isLandscape) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                            )
                        } else if (!currentControllerConfig.allowTouchOverlay) {
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                            )
                            PadContainer(
                                modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                            )
                        }

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

        // Draggable floating menu button — shown when virtual controls are hidden
        if (!touchControlsVisibleState.value) {
            DraggableMenuButton(viewModel)
        }

        // NDS screen layout editor overlay (works with or without touch controls visible).
        // Composed after DraggableMenuButton so its touches reach the editor first.
        val layoutState = screenLayoutState.value
        if (editScreenLayoutShown.value && layoutState != null) {
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
    CompositionLocalProvider(LocalButtonEdit provides { target -> viewModel.selectEditTarget(target) }) {
        Box(
            modifier = modifier.wrapContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            LemuroidButtonPressFeedback(
                pressed = menuPressed.value,
                animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
                icon = R.drawable.button_menu,
            )
            MenuEditTouchControls(viewModel, controllerConfig, touchControllerSettings)
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)
    val selectedButton = viewModel.getEditingSelection().collectAsState(null)
    if (!showEditControls.value) return

    val allButtons = TouchButtonId.values().toList()

    // Dropdown state
    var expanded = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Button selector — Exposed Dropdown Menu
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = expanded.value,
                onExpandedChange = { expanded.value = !expanded.value },
            ) {
                androidx.compose.material3.TextField(
                    value = selectedButton.value?.label ?: "选择要调节的按键",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false },
                ) {
                    allButtons.forEach { btn ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(btn.label) },
                            onClick = {
                                viewModel.selectEditTarget(btn)
                                expanded.value = false
                            },
                        )
                    }
                }
            }

            if (selectedButton.value != null) {
                val id = selectedButton.value!!
                val bs = touchControllerSettings.getButtonSettings(id)
                val isHidden = touchControllerSettings.isButtonHidden(id)

                // Size slider
                MenuEditTouchControlRow(Icons.Default.OpenInFull, "大小", 0f) {
                    Slider(
                        value = bs.scale,
                        onValueChange = { viewModel.updateButtonScale(id, it) },
                        valueRange = 0.5f..2f,
                    )
                }
                // Offset X
                MenuEditTouchControlRow(Icons.Filled.ArrowBack, "水平", 0f) {
                    Slider(
                        value = bs.offsetX,
                        onValueChange = {
                            viewModel.updateButtonOffset(id, it - bs.offsetX, 0f)
                        },
                        valueRange = -3f..3f,
                    )
                }
                // Offset Y
                MenuEditTouchControlRow(Icons.Default.ArrowDownward, "垂直", 0f) {
                    Slider(
                        value = bs.offsetY,
                        onValueChange = {
                            viewModel.updateButtonOffset(id, 0f, it - bs.offsetY)
                        },
                        valueRange = -3f..3f,
                    )
                }
                // Visibility toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("显示此按键", modifier = Modifier.padding(start = 4.dp))
                    androidx.compose.material3.Switch(
                        checked = !isHidden,
                        onCheckedChange = { viewModel.toggleButtonVisibility(id, !it) },
                    )
                }
                // Reset & Done — ALWAYS show "全部复位"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { viewModel.resetButtonSettings(id) }) { Text("复位此按键") }
                    TextButton(onClick = { viewModel.resetTouchControls() }) { Text("全部复位") }
                    TextButton(onClick = { viewModel.toggleEditControls(false) }) { Text("完成") }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { viewModel.resetTouchControls() }) { Text("全部复位") }
                    TextButton(onClick = { viewModel.toggleEditControls(false) }) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    rotation: Float,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            modifier = Modifier.rotate(rotation),
            imageVector = icon,
            contentDescription = label,
        )
        slider()
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
 */
private fun applyScreenLayoutTransform(
    base: Rect,
    transform: ScreenLayoutManager.ScreenTransform,
    gapSign: Float = 0f,
    isLandscape: Boolean = false,
): Rect {
    val centerX = (base.left + base.right) / 2f
    val centerY = (base.top + base.bottom) / 2f
    // Effective width = uniform scale × horizontal (width-axis) scale.
    val halfWidth = (base.right - base.left) * transform.scale * transform.scaleX / 2f
    // Effective height = uniform scale × vertical (height-axis) scale.
    val halfHeight = (base.bottom - base.top) * transform.scale * transform.scaleY / 2f
    return if (isLandscape) {
        val gapOffsetX = transform.gap * gapSign
        Rect(
            left = centerX - halfWidth + transform.offsetX + gapOffsetX,
            top = centerY - halfHeight + transform.offsetY,
            right = centerX + halfWidth + transform.offsetX + gapOffsetX,
            bottom = centerY + halfHeight + transform.offsetY,
        )
    } else {
        val gapOffsetY = transform.gap * gapSign
        Rect(
            left = centerX - halfWidth + transform.offsetX,
            top = centerY - halfHeight + transform.offsetY + gapOffsetY,
            right = centerX + halfWidth + transform.offsetX,
            bottom = centerY + halfHeight + transform.offsetY + gapOffsetY,
        )
    }
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
private fun Offset.distanceTo(other: Offset): Float = kotlin.math.hypot(x - other.x, y - other.y)

/**
 * Unified tap + drag/zoom gesture for the editor overlay. A single pointer handler owns ALL
 * touches on the full-screen Box (no competing detectTapGestures — two handlers fighting over
 * the same events made drags inside a frame stop working):
 *
 * - Press INSIDE a dashed frame → that screen is selected immediately; if the finger then
 *   moves, a single finger pans it and two fingers pinch-zoom around the midpoint.
 * - Press OUTSIDE every frame → nothing happens (no selection change, no move).
 */
private suspend fun PointerInputScope.dragInsideFrame(
    topRectLatest: State<Rect>,
    bottomRectLatest: State<Rect>,
    fullPosLatest: State<Rect?>,
    selectedScreen: MutableState<ScreenLayoutManager.ScreenId>,
    onTransform: (ScreenLayoutManager.ScreenId, Float, Float, Float) -> Unit,
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

            var lastX = pressPos.x
            var lastY = pressPos.y
            while (true) {
                val event = awaitPointerEvent()
                if (!event.changes.any { it.pressed }) break // all fingers lifted → end gesture
                val zoomChange =
                    if (event.changes.size >= 2) {
                        val prev = event.changes[0].previousPosition.distanceTo(event.changes[1].previousPosition)
                        val now = event.changes[0].position.distanceTo(event.changes[1].position)
                        if (prev > 0f) now / prev else 1f
                    } else {
                        1f
                    }
                val midX = event.changes.first().position.x +
                    (if (event.changes.size >= 2) (event.changes[1].position.x - event.changes[0].position.x) / 2f else 0f)
                val midY = event.changes.first().position.y +
                    (if (event.changes.size >= 2) (event.changes[1].position.y - event.changes[0].position.y) / 2f else 0f)
                onTransform(target, midX - lastX, midY - lastY, zoomChange)
                lastX = midX
                lastY = midY
            }
        }
    }
}

/**
 * Proportional-resize gesture (resize mode). Pressing INSIDE a frame selects that screen, then
 * dragging scales it PROPORTIONALLY with its TOP-LEFT corner pinned (user-pinned anchor): the
 * pointer's distance from the pinned top-left along the DOMINANT axis sets the new size, so both
 * width and height grow/shrink together. Pressing outside every frame does nothing.
 *
 * Math (Box-local space): the frame's current top-left corner TL is read from the LIVE layout
 * transform (it already includes offset + gap), and (hw, hh) are its effective half-size =
 * natural · scale · scaleX(Y) / 2. A press at distance d0 from TL on the dominant axis
 * (x in portrait, y in landscape) is the per-gesture reference; each move to distance d gives
 * k = d/d0 (so the first move event with a still finger is k = 1, i.e. no jump). newScale =
 * scale·k. The offsets are then shifted by hw·(k−1) / hh·(k−1) so TL never moves while the
 * width and height scale together.
 */
private suspend fun PointerInputScope.dragResizeHandle(
    selectedScreen: MutableState<ScreenLayoutManager.ScreenId>,
    naturalTopLatest: State<Rect>,
    naturalBottomLatest: State<Rect>,
    fullPosLatest: State<Rect?>,
    layoutStateLatest: State<ScreenLayoutManager.ScreenLayoutState>,
    isLandscape: Boolean,
    onResize: (ScreenLayoutManager.ScreenId, Float, Float, Float) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
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
            val fp = fullPosLatest.value ?: continue
            val layout = layoutStateLatest.value
            // Hit test uses the CURRENT (transformed) frame rects, in Box-local space.
            val topRectLocal =
                applyScreenLayoutTransform(naturalTopLatest.value, layout.topScreen, gapSign = -1f, isLandscape)
                    .translate(-fp.left, -fp.top)
            val bottomRectLocal =
                applyScreenLayoutTransform(
                    naturalBottomLatest.value,
                    layout.bottomScreen,
                    gapSign = +1f,
                    isLandscape,
                ).translate(-fp.left, -fp.top)
            val target =
                when {
                    topRectLocal.contains(pressPos) -> ScreenLayoutManager.ScreenId.TOP
                    bottomRectLocal.contains(pressPos) -> ScreenLayoutManager.ScreenId.BOTTOM
                    else -> null // outside every frame: ignore this gesture
                }
            if (target == null) continue
            selectedScreen.value = target

            // Pinned anchor = the selected frame's current top-left corner (includes offset/gap).
            val natural =
                if (target == ScreenLayoutManager.ScreenId.TOP) naturalTopLatest.value else naturalBottomLatest.value
            val frame = if (target == ScreenLayoutManager.ScreenId.TOP) topRectLocal else bottomRectLocal
            val tlx = frame.left
            val tly = frame.top
            // Dominant axis: width in portrait, height in landscape.
            val dominantX = !isLandscape
            val tlAxis = if (dominantX) tlx else tly
            // Reference distance on the dominant axis from the pinned top-left to the press.
            val ref =
                if (dominantX) pressPos.x - tlAxis else pressPos.y - tlAxis
            if (ref <= 0f) continue // press landed exactly on the pinned edge: nothing to measure

            while (true) {
                val event = awaitPointerEvent()
                if (!event.changes.any { it.pressed }) break // all fingers lifted → end gesture
                val pos = event.changes.first().position
                val d = if (dominantX) pos.x - tlAxis else pos.y - tlAxis
                if (d <= 0f) continue
                // Re-read the live transform: a previous event in this gesture already updated it.
                val t = layoutStateLatest.value.transformOf(target)
                val hw = natural.width * t.scale * t.scaleX / 2f
                val hh = natural.height * t.scale * t.scaleY / 2f
                if (hw <= 0f || hh <= 0f) break
                val k = (d / ref).coerceIn(0.1f, 10f)
                val newScale = (t.scale * k).coerceIn(ScreenLayoutManager.MIN_SCALE, ScreenLayoutManager.MAX_SCALE)
                // Apply the ACTUAL factor (clamping may have reduced k) and shift the offsets by
                // hw·(k−1) / hh·(k−1) so the top-left corner of the frame never moves.
                val kApplied = newScale / t.scale
                onResize(
                    target,
                    t.offsetX + hw * (kApplied - 1f),
                    t.offsetY + hh * (kApplied - 1f),
                    newScale,
                )
            }
        }
    }
}

/**
 * Full-screen editor overlay for the NDS dual-screen layout customizer.
 * Shows a dashed frame per screen; tap a frame to select it, drag to move, pinch to zoom.
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
        val isLandscape = screenWidthPx > screenHeightPx

        // Natural rects are needed both for display and for the align/center tools.
        val (naturalTop, naturalBottom) = computeNaturalScreenRects(viewPos, isLandscape)

        // Dashed frames are drawn from the same natural-rect math as the zoom semantics:
        // scale=1.0 = the anchor-fit default (full phone width in portrait / full height
        // side-by-side in landscape); 1x..7x map to native-resolution multiples via
        // nativeResolutionScale(). The game picture is hidden while editing, so the frames
        // are the single source of truth — no letterbox/aspect-fit indirection that could drift.
        val topRect = applyScreenLayoutTransform(naturalTop, layoutState.topScreen, gapSign = -1f, isLandscape)
        val bottomRect = applyScreenLayoutTransform(naturalBottom, layoutState.bottomScreen, gapSign = +1f, isLandscape)

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
        val alignToEdge: (ScreenLayoutManager.ScreenId, AlignEdge) -> Unit = { screen, edge ->
            val natural = if (screen == ScreenLayoutManager.ScreenId.TOP) naturalTop else naturalBottom
            val transform = layoutState.transformOf(screen)
            val halfW = natural.width * transform.scale * transform.scaleX / 2f
            val halfH = natural.height * transform.scale * transform.scaleY / 2f
            val cx = natural.center.x
            val cy = natural.center.y
            val (ox, oy) =
                when (edge) {
                    // TOP / BOTTOM_DEVICE anchor to the PHYSICAL device screen edges
                    // (fullPos.top / fullPos.bottom) — the 上对齐/下对齐 arrow buttons.
                    // LEFT/RIGHT still anchor to the game-view rect (== device width anyway).
                    AlignEdge.TOP -> transform.offsetX to (fullPos.top - (cy - halfH))
                    AlignEdge.BOTTOM -> transform.offsetX to (viewPos.bottom - (cy + halfH))
                    AlignEdge.BOTTOM_DEVICE -> transform.offsetX to (fullPos.bottom - (cy + halfH))
                    AlignEdge.LEFT -> (viewPos.left - (cx - halfW)) to transform.offsetY
                    AlignEdge.RIGHT -> (viewPos.right - (cx + halfW)) to transform.offsetY
                    AlignEdge.CENTER -> 0f to 0f
                }
            viewModel.setScreenLayoutOffset(screen, ox, oy)
        }

        // Single full-screen Box with ONE pointer handler. The handler is chosen by mode and the
        // block re-runs when [resizeMode] flips (keyed on it) so only one gesture loop is ever live:
        // - normal mode → dragInsideFrame (tap-to-select + drag/pinch);
        // - resize mode → dragResizeHandle (drag the corner handle to scale proportionally).
        // A second detectTapGestures here used to compete for the same events and broke drags — removed.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(resizeMode.value) {
                        if (resizeMode.value) {
                            dragResizeHandle(
                                selectedScreen = selectedScreen,
                                naturalTopLatest = naturalTopLatest,
                                naturalBottomLatest = naturalBottomLatest,
                                fullPosLatest = fullPosLatest,
                                layoutStateLatest = layoutStateLatest,
                                isLandscape = isLandscape,
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
                                onTransform = { screen, dx, dy, zoom ->
                                    val current = layoutStateLatest.value.transformOf(screen)
                                    viewModel.updateScreenLayoutTransform(
                                        screen,
                                        current.offsetX + dx,
                                        current.offsetY + dy,
                                        (current.scale * zoom).coerceIn(
                                            ScreenLayoutManager.MIN_SCALE,
                                            ScreenLayoutManager.MAX_SCALE,
                                        ),
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

            // Proportional-resize handle: in resize mode it sits at the SELECTED frame's
            // bottom-right corner (50dp square, ↘ arrow). Dragging it scales the frame with its
            // top-left corner pinned. Tapping a frame selects it; tap again to drag the handle.
            if (resizeMode.value) {
                val selRect =
                    if (selectedScreen.value == ScreenLayoutManager.ScreenId.TOP) topRect else bottomRect
                Box(
                    modifier = Modifier
                        .offset(
                            IntOffset(
                                (selRect.right - fullPos.left).toInt(),
                                (selRect.bottom - fullPos.top).toInt(),
                            ),
                        )
                        .size(50.dp)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                        .border(2.dp, Color(0xFF35b5e8), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // ↘ double-arrow drawn with Canvas (Icons.Default.SouthEast is not in core icons).
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val p = 4f.toPx()
                        val corner = size.minDimension - p
                        val lineColor = Color.Black
                        drawLine(lineColor, Offset(p, p), Offset(corner, corner), strokeWidth = 3f.toPx())
                        // Arrowhead at the bottom-right tip: two short strokes back toward center.
                        val head = 9f.toPx()
                        drawLine(lineColor, Offset(corner - head, corner), Offset(corner, corner), strokeWidth = 3f.toPx())
                        drawLine(lineColor, Offset(corner, corner - head), Offset(corner, corner), strokeWidth = 3f.toPx())
                    }
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
                    modifier = Modifier.align(Alignment.BottomCenter),
                    viewModel = viewModel,
                    layoutState = layoutState,
                    isLandscape = isLandscape,
                    onDismiss = { menuOpen.value = false },
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
@Composable
private fun ScreenLayoutSubmenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
    layoutState: ScreenLayoutManager.ScreenLayoutState,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    onReturnToGameMenu: () -> Unit,
) {
    val orientation =
        if (isLandscape) ScreenLayoutManager.Orientation.LANDSCAPE else ScreenLayoutManager.Orientation.PORTRAIT
    val dir = if (isLandscape) "横" else "竖"
    val activeLabel = viewModel.currentScreenLayoutSlotLabel()
    Surface(
        modifier = modifier.fillMaxWidth().clickable { /* swallow taps so the editor underneath never drags */ },
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
                    text = "保存 / 载入布局（$dir版 · 全局）",
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onReturnToGameMenu) {
                    Text("返回游戏菜单", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
