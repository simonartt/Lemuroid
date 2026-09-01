package com.swordfish.lemuroid.app.shared.game.viewmodel

import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenId
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager.ScreenLayoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel-side holder for the NDS screen layout customizer.
 * All functionality is gated behind [isNdsSystem] so non-NDS cores are never affected.
 */
class GameViewModelScreenLayout(
    private val screenLayoutManager: ScreenLayoutManager,
    private val isNdsSystem: Boolean,
    private val scope: CoroutineScope,
) {
    private val showEditor = MutableStateFlow(false)

    fun isNds() = isNdsSystem

    fun isEditorShown(): Flow<Boolean> = showEditor

    fun toggleEditor(show: Boolean) {
        if (!isNdsSystem) return
        showEditor.value = show
    }

    fun getLayoutState(): Flow<ScreenLayoutState> = screenLayoutManager.observeState()

    fun currentLayoutState(): ScreenLayoutState = screenLayoutManager.currentState()

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

    /** Sets the uniform scale of one screen (zoom-panel steps). */
    fun setScale(screen: ScreenId, scale: Float) {
        scope.launch {
            screenLayoutManager.updateTransform(screen, screenLayoutManager.currentState().transformOf(screen).copy(scale = scale))
        }
    }

    fun saveAsNewProfile(name: String) {
        scope.launch { screenLayoutManager.saveAsNewProfile(name) }
    }

    fun overwriteActiveProfile(newName: String? = null) {
        scope.launch { screenLayoutManager.overwriteActiveProfile(newName) }
    }

    fun selectProfile(id: String) {
        scope.launch { screenLayoutManager.selectProfile(id) }
    }

    fun deleteProfile(id: String) {
        scope.launch { screenLayoutManager.deleteProfile(id) }
    }

    fun resetScreen(screen: ScreenId) {
        scope.launch { screenLayoutManager.resetScreen(screen) }
    }

    fun resetToDefault() {
        scope.launch { screenLayoutManager.resetToDefault() }
    }

    fun suggestProfileName(): String = screenLayoutManager.suggestProfileName()
}
