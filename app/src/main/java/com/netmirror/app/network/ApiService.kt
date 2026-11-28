package com.netmirror.app.network

import android.util.Base64
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

const val APP_VERSION = "3.0"
private const val CHECK_VER_ENDPOINT_B64 = "Y2hlY2stdmVyLnBocD92ZXI9"

fun checkVerEndpoint(): String =
    String(Base64.decode(CHECK_VER_ENDPOINT_B64, Base64.DEFAULT)) + APP_VERSION

interface ApiService {

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate, max-age=0",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET
    suspend fun getUrl(@Url endpoint: String): Response<ApiResponse>

}
