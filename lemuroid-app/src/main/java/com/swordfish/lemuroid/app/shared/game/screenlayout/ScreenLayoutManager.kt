package com.swordfish.lemuroid.app.shared.game.screenlayout

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Persists NDS dual-screen layout customization (per-screen offset/scale) and named profiles.
 * Follows the same pattern as TouchControllerSettingsManager: JSON blob in SharedPreferences
 * backed by an in-memory MutableStateFlow cache.
 *
 * Each screen (top/bottom half of the frame) has its own transform, applied independently.
 * Offsets are in pixels relative to the screen's natural (centered, stacked) position.
 * Scale is a multiplier around the screen's own center. 1.0 = original size.
 */
class ScreenLayoutManager(private val sharedPreferences: SharedPreferences) {

    enum class ScreenId { TOP, BOTTOM }

    @Serializable
    data class ScreenTransform(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        // Uniform (equal-proportion) scale, driven by the zoom panel (1x..7x). 1.0 = original.
        val scale: Float = 1.0f,
        // Horizontal (width-axis) scale, independent of [scale]. 1.0 = original width.
        // Driven by "宽度 50%/100%" tools (R1C3 / R2C4). effectiveWidth = base * scale * scaleX.
        val scaleX: Float = 1.0f,
        // Vertical (height-axis) scale, independent of [scale]. 1.0 = original height.
        // Driven by "高度 50%/100%" tools (R1C1 / R1C4). effectiveHeight = base * scale * scaleY.
        val scaleY: Float = 1.0f,
        // Gap to the paired screen (pixels). Positive = move away, negative = overlap.
        // In the vertical-stack (portrait) layout this is the vertical spacing between the two screens.
        val gap: Float = 0f,
    ) {
        val isDefault: Boolean
            get() = offsetX == 0f && offsetY == 0f && scale == 1.0f && scaleX == 1.0f && scaleY == 1.0f && gap == 0f

        companion object {
            val DEFAULT = ScreenTransform()
        }
    }

    @Serializable
    data class ScreenLayoutProfile(
        val name: String,
        val topScreen: ScreenTransform = ScreenTransform.DEFAULT,
        val bottomScreen: ScreenTransform = ScreenTransform.DEFAULT,
        // Legacy v1.2 combined transform, migrated into per-screen values on load
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scale: Float = 1.0f,
    )

    @Serializable
    data class ScreenLayoutState(
        val topScreen: ScreenTransform = ScreenTransform.DEFAULT,
        val bottomScreen: ScreenTransform = ScreenTransform.DEFAULT,
        val profiles: Map<String, ScreenLayoutProfile> = emptyMap(),
        val activeProfileId: String? = null,
        // Legacy v1.2 combined transform, migrated into per-screen values on load
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scale: Float = 1.0f,
    ) {
        val isDefault: Boolean
            get() = topScreen.isDefault && bottomScreen.isDefault

        fun transformOf(screen: ScreenId): ScreenTransform =
            if (screen == ScreenId.TOP) topScreen else bottomScreen

        fun withTransform(screen: ScreenId, transform: ScreenTransform): ScreenLayoutState =
            if (screen == ScreenId.TOP) copy(topScreen = transform) else copy(bottomScreen = transform)
    }

    private val stateFlow: MutableStateFlow<ScreenLayoutState> by lazy {
        MutableStateFlow(loadState())
    }

    fun observeState(): Flow<ScreenLayoutState> = stateFlow

    fun currentState(): ScreenLayoutState = stateFlow.value

    /** Replaces the transform of one screen wholesale. */
    suspend fun updateTransform(screen: ScreenId, transform: ScreenTransform) {
        val current = stateFlow.value
        updateState(current.withTransform(screen, transform))
    }

