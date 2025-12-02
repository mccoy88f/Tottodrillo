package com.crocdb.friends.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Worker per estrarre archivi ZIP in background
 */
@HiltWorker
class ExtractionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_ARCHIVE_PATH = "archive_path"
        const val KEY_EXTRACTION_PATH = "extraction_path"
        const val KEY_DELETE_ARCHIVE = "delete_archive"
        const val KEY_ROM_TITLE = "rom_title"
        
        const val RESULT_EXTRACTED_PATH = "extracted_path"
        const val RESULT_FILES_COUNT = "files_count"
        
        private const val BUFFER_SIZE = 8192
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val archivePath = inputData.getString(KEY_ARCHIVE_PATH) 
            ?: return@withContext Result.failure()
        val extractionPath = inputData.getString(KEY_EXTRACTION_PATH) 
            ?: return@withContext Result.failure()
        val deleteArchive = inputData.getBoolean(KEY_DELETE_ARCHIVE, false)
        val romTitle = inputData.getString(KEY_ROM_TITLE) ?: "ROM"

        try {
            val archiveFile = File(archivePath)
            if (!archiveFile.exists()) {
                return@withContext Result.failure(
                    workDataOf("error" to "File archivio non trovato")
                )
            }

            // Determina tipo archivio
            val extractedFiles = when {
                archivePath.endsWith(".zip", ignoreCase = true) -> {
                    extractZip(archiveFile, extractionPath)
                }
                archivePath.endsWith(".rar", ignoreCase = true) -> {
                    // RAR richiede librerie esterne, per ora solo ZIP
                    return@withContext Result.failure(
                        workDataOf("error" to "Formato RAR non ancora supportato")
                    )
                }
                else -> {
                    return@withContext Result.failure(
                        workDataOf("error" to "Formato archivio non supportato")
                    )
                }
            }

            // Elimina archivio se richiesto
            if (deleteArchive && extractedFiles > 0) {
                archiveFile.delete()
            }

            Result.success(workDataOf(
                RESULT_EXTRACTED_PATH to extractionPath,
                RESULT_FILES_COUNT to extractedFiles
            ))

        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: "Errore durante estrazione")))
        }
    }

    /**
     * Estrae file ZIP
     */
    private suspend fun extractZip(zipFile: File, destDirectory: String): Int {
        val destDir = File(destDirectory)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        var filesExtracted = 0

        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry

            while (entry != null) {
                val filePath = File(destDirectory, entry.name)

                if (!entry.isDirectory) {
                    // Assicurati che la directory parent esista
                    filePath.parentFile?.mkdirs()
                    
                    // Estrai file
                    extractFile(zipIn, filePath)
                    filesExtracted++
                } else {
                    // Crea directory
                    filePath.mkdirs()
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        return filesExtracted
    }

    /**
     * Estrae singolo file da stream
     */
    private fun extractFile(zipIn: ZipInputStream, destFile: File) {
        FileOutputStream(destFile).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var len: Int
            while (zipIn.read(buffer).also { len = it } > 0) {
                output.write(buffer, 0, len)
            }
        }
    }

    /**
     * Verifica se un file è un archivio supportato
     */
    companion object {
        fun isArchive(fileName: String): Boolean {
            return fileName.endsWith(".zip", ignoreCase = true) ||
                   fileName.endsWith(".rar", ignoreCase = true) ||
                   fileName.endsWith(".7z", ignoreCase = true)
        }

        fun isSupportedArchive(fileName: String): Boolean {
            return fileName.endsWith(".zip", ignoreCase = true)
            // RAR e 7z richiedono librerie esterne
        }
    }
}
