package com.netmirror.app.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ApiResponse(
    @SerializedName("token_hash")
    val token_hash: String? = null,
    @SerializedName("doms")
    val doms: String? = null,
    @SerializedName("mwin")
    val mwin: String? = null,
    @SerializedName("popwin")
    val popwin: String? = null,
    @SerializedName("var")
    val version: String? = null,
    @SerializedName("stape")
    val stape: String? = null,
    @SerializedName("u")
    val u: List<String>? = null
)

data class AppConfigData(
    val webviewUrl: String,
    val sTape: String? = null,
    val stapeUrls: List<String> = emptyList(),
    val serverVersion: String? = null
) : Serializable