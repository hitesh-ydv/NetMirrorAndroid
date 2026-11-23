package com.netmirror.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

const val APP_VERSION = "3.0"

interface ApiService {

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate, max-age=0",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET("check-ver.php?ver=$APP_VERSION")
    suspend fun getUrl(): Response<ApiResponse>

}
