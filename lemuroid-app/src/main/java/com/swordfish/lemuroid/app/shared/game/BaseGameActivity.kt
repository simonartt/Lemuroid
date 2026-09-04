package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.game.GameActivity
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOption
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.tv.game.TVGameActivity
import com.swordfish.lemuroid.common.animationDuration
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.common.dump
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.ExposedSetting
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import dagger.Lazy
import java.io.File
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
abstract class BaseGameActivity : ImmersiveActivity() {
    protected lateinit var game: Game
    private lateinit var system: GameSystem
    protected lateinit var systemCoreConfig: SystemCoreConfig

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var savesManager: SavesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    @Inject
    lateinit var coreVariablesManager: CoreVariablesManager

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var gameLoader: GameLoader

    @Inject
    lateinit var controllerConfigsManager: ControllerConfigsManager

    @Inject
    lateinit var rumbleManager: RumbleManager

    @Inject
    lateinit var sharedPreferences: Lazy<SharedPreferences>

    private lateinit var baseGameScreenViewModel: BaseGameScreenViewModel

    private val startGameTime = System.currentTimeMillis()
    private var finishTriggered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpExceptionsHandler()
        GameService.startService(applicationContext, intent)
        game = intent.getSerializableExtra(EXTRA_GAME) as Game
        systemCoreConfig = intent.getSerializableExtra(EXTRA_SYSTEM_CORE_CONFIG) as SystemCoreConfig
        system = GameSystem.findById(game.systemId)

        val viewModel by viewModels<BaseGameScreenViewModel> {
            BaseGameScreenViewModel.Factory(
                applicationContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences.get(),
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
            )
        }

        baseGameScreenViewModel = viewModel

        lifecycle.addObserver(baseGameScreenViewModel)

        setContent {
            AppTheme {
                BaseGameScreen(viewModel = baseGameScreenViewModel) {
                    GameScreen(viewModel)
                }
            }
        }

