package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeneratedBookJson(
    @Json(name = "coverIllustrationPrompt") val coverIllustrationPrompt: String,
    @Json(name = "pages") val pages: List<GeneratedPageJson>
)

@JsonClass(generateAdapter = true)
data class GeneratedPageJson(
    @Json(name = "pageNumber") val pageNumber: Int,
    @Json(name = "text") val text: String,
    @Json(name = "hasIllustration") val hasIllustration: Boolean,
    @Json(name = "illustrationPrompt") val illustrationPrompt: String? = null
)
