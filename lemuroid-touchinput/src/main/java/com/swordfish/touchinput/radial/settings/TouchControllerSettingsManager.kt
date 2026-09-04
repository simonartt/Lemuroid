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
        DPAD("方向键"), L("L"), L2("菜单"), SELECT("Select"), THUMBL("左摇杆"),
        FACE("Y/X/A/B"), R("R"), START("Start"), MENU("全局菜单"), THUMBR("右摇杆/换屏")
    }

    @Serializable
    data class ButtonGroupSettings(
        val scale: Float = 1.0f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        // Free-drag translation in PIXELS (v1.20.5), applied on top of the legacy offset.
        // Dragging a button in the editor writes these; they let a button leave its radial
        // anchor zone and float anywhere on screen — including over the game picture.
        val freeX: Float = 0f,
        val freeY: Float = 0f,
    ) {
        companion object {
            val DEFAULT = ButtonGroupSettings()
            fun reset() = DEFAULT
        }
    }

    /**
     * A named A/B/C preset: a snapshot of the per-button layout (positions/sizes/visibility).
     * The working fields of [Settings] stay live; presets are loaded into / saved from them.
     */
    @Serializable
    data class Preset(
        val buttonSettings: Map<String, ButtonGroupSettings> = emptyMap(),
        val hiddenButtons: Set<String> = emptySet(),
    )

    @Serializable
    data class Settings(
        val scale: Float = DEFAULT_SCALE,
        val rotation: Float = DEFAULT_ROTATION,
        val marginX: Float = DEFAULT_MARGIN_X,
        val marginY: Float = DEFAULT_MARGIN_Y,
        // Per-button independent settings
        val buttonSettings: Map<String, ButtonGroupSettings> = emptyMap(),
        // Hidden buttons (by name)
        val hiddenButtons: Set<String> = emptySet(),
        // A/B/C layout presets, keyed by "A"/"B"/"C" (v1.20.5). Per controller AND orientation
        // — the whole Settings blob is already bucketed by (touchControllerID, orientation).
        val presets: Map<String, Preset> = emptyMap(),
        // Which preset the working fields currently mirror ("A"/"B"/"C"), null = unsaved edits.
        val activePreset: String? = null,
    ) {
        fun getButtonSettings(id: TouchButtonId) = buttonSettings[id.name] ?: ButtonGroupSettings()
        fun setButtonSettings(id: TouchButtonId, s: ButtonGroupSettings) = copy(buttonSettings = buttonSettings + (id.name to s))
        fun isButtonHidden(id: TouchButtonId) = id.name in hiddenButtons
        fun setButtonHidden(id: TouchButtonId, hidden: Boolean) =
            if (hidden) copy(hiddenButtons = hiddenButtons + id.name)
            else copy(hiddenButtons = hiddenButtons - id.name)
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
        // v1.20.5: while an A/B/C preset is active, every working-field edit mirrors into it
        // (user-pinned "auto-save into the current preset" behavior).
        val synced = settings.activePreset?.let { name ->
            if (settings.presets.containsKey(name)) {
                settings.copy(
                    presets = settings.presets + (
                        name to Preset(settings.buttonSettings, settings.hiddenButtons)
                        ),
                )
            } else {
                settings.copy(activePreset = null)
            }
        } ?: settings
        Timber.d("Updating touch settings for $touchControllerID at $orientation to $synced")
        updateCachedSettings(touchControllerID, orientation, synced)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(
                    getPreferenceString(touchControllerID, orientation),
                    Json.encodeToString(Settings.serializer(), synced),
                )
            }
        }
    }

    /**
     * Non-suspending read of the last known settings for a (controller, orientation) bucket —
     * populated by [observeSettings]. Falls back to parsing the persisted blob on first access.
     */
    fun currentSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
    ): Settings? {
        val settingsKey = getPreferenceString(touchControllerID, orientation)
        cachedSettings[settingsKey]?.value?.let { return it }
        return try {
            sharedPreferences.getString(settingsKey, null)
                ?.let { Json.decodeFromString(Settings.serializer(), it) }
        } catch (e: Exception) {
            Timber.w(e, "Touch settings corrupted for key $settingsKey")
            null
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
