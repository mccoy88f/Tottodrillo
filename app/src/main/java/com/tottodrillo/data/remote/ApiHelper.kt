package com.tottodrillo.data.remote

import com.tottodrillo.data.remote.interceptor.NoConnectivityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Wrapper per eseguire chiamate API con gestione errori uniforme
 */
suspend fun <T> safeApiCall(
    apiCall: suspend () -> Response<T>
): NetworkResult<T> = withContext(Dispatchers.IO) {
    try {
        val response = apiCall()
        
        if (response.isSuccessful) {
            response.body()?.let { data ->
                NetworkResult.Success(data)
            } ?: NetworkResult.Error(NetworkException.ParseError())
        } else {
            val errorMessage = response.errorBody()?.string() ?: "Errore sconosciuto"
            NetworkResult.Error(
                NetworkException.ServerError(response.code(), errorMessage)
            )
        }
    } catch (e: NoConnectivityException) {
        NetworkResult.Error(NetworkException.NoInternet())
    } catch (e: SocketTimeoutException) {
        NetworkResult.Error(NetworkException.Timeout())
    } catch (e: UnknownHostException) {
        NetworkResult.Error(NetworkException.NoInternet())
    } catch (e: Exception) {
        NetworkResult.Error(
            NetworkException.UnknownError(e.message ?: "Errore imprevisto")
        )
    }
}

/**
 * Extension per estrarre dati da ApiResponse
 */
fun <T> NetworkResult<com.tottodrillo.data.model.ApiResponse<T>>.extractData(): NetworkResult<T> {
    return when (this) {
        is NetworkResult.Success -> {
            val apiResponse = this.data
            if (apiResponse.info.error != null) {
                NetworkResult.Error(NetworkException.ApiError(apiResponse.info.error))
            } else {
                NetworkResult.Success(apiResponse.data)
            }
        }
        is NetworkResult.Error -> NetworkResult.Error(this.exception)
        is NetworkResult.Loading -> NetworkResult.Loading
    }
}
