package com.crocdb.friends.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.crocdb.friends.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Worker per gestire download di ROM in background
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_URL = "download_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TARGET_PATH = "target_path"
        const val KEY_ROM_TITLE = "rom_title"
        const val KEY_TASK_ID = "task_id"
        
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_MAX = "progress_max"
        const val PROGRESS_PERCENTAGE = "progress_percentage"
        
        private const val NOTIFICATION_CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001
        
        private const val BUFFER_SIZE = 8192
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return@withContext Result.failure()
        val targetPath = inputData.getString(KEY_TARGET_PATH) ?: return@withContext Result.failure()
        val romTitle = inputData.getString(KEY_ROM_TITLE) ?: "ROM"
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()

        createNotificationChannel()

        try {
            // Set foreground service
            setForeground(createForegroundInfo(romTitle, 0))

            // Esegui download
            val outputFile = File(targetPath, fileName)
            downloadFile(url, outputFile, romTitle)

            // Success
            showCompletedNotification(romTitle, outputFile.absolutePath)
            
            Result.success(workDataOf(
                "file_path" to outputFile.absolutePath,
                "file_size" to outputFile.length()
            ))
        } catch (e: Exception) {
            showErrorNotification(romTitle, e.message ?: "Errore sconosciuto")
            Result.failure(workDataOf("error" to e.message))
        }
    }

    /**
     * Scarica il file e aggiorna il progresso
     */
    private suspend fun downloadFile(url: String, outputFile: File, romTitle: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download fallito: ${response.code}")
            }

            val body = response.body ?: throw Exception("Response body vuoto")
            val contentLength = body.contentLength()
            
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    downloadWithProgress(input, output, contentLength, romTitle)
                }
            }
        }
    }

    /**
     * Scarica con aggiornamento progressivo
     */
    private suspend fun downloadWithProgress(
        input: InputStream,
        output: FileOutputStream,
        totalBytes: Long,
        romTitle: String
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalBytesRead = 0L
        var lastNotificationTime = System.currentTimeMillis()

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            // Aggiorna progresso ogni 500ms
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNotificationTime > 500) {
                val progress = if (totalBytes > 0) {
                    ((totalBytesRead.toFloat() / totalBytes) * 100).toInt()
                } else 0

                setProgress(workDataOf(
                    PROGRESS_CURRENT to totalBytesRead,
                    PROGRESS_MAX to totalBytes,
                    PROGRESS_PERCENTAGE to progress
                ))

                setForeground(createForegroundInfo(romTitle, progress))
                lastNotificationTime = currentTime
            }
        }
    }

    /**
     * Crea notification channel per Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download ROM in background"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea ForegroundInfo per il servizio
     */
    private fun createForegroundInfo(romTitle: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download in corso")
            .setContentText(romTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /**
     * Mostra notifica completamento
     */
    private fun showCompletedNotification(romTitle: String, filePath: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download completato")
            .setContentText(romTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    /**
     * Mostra notifica errore
     */
    private fun showErrorNotification(romTitle: String, error: String) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download fallito")
            .setContentText("$romTitle: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }
}
