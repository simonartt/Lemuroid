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
        scope.launch { screenLayoutManager.updateTransform(screen, offsetX, offsetY, scale) }
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
