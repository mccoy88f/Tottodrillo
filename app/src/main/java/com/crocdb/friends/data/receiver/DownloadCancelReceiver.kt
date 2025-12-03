package com.crocdb.friends.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import java.util.UUID

/**
 * BroadcastReceiver per gestire l'azione "Interrompi download" dalla notifica
 */
class DownloadCancelReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL_DOWNLOAD) {
            val workIdString = intent.getStringExtra(EXTRA_WORK_ID)
            if (workIdString != null) {
                try {
                    val workId = UUID.fromString(workIdString)
                    Log.d("DownloadCancelReceiver", "❌ Cancellazione download workId: $workId")
                    WorkManager.getInstance(context).cancelWorkById(workId)
                } catch (e: Exception) {
                    Log.e("DownloadCancelReceiver", "Errore nella cancellazione del download", e)
                }
            }
        }
    }
    
    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.crocdb.friends.CANCEL_DOWNLOAD"
        const val EXTRA_WORK_ID = "work_id"
    }
}