        lifecycleScope.launch {
            baseGameScreenViewModel.loadGame(
                applicationContext,
                game,
                systemCoreConfig,
                gameLoader,
                intent.getBooleanExtra(EXTRA_LOAD_SAVE, false),
            )
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    baseGameScreenViewModel.requestFinish()
                }
            },
        )

        initialiseFlows()
    }

    @Composable
    abstract fun GameScreen(viewModel: BaseGameScreenViewModel)

    private fun initialiseFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeViewModelsEffectsFlow()
        }
    }

    private fun setUpExceptionsHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            performUnexpectedErrorFinish(exception)
        }
    }

    private fun transformExposedSetting(
        exposedSetting: ExposedSetting,
        coreOptions: List<CoreOption>,
    ): LemuroidCoreOption? {
        return coreOptions
            .firstOrNull { it.variable.key == exposedSetting.key }
            ?.let { LemuroidCoreOption(exposedSetting, it) }
    }

    private fun displayOptionsDialog(
        currentTiltConfiguration: TiltConfiguration,
        tiltConfigurations: List<TiltConfiguration>,
    ) {
        if (baseGameScreenViewModel.loadingState.value) {
            return
        }

        val coreOptions = getCoreOptions()

        val options =
            systemCoreConfig.exposedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val advancedOptions =
            systemCoreConfig.exposedAdvancedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val intent =
            Intent(this, getDialogClass()).apply {
                this.putExtra(GameMenuContract.EXTRA_CORE_OPTIONS, options.toTypedArray())
                this.putExtra(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS, advancedOptions.toTypedArray())
                this.putExtra(
                    GameMenuContract.EXTRA_CURRENT_DISK,
                    baseGameScreenViewModel.retroGameView.retroGameView?.getCurrentDisk() ?: 0,
                )
                this.putExtra(
                    GameMenuContract.EXTRA_DISKS,
                    baseGameScreenViewModel.retroGameView.retroGameView?.getAvailableDisks() ?: 0,
                )
                this.putExtra(GameMenuContract.EXTRA_GAME, game)
                this.putExtra(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                this.putExtra(
                    GameMenuContract.EXTRA_AUDIO_ENABLED,
                    baseGameScreenViewModel.retroGameView.retroGameView?.audioEnabled,
                )
                this.putExtra(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, system.fastForwardSupport)
                this.putExtra(
                    GameMenuContract.EXTRA_FAST_FORWARD,
                    (baseGameScreenViewModel.retroGameView.retroGameView?.frameSpeed ?: 1) > 1,
                )
                this.putExtra(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG, currentTiltConfiguration)
                // TODO PADS... Make sure to avoid passing this if a physical pad is connected.
                this.putExtra(GameMenuContract.EXTRA_TILT_ALL_CONFIGS, tiltConfigurations.toTypedArray())
                this.putExtra(
                    GameMenuContract.EXTRA_TOUCH_CONTROLS_ENABLED,
                    baseGameScreenViewModel.isTouchControllerVisibleValue(),
                )
            }
        startActivityForResult(intent, DIALOG_REQUEST)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    protected abstract fun getDialogClass(): Class<out Activity>

    private fun getCoreOptions(): List<CoreOption> {
        return baseGameScreenViewModel.retroGameView.retroGameView?.getVariables()
            ?.mapNotNull {
                val coreOptionResult =
                    runCatching {
                        CoreOption.fromLibretroDroidVariable(it)
                    }
                coreOptionResult.getOrNull()
            } ?: listOf()
    }

    private suspend fun initializeViewModelsEffectsFlow() {
        baseGameScreenViewModel.getSideEffects()
            .collect {
                when (it) {
                    is GameViewModelSideEffects.UiEffect.ShowMenu ->
                        displayOptionsDialog(
                            it.currentTiltConfiguration,
                            it.tiltConfigurations,
                        )
                    is GameViewModelSideEffects.UiEffect.ShowToast -> displayToast(it.message)
                    is GameViewModelSideEffects.UiEffect.SuccessfulFinish -> performSuccessfulActivityFinish()
                    is GameViewModelSideEffects.UiEffect.FailureFinish -> performErrorFinish(it.message)
                    is GameViewModelSideEffects.UiEffect.SaveQuickSave -> performSaveQuickSave()
                    is GameViewModelSideEffects.UiEffect.LoadQuickSave -> performLoadQuickSave()
                    is GameViewModelSideEffects.UiEffect.ToggleFastForward -> performToggleFastForward()
                }
            }
    }

    private fun performSaveQuickSave() {
        baseGameScreenViewModel.saveQuickSave()
    }

    private fun performLoadQuickSave() {
        baseGameScreenViewModel.loadQuickSave()
    }

    private fun performToggleFastForward() {
        baseGameScreenViewModel.toggleFastForward()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = baseGameScreenViewModel.sendMotionEvent(event)
        if (handled) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun performSuccessfulActivityFinish() {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_SESSION_DURATION, System.currentTimeMillis() - startGameTime)
                putExtra(PLAY_GAME_RESULT_GAME, intent.getSerializableExtra(EXTRA_GAME))
                putExtra(PLAY_GAME_RESULT_LEANBACK, intent.getBooleanExtra(EXTRA_LEANBACK, false))
            }

        setResult(RESULT_OK, resultIntent)
        finishAndExitProcess()
    }

    private fun performUnexpectedErrorFinish(exception: Throwable) {
        Timber.e(exception, "Handling java exception in BaseGameActivity")
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, exception.message)
            }

        setResult(RESULT_UNEXPECTED_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun performErrorFinish(message: String) {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, message)
            }

        setResult(RESULT_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun finishAndExitProcess() {
        onFinishTriggered()
        GlobalScope.launch {
            delay(animationDuration().toLong())
            GameService.requestTermination()
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    open fun onFinishTriggered() {
        finishTriggered = true
    }

    override fun onStop() {
        if (!finishTriggered && !isFinishing && !isChangingConfigurations) {
            baseGameScreenViewModel.requestBackgroundSave()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            GameService.requestTermination()
        }
        super.onDestroy()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_REQUEST) {
            Timber.i("Game menu dialog response: ${data?.extras.dump()}")
            if (data?.getBooleanExtra(GameMenuContract.RESULT_RESET, false) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.reset()
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_SAVE) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.saveSlot(data.getIntExtra(GameMenuContract.RESULT_SAVE, 0))
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_LOAD) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.loadSlot(data.getIntExtra(GameMenuContract.RESULT_LOAD, 0))
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_LOAD_LOCAL_SAVE, false) == true) {
                launchLocalSavePicker()
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_QUIT, false) == true) {
                baseGameScreenViewModel.requestFinish()
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_DISK) == true) {
                val index = data.getIntExtra(GameMenuContract.RESULT_CHANGE_DISK, 0)
                baseGameScreenViewModel.retroGameView.retroGameView?.changeDisk(index)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_AUDIO) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    this.audioEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_AUDIO,
                            true,
                        )
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    val fastForwardEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_FAST_FORWARD,
                            false,
                        )
                    this.frameSpeed = if (fastForwardEnabled) 2 else 1
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, false) == true) {
                // v1.20.5: the touch-button editor lives INSIDE the unified layout editor —
                // legacy entries (TV menu / pref_game_edit_touch_controls) open it in controls mode.
                baseGameScreenViewModel.toggleEditScreenLayout(true)
                baseGameScreenViewModel.setEditControlsMode(true)
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_EDIT_SCREEN_LAYOUT, false) == true) {
                baseGameScreenViewModel.toggleEditScreenLayout(true)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_TOGGLE_TOUCH_CONTROLS) == true) {
                val enabled = data.getBooleanExtra(GameMenuContract.RESULT_TOGGLE_TOUCH_CONTROLS, true)
                baseGameScreenViewModel.setTouchControllerVisible(enabled)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_TILT_CONFIG) == true) {
                val tiltConfig = data.serializable<TiltConfiguration>(GameMenuContract.RESULT_CHANGE_TILT_CONFIG)
                baseGameScreenViewModel.changeTiltConfiguration(tiltConfig!!)
            }
        } else if (requestCode == REQUEST_CODE_PICK_LOCAL_SAVE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                displayToast(R.string.game_toast_load_local_save_failed)
                return
            }
            GlobalScope.launch(Dispatchers.Main) {
                loadLocalSave(uri)
            }
        }
    }

    private fun launchLocalSavePicker() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            }
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_LOCAL_SAVE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch local save picker")
            displayToast(R.string.game_toast_load_local_save_failed)
        }
    }

    private suspend fun loadLocalSave(uri: Uri) {
        try {
            val result =
                withContext(Dispatchers.IO) {
                    val data =
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@withContext LocalSaveLoadResult.Failure

                    if (data.isEmpty()) {
                        return@withContext LocalSaveLoadResult.Failure
                    }

                    // Oversized saves (e.g. from DraStic or padded dumps) are trimmed to 512KB for NDS compatibility.
                    val trimmedData = if (data.size > 1024 * 1024) data.copyOf(512 * 1024) else data

                    // The written save is always named after the NDS ROM file (game.fileName),
                    // never after the uploaded .sav's own name. melonDS derives its cartridge
                    // save name from the ROM basename, so this guarantees a match even when the
                    // uploaded file is called something else.
                    val baseName = game.fileName.substringBeforeLast(".")
                    val saveDirectory = savesManager.getSaveRAMDirectory()
                    // melonDS reads "ROMbase.srm" from the Save directory, so we write the
                    // imported content as .srm (the old generic .sav write is dropped: melonDS
                    // ignores it and it could leave a stale file behind).
                    File(saveDirectory, "$baseName.srm").writeBytes(trimmedData)

                    Timber.i("Loaded local save into %s (%d bytes)", "$baseName.srm", trimmedData.size)
                    LocalSaveLoadResult.Success(trimmedData)
                }

            if (result is LocalSaveLoadResult.Success) {
                displayToast(getString(R.string.game_toast_load_local_save_success))

                // melonDS (NDS) and several other cores only re-read the cartridge battery save
                // (*.srm) from disk at a fresh boot (retro_load_game). Neither SRAM memory
                // injection (retro_get_memory) nor a soft reset (retro_reset) makes the core reload
                // that file, so the only reliable way to apply the just-imported save is to restart
                // the game session: the relaunched activity builds a brand new core which reads the
                // freshly written *.srm. The current activity is finished WITHOUT writing its
                // in-memory (old) SRAM, otherwise it would overwrite the imported save first.
                restartGameToApplySave()
            } else {
                displayToast(R.string.game_toast_load_local_save_failed)
            }
        } catch (e: Throwable) {
            Timber.e(e, "Failed to load local save")
            displayToast(R.string.game_toast_load_local_save_failed)
        }
    }

    // Relaunches the game with a brand new core so a freshly imported local save (*.srm) is read
    // from disk. The current activity is finished normally (which skips the background save because
    // the activity is finishing), so the in-memory (old) SRAM is NOT written back over our new save.
    private fun restartGameToApplySave() {
        val leanback = intent.getBooleanExtra(EXTRA_LEANBACK, false)
        val gameActivity = if (leanback) TVGameActivity::class.java else GameActivity::class.java

        startActivity(
            Intent(this, gameActivity).apply {
                putExtra(EXTRA_GAME, game)
                putExtra(EXTRA_LOAD_SAVE, false)
                putExtra(EXTRA_LEANBACK, leanback)
                putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
            },
        )
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private sealed interface LocalSaveLoadResult {
        data class Success(val data: ByteArray) : LocalSaveLoadResult

        data object Failure : LocalSaveLoadResult
    }

    companion object {
        const val DIALOG_REQUEST = 100
        private const val REQUEST_CODE_PICK_LOCAL_SAVE = 101

        private const val EXTRA_GAME = "GAME"
        private const val EXTRA_LOAD_SAVE = "LOAD_SAVE"
        private const val EXTRA_LEANBACK = "LEANBACK"
        private const val EXTRA_SYSTEM_CORE_CONFIG = "EXTRA_SYSTEM_CORE_CONFIG"

        const val REQUEST_PLAY_GAME = 1001
        const val PLAY_GAME_RESULT_SESSION_DURATION = "PLAY_GAME_RESULT_SESSION_DURATION"
        const val PLAY_GAME_RESULT_GAME = "PLAY_GAME_RESULT_GAME"
        const val PLAY_GAME_RESULT_LEANBACK = "PLAY_GAME_RESULT_LEANBACK"
        const val PLAY_GAME_RESULT_ERROR = "PLAY_GAME_RESULT_ERROR"

        const val RESULT_ERROR = Activity.RESULT_FIRST_USER + 2
        const val RESULT_UNEXPECTED_ERROR = Activity.RESULT_FIRST_USER + 3

        fun launchGame(
            activity: Activity,
            systemCoreConfig: SystemCoreConfig,
            game: Game,
            loadSave: Boolean,
            useLeanback: Boolean,
        ) {
            val gameActivity =
                if (useLeanback) {
                    TVGameActivity::class.java
                } else {
                    GameActivity::class.java
                }
            activity.startActivityForResult(
                Intent(activity, gameActivity).apply {
                    putExtra(EXTRA_GAME, game)
                    putExtra(EXTRA_LOAD_SAVE, loadSave)
                    putExtra(EXTRA_LEANBACK, useLeanback)
                    putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                },
                REQUEST_PLAY_GAME,
            )
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
