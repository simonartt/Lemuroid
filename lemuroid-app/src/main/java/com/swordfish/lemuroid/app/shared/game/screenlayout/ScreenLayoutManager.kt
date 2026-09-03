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
 * Persists NDS dual-screen layout customization (per-screen offset/scale) in fixed slots.
 * Follows the same pattern as TouchControllerSettingsManager: JSON blob in SharedPreferences
 * backed by an in-memory MutableStateFlow cache.
 *
 * Storage model (v1.20.1, user-pinned "Plan A"): layouts are GLOBAL across all NDS games and
 * split by orientation — 3 slots for portrait + 3 for landscape. The [slots] map is keyed by
 * "[orientation]_[n]" (e.g. "portrait_1"). [topScreen]/[bottomScreen] hold the CURRENT working
 * values (what is on screen right now); [activeSlot] records which slot they were loaded from
 * so the UI can show "现在布局：横·槽2". On orientation change the manager auto-loads that
 * orientation's last-used slot if one exists.
 */
class ScreenLayoutManager(private val sharedPreferences: SharedPreferences) {

    enum class ScreenId { TOP, BOTTOM }

    enum class Orientation { PORTRAIT, LANDSCAPE }

    // Number of saved slots per orientation lives in the companion (SLOTS_PER_ORIENTATION).

    @Serializable
    data class ScreenTransform(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        // Uniform (equal-proportion) scale, driven by the zoom panel (1x..7x) and the
        // proportional-resize handle. 1.0 = original.
        val scale: Float = 1.0f,
        // Horizontal (width-axis) scale, independent of [scale]. 1.0 = original width.
        // Driven by "宽度 50%/100%" tools. effectiveWidth = base * scale * scaleX.
        val scaleX: Float = 1.0f,
        // Vertical (height-axis) scale, independent of [scale]. 1.0 = original height.
        // Driven by "高度 50%/100%" tools. effectiveHeight = base * scale * scaleY.
        val scaleY: Float = 1.0f,
        // Gap to the paired screen (pixels). Positive = move away, negative = overlap.
        // In the vertical-stack (portrait) layout this is the vertical spacing between the two screens.
        val gap: Float = 0f,
        // Per-screen enable (v1.20.4). When false the screen is NOT rendered (its split-viewport
        // quad is skipped natively) and receives no touch — but its frame stays selectable in
        // the editor, and swapping the screen positions (R/stick swap) can bring the hidden
        // content back on screen. Persisted with the layout slots.
        val enabled: Boolean = true,
    ) {
        val isDefault: Boolean
            get() = offsetX == 0f && offsetY == 0f && scale == 1.0f && scaleX == 1.0f && scaleY == 1.0f && gap == 0f && enabled

        companion object {
            val DEFAULT = ScreenTransform()
        }
    }

    /** A saved layout: one transform per screen, stored under a slot key. */
    @Serializable
    data class Slot(
        val topScreen: ScreenTransform = ScreenTransform.DEFAULT,
        val bottomScreen: ScreenTransform = ScreenTransform.DEFAULT,
    )

