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
    
    // 💡日本のJ-POP・同期歌詞に強い中継互換APIに変更！
    private const val BASE_URL = "https://api.lyric-hub.jp/api"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"

    data class LrcLibResponse(
        @SerializedName("id") val id: Int?,
        @SerializedName("trackName") val trackName: String?,
        @SerializedName("artistName") val artistName: String?,
        @SerializedName("albumName") val albumName: String?,
        @SerializedName("duration") val duration: Int?,
        @SerializedName("instrumental") val instrumental: Boolean?,
        @SerializedName("plainLyrics") val plainLyrics: String?,
        @SerializedName("syncedLyrics") val syncedLyrics: String?
    )

    fun getLyrics(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int
    ): LrcLibResponse? {
        // 💡日本の曲名やアーティスト名の前後の余計な空白を綺麗にお掃除
        val cleanTrack = trackName.trim()
        val cleanArtist = artistName.trim()
        val cleanAlbum = albumName.trim()

        // Priority 1: exact match with synced lyrics
        if (durationSec > 0 && cleanAlbum.isNotBlank()) {
            val exact = getExact(cleanTrack, cleanArtist, cleanAlbum, durationSec)
            if (exact?.syncedLyrics != null) return exact
        }

        // Priority 2: search for synced lyrics (duration-guarded)
        val searchResults = searchAll(cleanTrack, cleanArtist)
        val syncedResult = searchResults.firstOrNull {
            it.syncedLyrics != null && withinDuration(it, durationSec)
        }
        if (syncedResult != null) return syncedResult

        // Priority 3: exact match without album for synced lyrics
        if (durationSec > 0) {
            val exactNoAlbum = getExact(cleanTrack, cleanArtist, "", durationSec)
            if (exactNoAlbum?.syncedLyrics != null) return exactNoAlbum
        }

        // Priority 4: exact match with any lyrics (including plain)
        if (durationSec > 0 && cleanAlbum.isNotBlank()) {
            val exact = getExact(cleanTrack, cleanArtist, cleanAlbum, durationSec)
            if (exact?.plainLyrics != null) return exact
        }

        // Priority 5: search result with plain lyrics (duration-guarded)
        val plainResult = searchResults.firstOrNull {
            it.plainLyrics != null && withinDuration(it, durationSec)
        }
        if (plainResult != null) return plainResult

        return null
    }

    private fun withinDuration(result: LrcLibResponse, durationSec: Int): Boolean {
        if (durationSec <= 0 || result.duration == null) return true
        return kotlin.math.abs(result.duration - durationSec) <= 15 // 💡判定を少し甘くしてヒット率UP
    }

    private fun getExact(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSec: Int
    ): LrcLibResponse? {
        val urlBuilder = "$BASE_URL/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .addQueryParameter("album_name", albumName)
            .addQueryParameter("duration", durationSec.toString())

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    gson.fromJson(response.body?.string(), LrcLibResponse::class.java)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchAll(trackName: String, artistName: String): List<LrcLibResponse> {
        val urlBuilder = "$BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", trackName)

        if (artistName.isNotBlank()) {
            urlBuilder.addQueryParameter("artist_name", artistName)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val type = object : TypeToken<List<LrcLibResponse>>() {}.type
                    gson.fromJson(response.body?.string(), type) ?: emptyList()
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