    /** Copies the transform of one screen and applies a delta to the given fields. */
    suspend fun adjustTransform(
        screen: ScreenId,
        deltaOffsetX: Float = 0f,
        deltaOffsetY: Float = 0f,
        deltaScale: Float = 1f,
        deltaScaleX: Float = 1f,
        deltaScaleY: Float = 1f,
        deltaGap: Float = 0f,
    ) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        updateState(
            current.withTransform(
                screen,
                old.copy(
                    offsetX = old.offsetX + deltaOffsetX,
                    offsetY = old.offsetY + deltaOffsetY,
                    scale = old.scale * deltaScale,
                    scaleX = old.scaleX * deltaScaleX,
                    scaleY = old.scaleY * deltaScaleY,
                    gap = old.gap + deltaGap,
                ),
            ),
        )
    }

    /** Sets the horizontal (width-axis) scale of one screen; 0.5 = half width, 1.0 = full. */
    suspend fun setHorizontalScale(screen: ScreenId, scaleX: Float) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        updateState(current.withTransform(screen, old.copy(scaleX = scaleX)))
    }

    /** Sets the vertical (height-axis) scale of one screen; 0.5 = half height, 1.0 = full. */
    suspend fun setVerticalScale(screen: ScreenId, scaleY: Float) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        updateState(current.withTransform(screen, old.copy(scaleY = scaleY)))
    }

    /** Sets the absolute pixel offset of one screen relative to its natural center. */
    suspend fun setOffset(screen: ScreenId, offsetX: Float, offsetY: Float) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        updateState(current.withTransform(screen, old.copy(offsetX = offsetX, offsetY = offsetY)))
    }

    /** Sets the gap between the selected screen and its pair. */
    suspend fun setGap(screen: ScreenId, gap: Float) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        updateState(current.withTransform(screen, old.copy(gap = gap)))
    }

    /** Saves current working values as a new profile. Returns the new profile id. */
    suspend fun saveAsNewProfile(name: String): String {
        val current = stateFlow.value
        val newId = nextProfileId(current.profiles)
        val profile = ScreenLayoutProfile(name, current.topScreen, current.bottomScreen)
        updateState(
            current.copy(
                profiles = current.profiles + (newId to profile),
                activeProfileId = newId,
            ),
        )
        return newId
    }

    /** Overwrites the active profile with current working values, optionally renaming it. */
    suspend fun overwriteActiveProfile(newName: String? = null) {
        val current = stateFlow.value
        val activeId = current.activeProfileId ?: return
        val existing = current.profiles[activeId] ?: return
        val updated =
            existing.copy(
                name = newName ?: existing.name,
                topScreen = current.topScreen,
                bottomScreen = current.bottomScreen,
            )
        updateState(current.copy(profiles = current.profiles + (activeId to updated)))
    }

    /** Switches to a saved profile; working values are replaced by the profile's values. */
    suspend fun selectProfile(id: String) {
        val current = stateFlow.value
        val profile = current.profiles[id] ?: return
        updateState(
            current.copy(
                topScreen = profile.topScreen,
                bottomScreen = profile.bottomScreen,
                activeProfileId = id,
            ),
        )
    }

    /** Deletes a profile. If it was active, working values stay but become unsaved. */
    suspend fun deleteProfile(id: String) {
        val current = stateFlow.value
        updateState(
            current.copy(
                profiles = current.profiles - id,
                activeProfileId = if (current.activeProfileId == id) null else current.activeProfileId,
            ),
        )
    }

    /** Resets one screen to its natural position/scale. Profiles are not touched. */
    suspend fun resetScreen(screen: ScreenId) {
        val current = stateFlow.value
        updateState(current.withTransform(screen, ScreenTransform.DEFAULT))
    }

    /** Resets both screens to defaults. Does not touch saved profiles. */
    suspend fun resetToDefault() {
        val current = stateFlow.value
        updateState(
            current.copy(
                topScreen = ScreenTransform.DEFAULT,
                bottomScreen = ScreenTransform.DEFAULT,
                activeProfileId = null,
            ),
        )
    }

    private fun nextProfileId(profiles: Map<String, ScreenLayoutProfile>): String {
        var index = 1
        while (profiles.containsKey("profile_$index")) index++
        return "profile_$index"
    }

    /** Default name for a new profile, e.g. "方案 1". Picks the lowest free index. */
    fun suggestProfileName(): String {
        val usedNames = stateFlow.value.profiles.values.map { it.name }.toSet()
        var index = 1
        while ("方案 $index" in usedNames) index++
        return "方案 $index"
    }

    private fun loadState(): ScreenLayoutState {
        return try {
            sharedPreferences.getString(PREF_KEY, null)
                ?.let { Json.decodeFromString(ScreenLayoutState.serializer(), it) }
                ?.let { migrateLegacy(it) }
                ?.let { deriveWorkingValues(it) }
                ?: ScreenLayoutState()
        } catch (e: Exception) {
            Timber.w(e, "Screen layout settings corrupted, resetting to defaults")
            sharedPreferences.edit { remove(PREF_KEY) }
            ScreenLayoutState()
        }
    }

    /**
     * Working values are session-only: on startup they are derived from the explicitly
     * selected profile (or defaults). Unsaved tweaks never leak into the next session,
     * so a game always opens at the expected position.
     */
    private fun deriveWorkingValues(state: ScreenLayoutState): ScreenLayoutState {
        val active = state.activeProfileId?.let { state.profiles[it] }
        return if (active == null) {
            state.copy(
                topScreen = ScreenTransform.DEFAULT,
                bottomScreen = ScreenTransform.DEFAULT,
            )
        } else {
            state.copy(topScreen = active.topScreen, bottomScreen = active.bottomScreen)
        }
    }

    /** Migrates v1.2 combined transforms (single offset/scale) into per-screen values. */
    private fun migrateLegacy(state: ScreenLayoutState): ScreenLayoutState {
        val legacyTransform = ScreenTransform(state.offsetX, state.offsetY, state.scale)
        val hasLegacy = !legacyTransform.isDefault
        val migratedProfiles =
            state.profiles.mapValues { (_, profile) ->
                val legacyProfileTransform = ScreenTransform(profile.offsetX, profile.offsetY, profile.scale)
                if (!legacyProfileTransform.isDefault &&
                    profile.topScreen.isDefault &&
                    profile.bottomScreen.isDefault
                ) {
                    profile.copy(
                        topScreen = legacyProfileTransform,
                        bottomScreen = legacyProfileTransform,
                        offsetX = 0f,
                        offsetY = 0f,
                        scale = 1.0f,
                    )
                } else {
                    profile
                }
            }
        return if (hasLegacy && state.topScreen.isDefault && state.bottomScreen.isDefault) {
            state.copy(
                topScreen = legacyTransform,
                bottomScreen = legacyTransform,
                profiles = migratedProfiles,
                offsetX = 0f,
                offsetY = 0f,
                scale = 1.0f,
            )
        } else {
            state.copy(profiles = migratedProfiles)
        }
    }

    private suspend fun updateState(newState: ScreenLayoutState) {
        Timber.d("Updating screen layout state to $newState")
        stateFlow.value = newState
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(PREF_KEY, Json.encodeToString(ScreenLayoutState.serializer(), newState))
            }
        }
    }

    companion object {
        private const val PREF_KEY = "nds_screen_layout_settings"

        const val MIN_SCALE = 0.5f
        // Raised from 2.0 to support the zoom panel's 1x..7x stepped scale in the new editor UI.
        const val MAX_SCALE = 7.0f
        const val DEFAULT_SCALE = 1.0f

        // Vertical scale (scaleY) presets used by the "高度" tools.
        const val VERTICAL_SCALE_HALF = 0.5f
        const val VERTICAL_SCALE_FULL = 1.0f

        // Horizontal scale (scaleX) presets used by the "宽度" tools.
        const val HORIZONTAL_SCALE_HALF = 0.5f
        const val HORIZONTAL_SCALE_FULL = 1.0f

        // Amount each arrow tool nudges the selected screen (pixels).
        const val NUDGE_DELTA = 12f

        // Amount "间距" tools change the gap per press (pixels).
        const val GAP_DELTA = 8f
    }
}
