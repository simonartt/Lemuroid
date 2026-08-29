package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.migrators.getSavesMigrator
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SavesManager(private val directoriesManager: DirectoriesManager) {
        suspend fun getSaveRAM(
        game: Game,
        systemCoreConfig: SystemCoreConfig,
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            val baseName = game.fileName.substringBeforeLast(".")
            val extensions = if (game.systemId == "nds") listOf("sav", "srm") else listOf("srm", "sav")
            
            for (ext in extensions) {
                val saveFile = File(directoriesManager.getSavesDirectory(), "$baseName.$ext")
                if (saveFile.exists() && saveFile.length() > 0) {
                    var data = saveFile.readBytes()
                    
                    // Optimization: Handle oversized save files (e.g. from DraStic or padded dumps)
                    // NDS saves are typically max 512KB. If >1MB, it's likely a state or corrupted.
                    if (data.size > 1024 * 1024) {
                        // Try to find valid save data header or trim to 512KB
                        // We look for common patterns or just trim.
                        // Trimming to 512KB is safest for NDS.
                        data = data.copyOf(512 * 1024)
                    }
                    return@withContext data
                }
            }
            
            // Fallback to migrator for old .dsv files
            val migratorSave = systemCoreConfig.getSavesMigrator()?.loadPreviousSaveForGame(game, directoriesManager)
            migratorSave
        }
    }

suspend fun setSaveRAM(
        game: Game,
        data: ByteArray,
    ) {
        withContext(Dispatchers.IO) {
            val result =
                runCatchingWithRetry(FILE_ACCESS_RETRIES) {
                    if (data.isEmpty()) {
                        return@runCatchingWithRetry
                    }

                    val saveFile = getSaveFile(getSaveRAMFileName(game))
                    saveFile.writeBytesAtomic(data)
                }
            result.getOrNull()
        }
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo {
        return withContext(Dispatchers.IO) {
            val saveFile = getSaveFile(getSaveRAMFileName(game))
            val fileExists = saveFile.exists() && saveFile.length() > 0
            SaveInfo(fileExists, saveFile.lastModified())
        }
    }

    /** Returns the directory where RAM saves (.sav / .srm) are stored. */
    fun getSaveRAMDirectory(): File = directoriesManager.getSavesDirectory()

    private suspend fun getSaveFile(fileName: String): File {
        return withContext(Dispatchers.IO) {
            val savesDirectory = directoriesManager.getSavesDirectory()
            File(savesDirectory, fileName)
        }
    }

    /** This name should make it compatible with RetroArch so that users can freely sync saves across the two application. */
    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
