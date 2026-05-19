package com.autolyrics.media

import android.content.Context
import android.graphics.Bitmap
import com.autolyrics.model.AlbumColors
import com.autolyrics.model.LyricsState
import com.autolyrics.model.LyricsStatus
import com.autolyrics.model.TrackInfo
import com.autolyrics.util.LyricsParser // もしプロジェクト内に別のパーサーがある場合はパッケージ名に合わせてな
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaTracker private constructor(private val context: Context) {

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state

    companion object {
        @Volatile
        private var INSTANCE: MediaTracker? = null

        fun getInstance(context: Context): MediaTracker {
            return INSTANCE ?: synchronized(this) {
                val instance = MediaTracker(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun adjustOffset(ms: Long) {
        val current = _state.value
        _state.value = current.copy(offsetMs = current.offsetMs + ms)
    }

    fun resetOffset() {
        val current = _state.value
        _state.value = current.copy(offsetMs = 0L)
    }

    /**
     * ✨【新機能】手動検索画面で選んだ歌詞データをアプリに強制注入する！
     * これが動くことで、タイムスタンプ付き歌詞（スタンプなんちゃら）が画面にドバッと流れる。
     */
    fun injectManualLyrics(syncedLyrics: String?, plainLyrics: String?, trackName: String?, artistName: String?) {
        val current = _state.value
        
        // 1. もしタイムスタンプ付きの歌詞（syncedLyrics）がある場合
        if (!syncedLyrics.isNullOrBlank()) {
            // 文字列をアプリが読み取れる形式（Lineオブジェクトのリスト）にパースする
            val parsedLines = com.autolyrics.util.LyricsParser.parseSearchLyrics(syncedLyrics)
            
            _state.value = current.copy(
                status = LyricsStatus.FOUND,
                lines = parsedLines,
                track = TrackInfo(title = trackName ?: "Unknown", artist = artistName ?: "Unknown"),
                source = "LRCLIB (Manual)"
            )
        } 
        // 2. タイムスタンプはないけど、普通の歌詞テキスト（plainLyrics）がある場合
        else if (!plainLyrics.isNullOrBlank()) {
            val parsedLines = plainLyrics.lines().map { com.autolyrics.model.LyricsLine(text = it) }
            
            _state.value = current.copy(
                status = LyricsStatus.PLAIN_ONLY,
                lines = parsedLines,
                track = TrackInfo(title = trackName ?: "Unknown", artist = artistName ?: "Unknown"),
                source = "LRCLIB (Manual Plain)"
            )
        }
    }
}

