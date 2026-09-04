package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.controls.LemuroidControlButton
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonL
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonR
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager.TouchButtonId
import com.swordfish.touchinput.radial.ui.LemuroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** CompositionLocal to pass edit-mode callback into the PadKit tree */
val LocalButtonEdit = compositionLocalOf<((TouchButtonId) -> Unit)?>(defaultFactory = { null })

/**
 * CompositionLocal carrying the edit-mode drag callback (dx/dy in PIXELS): moving the selected
 * button writes into its ButtonGroupSettings.freeX/freeY (v1.20.5). Null outside edit mode.
 */
val LocalButtonDrag = compositionLocalOf<((TouchButtonId, Float, Float) -> Unit)?>(defaultFactory = { null })

/**
 * Currently SELECTED button in the touch-controls editor (v1.20.7). Since the pad is fully
 * neutralized while editing (no press highlight at all), this drives the blue selection ring
 * that tells the user which button the size slider / reset act on.
 */
val LocalSelectedButton = compositionLocalOf<TouchButtonId?>(defaultFactory = { null })

/** Wrapper that applies per-button offset & scale, and intercepts clicks in edit mode */
@Composable
fun PadKitScope.TweakableButton(
    id: TouchButtonId,
    settings: TouchControllerSettingsManager.Settings,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val bs = settings.getButtonSettings(id)
    val onEditSelect = LocalButtonEdit.current
    val onEditDrag = LocalButtonDrag.current
    val isEditing = onEditSelect != null
    val isHidden = settings.isButtonHidden(id)
    // Selected group gets a blue ring — the only press/selection feedback now that the pad is
    // neutralized in edit mode (v1.20.7).
    val isSelected = isEditing && LocalSelectedButton.current == id

    // Skip rendering if hidden (but still show in edit mode)
    if (isHidden && !isEditing) return

    // pointerInput captures its block ONCE per key — hold the latest callbacks in updated
    // states so the drag loop never invokes a stale closure.
    val selectLatest = rememberUpdatedState(onEditSelect)
    val dragLatest = rememberUpdatedState(onEditDrag)

    // Build modifier: base + graphicsLayer for customization + pointerInput for edit mode.
    // freeX/freeY are PIXEL translations from free dragging (v1.20.5) and stack on top of the
    // legacy relative offset inside the same graphicsLayer.
    val baseMod = if (bs.scale != 1.0f || bs.offsetX != 0f || bs.offsetY != 0f || bs.freeX != 0f || bs.freeY != 0f) {
        val ox = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetX + bs.freeX
        val oy = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetY + bs.freeY
        Modifier.graphicsLayer(
            translationX = ox,
            translationY = oy,
            scaleX = bs.scale,
            scaleY = bs.scale,
            // Hidden buttons are shown dimmed in edit mode so they can be found and re-enabled.
            alpha = if (isHidden) 0.4f else 1f,
        )
    } else {
        Modifier.then(
            if (isHidden) Modifier.graphicsLayer(alpha = 0.4f) else Modifier,
        )
    }
    val mod = modifier.then(baseMod)

    val finalMod = if (isEditing) {
        // Press = select; drag = move this button freely (v1.20.5). Both go through the raw
        // pointer loop below: Compose 1.6 has no awaitFirstDown, so wait for the down manually.
        mod.pointerInput(id) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    val down = ev.changes.firstOrNull { it.changedToDown() && !it.isConsumed }
                    if (down == null) continue
                    selectLatest.value?.invoke(id)
                    down.consume()
                    val downId = down.id
                    var lastX = down.position.x
                    var lastY = down.position.y
                    while (true) {
                        val move = awaitPointerEvent()
                        val ch = move.changes.firstOrNull { it.id == downId } ?: break
                        if (!ch.pressed) break
                        val dx = ch.position.x - lastX
                        val dy = ch.position.y - lastY
                        lastX = ch.position.x
                        lastY = ch.position.y
                        if (dx != 0f || dy != 0f) {
                            dragLatest.value?.invoke(id, dx, dy)
                        }
                        ch.consume()
                    }
                }
            }
        }
    } else {
        mod
    }

    // Blue selection ring for the currently edited button group (v1.20.7).
    val ringMod =
        if (isSelected) Modifier.border(2.dp, Color(0xFF35B5E8), RoundedCornerShape(16.dp))
        else Modifier

    content(finalMod.then(ringMod))
}

@Composable
fun PadKitScope.MelonDSLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButton(id = TouchButtonId.DPAD, settings = settings) { mod ->
                LemuroidControlCross(modifier = mod, id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD))
            }
        },
        secondaryDials = {
            TweakableButton(id = TouchButtonId.L, settings = settings) { mod -> SecondaryButtonL(modifier = mod) }
            TweakableButton(id = TouchButtonId.SELECT, settings = settings) { mod -> SecondaryButtonSelect(position = 2, modifier = mod) }
            TweakableButton(id = TouchButtonId.MENU, settings = settings) { mod -> SecondaryButtonMenuPlaceholder(settings, modifier = mod) }
            TweakableButton(id = TouchButtonId.L2, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                    icon = R.drawable.button_mic,
                )
            }
            TweakableButton(id = TouchButtonId.THUMBL, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-60f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
                    icon = R.drawable.button_close_screen,
                )
            }
        },
    )
}

@Composable
fun PadKitScope.MelonDSRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButton(id = TouchButtonId.FACE, settings = settings) { mod ->
                LemuroidControlFaceButtons(
                    modifier = mod,
                    ids =
                        persistentListOf(
                            Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                            Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                            Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                            Id.Key(KeyEvent.KEYCODE_BUTTON_X),
                        ),
                    idsForegrounds =
                        persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                            Id.Key(KeyEvent.KEYCODE_BUTTON_A) to { LemuroidButtonForeground(pressed = it, label = "A") },
                            Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { LemuroidButtonForeground(pressed = it, label = "B") },
                            Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { LemuroidButtonForeground(pressed = it, label = "Y") },
                            Id.Key(KeyEvent.KEYCODE_BUTTON_X) to { LemuroidButtonForeground(pressed = it, label = "X") },
                        ),
                )
            }
        },
        secondaryDials = {
            TweakableButton(id = TouchButtonId.R, settings = settings) { mod -> SecondaryButtonR(modifier = mod) }
            TweakableButton(id = TouchButtonId.START, settings = settings) { mod -> SecondaryButtonStart(position = 2, modifier = mod) }
            TweakableButton(id = TouchButtonId.MENU, settings = settings) { mod -> SecondaryButtonMenu(settings, modifier = mod) }
            TweakableButton(id = TouchButtonId.THUMBR, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    icon = R.drawable.button_swap_screens,
                )
            }
        },
    )
}
