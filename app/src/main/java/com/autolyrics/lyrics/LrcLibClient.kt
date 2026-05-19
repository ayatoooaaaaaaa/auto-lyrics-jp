package com.autolyrics.lyrics

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object LrcLibClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // 🌐 100%確実に存在する、元の本物の歌詞APIサーバー（LRCLIB）
    private const val BASE_URL = "https://lrclib.net/"

    fun getLyrics(trackName: String, artistName: String, duration: Int): LyricsResponse? {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("lyrics")
            .addQueryParameter("trackName", trackName)
            .addQueryParameter("artistName", artistName)
            .addQueryParameter("duration", duration.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AutoLyricsAndroid/1.0")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                gson.fromJson(bodyString, LyricsResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class LyricsResponse(
    val id: Long,
    val name: String,
    val trackName: String?,
    val artistName: String,
    val albumName: String?,
    val duration: Int,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
