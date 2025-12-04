package com.tottodrillo.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import java.util.UUID

/**
 * BroadcastReceiver per gestire l'azione "Interrompi estrazione" dalla notifica
 */
class ExtractionCancelReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL_EXTRACTION) {
            val workIdString = intent.getStringExtra(EXTRA_WORK_ID)
            if (workIdString != null) {
                try {
                    val workId = UUID.fromString(workIdString)
                    Log.d("ExtractionCancelReceiver", "❌ Cancellazione estrazione workId: $workId")
                    WorkManager.getInstance(context).cancelWorkById(workId)
                } catch (e: Exception) {
                    Log.e("ExtractionCancelReceiver", "Errore nella cancellazione dell'estrazione", e)
                }
            }
        }
    }
    
    companion object {
        const val ACTION_CANCEL_EXTRACTION = "com.tottodrillo.CANCEL_EXTRACTION"
        const val EXTRA_WORK_ID = "work_id"
    }
}

