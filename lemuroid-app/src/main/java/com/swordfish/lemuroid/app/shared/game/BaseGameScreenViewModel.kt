package com.swordfish.lemuroid.app.shared.game

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.game.screenlayout.ScreenLayoutManager
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelInput
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSaves
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelScreenLayout
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTilt
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager.TouchButtonId
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.common.longAnimationDuration
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class BaseGameScreenViewModel(
    private val appContext: Context,
    game: Game,
    settingsManager: SettingsManager,
    inputDeviceManager: InputDeviceManager,
    controllerConfigsManager: ControllerConfigsManager,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    sharedPreferences: SharedPreferences,
    savesManager: SavesManager,
    statesManager: StatesManager,
    statesPreviewManager: StatesPreviewManager,
    coreVariablesManager: CoreVariablesManager,
    rumbleManager: RumbleManager,
) : ViewModel(), DefaultLifecycleObserver {
    class Factory(
        private val appContext: Context,
        private val game: Game,
        private val settingsManager: SettingsManager,
        private val inputDeviceManager: InputDeviceManager,
        private val controllerConfigsManager: ControllerConfigsManager,
        private val system: GameSystem,
        private val systemCoreConfig: SystemCoreConfig,
        private val sharedPreferences: SharedPreferences,
        private val savesManager: SavesManager,
        private val statesManager: StatesManager,
        private val statesPreviewManager: StatesPreviewManager,
        private val coreVariablesManager: CoreVariablesManager,
        private val rumbleManager: RumbleManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BaseGameScreenViewModel(
                appContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences,
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
            ) as T
        }
    }

    private val sideEffects = GameViewModelSideEffects(viewModelScope)
    val retroGameView =
        GameViewModelRetroGameView(
            appContext,
            system,
            systemCoreConfig,
            settingsManager,
            coreVariablesManager,
            sideEffects,
            rumbleManager,
            viewModelScope,
        )
    private val tilt = GameViewModelTilt(appContext, settingsManager)
    private val inputs =
        GameViewModelInput(
            appContext,
            system,
            systemCoreConfig,
            inputDeviceManager,
            controllerConfigsManager,
            retroGameView,
            tilt,
            sideEffects,
            viewModelScope,
        )
    private val touchControls =
        GameViewModelTouchControls(
            settingsManager,
            TouchControllerSettingsManager(sharedPreferences),
            retroGameView,
            inputs,
            tilt,
            sideEffects,
            viewModelScope,
            swapScreensCallback = { swapNdsScreens() },
        )
    private val screenLayout =
        GameViewModelScreenLayout(
            ScreenLayoutManager(sharedPreferences),
            system.id == SystemID.NDS,
            viewModelScope,
        )

    // Remembered virtual-control visibility before the layout editor was opened,
    // so it can be restored on close.
    private var touchControlsVisibleBeforeEdit = true
    private val saves =
        GameViewModelSaves(
            appContext,
            system,
            game,
            systemCoreConfig,
            retroGameView,
            settingsManager,
            savesManager,
            statesManager,
            statesPreviewManager,
            sideEffects,
        )

    val loadingState = MutableStateFlow(false)

    private inline fun withLoading(block: () -> Unit) {
        loadingState.value = true
        block()
        loadingState.value = false
    }

    fun getGameState(): Flow<GameViewModelRetroGameView.GameState> {
        return retroGameView.getGameState()
    }

    fun getSideEffects(): Flow<GameViewModelSideEffects.UiEffect> {
        return sideEffects.getUiEffects()
    }

    fun getTiltConfiguration(): Flow<TiltConfiguration> {
        return tilt.getTiltConfiguration()
    }

    fun getSimulatedTiltEvents(): Flow<InputState> {
        return tilt.getSimulatedTiltEvents()
    }

    fun getTouchControlsSettings(
        density: Density,
        insets: WindowInsets,
    ): Flow<TouchControllerSettingsManager.Settings?> {
        return touchControls.getTouchControlsSettings(density, insets)
    }

    fun getTouchHapticFeedbackMode(): Flow<HapticFeedbackMode> {
        return touchControls.getTouchHapticFeedbackMode()
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): GLRetroView {
        val (gameData, result) = retroGameView.createRetroView(context, lifecycle)
        viewModelScope.launch {
            gameData.quickSaveData?.let {
                saves.restoreAutoSaveAsync(it)
            }
        }
        return result
    }

    suspend fun loadGame(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
    ) {
        Timber.i("Calling load game: $game")
        retroGameView.initialize(applicationContext, game, systemCoreConfig, gameLoader, requestLoadSave)
    }

    fun toggleEditControls(show: Boolean) {
        touchControls.toggleEditControls(show)
    }

    fun isEditControlShown(): Flow<Boolean> {
        return touchControls.isEditControlsShown()
    }

    fun updateTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        touchControls.updateTouchControllerSettings(touchControllerSettings)
    }

    fun resetTouchControls() {
        touchControls.resetTouchControls()
    }

    // Per-button editing
    fun getEditingSelection(): Flow<TouchButtonId?> = touchControls.getEditingSelection()
    fun selectEditTarget(target: TouchButtonId) { touchControls.selectEditTarget(target) }
    fun cycleEditTarget(direction: Int) { touchControls.cycleEditTarget(direction) }
    fun updateButtonOffset(id: TouchButtonId, dx: Float, dy: Float) { touchControls.updateButtonOffset(id, dx, dy) }
    fun updateButtonScale(id: TouchButtonId, newScale: Float) { touchControls.updateButtonScale(id, newScale) }
    fun resetButtonSettings(id: TouchButtonId) { touchControls.resetButtonSettings(id) }
    fun toggleButtonVisibility(id: TouchButtonId, hidden: Boolean) { touchControls.toggleButtonVisibility(id, hidden) }

    fun onScreenOrientationChanged(orientation: TouchControllerSettingsManager.Orientation) {
        touchControls.updateScreenOrientation(orientation)
    }

    fun isTouchControllerVisible(): Flow<Boolean> {
        return touchControls.isTouchControllerVisible()
    }

    fun isTouchControllerVisibleValue(): Boolean {
        return touchControls.isTouchControllerVisibleValue()
    }

    fun setTouchControllerVisible(enabled: Boolean) {
        touchControls.setTouchControllerVisible(enabled)
    }

    fun showGameMenu() {
        touchControls.showGameMenu()
    }

    // NDS screen layout customization (no-op on non-NDS systems)
    fun isNdsSystem(): Boolean = screenLayout.isNds()

    fun isEditScreenLayoutShown(): Flow<Boolean> = screenLayout.isEditorShown()

    fun toggleEditScreenLayout(show: Boolean) {
        screenLayout.toggleEditor(show)
        if (show) {
            // Save the pre-edit pad visibility BEFORE anything toggles it.
            touchControlsVisibleBeforeEdit = touchControls.isTouchControllerVisibleValue()
            if (!screenLayout.isNds()) {
                // Non-NDS systems have no screen layout to edit — open straight into the
                // unified controls editor (v1.20.5, one entry/mode for all systems).
                setEditControlsMode(true)
            } else {
                // Screen-layout mode hides the virtual pads; the user edits dashed frames.
                touchControls.setTouchControllerVisible(false)
            }
            // Freeze the game while the editor is open.
            retroGameView.retroGameView?.pauseEmulation()
        } else {
            screenLayout.setControlsMode(false)
            touchControls.toggleEditControls(false)
            retroGameView.retroGameView?.resumeEmulation()
            // Restore virtual controls to the state they were in before editing.
            touchControls.setTouchControllerVisible(touchControlsVisibleBeforeEdit)
        }
    }

    /** Editor sub-mode: true = touch-controls editor, false = NDS screen-layout editor. */
    fun isEditControlsModeShown(): Flow<Boolean> = screenLayout.isControlsModeShown()

    /** Switch between the screen-layout and controls sub-editors while the editor is open. */
    fun setEditControlsMode(on: Boolean) {
        screenLayout.setControlsMode(on)
        // Block game input from the pads while editing them (input guard reads showEditControls).
        touchControls.toggleEditControls(on)
        // The pads must be visible to edit them and hidden while dragging screen frames.
        touchControls.setTouchControllerVisible(on)
    }

    /** Leave the unified editor entirely (both sub-modes) and return to the game. */
    fun exitLayoutEditor() {
        setEditControlsMode(false)
        toggleEditScreenLayout(false)
    }

    // Touch-button free dragging & A/B/C presets (v1.20.5)
    fun updateButtonFreeDrag(id: TouchButtonId, dxPx: Float, dyPx: Float) =
        touchControls.updateButtonFreeDrag(id, dxPx, dyPx)

    fun loadTouchPreset(name: String) = touchControls.loadTouchPreset(name)

    fun saveTouchPreset(name: String) = touchControls.saveTouchPreset(name)

    fun isTouchPresetSaved(name: String): Boolean = touchControls.savedTouchPresets().contains(name)

    fun getScreenLayoutState(): Flow<ScreenLayoutManager.ScreenLayoutState> = screenLayout.getLayoutState()

    fun currentScreenLayoutState(): ScreenLayoutManager.ScreenLayoutState = screenLayout.currentLayoutState()

    fun updateScreenLayoutTransform(
        screen: ScreenLayoutManager.ScreenId,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
    ) = screenLayout.updateTransform(screen, offsetX, offsetY, scale)

    /** Nudges the selected screen by a pixel delta (arrow tools). */
    fun nudgeScreenLayout(screen: ScreenLayoutManager.ScreenId, dx: Float, dy: Float) =
        screenLayout.nudge(screen, dx, dy)

    /** Sets the horizontal (width-axis) scale of the selected screen. */
    fun setScreenLayoutHorizontalScale(screen: ScreenLayoutManager.ScreenId, scaleX: Float) =
        screenLayout.setHorizontalScale(screen, scaleX)

    /** Sets the vertical (height-axis) scale of the selected screen. */
    fun setScreenLayoutVerticalScale(screen: ScreenLayoutManager.ScreenId, scaleY: Float) =
        screenLayout.setVerticalScale(screen, scaleY)

    /** Sets the gap between the selected screen and its pair. */
    fun setScreenLayoutGap(screen: ScreenLayoutManager.ScreenId, gap: Float) =
        screenLayout.setGap(screen, gap)

    /** Sets the absolute pixel offset of the selected screen (align/center tools). */
    fun setScreenLayoutOffset(screen: ScreenLayoutManager.ScreenId, offsetX: Float, offsetY: Float) =
        screenLayout.setOffset(screen, offsetX, offsetY)

    /** Sets the uniform scale of the selected screen (zoom-panel steps). */
    fun setScreenLayoutScale(screen: ScreenLayoutManager.ScreenId, scale: Float) =
        screenLayout.setScale(screen, scale)

    /** Enables/disables rendering of one screen (per-frame visibility switch). */
    fun setScreenLayoutEnabled(screen: ScreenLayoutManager.ScreenId, enabled: Boolean) =
        screenLayout.setEnabled(screen, enabled)

    /** Saves the current working layout into a slot (orientation + 1..3). */
    fun saveScreenLayoutToSlot(orientation: ScreenLayoutManager.Orientation, slotNumber: Int) =
        screenLayout.saveToSlot(orientation, slotNumber)

    /** Loads a slot's saved layout into the working area. */
    fun loadScreenLayoutFromSlot(orientation: ScreenLayoutManager.Orientation, slotNumber: Int) =
        screenLayout.loadFromSlot(orientation, slotNumber)

    /** Auto-loads the new orientation's last-used slot on rotation (no-op if none saved). */
    fun onScreenLayoutOrientationChanged(orientation: ScreenLayoutManager.Orientation) =
        screenLayout.onOrientationChanged(orientation)

    /** Human label for the active slot (e.g. "横·槽2"), or null when not loaded from a slot. */
    fun currentScreenLayoutSlotLabel(): String? = screenLayout.activeSlotLabel()

    fun resetScreenLayoutScreen(screen: ScreenLayoutManager.ScreenId) = screenLayout.resetScreen(screen)

    fun resetScreenLayoutToDefault() = screenLayout.resetToDefault()

    fun getTouchControllerConfig(): Flow<ControllerConfig> {
        return touchControls.getTouchControllerConfig()
    }

    fun changeTiltConfiguration(tiltConfig: TiltConfiguration) {
        tilt.changeTiltConfiguration(tiltConfig)
    }

    fun isMenuPressed(): Flow<Boolean> {
        return touchControls.isMenuPressed()
    }

    suspend fun saveSlot(index: Int) {
        if (loadingState.value) return
        withLoading {
            saves.saveSlot(index)
        }
    }

    suspend fun loadSlot(index: Int) {
        if (loadingState.value) return
        withLoading {
            saves.loadSlot(index)
        }
    }

    fun saveQuickSave() {
        Timber.d("Saving quick save")
        if (loadingState.value) return
        withLoading {
            saves.saveQuickSave()
        }
    }

    fun loadQuickSave() {
        Timber.d("Loading quick save")
        if (loadingState.value) return
        withLoading {
            saves.loadQuickSave()
        }
    }

    fun toggleFastForward() {
        Timber.d("Loading quick save")
        retroGameView.retroGameView?.apply {
            frameSpeed = if (frameSpeed == 1) 2 else 1
        }
    }

    private val ndsScreenSwapped = MutableStateFlow(false)

    private fun swapNdsScreens() {
        if (system.id != SystemID.NDS) return

        ndsScreenSwapped.value = !ndsScreenSwapped.value
        val swapped = ndsScreenSwapped.value

        when (systemCoreConfig.coreID) {
            CoreID.MELONDS -> {
                val layoutValue = if (swapped) "bottom-top" else "top-bottom"
                Timber.i("Swapping MelonDS screens to layout: $layoutValue")
                retroGameView.retroGameView?.updateVariables(
                    com.swordfish.libretrodroid.Variable("melonds_screen_layout1", layoutValue),
                )
            }
            CoreID.DESMUME -> {
                val layoutValue = if (swapped) "bottom/top" else "top/bottom"
                Timber.i("Swapping DeSmuME screens to layout: $layoutValue")
                retroGameView.retroGameView?.updateVariables(
                    com.swordfish.libretrodroid.Variable("desmume_screens_layout", layoutValue),
                )
            }
            else -> {}
        }
    }

    suspend fun reset() =
        withLoading {
            try {
                delay(appContext.longAnimationDuration().toLong())
                retroGameView.retroGameViewFlow().reset()
            } catch (e: Throwable) {
                Timber.e(e, "Error in reset")
            }
        }

    fun requestFinish() {
        if (loadingState.value) return
        viewModelScope.launch {
            withLoading {
                val snapshot = saves.captureSaveSnapshot(true) ?: return@launch
                saves.writeSaveSnapshot(snapshot)
                sideEffects.requestSuccessfulFinish()
            }
        }
    }

    fun requestBackgroundSave() {
        if (loadingState.value) return
        GameService.schedule {
            val snapshot = saves.captureSaveSnapshot(false)
            saves.writeSaveSnapshot(snapshot)
        }
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        touchControls.handleVirtualInputEvent(events)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.lifecycle.addObserver(tilt)
        owner.lifecycle.addObserver(inputs)
        owner.lifecycle.addObserver(retroGameView)
        owner.lifecycle.addObserver(touchControls)
    }

    fun sendKeyEvent(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        return inputs.sendKeyEvent(keyCode, event)
    }

    fun sendMotionEvent(event: MotionEvent): Boolean {
        return inputs.sendMotionEvent(event)
    }
}
