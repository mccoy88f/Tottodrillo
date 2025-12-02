package com.crocdb.friends.data.worker

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

        Log.d(TAG, "Avvio estrazione: archivePath=$archivePath, extractionPath=$extractionPath")

        // Leggi configurazione notifiche
        val notificationsEnabled = inputData.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

        try {
            if (notificationsEnabled) {
                createNotificationChannel()
                setForeground(createForegroundInfo(romTitle, 0, "", romSlug))
            }

            val archiveFile = File(archivePath)
            Log.d(TAG, "Verifica archivio: exists=${archiveFile.exists()}, path=${archiveFile.absolutePath}, readable=${archiveFile.canRead()}")
            
            if (!archiveFile.exists()) {
                val errorMsg = "File archivio non trovato: $archivePath"
                Log.e(TAG, errorMsg)
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
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "File archivio non leggibile", romSlug)
                }
                return@withContext Result.failure(
                    workDataOf("error" to errorMsg)
                )
            }

            // Verifica cartella di estrazione
            val extractionDir = File(extractionPath)
            Log.d(TAG, "Verifica cartella estrazione: exists=${extractionDir.exists()}, writable=${extractionDir.canWrite()}, path=${extractionDir.absolutePath}")
            
            if (!extractionDir.exists()) {
                val created = extractionDir.mkdirs()
                Log.d(TAG, "Tentativo creazione cartella: success=$created")
                if (!created || !extractionDir.exists()) {
                    val errorMsg = "Impossibile creare cartella di estrazione: $extractionPath"
                    Log.e(TAG, errorMsg)
                    if (notificationsEnabled) {
                        showErrorNotification(romTitle, "Impossibile creare cartella di estrazione", romSlug)
                    }
                    return@withContext Result.failure(
                        workDataOf("error" to errorMsg)
                    )
                }
            }
            
            if (!extractionDir.canWrite()) {
                val errorMsg = "Cartella di estrazione non scrivibile: $extractionPath"
                Log.e(TAG, errorMsg)
                if (notificationsEnabled) {
                    showErrorNotification(romTitle, "Cartella di estrazione non scrivibile", romSlug)
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
                    Log.d(TAG, "Estrazione ZIP in corso...")
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
                    val errorMsg = "Formato archivio non supportato o file non valido"
                    Log.e(TAG, errorMsg)
                    if (notificationsEnabled) {
                        showErrorNotification(romTitle, errorMsg, romSlug)
                    }
                    return@withContext Result.failure(
                        workDataOf("error" to errorMsg)
                    )
                }
            }
            
            Log.d(TAG, "Estrazione completata: $extractedFiles file estratti")

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
                
                // Trova la riga che non ha ancora un path di estrazione (non contiene tab)
                // Se ci sono più righe senza path, aggiorniamo la prima
                var updated = false
                val updatedLines = existingLines.map { line ->
                    if (!updated && !line.contains('\t')) {
                        // Questa riga non ha ancora un path di estrazione, aggiornala
                        updated = true
                        "$line\t$extractionPath"
                    } else {
                        // Mantieni questa riga invariata
                        line
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
                    Log.d(TAG, "✅ File .status aggiornato: ${statusFile.absolutePath} -> Path estrazione: $extractionPath (righe totali: ${finalLines.size})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'aggiornamento del file .status", e)
            }

            // Elimina archivio se richiesto
            if (deleteArchive && extractedFiles > 0) {
                archiveFile.delete()
            }

            Log.i(TAG, "✅ [PASSO 1] ExtractionWorker: Estrazione completata con successo. File estratti: $extractedFiles, Path: $extractionPath")
            
            val resultData = workDataOf(
                RESULT_EXTRACTED_PATH to extractionPath,
                RESULT_FILES_COUNT to extractedFiles
            )
            
            Log.d(TAG, "✅ [PASSO 1] ExtractionWorker: Impostando WorkInfo.State.SUCCEEDED con dati: path=$extractionPath, count=$extractedFiles")
            
            Result.success(resultData)

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Errore durante estrazione"
            Log.e(TAG, "Errore durante estrazione", e)
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
                "Estrazione archivi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Estrazione archivi ZIP in background"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(romTitle: String, progress: Int = 0, currentFile: String = "", romSlug: String? = null): ForegroundInfo {
        val contentText = if (currentFile.isNotEmpty()) {
            "$romTitle\n$currentFile"
        } else {
            romTitle
        }
        
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Estrazione in corso")
            .setContentText(contentText)
            .setSmallIcon(com.crocdb.friends.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
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

    private fun showCompletedNotification(romTitle: String, extractionPath: String, filesCount: Int, extractedFileNames: List<String> = emptyList(), romSlug: String? = null) {
        val contentText = if (extractedFileNames.isNotEmpty() && extractedFileNames.size <= 3) {
            // Mostra i nomi dei file se sono pochi (max 3)
            "$romTitle\n${extractedFileNames.joinToString(", ")}"
        } else if (extractedFileNames.isNotEmpty()) {
            // Mostra i primi 2 file + "... e altri X"
            val others = extractedFileNames.size - 2
            "$romTitle\n${extractedFileNames.take(2).joinToString(", ")}... e altri $others"
        } else {
            "$romTitle • File estratti: $filesCount"
        }
        
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Estrazione completata")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(com.crocdb.friends.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(romTitle: String, error: String, romSlug: String? = null) {
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Estrazione fallita")
            .setContentText("$romTitle: $error")
            .setSmallIcon(com.crocdb.friends.R.drawable.ic_notification)
            .setContentIntent(createPendingIntent(romSlug))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    /**
     * Crea un PendingIntent per aprire l'app alla schermata della ROM
     */
    private fun createPendingIntent(romSlug: String?): PendingIntent {
        val intent = Intent(appContext, com.crocdb.friends.MainActivity::class.java).apply {
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
                                setForeground(createForegroundInfo(romTitle, progress, fileName, romSlug))
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
            showCompletedNotification(romTitle, destDirectory, filesExtracted, extractedFileNames, romSlug)
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
}
