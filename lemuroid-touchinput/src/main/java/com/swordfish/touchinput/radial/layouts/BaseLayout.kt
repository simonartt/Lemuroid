package com.swordfish.touchinput.radial.layouts

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.layouts.radial.LayoutRadial
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope)
@Composable
fun BaseLayoutLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val dpadScale = settings.dpadSettings.scale
    val interpolatedDialSize =
        remember(settings.scale, dpadScale) {
            lerp(
                TouchControllerSettingsManager.MIN_SCALE,
                TouchControllerSettingsManager.MAX_SCALE,
                settings.scale,
            ) * dpadScale
        }

    val leftPadding = maxOf(0f, settings.marginX)
    val bottomPadding = maxOf(0f, settings.marginY)

    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    left = TouchControllerSettingsManager.MAX_MARGINS.dp * leftPadding,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * bottomPadding,
                )
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize = 160.dp * interpolatedDialSize,
        secondaryDialsBaseRotationInDegrees = settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}

context(PadKitScope)
@Composable
fun BaseLayoutRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val faceScale = settings.faceButtonsSettings.scale
    val rightPadding = maxOf(0f, settings.marginX)
    val bottomPadding = maxOf(0f, settings.marginY)

    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    right = TouchControllerSettingsManager.MAX_MARGINS.dp * rightPadding,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * bottomPadding,
                )
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = primaryDial,
        secondaryDials = secondaryDials,
        primaryDialMaxSize =
            160.dp *
                lerp(
                    TouchControllerSettingsManager.MIN_SCALE,
                    TouchControllerSettingsManager.MAX_SCALE,
                    settings.scale,
                ) * faceScale,
        secondaryDialsBaseRotationInDegrees = -settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}
