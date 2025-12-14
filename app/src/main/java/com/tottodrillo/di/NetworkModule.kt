package com.tottodrillo.di

import android.content.Context
import com.tottodrillo.data.remote.ApiService
import com.tottodrillo.data.remote.interceptor.HeadersInterceptor
import com.tottodrillo.data.remote.interceptor.NetworkConnectionInterceptor
import com.tottodrillo.data.remote.interceptor.RetryInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Modulo Hilt per configurare Retrofit e network dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.crocdb.net/"
    private const val TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideUpdateManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        gson: Gson
    ): com.tottodrillo.domain.manager.UpdateManager {
        return com.tottodrillo.domain.manager.UpdateManager(context, okHttpClient, gson)
    }
    
    @Provides
    @Singleton
    fun provideRomCacheManager(
        @ApplicationContext context: Context,
        configRepository: com.tottodrillo.data.repository.DownloadConfigRepository,
        gson: Gson
    ): com.tottodrillo.data.repository.RomCacheManager {
        return com.tottodrillo.data.repository.RomCacheManager(context, configRepository, gson)
    }

    @Provides
    @Singleton
    fun provideSourceUpdateManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        gson: Gson,
        sourceManager: com.tottodrillo.domain.manager.SourceManager
    ): com.tottodrillo.domain.manager.SourceUpdateManager {
        return com.tottodrillo.domain.manager.SourceUpdateManager(context, okHttpClient, gson, sourceManager)
    }
    
    @Provides
    @Singleton
    fun provideIgdbManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        gson: Gson,
        downloadConfigRepository: com.tottodrillo.data.repository.DownloadConfigRepository
    ): com.tottodrillo.domain.manager.IgdbManager {
        return com.tottodrillo.domain.manager.IgdbManager(context, okHttpClient, gson, downloadConfigRepository)
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideNetworkConnectionInterceptor(
        @ApplicationContext context: Context
    ): NetworkConnectionInterceptor {
        return NetworkConnectionInterceptor(context)
    }

    @Provides
    @Singleton
    fun provideHeadersInterceptor(): HeadersInterceptor {
        return HeadersInterceptor()
    }

    @Provides
    @Singleton
    fun provideRetryInterceptor(): RetryInterceptor {
        return RetryInterceptor(maxRetries = 3)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        networkConnectionInterceptor: NetworkConnectionInterceptor,
        headersInterceptor: HeadersInterceptor,
        retryInterceptor: RetryInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(headersInterceptor)
            .addInterceptor(networkConnectionInterceptor)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideSourceServices(
        @ApplicationContext context: Context,
        sourceManager: com.tottodrillo.domain.manager.SourceManager,
        okHttpClient: OkHttpClient
    ): com.tottodrillo.domain.service.SourceServices {
        return com.tottodrillo.data.service.SourceServicesImpl(context, sourceManager, okHttpClient)
    }

    /**
     * @deprecated Usa SourceManager e SourceApiClient invece.
     * Mantenuto solo per retrocompatibilità durante la migrazione.
     */
    @Provides
    @Singleton
    @Deprecated("Usa SourceManager invece", ReplaceWith("SourceManager"))
    fun provideApiService(
        retrofit: Retrofit
    ): ApiService? {
        // Ritorna null per forzare l'uso di sorgenti installabili
        // Rimuovere completamente in una versione futura
        return null
        // return retrofit.create(ApiService::class.java)
    }
}
