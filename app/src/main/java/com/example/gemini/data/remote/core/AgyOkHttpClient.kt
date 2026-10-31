package com.example.gemini.data.remote.core

import com.example.gemini.data.preferences.AuthPreferences
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared singleton OkHttpClient provider for gRPC-Web and Wire transport.
 * Configured with high multiplexing dispatcher, connection pooling, timeouts, and dynamic CSRF token evaluation.
 */
object AgyOkHttpClient {
    private const val CSRF_RETRY_HEADER = "X-YunayCoder-Csrf-Retry"

    val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 256
        }
        val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(listOf(okhttp3.Protocol.H2_PRIOR_KNOWLEDGE))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (com.example.gemini.data.remote.inspector.NetworkInspectorManager.isEnabled) {
            builder.addInterceptor(com.example.gemini.data.remote.inspector.NetworkInspectorInterceptor("AGY Daemon (gRPC)"))
        }

        builder.addInterceptor { chain ->
                val original = chain.request()
                val hubUrl = AuthPreferences.currentHubUrl

                val request = original.newBuilder()
                    .header("User-Agent", "yunayCoderAi-Android-Native")
                    .header("X-Grpc-Web", "1")
                    .header(AuthPreferences.currentFramedHeader, currentCsrfToken().ifBlank { "true" })
                    .build()

                val response = chain.proceed(request)

                // Check for CSRF expiry (HTTP 401/403 or gRPC status 16 UNAUTHENTICATED)
                val isCsrfError = response.code == 401 || response.code == 403 || response.header("grpc-status") == "16"
                if (!isCsrfError) return@addInterceptor response

                AgyCsrfManager.instance.notifyCsrfExpired(hubUrl, original.url.encodedPath)

                // The hub patches a fresh header into the AGY binary when it restarts, so the cached token can be
                // stale. Refresh it from the bridge and retry the request exactly once.
                if (original.header(CSRF_RETRY_HEADER) != null) return@addInterceptor response
                val freshToken = refreshCsrfToken()
                if (freshToken.isBlank()) return@addInterceptor response

                val retried = original.newBuilder()
                    .header("User-Agent", "yunayCoderAi-Android-Native")
                    .header("X-Grpc-Web", "1")
                    .header(AuthPreferences.currentFramedHeader, freshToken)
                    .header(CSRF_RETRY_HEADER, "1")
                    .build()

                response.close()
                chain.proceed(retried)
            }
            .build()
    }

    private fun currentCsrfToken(): String {
        val cached = AgyCsrfManager.instance.token
        if (cached.isNotBlank()) return cached
        return fetchCsrfTokenFromBridge()
    }

    private fun refreshCsrfToken(): String {
        AgyCsrfManager.instance.clearToken()
        return fetchCsrfTokenFromBridge()
    }

    private fun fetchCsrfTokenFromBridge(): String {
        return try {
            val status = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                com.example.gemini.data.remote.AgyBridgeService.instance.fetchServerStatus(AuthPreferences.currentBridgeHttpUrl)
            }
            val token = status?.csrfToken.orEmpty()
            if (token.isNotBlank()) {
                AgyCsrfManager.instance.setCachedToken(AuthPreferences.currentHubUrl, token)
            }
            token
        } catch (_: Exception) {
            ""
        }
    }
}
