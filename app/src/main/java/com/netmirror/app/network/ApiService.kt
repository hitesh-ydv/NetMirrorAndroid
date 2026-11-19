package com.netmirror.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

const val APP_VERSION = "4.0"
const val UPDATE_APK_URL = "https://kjdrbruhhgilcropmvxh.supabase.co/storage/v1/object/public/unitrack/test/app-release.apk"

interface ApiService {

    @Headers(
        "Cache-Control: no-cache, no-store, must-revalidate, max-age=0",
        "Pragma: no-cache",
        "Expires: 0"
    )
    @GET("check-ver.php?ver=$APP_VERSION")
    suspend fun getUrl(): Response<ApiResponse>

}
