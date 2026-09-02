@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
                // sits on its natural rect (256×192 device px at 1x), centered and stacked on the
                // anchor. There is no aspect-fit single-viewport fallback for NDS anymore, which
                // is what made the editor frames drift from the actual runtime size.
                val applySplitLayout = viewModel.isNdsSystem() && screenLayout != null
                if (applySplitLayout) {
                    val (naturalTop, naturalBottom) = computeNaturalScreenRects(viewPos)
                    val topRect = applyScreenLayoutTransform(naturalTop, screenLayout!!.topScreen, gapSign = -1f)
                    val bottomRect = applyScreenLayoutTransform(naturalBottom, screenLayout.bottomScreen, gapSign = +1f)
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

/** NDS single-screen resolution in logical pixels. */
private const val NDS_SCREEN_WIDTH = 256f
private const val NDS_SCREEN_HEIGHT = 192f

/**
 * Computes the maximum uniform [ScreenLayoutManager.ScreenTransform.scale] that keeps the selected
 * screen fully inside the phone display.
 *
 * The editor hides the game picture and lets screens be placed anywhere on the display, so the cap
 * is the full display size (not the letterboxed anchor). The effective on-screen size is
 * base(256×192 device px) × scale × scaleX/scaleY, so the cap depends on the current independent
 * axis scales. A screen stays on-screen when BOTH its width and height fit, hence the min() of
 * the two per-axis limits.
 *
 * @param displayWidthPx full display width in physical px
 * @param displayHeightPx full display height in physical px
 * @param scaleX current horizontal (width-axis) scale of the screen
 * @param scaleY current vertical (height-axis) scale of the screen
 */
internal fun maxOnScreenScale(
    displayWidthPx: Float,
    displayHeightPx: Float,
    scaleX: Float,
    scaleY: Float,
): Float {
    val baseWidth = NDS_SCREEN_WIDTH
    val baseHeight = NDS_SCREEN_HEIGHT
    val maxByWidth = if (scaleX > 0f) displayWidthPx / (baseWidth * scaleX) else Float.MAX_VALUE
    val maxByHeight = if (scaleY > 0f) displayHeightPx / (baseHeight * scaleY) else Float.MAX_VALUE
    // Allow shrinking below 1x on very small displays; never exceed MAX_SCALE.
    return minOf(maxByWidth, maxByHeight).coerceAtMost(ScreenLayoutManager.MAX_SCALE)
}

/**
 * Computes the natural (untouched) rects of the top and bottom screens.
 *
 * The base size is the NDS original resolution in DEVICE pixels: 256×192 px on screen at 1x,
 * one emulator pixel per device pixel. Each screen is centered horizontally on the anchor and
 * stacked vertically around the anchor's vertical center, flush against each other. The zoom
 * panel's 1x..7x then multiplies this base size (see [applyScreenLayoutTransform]).
 *
 * NOTE: no density scaling — that was the bug that made "1x" render larger than the NDS
 * original resolution and drift the editor frames away from the runtime picture.
 */
private fun computeNaturalScreenRects(anchor: Rect): Pair<Rect, Rect> {
    val screenWidth = NDS_SCREEN_WIDTH
    val screenHeight = NDS_SCREEN_HEIGHT
    val centerX = (anchor.left + anchor.right) / 2f
    val centerY = (anchor.top + anchor.bottom) / 2f
    val left = centerX - screenWidth / 2f
    val right = centerX + screenWidth / 2f
    val top =
        Rect(
            left = left,
            top = centerY - screenHeight,
            right = right,
            bottom = centerY,
        )
    val bottom =
        Rect(
            left = left,
            top = centerY,
            right = right,
            bottom = centerY + screenHeight,
        )
    return top to bottom
}

/** Applies a per-screen transform (scale around own center + pixel translation + vertical gap). */
private fun applyScreenLayoutTransform(
    base: Rect,
    transform: ScreenLayoutManager.ScreenTransform,
    gapSign: Float = 0f,
): Rect {
    val centerX = (base.left + base.right) / 2f
    val centerY = (base.top + base.bottom) / 2f
    // Effective width = uniform scale × horizontal (width-axis) scale.
    val halfWidth = (base.right - base.left) * transform.scale * transform.scaleX / 2f
    // Effective height = uniform scale × vertical (height-axis) scale.
    val halfHeight = (base.bottom - base.top) * transform.scale * transform.scaleY / 2f
    // gap pushes the screen along the vertical stack axis: top screen gapSign=-1 (up),
    // bottom screen gapSign=+1 (down). This increases the spacing between the two screens.
    val gapOffsetY = transform.gap * gapSign
    return Rect(
        left = centerX - halfWidth + transform.offsetX,
        top = centerY - halfHeight + transform.offsetY + gapOffsetY,
        right = centerX + halfWidth + transform.offsetX,
        bottom = centerY + halfHeight + transform.offsetY + gapOffsetY,
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

/**
 * Full-screen editor overlay for the NDS dual-screen layout customizer.
 * Shows a dashed frame per screen; tap a frame to select it, drag to move, pinch to zoom.
 * The bottom card mirrors the selection and offers sliders plus profile management.
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
    // The floating toolbox is visible as soon as the editor opens; only "关闭工具箱"
    // in the bottom bar hides it (design §7.5). No separate open button exists.
    val toolboxVisible = remember { mutableStateOf(true) }

    if (fullPos != null && viewPos != null) {
        // Natural rects are needed both for display and for the align/center tools.
        val (naturalTop, naturalBottom) = computeNaturalScreenRects(viewPos)

        // Dashed frames are drawn from the same natural-rect math as the zoom semantics:
        // 1x = one NDS screen at its original resolution (256×192 logical px), centered and
        // stacked on the anchor. The game picture is hidden while editing, so the frames are
        // the single source of truth — no letterbox/aspect-fit indirection that could drift.
        val topRect = applyScreenLayoutTransform(naturalTop, layoutState.topScreen, gapSign = -1f)
        val bottomRect = applyScreenLayoutTransform(naturalBottom, layoutState.bottomScreen, gapSign = +1f)

        // Stable references for the pointer handlers: keying pointerInput on rects/layoutState
        // (which change every frame while dragging) restarts the gesture detector on each
        // recomposition and makes drags stutter one-frame-at-a-time. Instead the blocks are
        // registered once and always read the latest values through these state holders.
        val topRectLatest = rememberUpdatedState(topRect)
        val bottomRectLatest = rememberUpdatedState(bottomRect)
        val fullPosLatest = rememberUpdatedState(fullPos)
        val layoutStateLatest = rememberUpdatedState(layoutState)
        val viewPosLatest = rememberUpdatedState(viewPos)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val fp = fullPosLatest.value ?: return@detectTapGestures
                            val topLocal = topRectLatest.value.translate(-fp.left, -fp.top)
                            val bottomLocal = bottomRectLatest.value.translate(-fp.left, -fp.top)
                            selectedScreen.value =
                                when {
                                    topLocal.contains(tap) -> ScreenLayoutManager.ScreenId.TOP
                                    bottomLocal.contains(tap) -> ScreenLayoutManager.ScreenId.BOTTOM
                                    else -> selectedScreen.value
                                }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val selected = selectedScreen.value
                            val current = layoutStateLatest.value.transformOf(selected)
                            val maxScale =
                                maxOnScreenScale(screenWidthPx, screenHeightPx, current.scaleX, current.scaleY)
                            viewModel.updateScreenLayoutTransform(
                                selected,
                                current.offsetX + pan.x,
                                current.offsetY + pan.y,
                                (current.scale * zoom).coerceIn(
                                    ScreenLayoutManager.MIN_SCALE,
                                    maxScale,
                                ),
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
        }

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
                    AlignEdge.TOP -> transform.offsetX to (viewPos.top - (cy - halfH))
                    AlignEdge.BOTTOM -> transform.offsetX to (viewPos.bottom - (cy + halfH))
                    AlignEdge.LEFT -> (viewPos.left - (cx - halfW)) to transform.offsetY
                    AlignEdge.RIGHT -> (viewPos.right - (cx + halfW)) to transform.offsetY
                    AlignEdge.CENTER -> 0f to 0f
                }
            viewModel.setScreenLayoutOffset(screen, ox, oy)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            val isLandscape = screenWidthPx > screenHeightPx
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
            )
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
