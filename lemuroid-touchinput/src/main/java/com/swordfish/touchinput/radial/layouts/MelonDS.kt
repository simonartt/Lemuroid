package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
    val isEditing = onEditSelect != null

    // Skip rendering if hidden (but still show in edit mode)
    if (settings.isButtonHidden(id) && !isEditing) return

    // Build modifier: base + graphicsLayer for customization + pointerInput for edit mode
    val baseMod = if (bs.scale != 1.0f || bs.offsetX != 0f || bs.offsetY != 0f) {
        val ox = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetX
        val oy = TouchControllerSettingsManager.MAX_MARGINS * bs.offsetY
        Modifier.graphicsLayer(
            translationX = ox,
            translationY = oy,
            scaleX = bs.scale,
            scaleY = bs.scale,
        )
    } else {
        Modifier
    }
    val mod = modifier.then(baseMod)

    val finalMod = if (isEditing) {
        mod.pointerInput(Unit) {
            detectTapGestures(onTap = { onEditSelect(id) })
        }
    } else {
        mod
    }

    content(finalMod)
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
