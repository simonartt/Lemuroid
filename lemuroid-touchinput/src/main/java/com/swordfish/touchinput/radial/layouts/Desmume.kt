package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
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
    content: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bs = settings.getButtonSettings(id)
    val onEditSelect = LocalButtonEdit.current
    val isEditing = onEditSelect != null

    if (settings.isButtonHidden(id) && !isEditing) return

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
fun PadKitScope.DesmumeLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            TweakableButtonDesmume(id = TouchButtonId.DPAD, settings = settings, modifier = Modifier) { mod ->
                LemuroidControlCross(modifier = mod, id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD))
            }
        },
        secondaryDials = {
            TweakableButtonDesmume(id = TouchButtonId.L, settings = settings, modifier = Modifier) { mod -> SecondaryButtonL(modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.SELECT, settings = settings, modifier = Modifier) { mod -> SecondaryButtonSelect(position = 2, modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.MENU, settings = settings, modifier = Modifier) { mod -> SecondaryButtonMenuPlaceholder(settings, modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.THUMBL, settings = settings, modifier = Modifier) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
                    icon = R.drawable.button_mic,
                )
            }
            TweakableButtonDesmume(id = TouchButtonId.L2, settings = settings, modifier = Modifier) { mod ->
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
            TweakableButtonDesmume(id = TouchButtonId.FACE, settings = settings, modifier = Modifier) { mod ->
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
            TweakableButtonDesmume(id = TouchButtonId.R, settings = settings, modifier = Modifier) { mod -> SecondaryButtonR(modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.START, settings = settings, modifier = Modifier) { mod -> SecondaryButtonStart(position = 2, modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.MENU, settings = settings, modifier = Modifier) { mod -> SecondaryButtonMenu(settings, modifier = mod) }
            TweakableButtonDesmume(id = TouchButtonId.THUMBR, settings = settings, modifier = Modifier) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(-120f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    icon = R.drawable.button_swap_screens,
                )
            }
        },
    )
}
