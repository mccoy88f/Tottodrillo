package com.crocdb.friends.data.remote.interceptor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor per verificare la connettività di rete
 */
class NetworkConnectionInterceptor(
    private val context: Context
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isNetworkAvailable()) {
            throw NoConnectivityException()
        }
        return chain.proceed(chain.request())
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as? ConnectivityManager ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class NoConnectivityException : IOException("Nessuna connessione internet disponibile")

/**
 * Interceptor per aggiungere headers comuni
 */
class HeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "CrocdbFriends/1.0.0 Android")
            .build()
        
        return chain.proceed(request)
    }
}

/**
 * Interceptor per retry automatico in caso di fallimento
 */
class RetryInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response: Response? = null
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                response?.close() // Chiude la risposta precedente se esiste
                response = chain.proceed(chain.request())
                
                if (response.isSuccessful) {
                    return response
                }
                
                // Non ritenta per errori client (4xx)
                if (response.code in 400..499) {
                    return response
                }
                
            } catch (e: Exception) {
                lastException = e
                if (attempt == maxRetries - 1) {
                    throw e
                }
            }
            
            attempt++
            
            // Backoff esponenziale
            if (attempt < maxRetries) {
                Thread.sleep(1000L * attempt)
            }
        }

        return response ?: throw lastException ?: IOException("Retry fallito")
    }
}
