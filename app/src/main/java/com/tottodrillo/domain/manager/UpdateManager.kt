package com.tottodrillo.domain.manager

import android.content.Context
import android.content.pm.PackageManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modello per una release GitHub
 */
data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String,
    val name: String,
    val body: String?,
    @SerializedName("published_at")
    val publishedAt: String,
    val assets: List<ReleaseAsset> = emptyList()
)

/**
 * Modello per un asset di release (APK)
 */
data class ReleaseAsset(
    val name: String,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,
    val size: Long
)

/**
 * Manager per verificare e gestire aggiornamenti dell'app da GitHub
 */
@Singleton
class UpdateManager @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/mccoy88f/Tottodrillo/releases/latest"
        private const val GITHUB_REPO_OWNER = "mccoy88f"
        private const val GITHUB_REPO_NAME = "Tottodrillo"
    }

    /**
     * Ottiene la versione corrente dell'app
     */
    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Errore ottenimento versione", e)
            0
        }
    }

    /**
     * Ottiene la versione corrente dell'app come stringa
     */
    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            packageInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Errore ottenimento versionName", e)
            "0.0.0"
        }
    }

    /**
     * Verifica se c'è una nuova release disponibile su GitHub
     * @return GitHubRelease se c'è un aggiornamento disponibile, null altrimenti
     */
    suspend fun checkForUpdate(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("UpdateManager", "Errore richiesta GitHub: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                android.util.Log.e("UpdateManager", "Response body null")
                return@withContext null
            }

            val release: GitHubRelease = gson.fromJson(responseBody, GitHubRelease::class.java)
            
            // Confronta il versionName invece del versionCode per maggiore affidabilità
            val currentVersionName = getCurrentVersionName()
            val releaseVersion = normalizeVersion(release.tagName)
            
            android.util.Log.d("UpdateManager", "Versione corrente: $currentVersionName, Release: ${release.tagName} (normalizzata: $releaseVersion)")
            
            // Se la release ha una versione più recente, c'è un aggiornamento
            if (isVersionNewer(releaseVersion, currentVersionName)) {
                // Verifica che ci sia un APK disponibile
                val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset != null) {
                    return@withContext release
                }
            }
            }
            
            return@withContext null
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Errore verifica aggiornamento", e)
            return@withContext null
        }
    }

    /**
     * Normalizza una versione rimuovendo il prefisso "v" o "V"
     * Esempi: "v2.5.0" -> "2.5.0", "V2.1" -> "2.1"
     */
    private fun normalizeVersion(version: String): String {
        return version.removePrefix("v").removePrefix("V").trim()
    }
    
    /**
     * Confronta due versioni e restituisce true se newVersion è più recente di oldVersion
     * Supporta versioni nel formato semver (es. "1.0.0", "2.5.0", "2.1")
     */
    private fun isVersionNewer(newVersion: String, oldVersion: String): Boolean {
        val newParts = newVersion.split(".").mapNotNull { it.toIntOrNull() }
        val oldParts = oldVersion.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(newParts.size, oldParts.size)
        
        for (i in 0 until maxLength) {
            val newPart = newParts.getOrElse(i) { 0 }
            val oldPart = oldParts.getOrElse(i) { 0 }
            
            when {
                newPart > oldPart -> return true
                newPart < oldPart -> return false
                // Continua al prossimo livello se sono uguali
            }
        }
        
        return false // Le versioni sono uguali
    }

    /**
     * Ottiene l'URL dell'APK dalla release
     */
    fun getApkDownloadUrl(release: GitHubRelease): String? {
        return release.assets
            .find { it.name.endsWith(".apk", ignoreCase = true) }
            ?.browserDownloadUrl
    }
}

