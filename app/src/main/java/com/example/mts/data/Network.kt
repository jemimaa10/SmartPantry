package com.example.mts.data

import com.example.mts.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

private val jsonType = "application/json".toMediaType()

/** A failure with a message that is safe to show to the user. */
class AppFailure(message: String, val code: String = "") : Exception(message)

private const val OFFLINE_MESSAGE = "Tidak dapat terhubung ke server. Periksa koneksi kamu."
private const val GENERIC_MESSAGE = "Terjadi kesalahan. Coba lagi."

fun Throwable.toAppFailure(): AppFailure = when (this) {
    is AppFailure -> this
    is HttpException -> {
        val body = response()?.errorBody()?.string().orEmpty()
        val detail = runCatching { AppJson.decodeFromString<ApiErrorBody>(body).error }.getOrNull()
        AppFailure(detail?.message?.ifBlank { null } ?: GENERIC_MESSAGE, detail?.code.orEmpty())
    }
    is IOException -> AppFailure(OFFLINE_MESSAGE, "OFFLINE")
    else -> AppFailure(GENERIC_MESSAGE)
}

private class AuthHeader(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokens.accessToken() }
        val request = chain.request().newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return chain.proceed(request)
    }
}

/** Refreshes an expired access token once, then replays the failed request. */
private class TokenRefresher(
    private val tokens: TokenStore,
    private val refreshClient: OkHttpClient,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("X-Auth-Retry") != null) return null
        val sent = response.request.header("Authorization")?.removePrefix("Bearer ")

        return synchronized(lock) {
            val current = runBlocking { tokens.accessToken() }
            // Another request may have refreshed while this one waited for the lock.
            val accessToken = if (current != null && current != sent) current else refresh()
            accessToken?.let {
                response.request.newBuilder()
                    .header("Authorization", "Bearer $it")
                    .header("X-Auth-Retry", "1")
                    .build()
            }
        }
    }

    private fun refresh(): String? {
        val refreshToken = runBlocking { tokens.refreshToken() } ?: return null
        val body = AppJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + "api/auth/refresh")
            .post(body.toRequestBody(jsonType))
            .build()
        return try {
            refreshClient.newCall(request).execute().use { result ->
                when {
                    result.isSuccessful -> {
                        val session = AppJson.decodeFromString<Session>(result.body!!.string())
                        runBlocking { tokens.save(session) }
                        session.accessToken
                    }
                    // Only a rejected token ends the session; a server error should not sign the user out.
                    result.code == 401 -> {
                        runBlocking { tokens.clear() }
                        null
                    }
                    else -> null
                }
            }
        } catch (_: IOException) {
            null
        }
    }
}

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

fun createApi(tokens: TokenStore, base: OkHttpClient): Api {
    val client = base.newBuilder()
        .addInterceptor(AuthHeader(tokens))
        .authenticator(TokenRefresher(tokens, base))
        .build()
    return Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(AppJson.asConverterFactory(jsonType))
        .build()
        .create(Api::class.java)
}
