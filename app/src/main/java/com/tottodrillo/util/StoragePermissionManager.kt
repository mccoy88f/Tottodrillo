package com.tottodrillo.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Utility per gestire il permesso MANAGE_EXTERNAL_STORAGE
 * Permette accesso completo ai file su Android 11+ per download ed estrazioni su qualsiasi cartella
 */
object StoragePermissionManager {
    
    /**
     * Verifica se il permesso MANAGE_EXTERNAL_STORAGE è concesso
     * @return true se il permesso è concesso, false altrimenti
     */
    fun hasManageExternalStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            // Su Android < 11, i permessi di storage vengono gestiti diversamente
            // ma generalmente abbiamo accesso con WRITE_EXTERNAL_STORAGE
            true
        }
    }
    
    /**
     * Apre le impostazioni di sistema per concedere il permesso MANAGE_EXTERNAL_STORAGE
     * @param activity Activity necessaria per avviare l'intent
     */
    fun requestManageExternalStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        }
    }
    
    /**
     * Verifica se è necessario richiedere il permesso
     * @return true se siamo su Android 11+ e il permesso non è concesso
     */
    fun shouldRequestPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
               !hasManageExternalStoragePermission(context)
    }
}

