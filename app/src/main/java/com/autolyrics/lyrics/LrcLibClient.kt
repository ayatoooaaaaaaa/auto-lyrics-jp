package com.autolyrics.lyrics

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object LrcLibClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun getLyrics(trackName: String, artistName: String, duration: Int): LyricsResponse? {
        
        // -----------------------------------------------------------------
        // 【第1希望】本命の「LRCLIB」にアクセス
        // -----------------------------------------------------------------
        try {
            val url = "https://lrclib.net/".toHttpUrl().newBuilder()
                .addPathSegment("api")
                .addPathSegment("lyrics")
                .addQueryParameter("trackName", trackName)
                .addQueryParameter("artistName", artistName)
                .addQueryParameter("duration", duration.toString())
                .build()

            val request = Request.Builder().url(url).header("User-Agent", "AutoLyricsAndroid/1.0").build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    val data = gson.fromJson(bodyString, LyricsResponse::class.java)
                    if (data != null && (!data.syncedLyrics.isNullOrEmpty() || !data.plainLyrics.isNullOrEmpty())) {
                        return data // 歌詞が見つかったら終了！
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // エラーが起きても次へ進む
        }

        // -----------------------------------------------------------------
        // 【第2希望】バックアップの「Textyl」にアクセス（通信の仕組みをTextyl専用に調整）
        // -----------------------------------------------------------------
        try {
            val queryText = "$artistName $trackName"
            val url = "https://api.textyl.co/".toHttpUrl().newBuilder()
                .addPathSegment("api")
                .addPathSegment("lyrics")
                .addQueryParameter("q", queryText) // Textyl専用の検索パラメーター
                .build()

            val request = Request.Builder().url(url).header("User-Agent", "AutoLyricsAndroid/1.0").build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    val data = gson.fromJson(bodyString, LyricsResponse::class.java)
                    if (data != null && (!data.syncedLyrics.isNullOrEmpty() || !data.plainLyrics.isNullOrEmpty())) {
                        return data // 見つかったら終了！
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 両方全滅したときだけnullを返す
        return null
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
