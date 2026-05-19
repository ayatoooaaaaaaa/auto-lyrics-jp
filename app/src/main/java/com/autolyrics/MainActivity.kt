package com.autolyrics.media

import android.content.Context
import android.graphics.Bitmap
import com.autolyrics.model.AlbumColors
import com.autolyrics.model.LyricsState
import com.autolyrics.model.LyricsStatus
import com.autolyrics.model.TrackInfo
import com.autolyrics.util.LyricsParser
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

    fun updateTrack(track: TrackInfo?, albumArt: Bitmap?, colors: AlbumColors?) {
        val current = _state.value
        if (current.track == track && current.albumArt == albumArt) return
        _state.value = current.copy(
            track = track,
            albumArt = albumArt,
            albumColors = colors,
            status = if (track == null) LyricsStatus.NO_MEDIA else LyricsStatus.LOADING,
            lines = emptyList(),
            currentIndex = -1,
            currentWordIndex = -1
        )
    }

    fun updateLyrics(status: LyricsStatus, lines: List<com.autolyrics.model.LyricsLine>, source: String = "") {
        val current = _state.value
        _state.value = current.copy(
            status = status,
            lines = lines,
            source = source,
            currentIndex = -1,
            currentWordIndex = -1
        )
    }

    fun updatePlayback(positionMs: Long, isPlaying: Boolean) {
        val current = _state.value
        val updatedPos = positionMs + current.offsetMs
        
        var newIdx = -1
        var newWordIdx = -1

        if (current.status == LyricsStatus.FOUND && current.lines.isNotEmpty()) {
            for (i in current.lines.indices) {
                if (updatedPos >= current.lines[i].startTimeMs) {
                    newIdx = i
                } else {
                    break
                }
            }
            if (newIdx famine >= 0) {
                val line = current.lines[newIdx]
                if (line.words.isNotEmpty()) {
                    for (wi in line.words.indices) {
                        if (updatedPos >= line.words[wi].startTimeMs) {
                            newWordIdx = wi
                        } else {
                            break
                        }
                    }
                }
            }
        }

        _state.value = current.copy(
            playbackPositionMs = positionMs,
            isPlaying = isPlaying,
            currentIndex = newIdx,
            currentWordIndex = newWordIdx
        )
    }

    fun adjustOffset(ms: Long) {
        val current = _state.value
        _state.value = current.copy(offsetMs = current.offsetMs + ms)
        updatePlayback(current.playbackPositionMs, current.isPlaying)
    }

    fun resetOffset() {
        val current = _state.value
        _state.value = current.copy(offsetMs = 0L)
        updatePlayback(current.playbackPositionMs, current.isPlaying)
    }

    /**
     * ✨【手動検索】選んだ歌詞データをアプリに強制注入する関数
     */
    fun injectManualLyrics(syncedLyrics: String?, plainLyrics: String?, trackName: String?, artistName: String?) {
        val current = _state.value
        
        if (!syncedLyrics.isNullOrBlank()) {
            // 元のアプリが持っている本物のLyricsParserを使って時間付き歌詞をパース
            val parsedLines = LyricsParser.parse(syncedLyrics)
            _state.value = current.copy(
                status = LyricsStatus.FOUND,
                lines = parsedLines,
                track = TrackInfo(title = trackName ?: "Unknown", artist = artistName ?: "Unknown", durationMs = current.track?.durationMs ?: 0),
                source = "LRCLIB (Manual)"
            )
        } else if (!plainLyrics.isNullOrBlank()) {
            val parsedLines = plainLyrics.lines().map { com.autolyrics.model.LyricsLine(text = it, startTimeMs = 0L) }
            _state.value = current.copy(
                status = LyricsStatus.PLAIN_ONLY,
                lines = parsedLines,
                track = TrackInfo(title = trackName ?: "Unknown", artist = artistName ?: "Unknown", durationMs = current.track?.durationMs ?: 0),
                source = "LRCLIB (Manual Plain)"
            )
        }
    }
}
