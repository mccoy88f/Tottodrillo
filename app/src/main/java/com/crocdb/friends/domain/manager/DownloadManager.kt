package com.crocdb.friends.domain.manager

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.crocdb.friends.data.repository.DownloadConfigRepository
import com.crocdb.friends.data.worker.DownloadWorker
import com.crocdb.friends.data.worker.ExtractionWorker
import com.crocdb.friends.domain.model.DownloadLink
import com.crocdb.friends.domain.model.DownloadStatus
import com.crocdb.friends.domain.model.DownloadTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager per gestire download e estrazioni
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: DownloadConfigRepository
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Avvia download di una ROM
     */
    suspend fun startDownload(
        romSlug: String,
        romTitle: String,
        downloadLink: DownloadLink,
        customPath: String? = null
    ): UUID {
        val config = configRepository.downloadConfig.first()
        val targetPath = customPath ?: config.downloadPath
        
        // Verifica spazio disponibile
        val availableSpace = configRepository.getAvailableSpace(targetPath)
        // Stima minima: 50MB per ROM
        if (availableSpace < 50 * 1024 * 1024) {
            throw InsufficientStorageException("Spazio insufficiente")
        }

        // Assicura che la directory esista
        if (!configRepository.ensureDirectoryExists(targetPath)) {
            throw Exception("Impossibile creare directory di download")
        }

        val taskId = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(downloadLink.name)
        val isArchive = ExtractionWorker.isArchive(fileName)
        val willExtract = isArchive && 
                         ExtractionWorker.isSupportedArchive(fileName) && 
                         config.autoExtractArchives

        // Crea constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (config.useWifiOnly) NetworkType.UNMETERED 
                else NetworkType.CONNECTED
            )
            .setRequiresStorageNotLow(true)
            .build()

        // Input data per il worker
        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_URL, downloadLink.url)
            .putString(DownloadWorker.KEY_FILE_NAME, fileName)
            .putString(DownloadWorker.KEY_TARGET_PATH, targetPath)
            .putString(DownloadWorker.KEY_ROM_TITLE, romTitle)
            .putString(DownloadWorker.KEY_TASK_ID, taskId)
            .build()

        // Crea download work request
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(TAG_DOWNLOAD)
            .addTag(taskId)
            .build()

        // Se deve estrarre, crea chain con extraction worker
        if (willExtract) {
            val extractionData = Data.Builder()
                .putString(ExtractionWorker.KEY_ARCHIVE_PATH, "$targetPath/$fileName")
                .putString(ExtractionWorker.KEY_EXTRACTION_PATH, targetPath)
                .putBoolean(ExtractionWorker.KEY_DELETE_ARCHIVE, config.deleteArchiveAfterExtraction)
                .putString(ExtractionWorker.KEY_ROM_TITLE, romTitle)
                .build()

            val extractionRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
                .setInputData(extractionData)
                .addTag(TAG_EXTRACTION)
                .addTag(taskId)
                .build()

            // Chain: download -> extraction
            workManager.beginUniqueWork(
                taskId,
                ExistingWorkPolicy.KEEP,
                downloadRequest
            ).then(extractionRequest).enqueue()
        } else {
            // Solo download
            workManager.enqueueUniqueWork(
                taskId,
                ExistingWorkPolicy.KEEP,
                downloadRequest
            )
        }

        return downloadRequest.id
    }

    /**
     * Osserva lo stato di un download
     */
    fun observeDownload(workId: UUID): Flow<DownloadTask?> {
        return workManager.getWorkInfoByIdFlow(workId).map { workInfo ->
            workInfo?.let { convertWorkInfoToTask(it) }
        }
    }

    /**
     * Osserva tutti i download attivi
     */
    fun observeActiveDownloads(): Flow<List<DownloadTask>> {
        return workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD).map { workInfos ->
            workInfos.mapNotNull { convertWorkInfoToTask(it) }
        }
    }

    /**
     * Cancella un download
     */
    fun cancelDownload(workId: UUID) {
        workManager.cancelWorkById(workId)
    }

    /**
     * Cancella tutti i download
     */
    fun cancelAllDownloads() {
        workManager.cancelAllWorkByTag(TAG_DOWNLOAD)
    }

    /**
     * Converte WorkInfo in DownloadTask
     */
    private fun convertWorkInfoToTask(workInfo: WorkInfo): DownloadTask? {
        val inputData = workInfo.inputData
        val progress = workInfo.progress

        val romTitle = inputData.getString(DownloadWorker.KEY_ROM_TITLE) ?: return null
        val fileName = inputData.getString(DownloadWorker.KEY_FILE_NAME) ?: return null
        val taskId = inputData.getString(DownloadWorker.KEY_TASK_ID) ?: return null
        val url = inputData.getString(DownloadWorker.KEY_URL) ?: return null
        val targetPath = inputData.getString(DownloadWorker.KEY_TARGET_PATH) ?: return null

        val currentBytes = progress.getLong(DownloadWorker.PROGRESS_CURRENT, 0)
        val totalBytes = progress.getLong(DownloadWorker.PROGRESS_MAX, 0)
        val percentage = progress.getInt(DownloadWorker.PROGRESS_PERCENTAGE, 0)

        val status = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> DownloadStatus.Pending(romTitle)
            WorkInfo.State.RUNNING -> DownloadStatus.InProgress(
                romTitle, percentage, currentBytes, totalBytes
            )
            WorkInfo.State.SUCCEEDED -> {
                val filePath = workInfo.outputData.getString("file_path") ?: ""
                DownloadStatus.Completed(romTitle, filePath)
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString("error") ?: "Errore sconosciuto"
                DownloadStatus.Failed(romTitle, error)
            }
            WorkInfo.State.CANCELLED -> DownloadStatus.Failed(romTitle, "Download cancellato")
            WorkInfo.State.BLOCKED -> DownloadStatus.Pending(romTitle)
        }

        return DownloadTask(
            id = taskId,
            romSlug = "", // Non disponibile dal WorkInfo
            romTitle = romTitle,
            url = url,
            fileName = fileName,
            targetPath = targetPath,
            status = status,
            totalBytes = totalBytes,
            downloadedBytes = currentBytes,
            startTime = 0, // Non disponibile dal WorkInfo
            isArchive = ExtractionWorker.isArchive(fileName),
            willAutoExtract = ExtractionWorker.isSupportedArchive(fileName)
        )
    }

    /**
     * Sanitizza nome file
     */
    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(255) // Max file name length
    }

    companion object {
        private const val TAG_DOWNLOAD = "download"
        private const val TAG_EXTRACTION = "extraction"
    }
}

class InsufficientStorageException(message: String) : Exception(message)