    @Serializable
    data class ScreenLayoutState(
        val topScreen: ScreenTransform = ScreenTransform.DEFAULT,
        val bottomScreen: ScreenTransform = ScreenTransform.DEFAULT,
        // Keyed by slotKey(orientation, n). Legacy "profiles" blobs are ignored on load.
        val slots: Map<String, Slot> = emptyMap(),
        val activeSlot: String? = null,
    ) {
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

    /** Sets the uniform scale of one screen (zoom-panel steps / proportional-resize handle). */
    suspend fun setUniformScale(screen: ScreenId, scale: Float) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        updateState(current.withTransform(screen, old.copy(scale = scale)))
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

    /** Enables/disables rendering of one screen (the editor's per-frame visibility switch). */
    suspend fun setEnabled(screen: ScreenId, enabled: Boolean) {
        val current = stateFlow.value
        val old = current.transformOf(screen)
        if (old.enabled == enabled) return
        updateState(current.withTransform(screen, old.copy(enabled = enabled)))
    }

    /** Saves the current working values into a slot (overwriting whatever was there). */
    suspend fun saveToSlot(orientation: Orientation, slotNumber: Int) {
        val key = slotKey(orientation, slotNumber)
        val current = stateFlow.value
        updateState(
            current.copy(
                slots = current.slots + (key to Slot(current.topScreen, current.bottomScreen)),
                activeSlot = key,
            ),
        )
    }

    /** Loads a slot's saved values into the working area. No-op if the slot is empty. */
    suspend fun loadFromSlot(orientation: Orientation, slotNumber: Int) {
        val key = slotKey(orientation, slotNumber)
        val current = stateFlow.value
        val slot = current.slots[key] ?: return
        updateState(current.copy(topScreen = slot.topScreen, bottomScreen = slot.bottomScreen, activeSlot = key))
    }

    /**
     * Called on orientation change. If the NEW orientation has a last-used slot, load it; if the
     * current working values already came from that orientation's slot, keep them untouched. If
     * the new orientation has NO saved slot, keep the working values but clear [activeSlot] so the
     * UI shows "默认（未保存）" instead of a stale label from the other orientation.
     */
    suspend fun onOrientationChanged(orientation: Orientation) {
        val current = stateFlow.value
        val activeKey = current.activeSlot
        val activeInThisOrientation =
            activeKey != null &&
                activeKey.startsWith(orientation.name.lowercase()) &&
                current.slots.containsKey(activeKey)
        if (activeInThisOrientation) return
        val lastUsed =
            (1..SLOTS_PER_ORIENTATION)
                .map { slotKey(orientation, it) }
                .lastOrNull { current.slots.containsKey(it) }
        val slot = lastUsed?.let { current.slots[it] }
        updateState(
            if (slot != null) {
                current.copy(topScreen = slot.topScreen, bottomScreen = slot.bottomScreen, activeSlot = lastUsed)
            } else {
                // No saved layout for this orientation: carry over the working values as unsaved.
                current.copy(activeSlot = null)
            },
        )
    }

    /** Resets one screen to its natural position/scale. Slots are not touched. */
    suspend fun resetScreen(screen: ScreenId) {
        val current = stateFlow.value
        updateState(current.withTransform(screen, ScreenTransform.DEFAULT))
    }

    /** Resets both screens to defaults. Does not touch saved slots. */
    suspend fun resetToDefault() {
        val current = stateFlow.value
        updateState(
            current.copy(
                topScreen = ScreenTransform.DEFAULT,
                bottomScreen = ScreenTransform.DEFAULT,
                activeSlot = null,
            ),
        )
    }

    /** Human label for the active slot (e.g. "横·槽2"), or null when not loaded from a slot. */
    fun activeSlotLabel(): String? {
        val key = stateFlow.value.activeSlot ?: return null
        val orientation = if (key.startsWith("portrait")) "竖" else "横"
        val number = key.substringAfterLast('_').toIntOrNull() ?: return null
        return "$orientation·槽$number"
    }

    private fun loadState(): ScreenLayoutState {
        return try {
            sharedPreferences.getString(PREF_KEY, null)
                ?.let { Json.decodeFromString(ScreenLayoutState.serializer(), it) }
                ?.let { deriveWorkingValues(it) }
                ?: ScreenLayoutState()
        } catch (e: Exception) {
            Timber.w(e, "Screen layout settings corrupted, resetting to defaults")
            sharedPreferences.edit { remove(PREF_KEY) }
            ScreenLayoutState()
        }
    }

    /**
     * Working values are derived from the explicitly selected slot (or defaults). A legacy
     * blob that only had "profiles" (pre-v1.20.1) has no slots, so it simply starts at
     * defaults — profiles were never exposed in the UI.
     */
    private fun deriveWorkingValues(state: ScreenLayoutState): ScreenLayoutState {
        val active = state.activeSlot?.let { state.slots[it] }
        return if (active == null) {
            state.copy(topScreen = ScreenTransform.DEFAULT, bottomScreen = ScreenTransform.DEFAULT)
        } else {
            state.copy(topScreen = active.topScreen, bottomScreen = active.bottomScreen)
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

        /** Number of saved slots per orientation (user-pinned: 3). */
        const val SLOTS_PER_ORIENTATION = 3

        const val MIN_SCALE = 0.15f
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

        /** Slot map key: "portrait_1" … "landscape_3". */
        fun slotKey(orientation: Orientation, slotNumber: Int): String =
            "${orientation.name.lowercase()}_$slotNumber"
    }
}
