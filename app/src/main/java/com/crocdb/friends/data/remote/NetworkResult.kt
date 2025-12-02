package com.crocdb.friends.data.remote

import retrofit2.Response

/**
 * Rappresenta il risultato di un'operazione di rete
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val exception: NetworkException) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}

/**
 * Eccezioni personalizzate per errori di rete
 */
sealed class NetworkException(message: String) : Exception(message) {
    class NoInternet : NetworkException("Nessuna connessione internet disponibile")
    class Timeout : NetworkException("Timeout della richiesta")
    class ServerError(val code: Int, message: String) : NetworkException("Errore server ($code): $message")
    class ApiError(message: String) : NetworkException("Errore API: $message")
    class UnknownError(message: String) : NetworkException("Errore sconosciuto: $message")
    class ParseError : NetworkException("Errore nel parsing della risposta")
}

/**
 * Extension function per gestire le Response di Retrofit
 */
fun <T> Response<T>.toNetworkResult(): NetworkResult<T> {
    return try {
        if (isSuccessful) {
            body()?.let { data ->
                NetworkResult.Success(data)
            } ?: NetworkResult.Error(NetworkException.ParseError())
        } else {
            val errorMessage = errorBody()?.string() ?: "Errore sconosciuto"
            NetworkResult.Error(
                NetworkException.ServerError(code(), errorMessage)
            )
        }
    } catch (e: Exception) {
        NetworkResult.Error(NetworkException.UnknownError(e.message ?: "Errore imprevisto"))
    }
}

/**
 * Helper per estrarre messaggi user-friendly dagli errori
 */
fun NetworkException.getUserMessage(): String = when (this) {
    is NetworkException.NoInternet -> "Controlla la tua connessione internet"
    is NetworkException.Timeout -> "La richiesta ha impiegato troppo tempo"
    is NetworkException.ServerError -> "Il server ha restituito un errore"
    is NetworkException.ApiError -> message ?: "Errore nell'API"
    is NetworkException.UnknownError -> "Qualcosa è andato storto"
    is NetworkException.ParseError -> "Errore nel processare i dati"
}
