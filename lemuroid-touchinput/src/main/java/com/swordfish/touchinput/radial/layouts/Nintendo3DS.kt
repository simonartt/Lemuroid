package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.radial.controls.LemuroidControlCross
import com.swordfish.touchinput.radial.controls.LemuroidControlFaceButtons
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.SecondaryAnalogLeft
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonL
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonR
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.swordfish.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.ui.LemuroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.Nintendo3DSLeft(
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
            TweakableButton(id = TouchButtonId.MENU, settings = settings) { mod -> SecondaryButtonMenuPlaceholder(settings = settings, modifier = mod) }
            TweakableButton(id = TouchButtonId.THUMBL, settings = settings) { mod ->
                SecondaryAnalogLeft()
            }
        },
    )
}

@Composable
fun PadKitScope.Nintendo3DSRight(
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
            // THUMBR = 换屏按钮（3DS 专属）
            TweakableButton(id = TouchButtonId.THUMBR, settings = settings) { mod ->
                LemuroidControlButton(
                    modifier = mod.then(Modifier.radialPosition(+80f - 180f).radialScale(2.2f)),
                    id = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
                    icon = R.drawable.button_close_screen,
                )
            }
            TweakableButton(id = TouchButtonId.L2, settings = settings) { mod ->
                SecondaryButtonMenu(settings = settings, modifier = mod)
            }
        },
    )
}
