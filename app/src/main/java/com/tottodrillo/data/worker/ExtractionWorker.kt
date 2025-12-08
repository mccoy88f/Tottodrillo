package com.tottodrillo.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
class ExtractionWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_ARCHIVE_PATH = "archive_path"
        const val KEY_EXTRACTION_PATH = "extraction_path"
        const val KEY_DELETE_ARCHIVE = "delete_archive"
        const val KEY_ROM_TITLE = "rom_title"
        const val KEY_ROM_SLUG = "rom_slug"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_FILE_NAME = "file_name" // Nome del file originale (sanitizzato) usato per il download
        const val KEY_DOWNLOAD_BASE_PATH = "download_base_path" // Directory base dove è stato scaricato il file
        
        const val RESULT_EXTRACTED_PATH = "extracted_path"
        const val RESULT_FILES_COUNT = "files_count"
        
        const val PROGRESS_PERCENTAGE = "progress_percentage"
        const val PROGRESS_CURRENT_FILE = "progress_current_file"
        const val PROGRESS_TOTAL_FILES = "progress_total_files"
        
        private const val BUFFER_SIZE = 8192
        private const val NOTIFICATION_CHANNEL_ID = "extraction_channel"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "ExtractionWorker"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val archivePath = inputData.getString(KEY_ARCHIVE_PATH) 
            ?: return@withContext run {
                Log.e(TAG, "archivePath mancante")
                Result.failure(workDataOf("error" to "Percorso archivio mancante"))
            }
        val extractionPath = inputData.getString(KEY_EXTRACTION_PATH) 
            ?: return@withContext run {
                Log.e(TAG, "extractionPath mancante")
                Result.failure(workDataOf("error" to "Percorso estrazione mancante"))
            }
        val deleteArchive = inputData.getBoolean(KEY_DELETE_ARCHIVE, false)
        val romTitle = inputData.getString(KEY_ROM_TITLE) ?: "ROM"
        val romSlug = inputData.getString(KEY_ROM_SLUG)
        // Nome del file originale (sanitizzato) usato per il download - usato per creare il file .txt
        val fileName = inputData.getString(KEY_FILE_NAME)
        // Directory base dove è stato scaricato il file (per il file .status)
        val downloadBasePath = inputData.getString(KEY_DOWNLOAD_BASE_PATH)

        Log.d(TAG, "Avvio installazione: archivePath=$archivePath, extractionPath=$extractionPath")

        // Leggi configurazione notifiche
        val notificationsEnabled = inputData.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

        try {
            if (notificationsEnabled) {
                createNotificationChannel()
                setForeground(createForegroundInfo(romTitle, 0, "", romSlug, id))
            }

            val archiveFile = File(archivePath)
            Log.d(TAG, "Verifica archivio: exists=${archiveFile.exists()}, path=${archiveFile.absolutePath}, readable=${archiveFile.canRead()}")
            
            if (!archiveFile.exists()) {
                val errorMsg = "File archivio non trovato: $archivePath"
                Log.e(TAG, errorMsg)
                
                // IMPORTANTE: Anche se il file .zip non esiste, salva l'errore nel file .status
                // Il file .status NON deve mai essere eliminato
                saveErrorToStatusFile(archivePath, fileName, downloadBasePath, errorMsg)
                
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "File archivio non trovato", romSlug)
                }
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }
            
            if (!archiveFile.canRead()) {
                val errorMsg = "File archivio non leggibile: $archivePath"
                Log.e(TAG, errorMsg)
                
                // IMPORTANTE: Salva l'errore nel file .status anche in questo caso
                saveErrorToStatusFile(archivePath, fileName, downloadBasePath, errorMsg)
                
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "File archivio non leggibile", romSlug)
                }
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }

            // Verifica cartella di estrazione
            val extractionDir = File(extractionPath)
            // Se il path è su SD card e non è accessibile, potrebbe essere un problema di permessi
            val isSdCard = extractionPath.startsWith("/storage/") && !extractionPath.startsWith("/storage/emulated/")
            if (isSdCard && !extractionDir.exists()) {
                Log.w(TAG, "Path su SD card non accessibile direttamente. Potrebbe essere necessario usare DocumentFile.")
            }
            
            // Verifica che la cartella padre esista e sia scrivibile
            val parentDir = extractionDir.parentFile
            if (parentDir == null) {
                val errorMsg = "Cartella padre è null per path: $extractionPath"
                Log.e(TAG, errorMsg)
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "Cartella di destinazione non valida", romSlug)
                }
                saveErrorToStatusFile(archivePath, fileName, downloadBasePath, errorMsg)
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }
            Log.d(TAG, "   - È directory: ${parentDir.isDirectory}")
            Log.d(TAG, "   - È scrivibile: ${parentDir.canWrite()}")
            
            if (!parentDir.exists()) {
                val errorMsg = "Cartella padre non esiste: ${parentDir.absolutePath}"
                Log.e(TAG, errorMsg)
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "Cartella di destinazione non valida", romSlug)
                }
                saveErrorToStatusFile(archivePath, fileName, downloadBasePath, errorMsg)
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }
            
            if (!parentDir.canWrite()) {
                val errorMsg = "Cartella padre non scrivibile: ${parentDir.absolutePath}. Potrebbe essere un problema di permessi su SD card."
                Log.e(TAG, errorMsg)
                Log.e(TAG, "💡 Suggerimento: Assicurati di aver selezionato la cartella tramite il file picker e di aver concesso i permessi.")
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "Permessi insufficienti per la cartella di destinazione", romSlug)
                }
                saveErrorToStatusFile(archivePath, fileName, downloadBasePath, errorMsg)
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }
            
            // Crea la cartella di installazione se non esiste (NON eliminare se esiste già)
            if (!extractionDir.exists()) {
                // La cartella non esiste, creala
                val created = extractionDir.mkdirs()
                Log.d(TAG, "Tentativo creazione cartella: success=$created")
                if (!created || !extractionDir.exists()) {
                    val errorMsg = "Impossibile creare cartella di installazione: $extractionPath"
                    Log.e(TAG, errorMsg)
                    if (notificationsEnabled) {
                        showErrorNotification(romTitle, "Impossibile creare cartella di installazione", romSlug)
                    }
                    return@withContext Result.failure(
                        workDataOf("error" to errorMsg)
                    )
                }
            } else {
                Log.d(TAG, "Cartella già esistente, verifica se è utilizzabile: ${extractionDir.absolutePath}")
            }
            
            // Verifica che la cartella sia scrivibile
            if (!extractionDir.canWrite()) {
                val errorMsg = "Cartella di installazione non scrivibile: $extractionPath"
                Log.e(TAG, errorMsg)
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "Cartella di installazione non scrivibile", romSlug)
                }
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }

            // Determina tipo archivio dal contenuto (magic bytes) o estensione
            val archiveType = detectArchiveType(archiveFile)
            Log.d(TAG, "Tipo archivio rilevato: $archiveType")
            
            val extractedFiles = when (archiveType) {
                ArchiveType.ZIP -> {
                    Log.d(TAG, "Installazione ZIP in corso...")
                    extractZip(archiveFile, extractionPath, romTitle, romSlug, notificationsEnabled)
                }
                ArchiveType.RAR -> {
                    // RAR richiede librerie esterne, per ora solo ZIP
                    val errorMsg = "Formato RAR non ancora supportato"
                    Log.e(TAG, errorMsg)
                    if (notificationsEnabled) {
                        showErrorNotification(romTitle, "Formato RAR non ancora supportato", romSlug)
                    }
                    return@withContext Result.failure(
                        workDataOf("error" to errorMsg)
                    )
                }
                ArchiveType.UNKNOWN -> {
                    // File non-archivio: copia/sposta il file nella cartella di destinazione
                    Log.d(TAG, "File non-archivio rilevato, avvio copia/spostamento...")
                    copyFile(archiveFile, extractionPath, romTitle, romSlug, notificationsEnabled)
                }
            }
            
            Log.d(TAG, "Installazione completata: $extractedFiles file installati")

            // Aggiorna il file .status con il path di estrazione
            // Il file .status viene creato dal DownloadWorker quando il download termina
            // Formato multi-riga: una riga per ogni URL scaricato
            // Ogni riga: <URL> o <URL>\t<PATH_ESTRAZIONE>
            // IMPORTANTE: il file .status deve essere sempre nella directory base di download
            try {
                val fileNameToUse = fileName ?: archiveFile.name
                // Usa sempre la directory base di download per il file .status
                val statusFileDir = downloadBasePath ?: archiveFile.parent ?: extractionPath
                val statusFile = File(statusFileDir, "$fileNameToUse.status")
                
                // Leggi tutte le righe esistenti
                val existingLines = if (statusFile.exists()) {
                    statusFile.readLines().filter { it.isNotBlank() }
                } else {
                    emptyList()
                }
                
                // Trova la riga che non ha ancora un path di estrazione (non contiene tab o ha ERROR:)
                // Se ci sono più righe senza path, aggiorniamo la prima
                var updated = false
                val updatedLines = existingLines.map { line ->
                    if (updated) {
                        // Già aggiornata una riga, mantieni le altre invariate
                        line
                    } else {
                        val lineUrl = if (line.contains('\t')) {
                            line.substringBefore('\t')
                        } else {
                            line.trim()
                        }
                        val afterTab = if (line.contains('\t')) {
                            line.substringAfter('\t')
                        } else {
                            ""
                        }
                        
                        // Se questa riga non ha ancora un path (solo URL o ha ERROR:), aggiornala
                        if (!line.contains('\t') || afterTab.startsWith("ERROR:")) {
                            updated = true
                            "$lineUrl\t$extractionPath"
                        } else {
                            // Mantieni questa riga invariata (ha già un path di estrazione)
                            line
                        }
                    }
                }
                
                // Se non abbiamo trovato una riga da aggiornare, aggiungiamo una nuova riga
                // (non dovrebbe succedere, ma gestiamo il caso)
                val finalLines = if (updated) {
                    updatedLines
                } else {
                    // Se tutte le righe hanno già un path, non facciamo nulla
                    // (significa che il file è già stato estratto)
                    existingLines
                }
                
                // Scrivi tutte le righe nel file
                if (finalLines.isNotEmpty()) {
                    statusFile.writeText(finalLines.joinToString("\n"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'aggiornamento del file .status", e)
            }

            // Elimina archivio se richiesto SOLO se l'estrazione è completata con successo
            // IMPORTANTE: Non eliminare mai il file .zip se l'estrazione fallisce
            if (deleteArchive && extractedFiles > 0) {
                try {
                    val deleted = archiveFile.delete()
                    if (deleted) {
                    } else {
                        Log.w(TAG, "⚠️ Impossibile eliminare archivio: ${archiveFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore nell'eliminazione dell'archivio", e)
                }
            }

            Log.i(TAG, "✅ [PASSO 1] ExtractionWorker: Installazione completata con successo. File installati: $extractedFiles, Path: $extractionPath")
            
            val resultData = workDataOf(
                RESULT_EXTRACTED_PATH to extractionPath,
                RESULT_FILES_COUNT to extractedFiles
            )
            
            
            Result.success(resultData)

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Errore durante installazione"
            Log.e(TAG, "❌ Errore durante installazione", e)
            
            // IMPORTANTE: NON eliminare mai il file .zip quando l'installazione fallisce
            // Il file .zip deve rimanere per permettere un nuovo tentativo di installazione
            
            // IMPORTANTE: Il file .status NON deve mai essere eliminato
            // Salva lo stato di fallimento nel file .status per permettere all'UI di mostrare l'errore
            saveErrorToStatusFile(archivePath, fileName, downloadBasePath, errorMsg)
            
            if (notificationsEnabled) {
                showErrorNotification(romTitle, errorMsg, romSlug)
            }
            Result.failure(workDataOf("error" to errorMsg))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Installazione ROM",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Installazione ROM in background"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(romTitle: String, progress: Int = 0, currentFile: String = "", romSlug: String? = null, workId: java.util.UUID = id): ForegroundInfo {
        val contentText = if (currentFile.isNotEmpty()) {
            "$romTitle\n$currentFile"
        } else {
            romTitle
        }
        
        // Crea Intent per l'azione "Interrompi installazione"
        val cancelIntent = Intent(appContext, com.tottodrillo.data.receiver.ExtractionCancelReceiver::class.java).apply {
            action = com.tottodrillo.data.receiver.ExtractionCancelReceiver.ACTION_CANCEL_EXTRACTION
            putExtra(com.tottodrillo.data.receiver.ExtractionCancelReceiver.EXTRA_WORK_ID, workId.toString())
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(com.tottodrillo.R.string.notif_installation_progress))
            .setContentText(contentText)
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                appContext.getString(com.tottodrillo.R.string.notif_cancel_installation),
                cancelPendingIntent
            )
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun showProgressNotification(romTitle: String) {
        // Usato solo per notifiche di errore/completamento
        // Il progresso viene mostrato tramite foreground service
    }

    private fun showCompletedNotification(archiveFileName: String, extractionPath: String, filesCount: Int, extractedFileNames: List<String> = emptyList(), romSlug: String? = null) {
        // Mostra il nome del file senza estensione e poi con estensione
        val fileNameWithoutExt = if (archiveFileName.contains('.')) {
            archiveFileName.substringBeforeLast('.')
        } else {
            archiveFileName
        }
        val fileNameWithExt = archiveFileName
        
        val contentText = "$fileNameWithoutExt\n$fileNameWithExt"
        
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(com.tottodrillo.R.string.notif_installation_completed))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(romTitle: String, error: String, romSlug: String? = null) {
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(com.tottodrillo.R.string.notif_installation_failed))
            .setContentText("$romTitle: $error")
            .setSmallIcon(com.tottodrillo.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    /**
     * Crea un PendingIntent per aprire l'app alla schermata della ROM
     */
    private fun createPendingIntent(romSlug: String?): PendingIntent {
        val intent = Intent(appContext, com.tottodrillo.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (romSlug != null) {
                putExtra("romSlug", romSlug)
                action = "OPEN_ROM_DETAIL"
            }
        }
        
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Estrae file ZIP con tracciamento progresso
     */
    private suspend fun extractZip(zipFile: File, destDirectory: String, romTitle: String, romSlug: String?, notificationsEnabled: Boolean): Int {
        val destDir = File(destDirectory)
        if (!destDir.exists()) {
            val created = destDir.mkdirs()
            Log.d(TAG, "Creazione cartella destinazione: $destDirectory, success=$created")
        }

        // Prima passata: conta i file totali
        var totalFiles = 0
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    totalFiles++
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        Log.d(TAG, "Totale file da estrarre: $totalFiles")

        var filesExtracted = 0
        val extractedFileNames = mutableListOf<String>()

        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry

                while (entry != null) {
                    // Salva il valore corrente per evitare problemi di smart cast
                    val currentEntry = entry!!
                    val filePath = File(destDirectory, currentEntry.name)

                    if (!currentEntry.isDirectory) {
                        // Assicurati che la directory parent esista
                        filePath.parentFile?.mkdirs()
                        
                        // Estrai file
                        try {
                            extractFile(zipIn, filePath)
                            filesExtracted++
                            val fileName = currentEntry.name.substringAfterLast('/')
                            extractedFileNames.add(fileName)
                            
                            // Aggiorna progresso
                            val progress = if (totalFiles > 0) {
                                ((filesExtracted.toFloat() / totalFiles) * 100).toInt()
                            } else 0
                            
                            setProgress(workDataOf(
                                PROGRESS_PERCENTAGE to progress,
                                PROGRESS_CURRENT_FILE to fileName,
                                PROGRESS_TOTAL_FILES to totalFiles
                            ))
                            
                            // Aggiorna notifica foreground
                            if (notificationsEnabled) {
                                setForeground(createForegroundInfo(romTitle, progress, fileName, romSlug, id))
                            }
                            
                            Log.v(TAG, "File estratto: ${currentEntry.name} ($filesExtracted/$totalFiles)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore estrazione file ${currentEntry.name}", e)
                            throw e
                        }
                    } else {
                        // Crea directory
                        filePath.mkdirs()
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante estrazione ZIP", e)
            throw e
        }

        // Mostra notifica completamento con nomi file
        if (notificationsEnabled) {
            showCompletedNotification(zipFile.name, destDirectory, filesExtracted, extractedFileNames, romSlug)
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
     * Copia/sposta un file non-archivio nella cartella di destinazione
     * Questa funzione viene chiamata quando il file non è un archivio supportato (zip, rar, 7z)
     */
    private suspend fun copyFile(
        sourceFile: File,
        destDirectory: String,
        romTitle: String,
        romSlug: String?,
        notificationsEnabled: Boolean
    ): Int {
        try {
            val destDir = File(destDirectory)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val destFile = File(destDir, sourceFile.name)
            
            // Sovrascrivi sempre: elimina il file di destinazione se esiste
            if (destFile.exists()) {
                destFile.delete()
            }

            // Copia il file
            sourceFile.copyTo(destFile, overwrite = true)
            

            // Aggiorna notifica
            if (notificationsEnabled) {
                setForeground(
                    createForegroundInfo(
                        romTitle,
                        100,
                        sourceFile.name,
                        romSlug,
                        id
                    )
                )
                showCompletedNotification(
                    sourceFile.nameWithoutExtension,
                    destDirectory,
                    1,
                    listOf(sourceFile.name),
                    romSlug
                )
            }

            return 1 // Un solo file copiato
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante copia file", e)
            throw e
        }
    }

    /**
     * Enum per i tipi di archivio supportati
     */
    private enum class ArchiveType {
        ZIP, RAR, UNKNOWN
    }

    /**
     * Rileva il tipo di archivio dal contenuto del file (magic bytes) o dall'estensione
     */
    private fun detectArchiveType(file: File): ArchiveType {
        // Prima controlla l'estensione (più veloce)
        val fileName = file.name.lowercase()
        when {
            fileName.endsWith(".zip") -> return ArchiveType.ZIP
            fileName.endsWith(".rar") -> return ArchiveType.RAR
        }

        // Se non ha estensione, controlla i magic bytes
        try {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                val bytesRead = input.read(header)
                
                if (bytesRead >= 4) {
                    // ZIP: PK.. (50 4B 03 04)
                    if (header[0] == 0x50.toByte() && 
                        header[1] == 0x4B.toByte() && 
                        header[2] == 0x03.toByte() && 
                        header[3] == 0x04.toByte()) {
                        Log.d(TAG, "Rilevato ZIP dai magic bytes")
                        return ArchiveType.ZIP
                    }
                    
                    // RAR: Rar! (52 61 72 21)
                    if (header[0] == 0x52.toByte() && 
                        header[1] == 0x61.toByte() && 
                        header[2] == 0x72.toByte() && 
                        header[3] == 0x21.toByte()) {
                        Log.d(TAG, "Rilevato RAR dai magic bytes")
                        return ArchiveType.RAR
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante rilevamento tipo archivio", e)
        }

        Log.w(TAG, "Tipo archivio non riconosciuto per: ${file.name}")
        return ArchiveType.UNKNOWN
    }

    /**
     * Verifica se un file è un archivio supportato
     */
    private fun isArchive(fileName: String): Boolean {
        return fileName.endsWith(".zip", ignoreCase = true) ||
               fileName.endsWith(".rar", ignoreCase = true) ||
               fileName.endsWith(".7z", ignoreCase = true)
    }

    private fun isSupportedArchive(fileName: String): Boolean {
        return fileName.endsWith(".zip", ignoreCase = true)
        // RAR e 7z richiedono librerie esterne
    }
    
    /**
     * Salva un errore nel file .status
     * IMPORTANTE: Il file .status NON deve mai essere eliminato
     * Questa funzione viene chiamata quando l'estrazione fallisce per qualsiasi motivo
     */
    private fun saveErrorToStatusFile(
        archivePath: String,
        fileName: String?,
        downloadBasePath: String?,
        errorMsg: String
    ) {
        try {
            val archiveFile = File(archivePath)
            val fileNameToUse = fileName ?: archiveFile.name
            val statusFileDir = downloadBasePath ?: archiveFile.parent
            if (statusFileDir != null) {
                val statusFile = File(statusFileDir, "$fileNameToUse.status")
                
                // IMPORTANTE: Se il file .status non esiste, non crearlo qui
                // Il file .status viene creato dal DownloadWorker quando il download termina
                // Se non esiste, significa che il download non è mai stato completato
                if (!statusFile.exists()) {
                    Log.w(TAG, "⚠️ File .status non trovato per: $fileNameToUse (download potrebbe non essere completato)")
                    return
                }
                
                // Leggi tutte le righe esistenti
                val existingLines = statusFile.readLines().filter { it.isNotBlank() }
                
                // Trova la prima riga senza stato (solo URL) o con errore e aggiorna con l'errore
                // Formato: <URL>\tERROR:<messaggio>
                var updated = false
                val updatedLines = existingLines.map { line ->
                    if (updated) {
                        // Già aggiornata una riga, mantieni le altre invariate
                        line
                    } else {
                        val lineUrl = if (line.contains('\t')) {
                            line.substringBefore('\t')
                        } else {
                            line.trim()
                        }
                        
                        // Se questa riga non ha ancora uno stato (solo URL), aggiungi l'errore
                        if (!line.contains('\t') && lineUrl.isNotEmpty()) {
                            updated = true
                            "$lineUrl\tERROR:$errorMsg"
                        } else if (line.contains('\t')) {
                            val afterTab = line.substringAfter('\t')
                            // Se c'è già un errore (inizia con ERROR:), sostituiscilo
                            if (afterTab.startsWith("ERROR:")) {
                                updated = true
                                "$lineUrl\tERROR:$errorMsg"
                            } else {
                                // Ha un path di estrazione, non toccarlo
                                line
                            }
                        } else {
                            line
                        }
                    }
                }
                
                // Se non abbiamo trovato una riga da aggiornare, aggiungiamo l'errore alla prima riga
                val finalLines = if (updated) {
                    updatedLines
                } else if (existingLines.isNotEmpty()) {
                    // Aggiorna la prima riga anche se non corrisponde ai criteri
                    existingLines.mapIndexed { index, line ->
                        if (index == 0) {
                            val lineUrl = if (line.contains('\t')) {
                                line.substringBefore('\t')
                            } else {
                                line.trim()
                            }
                            "$lineUrl\tERROR:$errorMsg"
                        } else {
                            line
                        }
                    }
                } else {
                    existingLines
                }
                
                // IMPORTANTE: Scrivi sempre il file .status, anche se vuoto
                // Il file .status NON deve mai essere eliminato
                if (finalLines.isNotEmpty()) {
                    statusFile.writeText(finalLines.joinToString("\n"))
                } else {
                    Log.w(TAG, "⚠️ Nessuna riga da scrivere nel file .status per: $fileNameToUse")
                }
            } else {
                Log.e(TAG, "❌ Impossibile determinare directory per file .status")
            }
        } catch (statusError: Exception) {
            Log.e(TAG, "❌ Errore CRITICO nel salvataggio dello stato di fallimento nel file .status", statusError)
            // Non propagare l'errore, il file .status potrebbe essere corrotto ma non vogliamo perdere l'informazione dell'errore
        }
    }
}
