package com.netmirror.app.network

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class UpdateImageConfig(
    @SerializedName("i")
    val imageUrl: String? = null,
    @SerializedName("h")
    val heightRatio: Double = 0.0,
    @SerializedName("w")
    val widthRatio: Double = 1.0,
    @SerializedName("h2")
    val maxHeightDp: Int = 0
)

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
    val u: List<String>? = null,
    @SerializedName("uu", alternate = ["update_url"])
    val uu: String? = null,
    @SerializedName("i")
    val updateImage: UpdateImageConfig? = null,
    @SerializedName("b", alternate = ["btn_ok", "btnOk"])
    val showLaterButton: String? = null
)

data class AppConfigData(
    val webviewUrl: String,
    val sTape: String? = null,
    val stapeUrls: List<String> = emptyList(),
    val serverVersion: String? = null,
    val updateUrl: String? = null,
    val updateImageUrl: String? = null,
    val updateImageHeightRatio: Double = 0.0,
    val updateImageWidthRatio: Double = 1.0,
    val updateImageMaxHeightDp: Int = 0,
    val showLaterButton: Boolean = true
) : Serializable