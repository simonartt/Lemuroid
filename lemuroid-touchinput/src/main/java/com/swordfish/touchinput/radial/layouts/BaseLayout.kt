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
    groupSettings: TouchControllerSettingsManager.ButtonGroupSettings? = null,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val g = groupSettings ?: TouchControllerSettingsManager.ButtonGroupSettings()
    val interpolatedDialSize =
        remember(settings.scale, g.scale) {
            lerp(
                TouchControllerSettingsManager.MIN_SCALE,
                TouchControllerSettingsManager.MAX_SCALE,
                settings.scale,
            ) * g.scale
        }

    // Clamp padding to prevent negative values from crashing
    // offsetX/Y applied via Modifier.offset to primaryDial only (secondary dials stay in place)
    val leftPadding = maxOf(0f, settings.marginX)
    val bottomPadding = maxOf(0f, settings.marginY)
    val offsetX = (TouchControllerSettingsManager.MAX_MARGINS * g.offsetX).dp
    val offsetY = (TouchControllerSettingsManager.MAX_MARGINS * g.offsetY).dp

    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    left = TouchControllerSettingsManager.MAX_MARGINS.dp * leftPadding,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * bottomPadding,
                )
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = {
            androidx.compose.ui.layout.Layout(content = primaryDial) { measurable, constraints ->
                val placeable = measurable.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(offsetX.roundToPx(), offsetY.roundToPx())
                }
            }
        },
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
    groupSettings: TouchControllerSettingsManager.ButtonGroupSettings? = null,
    primaryDial: @Composable () -> Unit,
    secondaryDials: @Composable LayoutRadialSecondaryDialsScope.() -> Unit,
) {
    val g = groupSettings ?: TouchControllerSettingsManager.ButtonGroupSettings()
    // Clamp padding to prevent negative values from crashing
    // offsetX/Y applied via layout offset to primaryDial only (secondary dials stay in place)
    val rightPadding = maxOf(0f, settings.marginX)
    val bottomPadding = maxOf(0f, settings.marginY)
    val offsetX = (TouchControllerSettingsManager.MAX_MARGINS * g.offsetX).dp
    val offsetY = (TouchControllerSettingsManager.MAX_MARGINS * g.offsetY).dp

    LayoutRadial(
        modifier =
            modifier
                .absolutePadding(
                    right = TouchControllerSettingsManager.MAX_MARGINS.dp * rightPadding,
                    bottom = TouchControllerSettingsManager.MAX_MARGINS.dp * bottomPadding,
                )
                .padding(LocalLemuroidPadTheme.current.padding),
        primaryDial = {
            androidx.compose.ui.layout.Layout(content = primaryDial) { measurable, constraints ->
                val placeable = measurable.first().measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.place(offsetX.roundToPx(), offsetY.roundToPx())
                }
            }
        },
        secondaryDials = secondaryDials,
        primaryDialMaxSize =
            160.dp *
                lerp(
                    TouchControllerSettingsManager.MIN_SCALE,
                    TouchControllerSettingsManager.MAX_SCALE,
                    settings.scale,
                ) * g.scale,
        secondaryDialsBaseRotationInDegrees = -settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
    )
}
