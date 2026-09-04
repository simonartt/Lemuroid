package com.swordfish.lemuroid.app.shared.game.viewmodel

import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.Orientation
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenId
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenLayoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel-side holder for the layout editor.
 * Two modes share one editor state (v1.20.5): SCREEN layout (NDS dashed frames) and CONTROLS
 * (touch button editor). Non-NDS systems open directly in CONTROLS mode; NDS starts in SCREEN
 * mode and can switch to CONTROLS via the bottom-bar sub-menu.
 */
class GameViewModelScreenLayout(
    private val screenLayoutManager: ScreenLayoutManager,
    private val isNdsSystem: Boolean,
    private val scope: CoroutineScope,
) {
    private val showEditor = MutableStateFlow(false)
    private val controlsMode = MutableStateFlow(false)

    fun isNds() = isNdsSystem

    fun isEditorShown(): Flow<Boolean> = showEditor

    fun isControlsModeShown(): Flow<Boolean> = controlsMode

    fun setControlsMode(on: Boolean) {
        controlsMode.value = on
    }

    fun isControlsMode(): Boolean = controlsMode.value

    fun toggleEditor(show: Boolean) {
        showEditor.value = show
        if (!show) controlsMode.value = false
    }

    fun getLayoutState(): Flow<ScreenLayoutState> = screenLayoutManager.observeState()

    fun currentLayoutState(): ScreenLayoutState = screenLayoutManager.currentState()

    /** Replaces a screen's transform wholesale (offset + uniform scale). Used by drag and the resize handle. */
    fun updateTransform(screen: ScreenId, offsetX: Float, offsetY: Float, scale: Float) {
        val old = screenLayoutManager.currentState().transformOf(screen)
        scope.launch {
            screenLayoutManager.updateTransform(
                screen,
                old.copy(offsetX = offsetX, offsetY = offsetY, scale = scale),
            )
        }
    }

    /** Nudges the selected screen by a pixel delta on the given axis. */
    fun nudge(screen: ScreenId, dx: Float, dy: Float) {
        scope.launch { screenLayoutManager.adjustTransform(screen, deltaOffsetX = dx, deltaOffsetY = dy) }
    }

    /** Sets the horizontal (width-axis) scale; 0.5 = half width, 1.0 = full. */
    fun setHorizontalScale(screen: ScreenId, scaleX: Float) {
        scope.launch { screenLayoutManager.setHorizontalScale(screen, scaleX) }
    }

    /** Sets the vertical (height-axis) scale; 0.5 = half height, 1.0 = full. */
    fun setVerticalScale(screen: ScreenId, scaleY: Float) {
        scope.launch { screenLayoutManager.setVerticalScale(screen, scaleY) }
    }

    /** Sets the absolute pixel offset of one screen (used by align/center tools). */
    fun setOffset(screen: ScreenId, offsetX: Float, offsetY: Float) {
        scope.launch { screenLayoutManager.setOffset(screen, offsetX, offsetY) }
    }

    /** Sets the gap to the paired screen. */
    fun setGap(screen: ScreenId, gap: Float) {
        scope.launch { screenLayoutManager.setGap(screen, gap) }
    }

    /** Enables/disables rendering of one screen (visibility switch on the editor frame). */
    fun setEnabled(screen: ScreenId, enabled: Boolean) {
        scope.launch { screenLayoutManager.setEnabled(screen, enabled) }
    }

    /** Sets the uniform scale of one screen (zoom-panel steps). */
    fun setScale(screen: ScreenId, scale: Float) {
        scope.launch {
            screenLayoutManager.updateTransform(screen, screenLayoutManager.currentState().transformOf(screen).copy(scale = scale))
        }
    }

    /** Saves the current working values into a slot (orientation + 1..3). */
    fun saveToSlot(orientation: Orientation, slotNumber: Int) {
        scope.launch { screenLayoutManager.saveToSlot(orientation, slotNumber) }
    }

    /** Loads a slot's saved values into the working area. */
    fun loadFromSlot(orientation: Orientation, slotNumber: Int) {
        scope.launch { screenLayoutManager.loadFromSlot(orientation, slotNumber) }
    }

    /** Auto-loads the new orientation's last-used slot on rotation (no-op if none saved). */
    fun onOrientationChanged(orientation: Orientation) {
        scope.launch { screenLayoutManager.onOrientationChanged(orientation) }
    }

    /** Human label for the active slot (e.g. "横·槽2"), or null when not loaded from a slot. */
    fun activeSlotLabel(): String? = screenLayoutManager.activeSlotLabel()

    fun resetScreen(screen: ScreenId) {
        scope.launch { screenLayoutManager.resetScreen(screen) }
    }

    fun resetToDefault() {
        scope.launch { screenLayoutManager.resetToDefault() }
    }
}
