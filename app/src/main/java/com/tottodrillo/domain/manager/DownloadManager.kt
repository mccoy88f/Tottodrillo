package com.tottodrillo.domain.manager

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tottodrillo.data.repository.DownloadConfigRepository
import com.tottodrillo.data.worker.DownloadWorker
import com.tottodrillo.data.worker.ExtractionWorker
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.domain.model.DownloadStatus
import com.tottodrillo.domain.model.DownloadTask
import com.tottodrillo.domain.model.ExtractionStatus
import com.tottodrillo.util.StoragePermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
        
        // Verifica permessi di accesso ai file (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!StoragePermissionManager.hasManageExternalStoragePermission(context)) {
                throw StoragePermissionException("Permesso di accesso ai file non concesso")
            }
        }
        
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
            .addTag("url:${downloadLink.url}") // Aggiungi URL come tag per identificarlo
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
        // Verifica permessi di accesso ai file (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!StoragePermissionManager.hasManageExternalStoragePermission(context)) {
                throw StoragePermissionException("Permesso di accesso ai file non concesso")
            }
        }
        
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
            .addTag("archive:${archivePath}") // Aggiungi percorso archivio come tag per identificarlo
            .build()

        workManager.enqueue(extractionRequest)
        return extractionRequest.id
    }

    /**
     * Osserva lo stato di un'estrazione
     */
    fun observeExtraction(workId: UUID): Flow<ExtractionStatus> {
        return flow {
            // Emetti lo stato iniziale immediatamente
            try {
                val initialWorkInfo = workManager.getWorkInfoById(workId).get()
                if (initialWorkInfo != null) {
                    val initialStatus = convertWorkInfoToExtractionStatus(initialWorkInfo)
                    emit(initialStatus)
                    
                    // Se il work è già completato o fallito, non c'è bisogno di osservare ulteriori cambiamenti
                    if (initialWorkInfo.state == WorkInfo.State.SUCCEEDED || 
                        initialWorkInfo.state == WorkInfo.State.FAILED || 
                        initialWorkInfo.state == WorkInfo.State.CANCELLED) {
                        return@flow
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "Errore nel recupero stato iniziale estrazione", e)
            }
            
            // Poi osserva i cambiamenti (solo se il work non è già terminato)
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo != null) {
                    val status = convertWorkInfoToExtractionStatus(workInfo)
                    emit(status)
                } else {
                    emit(ExtractionStatus.Idle)
                }
            }
        }
    }

    /**
     * Converte WorkInfo in ExtractionStatus
     */
    fun convertWorkInfoToExtractionStatus(workInfo: WorkInfo): ExtractionStatus {
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
     * Verifica se c'è stato un errore durante l'estrazione per un URL specifico
     * Legge il file .status: Formato <URL>\tERROR:<messaggio>
     */
    private suspend fun getExtractionError(fileName: String, url: String?): String? {
        val config = configRepository.downloadConfig.first()
        val statusFile = File(config.downloadPath, "$fileName.status")
        
        if (!statusFile.exists() || !statusFile.isFile) {
            return null
        }
        
        return try {
            val lines = statusFile.readLines().filter { it.isNotBlank() }
            
            // Se è specificato un URL, cerca quella riga specifica
            if (url != null) {
                val matchingLine = lines.firstOrNull { line ->
                    val lineUrl = if (line.contains('\t')) {
                        line.substringBefore('\t')
                    } else {
                        line.trim()
                    }
                    lineUrl == url && line.contains('\t') && line.substringAfter('\t').startsWith("ERROR:")
                }
                
                if (matchingLine != null) {
                    val errorPart = matchingLine.substringAfter('\t')
                    if (errorPart.startsWith("ERROR:")) {
                        val errorMsg = errorPart.substringAfter("ERROR:")
                        if (errorMsg.isNotEmpty()) {
                            android.util.Log.d("DownloadManager", "⚠️ Errore estrazione trovato per URL specifico: $errorMsg")
                            return errorMsg
                        }
                    }
                }
            } else {
                // Se non è specificato un URL, cerca la prima riga con errore (per retrocompatibilità)
                val firstLineWithError = lines.firstOrNull { 
                    it.contains('\t') && it.substringAfter('\t').startsWith("ERROR:")
                }
                
                if (firstLineWithError != null) {
                    val errorPart = firstLineWithError.substringAfter('\t')
                    if (errorPart.startsWith("ERROR:")) {
                        val errorMsg = errorPart.substringAfter("ERROR:")
                        if (errorMsg.isNotEmpty()) {
                            android.util.Log.d("DownloadManager", "⚠️ Errore estrazione trovato (prima riga): $errorMsg")
                            return errorMsg
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "❌ Errore lettura errore estrazione dal file .status", e)
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
                        if (lineUrl == url && line.contains('\t')) {
                            val afterTab = line.substringAfter('\t')
                            // Ignora le righe con ERROR: e cerca solo path validi
                            afterTab.isNotEmpty() && !afterTab.startsWith("ERROR:")
                        } else {
                            false
                        }
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
                    // Se non è specificato un URL, restituisci il path della prima riga che ha un path valido (per retrocompatibilità)
                    val firstLineWithPath = lines.firstOrNull { line ->
                        line.contains('\t') && !line.substringAfter('\t').startsWith("ERROR:")
                    }
                    if (firstLineWithPath != null) {
                        val extractionPath = firstLineWithPath.substringAfter('\t')
                        if (extractionPath.isNotEmpty()) {
                            android.util.Log.d("DownloadManager", "✅ Path estrazione trovato (prima riga): $extractionPath")
                            return extractionPath
                        }
                    }
                    android.util.Log.d("DownloadManager", "ℹ️ Nessuna estrazione trovata (file .status contiene solo URL o errori)")
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
     * Verifica se c'è un download attivo per un URL specifico
     */
    private suspend fun hasActiveDownloadForUrl(url: String): WorkInfo? {
        val workInfos = workManager.getWorkInfosByTag("url:$url").get()
        return workInfos.firstOrNull { workInfo ->
            workInfo.tags.contains(TAG_DOWNLOAD) &&
            (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED)
        }
    }
    
    /**
     * Ottiene il workId di un download attivo per un URL specifico
     */
    suspend fun getActiveDownloadWorkId(url: String): UUID? {
        val workInfo = hasActiveDownloadForUrl(url)
        return workInfo?.id
    }
    
    /**
     * Verifica se c'è un'estrazione attiva per un percorso file specifico
     */
    private suspend fun hasActiveExtractionForArchivePath(archivePath: String): WorkInfo? {
        val workInfos = workManager.getWorkInfosByTag("archive:$archivePath").get()
        return workInfos.firstOrNull { workInfo ->
            workInfo.tags.contains(TAG_EXTRACTION) &&
            (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED)
        }
    }
    
    /**
     * Ottiene il workId di un'estrazione attiva per un percorso archivio specifico
     */
    suspend fun getActiveExtractionWorkId(archivePath: String): UUID? {
        val workInfo = hasActiveExtractionForArchivePath(archivePath)
        return workInfo?.id
    }
    
    /**
     * Verifica lo stato di download ed estrazione per un link specifico
     * Verifica l'URL nel file .status per identificare quale link è stato scaricato
     */
    suspend fun checkLinkStatus(link: com.tottodrillo.domain.model.DownloadLink): Pair<com.tottodrillo.domain.model.DownloadStatus, com.tottodrillo.domain.model.ExtractionStatus> {
        val fileName = sanitizeFileName(link.name, link.url)
        android.util.Log.d("DownloadManager", "🔍 Verifica stato link: ${link.name} -> fileName: $fileName, URL: ${link.url}")
        
        // PRIMA: Verifica se c'è un download attivo per questo URL specifico
        val activeDownload = hasActiveDownloadForUrl(link.url)
        if (activeDownload != null) {
            android.util.Log.i("DownloadManager", "🔄 Download attivo trovato per URL: ${link.url}")
            val progress = activeDownload.progress
            val progressPercent = progress.getInt(DownloadWorker.PROGRESS_PERCENTAGE, 0)
            val currentBytes = progress.getLong(DownloadWorker.PROGRESS_CURRENT, 0)
            val totalBytes = progress.getLong(DownloadWorker.PROGRESS_MAX, 0)
            
            val downloadStatus = com.tottodrillo.domain.model.DownloadStatus.InProgress(
                link.name,
                progressPercent,
                currentBytes,
                totalBytes
            )
            return Pair(downloadStatus, com.tottodrillo.domain.model.ExtractionStatus.Idle)
        }
        
        // SECONDO: Verifica se il file è stato scaricato
        if (!isFileDownloaded(fileName)) {
            return Pair(
                com.tottodrillo.domain.model.DownloadStatus.Idle,
                com.tottodrillo.domain.model.ExtractionStatus.Idle
            )
        }
        
        // Verifica se l'URL di questo link è presente nel file .status
        if (!isUrlInStatusFile(fileName, link.url)) {
            android.util.Log.d("DownloadManager", "ℹ️ File trovato ma URL non presente nel file .status: link URL=${link.url}")
            return Pair(
                com.tottodrillo.domain.model.DownloadStatus.Idle,
                com.tottodrillo.domain.model.ExtractionStatus.Idle
            )
        }
        
        // URL presente nel file .status, questo è il link scaricato
        val config = configRepository.downloadConfig.first()
        val filePath = File(config.downloadPath, fileName).absolutePath
        val downloadStatus = com.tottodrillo.domain.model.DownloadStatus.Completed(filePath, link.name)
        android.util.Log.i("DownloadManager", "✅ Link scaricato trovato: $filePath")
        
        // TERZO: Verifica se c'è un'estrazione attiva per questo file
        val activeExtraction = hasActiveExtractionForArchivePath(filePath)
        val extractionStatus = if (activeExtraction != null) {
            android.util.Log.i("DownloadManager", "🔄 Estrazione attiva trovata per file: $filePath")
            val progress = activeExtraction.progress
            val progressPercent = progress.getInt(ExtractionWorker.PROGRESS_PERCENTAGE, 0)
            val currentFile = progress.getString(ExtractionWorker.PROGRESS_CURRENT_FILE) ?: ""
            com.tottodrillo.domain.model.ExtractionStatus.InProgress(progressPercent, currentFile)
        } else {
            // Verifica se è stato estratto o se c'è stato un errore (per questo URL specifico)
            val extractionError = getExtractionError(fileName, link.url)
            if (extractionError != null) {
                // C'è stato un errore durante l'estrazione
                android.util.Log.w("DownloadManager", "⚠️ Errore estrazione trovato: $extractionError")
                com.tottodrillo.domain.model.ExtractionStatus.Failed(extractionError)
            } else {
                val extractionPath = getExtractionPath(fileName, link.url)
                if (extractionPath != null) {
                    // Conta i file estratti
                    val extractedDir = File(extractionPath)
                    val filesCount = if (extractedDir.exists() && extractedDir.isDirectory) {
                        extractedDir.listFiles()?.size ?: 0
                    } else {
                        0
                    }
                    android.util.Log.i("DownloadManager", "✅ Estrazione trovata: $extractionPath con $filesCount file")
                    com.tottodrillo.domain.model.ExtractionStatus.Completed(extractionPath, filesCount)
                } else {
                    android.util.Log.d("DownloadManager", "ℹ️ Nessuna estrazione trovata per: $fileName")
                    com.tottodrillo.domain.model.ExtractionStatus.Idle
                }
            }
        }
        
        return Pair(downloadStatus, extractionStatus)
    }
    
    /**
     * Verifica lo stato di download ed estrazione per una ROM
     * Restituisce lo stato per il primo link che corrisponde a un file scaricato
     */
    suspend fun checkRomStatus(romSlug: String, downloadLinks: List<com.tottodrillo.domain.model.DownloadLink>): Pair<com.tottodrillo.domain.model.DownloadStatus, com.tottodrillo.domain.model.ExtractionStatus> {
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
            
            val downloadStatus = com.tottodrillo.domain.model.DownloadStatus.InProgress(
                "Download",
                progressPercent,
                currentBytes,
                totalBytes
            )
            return Pair(downloadStatus, com.tottodrillo.domain.model.ExtractionStatus.Idle)
        }
        
        // SECONDO: Verifica ogni link per trovare quello scaricato
        for (link in downloadLinks) {
            val (downloadStatus, extractionStatus) = checkLinkStatus(link)
            if (downloadStatus !is com.tottodrillo.domain.model.DownloadStatus.Idle) {
                return Pair(downloadStatus, extractionStatus)
            }
        }
        
        // Nessun file trovato e nessun download attivo
        android.util.Log.d("DownloadManager", "ℹ️ Nessun file scaricato trovato per ROM: $romSlug")
        return Pair(
            com.tottodrillo.domain.model.DownloadStatus.Idle,
            com.tottodrillo.domain.model.ExtractionStatus.Idle
        )
    }

    companion object {
        private const val TAG_DOWNLOAD = "download"
        private const val TAG_EXTRACTION = "extraction"
    }
}

class InsufficientStorageException(message: String) : Exception(message)

class StoragePermissionException(message: String) : Exception(message)
