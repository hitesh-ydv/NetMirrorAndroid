package com.netmirror.app.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val magentas = listOf(
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==",
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3QuYXBw",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNsaWNr",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lmluaw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNob3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnN0b3Jl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lndpa2k=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbms=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo="
    )

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d("Api_urls", "Full URL: ${request.url}")
            chain.proceed(request)
        }
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private var resolvedBaseUrl: String? = null
    private var resolvedApi: ApiService? = null
    private var resolvedConfig: AppConfigData? = null

    suspend fun resolveWorkingConfig(): AppConfigData = withContext(Dispatchers.IO) {
        resolvedConfig?.let { return@withContext it }

        for (encoded in magentas) {
            val baseUrl = try {
                String(Base64.decode(encoded, Base64.DEFAULT)).trim()
            } catch (e: Exception) {
                Log.d("Api_urls", "Failed BASE URL: $e")
                continue
            }

            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val endpoint = checkVerEndpoint()
            val fullUrl = "$normalizedBase$endpoint"
            Log.d("Api_urls", "BASE URL: $baseUrl")
            Log.d("Api_urls", "Full URL: $fullUrl")

            val candidateApi = buildApi(baseUrl)

            try {
                val response = candidateApi.getUrl(endpoint)
                if (response.isSuccessful) {
                    val body = response.body()
                    val token = body?.token_hash
                    if (!token.isNullOrBlank()) {
                        val webviewUrl = String(Base64.decode(token, Base64.DEFAULT)).trim()

                        val sTape = try {
                            if (!body.stape.isNullOrBlank()) {
                                String(Base64.decode(body.stape, Base64.DEFAULT)).trim()
                            } else null
                        } catch (e: Exception) {
                            null
                        }

                        val updateUrl = decodeBase64Url(body.uu)
                        val updateImageUrl = decodeBase64Url(body.updateImage?.imageUrl)

                        val config = AppConfigData(
                            webviewUrl = webviewUrl,
                            sTape = sTape,
                            stapeUrls = body.u ?: emptyList(),
                            serverVersion = body.version,
                            updateUrl = updateUrl,
                            updateImageUrl = updateImageUrl,
                            updateImageHeightRatio = body.updateImage?.heightRatio ?: 0.0,
                            updateImageWidthRatio = body.updateImage?.widthRatio ?: 1.0,
//                            updateImageMaxHeightDp = body.updateImage?.maxHeightDp ?: 0,
                            showLaterButton = body.showLaterButton?.equals("true", ignoreCase = true) == true
                        )

                        Log.d("Api_urls", "Update URL: $updateUrl")
                        Log.d("Api_urls", "Update Image URL: $updateImageUrl")
                        Log.d("Api_urls", "Show Later Button: ${config.showLaterButton}")

                        resolvedBaseUrl = baseUrl
                        resolvedApi = candidateApi
                        resolvedConfig = config
                        return@withContext config
                    }
                }
            } catch (e: Exception) {
                // Continue to next candidate
                Log.d("Api_urls", "Failed Full URL: $fullUrl (Error: $e)")
            }
        }

        throw IllegalStateException("ERROR Code: 69: Something Went Wrong, Download Latest App from netmirror.gg")
    }

    suspend fun resolveWorkingApi(): ApiService = withContext(Dispatchers.IO) {
        resolvedApi?.let { return@withContext it }
        resolveWorkingConfig()
        return@withContext resolvedApi ?: throw IllegalStateException("No working API endpoint found among candidates")
    }

    fun decodeBase64Url(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        try {
            val decoded = String(Base64.decode(trimmed, Base64.DEFAULT)).trim()
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                return decoded
            }
        } catch (_: Exception) {
        }
        return trimmed
    }

    fun currentBaseUrl(): String? = resolvedBaseUrl
    fun currentConfig(): AppConfigData? = resolvedConfig

    private fun buildApi(baseUrl: String): ApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}