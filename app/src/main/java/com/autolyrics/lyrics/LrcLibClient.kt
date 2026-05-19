package com.autolyrics.lyrics

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
    private const val BASE_URL = "https://lrclib.net/"

    /**
     * 【既存の機能】通知から自動で1件だけ完璧にマッチする歌詞を取る（MediaTracker用）
     */
    fun getLyrics(
        trackName: String, 
        artistName: String, 
        duration: Int = 0,
        albumName: String? = null,
        durationSec: Int? = null
    ): LyricsResponse? {
        val finalDuration = durationSec ?: duration
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("lyrics")
            .addQueryParameter("trackName", trackName)
            .addQueryParameter("artistName", artistName)
            .addQueryParameter("duration", finalDuration.toString())
            .build()

        val request = Request.Builder().url(url).header("User-Agent", "AutoLyricsAndroid/1.0").build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                gson.fromJson(response.body?.string(), LyricsResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * ✨【新機能】キーワードで検索して、当てはまる曲の候補をリストで全部持ってくる！
     * これを使って、画面に検索結果の候補をポンポン並べる。
     */
    fun searchLyrics(query: String): List<LyricsResponse> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("search")
            .addQueryParameter("q", query) // ここに「米津玄師 LADY」とかを入れる
            .build()

        val request = Request.Builder().url(url).header("User-Agent", "AutoLyricsAndroid/1.0").build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyString = response.body?.string() ?: return emptyList()
                
                // 候補が複数返ってくる（配列型）ので、Listとして読み込む
                gson.fromJson(bodyString, Array<LyricsResponse>::class.java).toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

// 歌詞データの設計図（タイムスタンプ付きの syncedLyrics もしっかり入ってる）
data class LyricsResponse(
    val id: Long,
    val name: String?,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Int,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String? // 👈 これが例の「スタンプなんちゃら（同期歌詞）」やで！
)
