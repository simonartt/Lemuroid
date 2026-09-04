package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.swordfish.touchinput.radial.layouts.LocalButtonEdit
import com.swordfish.touchinput.radial.ui.LemuroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Wrapper that applies per-button offset & scale, and intercepts clicks in edit mode */
@Composable
fun PadKitScope.TweakableButtonDesmume(
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

    if (isHidden && !isEditing) return

    // pointerInput captures its block once — read the latest callbacks through updated states.
    val selectLatest = rememberUpdatedState(onEditSelect)
    val dragCommitLatest = rememberUpdatedState(onEditDrag)
    // Latest COMMITTED freeX/freeY for the once-built pointerInput closure.
    val committedFree = rememberUpdatedState(bs.freeX to bs.freeY)

    // LIVE drag delta (v1.20.8): the button follows the finger via a local pixel offset; the
    // Settings store is written ONCE on finger-up. Per-event VM writes meant a full JSON encode
    // + SharedPreferences write + pad rebuild at ~120Hz — the backlog caused the ghosting /
    // shattering (worse in landscape). The live delta stays visible until the commit lands in
    // bs.freeX/freeY (absorbed check): no snap-back, no double-move frame.
    var liveDx by remember { mutableFloatStateOf(0f) }
    var liveDy by remember { mutableFloatStateOf(0f) }
    var startFreeX by remember { mutableFloatStateOf(0f) }
    var startFreeY by remember { mutableFloatStateOf(0f) }
    val absorbed = bs.freeX != startFreeX || bs.freeY != startFreeY
    val lx = if (absorbed) 0f else liveDx
    val ly = if (absorbed) 0f else liveDy

    val needsLayer = bs.scale != 1.0f || bs.offsetX != 0f || bs.offsetY != 0f ||
        bs.freeX != 0f || bs.freeY != 0f || lx != 0f || ly != 0f
    val baseMod = if (needsLayer) {
        val ox = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetX + bs.freeX + lx
        val oy = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetY + bs.freeY + ly
        Modifier.graphicsLayer(
            translationX = ox,
            translationY = oy,
            scaleX = bs.scale,
            scaleY = bs.scale,
            alpha = if (isHidden) 0.4f else 1f,
        )
    } else {
        Modifier.then(
            if (isHidden) Modifier.graphicsLayer(alpha = 0.4f) else Modifier,
        )
    }
    val mod = modifier.then(baseMod)

    val finalMod = if (isEditing) {
        mod.pointerInput(id) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    val down = ev.changes.firstOrNull { it.changedToDown() && !it.isConsumed }
                    if (down == null) continue
                    selectLatest.value?.invoke(id)
                    down.consume()
                    val downId = down.id
                    var accX = 0f
                    var accY = 0f
                    var lastX = down.position.x
                    var lastY = down.position.y
                    val start = committedFree.value
                    startFreeX = start.first
                    startFreeY = start.second
                    liveDx = 0f
                    liveDy = 0f
                    while (true) {
                        val move = awaitPointerEvent()
                        val ch = move.changes.firstOrNull { it.id == downId } ?: break
                        if (!ch.pressed) break
                        val dx = ch.position.x - lastX
                        val dy = ch.position.y - lastY
                        lastX = ch.position.x
                        lastY = ch.position.y
                        if (dx != 0f || dy != 0f) {
                            accX += dx
                            accY += dy
                            liveDx = accX
                            liveDy = accY
                        }
                        ch.consume()
                    }
                    // Finger lifted: commit the whole accumulated drag exactly once.
                    if (accX != 0f || accY != 0f) {
                        dragCommitLatest.value?.invoke(id, accX, accY)
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
fun PadKitScope.DesmumeLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButtonDesmume(id = TouchButtonId.DPAD, settings = settings) { mod ->
                LemuroidControlCross(modifier = mod, id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD))
            }
        },
        secondaryDials = {
            TweakableButtonDesmume(id = TouchButtonId.L, settings = settings) { mod -> SecondaryButtonL(modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.SELECT, settings = settings) { mod -> SecondaryButtonSelect(position = 2, modifier = mod) }
            // v1.20.9 bug2 fix: same as MelonDS — the invisible MENU placeholder must not be an
            // editable button, or selecting 全局菜单 draws a phantom ring over the neighbouring
            // slot and the slider scales both rings. The placeholder positions itself.
            SecondaryButtonMenuPlaceholder(settings)
            TweakableButtonDesmume(id = TouchButtonId.THUMBL, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
                    icon = R.drawable.button_mic,
                )
            }
            TweakableButtonDesmume(id = TouchButtonId.L2, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-60f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                    icon = R.drawable.button_close_screen,
                )
            }
        },
    )
}

@Composable
fun PadKitScope.DesmumeRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButtonDesmume(id = TouchButtonId.FACE, settings = settings) { mod ->
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
            TweakableButtonDesmume(id = TouchButtonId.R, settings = settings) { mod -> SecondaryButtonR(modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.START, settings = settings) { mod -> SecondaryButtonStart(position = 2, modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.MENU, settings = settings) { mod -> SecondaryButtonMenu(settings, modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.THUMBR, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    icon = R.drawable.button_swap_screens,
                )
            }
        },
    )
}
