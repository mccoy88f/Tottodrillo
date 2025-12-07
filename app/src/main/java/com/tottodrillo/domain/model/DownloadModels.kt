package com.tottodrillo.domain.model

/**
 * Configurazione download
 */
data class DownloadConfig(
    val downloadPath: String,
    val autoExtractArchives: Boolean = true,
    val deleteArchiveAfterExtraction: Boolean = false,
    val useWifiOnly: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val enableEsDeCompatibility: Boolean = false,
    val esDeRomsPath: String? = null,
    val romInfoSearchProvider: String = "gamefaqs" // "gamefaqs" o "mobygames"
)

/**
 * Task di download attivo
 */
data class DownloadTask(
    val id: String,
    val romSlug: String,
    val romTitle: String,
    val url: String,
    val fileName: String,
    val targetPath: String,
    val status: DownloadStatus,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val startTime: Long,
    val estimatedTimeRemaining: Long? = null,
    val downloadSpeed: Long? = null, // bytes per second
    val isArchive: Boolean = false,
    val willAutoExtract: Boolean = false
) {
    val progressPercentage: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
        } else 0

    val isCompleted: Boolean
        get() = status is DownloadStatus.Completed

    val isFailed: Boolean
        get() = status is DownloadStatus.Failed

    val isActive: Boolean
        get() = status is DownloadStatus.InProgress || status is DownloadStatus.Pending
}

/**
 * Stato di estrazione archivi
 */
sealed class ExtractionStatus {
    data object Idle : ExtractionStatus()
    data class InProgress(val progress: Int, val currentFile: String) : ExtractionStatus()
    data class Completed(val extractedPath: String, val filesCount: Int) : ExtractionStatus()
    data class Failed(val error: String) : ExtractionStatus()
}

/**
 * Entry per la cronologia download
 */
data class DownloadHistoryEntry(
    val id: String,
    val romTitle: String,
    val fileName: String,
    val filePath: String,
    val downloadedAt: Long,
    val fileSize: Long,
    val wasExtracted: Boolean = false
)
