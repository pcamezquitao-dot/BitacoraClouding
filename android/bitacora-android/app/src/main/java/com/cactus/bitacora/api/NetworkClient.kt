package com.cactus.bitacora.api

import com.cactus.bitacora.util.AppConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    @Volatile private var provisionalAdminKey: String = ""

    fun setProvisionalAdminKey(value: String) { provisionalAdminKey = value.trim() }
    fun clearProvisionalAdminKey() { provisionalAdminKey = "" }
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val key = provisionalAdminKey
                val protectedPath = request.url.encodedPath.contains("/admin/")
                chain.proceed(if (protectedPath && key.isNotEmpty()) request.newBuilder().header("X-Admin-Key", key).build() else request)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> createService(serviceClass: Class<T>): T =
        retrofit.create(serviceClass)
}
