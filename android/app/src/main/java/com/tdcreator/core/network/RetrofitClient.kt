package com.tdcreator.core.network

import com.tdcreator.core.data.prefs.PreferencesRepository
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the singleton [ApiService]. Auth is injected via an OkHttp interceptor that reads
 * the JWT from [PreferencesRepository]. The presigned-URL PUT must NOT carry the auth header,
 * so the interceptor skips requests whose URL host differs from our API host.
 */
object RetrofitClient {

    private val JsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun create(baseUrl: String, prefs: PreferencesRepository): ApiService {
        val apiHost = runCatching { java.net.URL(baseUrl).host }.getOrDefault("")

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            // Only attach the bearer token to our own API host, never to presigned R2 URLs.
            val token = runBlocking { prefs.authToken.first() }
            val newRequest = if (token.isNotEmpty() && request.url.host == apiHost) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(newRequest)
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES) // large video uploads
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(JsonConfig.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
