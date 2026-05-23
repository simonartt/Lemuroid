package com.swordfish.touchinput.radial.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.max
import androidx.core.content.edit
import com.swordfish.lemuroid.common.compose.pxToDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

class TouchControllerSettingsManager(private val sharedPreferences: SharedPreferences) {
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
    }

    enum class TouchButtonId(val label: String) {
        DPAD("方向键"), L("L"), L2("麦克风"), SELECT("Select"), THUMBL("关屏"),
        FACE("Y/X/A/B"), R("R"), START("Start"), MENU("菜单"), THUMBR("换屏")
    }

    @Serializable
    data class ButtonGroupSettings(
        val scale: Float = 1.0f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
    ) {
        companion object {
            val DEFAULT = ButtonGroupSettings()
            fun reset() = DEFAULT
        }
    }

    @Serializable
    data class Settings(
        val scale: Float = DEFAULT_SCALE,
        val rotation: Float = DEFAULT_ROTATION,
        val marginX: Float = DEFAULT_MARGIN_X,
        val marginY: Float = DEFAULT_MARGIN_Y,
        // Per-button independent settings
        val buttonSettings: Map<String, ButtonGroupSettings> = emptyMap(),
    ) {
        fun getButtonSettings(id: TouchButtonId) = buttonSettings[id.name] ?: ButtonGroupSettings()
        fun setButtonSettings(id: TouchButtonId, s: ButtonGroupSettings) = copy(buttonSettings = buttonSettings + (id.name to s))
        // Legacy fields kept for backward compat
        val dpadSettings: ButtonGroupSettings get() = getButtonSettings(TouchButtonId.DPAD)
        val faceButtonsSettings: ButtonGroupSettings get() = getButtonSettings(TouchButtonId.FACE)
    }

    private fun computeInsetsPaddings(
        density: Density,
        insets: WindowInsets,
    ): PaddingValues {
        val result =
            PaddingValues(
                insets.getLeft(density, layoutDirection = LayoutDirection.Ltr).pxToDp(density),
                insets.getTop(density).pxToDp(density),
                insets.getRight(density, layoutDirection = LayoutDirection.Ltr).pxToDp(density),
                insets.getBottom(density).pxToDp(density),
            )
        return result
    }

    private val cachedSettings = mutableMapOf<String, MutableStateFlow<Settings?>>()

    fun observeSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        density: Density,
        insets: WindowInsets,
    ): Flow<Settings> {
        val paddings = computeInsetsPaddings(density, insets)
        val horizontalPadding =
            max(
                paddings.calculateLeftPadding(LayoutDirection.Ltr),
                paddings.calculateRightPadding(LayoutDirection.Ltr),
            )
        val verticalPadding = paddings.calculateBottomPadding()
        val defaultSettings =
            Settings(
                scale = DEFAULT_SCALE,
                rotation = DEFAULT_ROTATION,
                marginX = horizontalPadding.value / MAX_MARGINS,
                marginY = verticalPadding.value / MAX_MARGINS,
            )
        val settingsKey = getPreferenceString(touchControllerID, orientation)
        val cachedStateFlow =
            cachedSettings.getOrPut(settingsKey) {
                val currentSettings =
                    try {
                        sharedPreferences.getString(settingsKey, null)
                            ?.let { Json.decodeFromString(Settings.serializer(), it) }
                    } catch (e: Exception) {
                        // Settings corrupted or contains invalid values — auto-reset to defaults
                        Timber.w(e, "Touch settings corrupted for key $settingsKey, resetting to defaults")
                        sharedPreferences.edit { remove(settingsKey) }
                        null
                    }

                MutableStateFlow(currentSettings)
            }
        return cachedStateFlow.map { it ?: defaultSettings }
    }

    suspend fun storeSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings,
    ) {
        Timber.d("Updating touch settings for $touchControllerID at $orientation to $settings")
        updateCachedSettings(touchControllerID, orientation, settings)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(
                    getPreferenceString(touchControllerID, orientation),
                    Json.encodeToString(Settings.serializer(), settings),
                )
            }
        }
    }

    private fun updateCachedSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings?,
    ) {
        val cacheKey = getPreferenceString(touchControllerID, orientation)
        val cacheFlow = cachedSettings.getOrPut(cacheKey) { MutableStateFlow(settings) }
        cacheFlow.value = settings
    }

    suspend fun resetSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
    ) {
        updateCachedSettings(touchControllerID, orientation, null)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                remove(getPreferenceString(touchControllerID, orientation))
            }
        }
    }

    companion object {
        const val DEFAULT_SCALE = 0.5f
        const val DEFAULT_ROTATION = 0.0f
        const val DEFAULT_MARGIN_X = 0.0f
        const val DEFAULT_MARGIN_Y = 0.0f

        const val MAX_ROTATION = 45f
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.5f

        const val MAX_MARGINS = 96f
    }

    private fun getPreferenceString(
        controllerID: TouchControllerID,
        orientation: Orientation,
    ): String {
        return "touch_controller_settings_${controllerID}_${orientation.ordinal}"
    }
}
