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
import com.crocdb.friends.domain.model.ExtractionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
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
        val fileName = sanitizeFileName(downloadLink.name, downloadLink.url)

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
            .putString("rom_slug", romSlug)
            .build()

        // Crea download work request
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(TAG_DOWNLOAD)
            .addTag(taskId)
            .addTag(romSlug) // Aggiungi romSlug come tag per poterlo filtrare
            .build()

        // Solo download; l'estrazione è sempre manuale
        workManager.enqueueUniqueWork(
            taskId,
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

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
     * Avvia estrazione manuale in una cartella scelta
     */
    suspend fun startExtraction(
        archivePath: String,
        extractionPath: String,
        romTitle: String,
        romSlug: String? = null
    ): UUID {
        val config = configRepository.downloadConfig.first()

        // Estrai il nome del file dall'archivePath (il percorso completo)
        val archiveFile = File(archivePath)
        val fileName = archiveFile.name
        
        // La directory base dove è stato scaricato il file (per il file .status)
        // Assicurati che archivePath sia nella directory base di download
        val downloadBasePath = config.downloadPath

        val extractionData = Data.Builder()
            .putString(ExtractionWorker.KEY_ARCHIVE_PATH, archivePath)
            .putString(ExtractionWorker.KEY_EXTRACTION_PATH, extractionPath)
            .putBoolean(ExtractionWorker.KEY_DELETE_ARCHIVE, config.deleteArchiveAfterExtraction)
            .putString(ExtractionWorker.KEY_ROM_TITLE, romTitle)
            .putString(ExtractionWorker.KEY_ROM_SLUG, romSlug)
            .putBoolean(ExtractionWorker.KEY_NOTIFICATIONS_ENABLED, config.notificationsEnabled)
            .putString(ExtractionWorker.KEY_FILE_NAME, fileName) // Passa il nome del file per creare il .txt
            .putString(ExtractionWorker.KEY_DOWNLOAD_BASE_PATH, downloadBasePath) // Directory base per il file .status
            .build()

        val extractionRequest = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(extractionData)
            .addTag(TAG_EXTRACTION)
            .build()

        workManager.enqueue(extractionRequest)
        return extractionRequest.id
    }

    /**
     * Osserva lo stato di un'estrazione
     */
    fun observeExtraction(workId: UUID): Flow<ExtractionStatus> {
        return workManager.getWorkInfoByIdFlow(workId).map { workInfo ->
            workInfo?.let { convertWorkInfoToExtractionStatus(it) } ?: ExtractionStatus.Idle
        }
    }

    /**
     * Converte WorkInfo in ExtractionStatus
     */
    private fun convertWorkInfoToExtractionStatus(workInfo: WorkInfo): ExtractionStatus {
        val progress = workInfo.progress
        val progressPercent = progress.getInt(ExtractionWorker.PROGRESS_PERCENTAGE, 0)
        val currentFile = progress.getString(ExtractionWorker.PROGRESS_CURRENT_FILE) ?: ""

        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> {
                android.util.Log.d("DownloadManager", "🔄 [PASSO 2] convertWorkInfoToExtractionStatus: Stato ENQUEUED -> Idle")
                ExtractionStatus.Idle
            }
            WorkInfo.State.RUNNING -> {
                android.util.Log.d("DownloadManager", "🔄 [PASSO 2] convertWorkInfoToExtractionStatus: Stato RUNNING -> InProgress($progressPercent%, $currentFile)")
                ExtractionStatus.InProgress(progressPercent, currentFile)
            }
            WorkInfo.State.SUCCEEDED -> {
                val extractedPath = workInfo.outputData.getString(ExtractionWorker.RESULT_EXTRACTED_PATH) ?: ""
                val filesCount = workInfo.outputData.getInt(ExtractionWorker.RESULT_FILES_COUNT, 0)
                android.util.Log.i("DownloadManager", "✅ [PASSO 2] convertWorkInfoToExtractionStatus: Stato SUCCEEDED -> Completed(path=$extractedPath, count=$filesCount)")
                ExtractionStatus.Completed(extractedPath, filesCount)
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString("error") ?: "Errore sconosciuto"
                android.util.Log.e("DownloadManager", "❌ [PASSO 2] convertWorkInfoToExtractionStatus: Stato FAILED -> Failed($error)")
                ExtractionStatus.Failed(error)
            }
            WorkInfo.State.CANCELLED -> {
                android.util.Log.w("DownloadManager", "⚠️ [PASSO 2] convertWorkInfoToExtractionStatus: Stato CANCELLED -> Failed")
                ExtractionStatus.Failed("Estrazione cancellata")
            }
            WorkInfo.State.BLOCKED -> {
                android.util.Log.d("DownloadManager", "🔄 [PASSO 2] convertWorkInfoToExtractionStatus: Stato BLOCKED -> Idle")
                ExtractionStatus.Idle
            }
        }
    }

    /**
     * Converte WorkInfo in DownloadTask
     */
    private fun convertWorkInfoToTask(workInfo: WorkInfo): DownloadTask? {
        // Estrai dati dai tags (il primo tag è sempre il taskId)
        val taskId = workInfo.tags.firstOrNull { it != TAG_DOWNLOAD } ?: return null
        val progress = workInfo.progress

        // Usa dati dal progress e output per creare il task
        val currentBytes = progress.getLong(DownloadWorker.PROGRESS_CURRENT, 0)
        val totalBytes = progress.getLong(DownloadWorker.PROGRESS_MAX, 0)
        val percentage = progress.getInt(DownloadWorker.PROGRESS_PERCENTAGE, 0)

        val status = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> DownloadStatus.Pending("Download")
            WorkInfo.State.RUNNING -> DownloadStatus.InProgress(
                "Download", percentage, currentBytes, totalBytes
            )
            WorkInfo.State.SUCCEEDED -> {
                val filePath = workInfo.outputData.getString("file_path") ?: ""
                DownloadStatus.Completed("Download", filePath)
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString("error") ?: "Errore sconosciuto"
                DownloadStatus.Failed("Download", error)
            }
            WorkInfo.State.CANCELLED -> DownloadStatus.Failed("Download", "Download cancellato")
            WorkInfo.State.BLOCKED -> DownloadStatus.Pending("Download")
        }

        return DownloadTask(
            id = taskId,
            romSlug = "",
            romTitle = "Download",
            url = "",
            fileName = "file",
            targetPath = "",
            status = status,
            totalBytes = totalBytes,
            downloadedBytes = currentBytes,
            startTime = 0,
            isArchive = false,
            willAutoExtract = false
        )
    }

    /**
     * Sanitizza nome file preservando l'estensione
     * Se il nome non ha estensione, prova a recuperarla dall'URL
     */
    private fun sanitizeFileName(fileName: String, url: String): String {
        // Estrai estensione dal nome se presente
        val nameParts = fileName.split('.')
        val hasExtension = nameParts.size > 1 && nameParts.last().length <= 5 // Estensioni max 5 caratteri
        
        // Se non ha estensione, prova a recuperarla dall'URL
        val extension = if (hasExtension) {
            ".${nameParts.last()}"
        } else {
            extractExtensionFromUrl(url) ?: ""
        }
        
        // Sanitizza il nome base (senza estensione)
        val baseName = if (hasExtension) {
            nameParts.dropLast(1).joinToString(".")
        } else {
            fileName
        }
        
        val sanitizedBase = baseName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(255 - extension.length) // Lascia spazio per l'estensione
        
        return "$sanitizedBase$extension"
    }
    
    /**
     * Estrae l'estensione dall'URL (es. .zip, .rar, .7z)
     */
    private fun extractExtensionFromUrl(url: String): String? {
        // Estrai il path dall'URL
        val path = try {
            java.net.URL(url).path
        } catch (e: Exception) {
            null
        } ?: return null
        
        // Cerca estensioni supportate
        val supportedExtensions = listOf(".zip", ".rar", ".7z")
        for (ext in supportedExtensions) {
            if (path.lowercase().endsWith(ext)) {
                return ext
            }
        }
        
        // Se non trova estensioni supportate, prova a estrarre l'ultima estensione
        val lastDot = path.lastIndexOf('.')
        if (lastDot > 0 && lastDot < path.length - 1) {
            val ext = path.substring(lastDot)
            if (ext.length <= 5) { // Estensioni max 5 caratteri
                return ext.lowercase()
            }
        }
        
        return null
    }
    
    /**
     * Verifica se un file è stato scaricato
     * Controlla se esiste il file .status (più affidabile del file stesso)
     */
    suspend fun isFileDownloaded(fileName: String): Boolean {
        val config = configRepository.downloadConfig.first()
        val statusFile = File(config.downloadPath, "$fileName.status")
        val exists = statusFile.exists() && statusFile.isFile
        android.util.Log.d("DownloadManager", "🔍 Verifica file scaricato: $fileName -> $exists (status file: ${statusFile.absolutePath})")
        return exists
    }
    
    /**
     * Verifica se un URL specifico è presente nel file .status
     * Formato file multi-riga: una riga per ogni URL
     * Ogni riga: <URL> o <URL>\t<PATH_ESTRAZIONE>
     */
    private suspend fun isUrlInStatusFile(fileName: String, url: String): Boolean {
        val config = configRepository.downloadConfig.first()
        val statusFile = File(config.downloadPath, "$fileName.status")
        
        if (!statusFile.exists() || !statusFile.isFile) {
            return false
        }
        
        return try {
            val lines = statusFile.readLines().filter { it.isNotBlank() }
            lines.any { line ->
                val lineUrl = if (line.contains('\t')) {
                    line.substringBefore('\t')
                } else {
                    line.trim()
                }
                lineUrl == url
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "❌ Errore lettura URL dal file .status", e)
            false
        }
    }
    
    /**
     * Legge l'URL dal file .status (prima riga, per retrocompatibilità)
     * Formato file multi-riga: una riga per ogni URL
     */
    private suspend fun getUrlFromStatusFile(fileName: String): String? {
        val config = configRepository.downloadConfig.first()
        val statusFile = File(config.downloadPath, "$fileName.status")
        
        if (!statusFile.exists() || !statusFile.isFile) {
            return null
        }
        
        return try {
            val lines = statusFile.readLines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                // Restituisci l'URL della prima riga (per retrocompatibilità)
                val firstLine = lines.first()
                val url = if (firstLine.contains('\t')) {
                    firstLine.substringBefore('\t')
                } else {
                    firstLine.trim()
                }
                android.util.Log.d("DownloadManager", "🔍 URL letto dal file .status (prima riga): $url (totale righe: ${lines.size})")
                url
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "❌ Errore lettura URL dal file .status", e)
            null
        }
    }
    
    /**
     * Verifica se un file è stato estratto e restituisce il path di estrazione per un URL specifico
     * Legge il file .status: Formato multi-riga, ogni riga: <URL>\t<PATH_ESTRAZIONE>
     */
    suspend fun getExtractionPath(fileName: String, url: String? = null): String? {
        val config = configRepository.downloadConfig.first()
        val statusFile = File(config.downloadPath, "$fileName.status")
        
        android.util.Log.d("DownloadManager", "🔍 Verifica estrazione: fileName=$fileName, url=$url -> status file: ${statusFile.absolutePath}, exists: ${statusFile.exists()}")
        
        return if (statusFile.exists() && statusFile.isFile) {
            try {
                val lines = statusFile.readLines().filter { it.isNotBlank() }
                
                // Se è specificato un URL, cerca quella riga specifica
                if (url != null) {
                    val matchingLine = lines.firstOrNull { line ->
                        val lineUrl = if (line.contains('\t')) {
                            line.substringBefore('\t')
                        } else {
                            line.trim()
                        }
                        lineUrl == url && line.contains('\t')
                    }
                    
                    if (matchingLine != null) {
                        val extractionPath = matchingLine.substringAfter('\t')
                        if (extractionPath.isNotEmpty()) {
                            android.util.Log.d("DownloadManager", "✅ Path estrazione trovato per URL specifico: $extractionPath")
                            return extractionPath
                        }
                    }
                    android.util.Log.d("DownloadManager", "ℹ️ Nessuna estrazione trovata per URL specifico: $url")
                    return null
                } else {
                    // Se non è specificato un URL, restituisci il path della prima riga che ha un path (per retrocompatibilità)
                    val firstLineWithPath = lines.firstOrNull { it.contains('\t') }
                    if (firstLineWithPath != null) {
                        val extractionPath = firstLineWithPath.substringAfter('\t')
                        if (extractionPath.isNotEmpty()) {
                            android.util.Log.d("DownloadManager", "✅ Path estrazione trovato (prima riga): $extractionPath")
                            return extractionPath
                        }
                    }
                    android.util.Log.d("DownloadManager", "ℹ️ Nessuna estrazione trovata (file .status contiene solo URL)")
                    return null
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "❌ Errore lettura file .status", e)
                null
            }
        } else {
            android.util.Log.d("DownloadManager", "ℹ️ File .status non trovato: ${statusFile.absolutePath}")
            null
        }
    }
    
    /**
     * Verifica se c'è un download attivo per una ROM
     */
    private suspend fun hasActiveDownloadForRom(romSlug: String): WorkInfo? {
        val workInfos = workManager.getWorkInfosByTag(romSlug).get()
        return workInfos.firstOrNull { workInfo ->
            workInfo.tags.contains(TAG_DOWNLOAD) &&
            (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED)
        }
    }
    
    /**
     * Verifica lo stato di download ed estrazione per un link specifico
     * Verifica l'URL nel file .status per identificare quale link è stato scaricato
     */
    suspend fun checkLinkStatus(link: com.crocdb.friends.domain.model.DownloadLink): Pair<com.crocdb.friends.domain.model.DownloadStatus, com.crocdb.friends.domain.model.ExtractionStatus> {
        val fileName = sanitizeFileName(link.name, link.url)
        android.util.Log.d("DownloadManager", "🔍 Verifica stato link: ${link.name} -> fileName: $fileName, URL: ${link.url}")
        
        if (!isFileDownloaded(fileName)) {
            return Pair(
                com.crocdb.friends.domain.model.DownloadStatus.Idle,
                com.crocdb.friends.domain.model.ExtractionStatus.Idle
            )
        }
        
        // Verifica se l'URL di questo link è presente nel file .status
        if (!isUrlInStatusFile(fileName, link.url)) {
            android.util.Log.d("DownloadManager", "ℹ️ File trovato ma URL non presente nel file .status: link URL=${link.url}")
            return Pair(
                com.crocdb.friends.domain.model.DownloadStatus.Idle,
                com.crocdb.friends.domain.model.ExtractionStatus.Idle
            )
        }
        
        // URL presente nel file .status, questo è il link scaricato
        val config = configRepository.downloadConfig.first()
        val filePath = File(config.downloadPath, fileName).absolutePath
        val downloadStatus = com.crocdb.friends.domain.model.DownloadStatus.Completed(filePath, link.name)
        android.util.Log.i("DownloadManager", "✅ Link scaricato trovato: $filePath")
        
        // Verifica se è stato estratto (per questo URL specifico)
        val extractionPath = getExtractionPath(fileName, link.url)
        val extractionStatus = if (extractionPath != null) {
            // Conta i file estratti
            val extractedDir = File(extractionPath)
            val filesCount = if (extractedDir.exists() && extractedDir.isDirectory) {
                extractedDir.listFiles()?.size ?: 0
            } else {
                0
            }
            android.util.Log.i("DownloadManager", "✅ Estrazione trovata: $extractionPath con $filesCount file")
            com.crocdb.friends.domain.model.ExtractionStatus.Completed(extractionPath, filesCount)
        } else {
            android.util.Log.d("DownloadManager", "ℹ️ Nessuna estrazione trovata per: $fileName")
            com.crocdb.friends.domain.model.ExtractionStatus.Idle
        }
        
        return Pair(downloadStatus, extractionStatus)
    }
    
    /**
     * Verifica lo stato di download ed estrazione per una ROM
     * Restituisce lo stato per il primo link che corrisponde a un file scaricato
     */
    suspend fun checkRomStatus(romSlug: String, downloadLinks: List<com.crocdb.friends.domain.model.DownloadLink>): Pair<com.crocdb.friends.domain.model.DownloadStatus, com.crocdb.friends.domain.model.ExtractionStatus> {
        android.util.Log.d("DownloadManager", "🔍 Verifica stato ROM: $romSlug con ${downloadLinks.size} link")
        
        // PRIMA: Verifica se c'è un download attivo per questa ROM
        val activeDownload = hasActiveDownloadForRom(romSlug)
        if (activeDownload != null) {
            android.util.Log.i("DownloadManager", "🔄 Download attivo trovato per ROM: $romSlug")
            // C'è un download in corso, restituisci lo stato InProgress
            val progress = activeDownload.progress
            val progressPercent = progress.getInt(DownloadWorker.PROGRESS_PERCENTAGE, 0)
            val currentBytes = progress.getLong(DownloadWorker.PROGRESS_CURRENT, 0)
            val totalBytes = progress.getLong(DownloadWorker.PROGRESS_MAX, 0)
            
            val downloadStatus = com.crocdb.friends.domain.model.DownloadStatus.InProgress(
                "Download",
                progressPercent,
                currentBytes,
                totalBytes
            )
            return Pair(downloadStatus, com.crocdb.friends.domain.model.ExtractionStatus.Idle)
        }
        
        // SECONDO: Verifica ogni link per trovare quello scaricato
        for (link in downloadLinks) {
            val (downloadStatus, extractionStatus) = checkLinkStatus(link)
            if (downloadStatus !is com.crocdb.friends.domain.model.DownloadStatus.Idle) {
                return Pair(downloadStatus, extractionStatus)
            }
        }
        
        // Nessun file trovato e nessun download attivo
        android.util.Log.d("DownloadManager", "ℹ️ Nessun file scaricato trovato per ROM: $romSlug")
        return Pair(
            com.crocdb.friends.domain.model.DownloadStatus.Idle,
            com.crocdb.friends.domain.model.ExtractionStatus.Idle
        )
    }

    companion object {
        private const val TAG_DOWNLOAD = "download"
        private const val TAG_EXTRACTION = "extraction"
    }
}

class InsufficientStorageException(message: String) : Exception(message)
