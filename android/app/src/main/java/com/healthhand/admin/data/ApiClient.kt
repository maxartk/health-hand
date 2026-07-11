package com.healthhand.admin.data

import com.healthhand.admin.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual DI singleton. Holds the Retrofit client, token store reference and a
 * [forceLogout] flow that screens collect to react to 403 responses.
 */
object ApiClient {

    private lateinit var tokenStoreRef: TokenStore

    private val _forceLogout = MutableStateFlow(false)
    val forceLogout: StateFlow<Boolean> = _forceLogout

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var okHttpClient: OkHttpClient = buildOkHttp(BuildConfig.BASE_URL)
    private var retrofit: Retrofit = buildRetrofit(BuildConfig.BASE_URL, okHttpClient)
    private var api: AdminApi = retrofit.create(AdminApi::class.java)

    var baseUrl: String = BuildConfig.BASE_URL
        private set

    fun init(tokenStore: TokenStore) {
        tokenStoreRef = tokenStore
    }

    fun resetForcedLogout() {
        _forceLogout.value = false
    }

    fun rebuild(baseUrlString: String) {
        val normalized = if (baseUrlString.isBlank()) BuildConfig.BASE_URL else {
            if (baseUrlString.endsWith("/")) baseUrlString else "$baseUrlString/"
        }
        baseUrl = normalized
        okHttpClient = buildOkHttp(normalized)
        retrofit = buildRetrofit(normalized, okHttpClient)
        api = retrofit.create(AdminApi::class.java)
    }

    fun tokenStore(): TokenStore = tokenStoreRef

    fun api(): AdminApi = api

    private fun buildOkHttp(base: String): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = if (::tokenStoreRef.isInitialized) tokenStoreRef.getToken() else null
                val request = chain.request().newBuilder()
                    .apply {
                        if (!token.isNullOrBlank()) {
                            addHeader("Authorization", "Bearer $token")
                        }
                    }
                    .build()
                val response = chain.proceed(request)
                if (response.code == 403) {
                    _forceLogout.value = true
                }
                response
            }
            .addInterceptor(logging)
            .build()
    }

    private fun buildRetrofit(base: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}