package com.autolyrics

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.autolyrics.media.MediaListenerService
import com.autolyrics.media.MediaTracker
import com.autolyrics.model.AlbumColors
import com.autolyrics.model.LyricsState
import com.autolyrics.model.LyricsStatus
import com.autolyrics.lyrics.LrcLibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var mediaTracker: MediaTracker
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var rootLayout: LinearLayout
    private lateinit var appBar: LinearLayout
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSubtitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPermission: Button
    private lateinit var ivAlbumArt: ImageView
    private lateinit var tvTrack: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvLyrics: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var delayBar: LinearLayout
    private lateinit var tvDelay: TextView
    private lateinit var divider: View
    private lateinit var fontSettingsPanel: LinearLayout
    private lateinit var tvFontSize: TextView
    private lateinit var switchAaKaraoke: SwitchCompat
    private lateinit var switchTranslation: SwitchCompat
    private lateinit var tvAaDelay: TextView
    private lateinit var btnJumpToCurrent: Button
    private lateinit var prefs: SharedPreferences
    private var lastScrolledIndex = -1
    private var currentColors: AlbumColors? = null
    private var lyricsFontSizeSp = 16
    private var lyricsFontFamily = "sans-serif"
    private var aaOffsetMs = 0L
    private var userScrolling = false
    private var userTouching = false

    private var plainScrollAnimator: ValueAnimator? = null
    private var lastPlainTrackTitle: String? = null

    private val fontButtons = mutableMapOf<String, Button>()
    private val scrollResetRunnable = Runnable {
        userScrolling = false
        btnJumpToCurrent.visibility = View.GONE
    }

    // 手動検索用UIの変数宣言
    private lateinit var layoutSearchPanel: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var btnSearchSubmit: Button
    private lateinit var layoutSearchResults: LinearLayout

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaTracker = MediaTracker.getInstance(this)
        prefs = getSharedPreferences("auto_lyrics_prefs", MODE_PRIVATE)

        rootLayout = findViewById(R.id.root_layout)
        appBar = findViewById(R.id.layout_app_bar)
        tvAppTitle = findViewById(R.id.tv_app_title)
        tvAppSubtitle = findViewById(R.id.tv_app_subtitle)
        tvStatus = findViewById(R.id.tv_status)
        btnPermission = findViewById(R.id.btn_permission)
        ivAlbumArt = findViewById(R.id.iv_album_art)
        tvTrack = findViewById(R.id.tv_track)
        tvSource = findViewById(R.id.tv_source)
        tvLyrics = findViewById(R.id.tv_lyrics)
        scrollView = findViewById(R.id.scroll_lyrics)
        delayBar = findViewById(R.id.layout_delay)
        tvDelay = findViewById(R.id.tv_delay)
        divider = findViewById(R.id.divider)

        ivAlbumArt.clipToOutline = true
        btnJumpToCurrent = findViewById(R.id.btn_jump_to_current)

        setupScrollDetection()

        btnPermission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        findViewById<Button>(R.id.btn_delay_minus).setOnClickListener {
            mediaTracker.adjustOffset(-100)
        }
        findViewById<Button>(R.id.btn_delay_plus).setOnClickListener {
            mediaTracker.adjustOffset(100)
        }
        findViewById<Button>(R.id.btn_delay_reset).setOnClickListener {
            mediaTracker.resetOffset()
        }

        fontSettingsPanel = findViewById(R.id.layout_font_settings)
        tvFontSize = findViewById(R.id.tv_font_size)

        lyricsFontSizeSp = prefs.getInt("lyrics_font_size", 16)
        lyricsFontFamily = prefs.getString("lyrics_font_family", "sans-serif") ?: "sans-serif"

        applyFontSettings()

        findViewById<ImageButton>(R.id.btn_settings_toggle).setOnClickListener {
            layoutSearchPanel.visibility = View.GONE
            fontSettingsPanel.visibility = if (fontSettingsPanel.visibility == View.VISIBLE)
                View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.btn_font_size_minus).setOnClickListener {
            if (lyricsFontSizeSp > 12) {
                lyricsFontSizeSp -= 2
                saveFontSettings()
                applyFontSettings()
            }
        }
        findViewById<Button>(R.id.btn_font_size_plus).setOnClickListener {
            if (lyricsFontSizeSp < 28) {
                lyricsFontSizeSp += 2
                saveFontSettings()
                applyFontSettings()
            }
        }

        val btnSans = findViewById<Button>(R.id.btn_font_sans)
        val btnSerif = findViewById<Button>(R.id.btn_font_serif)
        val btnMono = findViewById<Button>(R.id.btn_font_mono)
        val btnCursive = findViewById<Button>(R.id.btn_font_cursive)

        fontButtons["sans-serif"] = btnSans
        fontButtons["serif"] = btnSerif
        fontButtons["monospace"] = btnMono
        fontButtons["cursive"] = btnCursive

        btnSans.setOnClickListener { selectFont("sans-serif") }
        btnSerif.setOnClickListener { selectFont("serif") }
        btnMono.setOnClickListener { selectFont("monospace") }
        btnCursive.setOnClickListener { selectFont("cursive") }

        updateFontButtonHighlights()

        switchAaKaraoke = findViewById(R.id.switch_aa_karaoke)
        tvAaDelay = findViewById(R.id.tv_aa_delay)

        switchAaKaraoke.isChecked = prefs.getBoolean("aa_karaoke_enabled", true)
        aaOffsetMs = prefs.getLong("aa_offset_ms", 0L)
        updateAaDelayDisplay()

        switchAaKaraoke.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("aa_karaoke_enabled", isChecked).apply()
        }

        switchTranslation = findViewById(R.id.switch_translation)
        switchTranslation.isChecked = prefs.getBoolean("translation_enabled", true)
        switchTranslation.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("translation_enabled", isChecked).apply()
        }

        findViewById<Button>(R.id.btn_aa_delay_minus).setOnClickListener {
            aaOffsetMs -= 100
            prefs.edit().putLong("aa_offset_ms", aaOffsetMs).apply()
            updateAaDelayDisplay()
        }
        findViewById<Button>(R.id.btn_aa_delay_plus).setOnClickListener {
            aaOffsetMs += 100
            prefs.edit().putLong("aa_offset_ms", aaOffsetMs).apply()
            updateAaDelayDisplay()
        }
        findViewById<Button>(R.id.btn_aa_delay_reset).setOnClickListener {
            aaOffsetMs = 0L
            prefs.edit().putLong("aa_offset_ms", 0L).apply()
            updateAaDelayDisplay()
        }

        // 手動検索UIを画面にドッキングさせる
        setupSearchUi()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaTracker.state.collect { state ->
                    updatePermissionUi()
                    updateDelayDisplay(state.offsetMs)
                    updateAlbumArt(state)
                    applyThemeColors(state.albumColors)

                    if (state.track != null) {
                        val artistText = if (state.track.artist.isNotBlank())
                            state.track.artist else "Unknown Artist"
                        tvTrack.text = "${state.track.title}\n$artistText"
                        tvTrack.visibility = View.VISIBLE
                    } else {
                        tvTrack.text = ""
                        tvTrack.visibility = View.GONE
                    }

                    if (state.source.isNotBlank()) {
                        val srcText = if (state.detectedLanguage != null) {
                            "${state.source} · ${state.detectedLanguage}→en"
                        } else state.source
                        tvSource.text = srcText
                        tvSource.visibility = View.VISIBLE
                    } else {
                        tvSource.visibility = View.GONE
                    }

                    if (state.status != LyricsStatus.PLAIN_ONLY) {
                        stopPlainScroll()
                    } else {
                        plainScrollAnimator?.let { anim ->
                            if (state.isPlaying && anim.isPaused) anim.resume()
                            else if (!state.isPlaying && anim.isRunning) anim.pause()
                        }
                    }

                    when (state.status) {
                        LyricsStatus.NO_MEDIA -> {
                            tvLyrics.text = "Play a song to see lyrics here.\n\nLyrics will also appear on Android Auto."
                        }
                        LyricsStatus.LOADING -> {
                            tvLyrics.text = "Loading lyrics…"
                        }
                        LyricsStatus.NOT_FOUND -> {
                            tvLyrics.text = "No lyrics found for this track.\n\n💡 Try using the manual search button at the top!"
                        }
                        LyricsStatus.ERROR -> {
                            tvLyrics.text = "Error loading lyrics.\nCheck your internet connection."
                        }
                        LyricsStatus.FOUND -> {
                            renderSyncedLyrics(state)
                        }
                        LyricsStatus.PLAIN_ONLY -> {
                            stopPlainScroll()
                            val hasTrans = state.translatedLines != null
                            val sb = SpannableStringBuilder()
                            sb.append("ℹ  Lyrics are not synced to playback\n\n")
                            sb.append("─────────────────────\n\n")
                            val dimColor = state.albumColors?.textDim ?: DEFAULT_DIM
                            state.lines.forEachIndexed { i, line ->
                                sb.append("${line.text}\n")
                                if (hasTrans) {
                                    val tl = state.translatedLines?.getOrNull(i)
                                    if (!tl.isNullOrBlank()) {
                                        val tlStart = sb.length
                                        sb.append("$tl\n")
                                        sb.setSpan(
                                            RelativeSizeSpan(0.8f),
                                            tlStart, sb.length,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                        sb.setSpan(
                                            ForegroundColorSpan(dimColor),
                                            tlStart, sb.length,
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                }
                                sb.append("\n")
                            }
                            tvLyrics.text = sb
                            startPlainScroll(state)
                        }
                    }
                }
            }
        }
    }

    private fun setupSearchUi() {
        val btnSearchToggle = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_search_category_default)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
        }
        
        val btnSettingsToggle = findViewById<ImageButton>(R.id.btn_settings_toggle)
        (btnSettingsToggle.parent as? LinearLayout)?.addView(btnSearchToggle, 0)

        layoutSearchPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#222233"))
        }

        val layoutInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        etSearchQuery = EditText(this).apply {
            hint = "Search Song (e.g. Kenshi Yonezu LADY)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnSearchSubmit = Button(this).apply {
            text = "SEARCH"
        }

        layoutInputRow.addView(etSearchQuery)
        layoutInputRow.addView(btnSearchSubmit)
        layoutSearchPanel.addView(layoutInputRow)

        val resultsScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400)
        }
        layoutSearchResults = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        resultsScrollView.addView(layoutSearchResults)
        layoutSearchPanel.addView(resultsScrollView)

        rootLayout.addView(layoutSearchPanel, rootLayout.indexOfChild(findViewById(R.id.scroll_lyrics)))

        btnSearchToggle.setOnClickListener {
            fontSettingsPanel.visibility = View.GONE
            layoutSearchPanel.visibility = if (layoutSearchPanel.visibility == View.VISIBLE)
                View.GONE else View.VISIBLE
                
            if (layoutSearchPanel.visibility == View.VISIBLE) {
                val currentTrack = mediaTracker.state.value.track
                if (currentTrack != null) {
                    etSearchQuery.setText("${currentTrack.title} ${currentTrack.artist}")
                }
            }
        }

        btnSearchSubmit.setOnClickListener {
            val query = etSearchQuery.text.toString().trim()
            if (query.isNotBlank()) {
                executeLyricsSearch(query)
            }
        }
    }

    private fun executeLyricsSearch(query: String) {
        layoutSearchResults.removeAllViews()
        val tvLoading = TextView(this).apply { 
            text = "Searching..."
            setTextColor(Color.WHITE)
        }
        layoutSearchResults.addView(tvLoading)

        lifecycleScope.launch(Dispatchers.IO) {
            val results = LrcLibClient.searchLyrics(query)
            withContext(Dispatchers.Main) {
                layoutSearchResults.removeAllViews()
                if (results.isEmpty()) {
                    val tvNoResult = TextView(this@MainActivity).apply { 
                        text = "No tracks found on LRCLIB."
                        setTextColor(Color.RED)
                    }
                    layoutSearchResults.addView(tvNoResult)
                    return@withContext
                }

                results.forEach { track ->
                    val hasSynced = if (track.syncedLyrics != null) " [Synced ✨]" else " [Plain]"
                    val btnTrackOpt = Button(this@MainActivity).apply {
                        text = "${track.trackName} - ${track.artistName}$hasSynced"
                        isAllCaps = false
                        setOnClickListener {
                            mediaTracker.injectManualLyrics(track.syncedLyrics, track.plainLyrics, track.trackName, track.artistName)
                            layoutSearchPanel.visibility = View.GONE
                        }
                    }
                    layoutSearchResults.addView(btnTrackOpt)
                }
            }
        }
    }

    private fun updateAlbumArt(state: LyricsState) {
        val art = state.albumArt
        if (art != null) {
            ivAlbumArt.setImageBitmap(art)
            ivAlbumArt.visibility = View.VISIBLE
        } else {
            ivAlbumArt.setImageDrawable(null)
            ivAlbumArt.visibility = View.GONE
        }
    }

    private fun applyThemeColors(colors: AlbumColors?) {
        if (colors == currentColors) return
        currentColors = colors

        if (colors != null) {
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(colors.dominant, colors.dominantDark)
            )
            rootLayout.background = gradient

            appBar.setBackgroundColor(lighten(colors.dominant, 1.3f))
            delayBar.setBackgroundColor(lighten(colors.dominantDark, 1.2f))
            divider.setBackgroundColor(lighten(colors.dominant, 1.8f))

            tvAppTitle.setTextColor(colors.textPrimary)
            tvAppSubtitle.setTextColor(colors.textDim)
            tvTrack.setTextColor(colors.vibrant)
            tvLyrics.setTextColor(colors.textPrimary)
        } else {
            rootLayout.setBackgroundColor(DEFAULT_BG)
            appBar.setBackgroundColor(DEFAULT_APP_BAR)
            delayBar.setBackgroundColor(DEFAULT_DELAY_BAR)
            divider.setBackgroundColor(DEFAULT_DIVIDER)

            tvAppTitle.setTextColor(Color.parseColor("#E0E0FF"))
            tvAppSubtitle.setTextColor(Color.parseColor("#8888AA"))
            tvTrack.setTextColor(Color.parseColor("#BB86FC"))
            tvLyrics.setTextColor(Color.parseColor("#CCCCDD"))
        }
    }

    private fun lighten(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun updateDelayDisplay(offsetMs: Long) {
        val sign = when {
            offsetMs > 0 -> "+"
            else -> ""
        }
        tvDelay.text = "Phone Sync: ${sign}${offsetMs}ms"
    }

    private fun renderSyncedLyrics(state: LyricsState) {
        val ssb = SpannableStringBuilder()
        val hasKaraoke = state.lines.any { it.words.isNotEmpty() }
        val colors = state.albumColors
        val highlightColor = colors?.vibrant ?: DEFAULT_HIGHLIGHT
        val highlightBg = setAlpha(highlightColor, 0.2f)
        val dimColor = colors?.textDim ?: DEFAULT_DIM

        state.lines.forEachIndexed { i, line ->
            val isCurrentLine = i == state.currentIndex
            val lineStart = ssb.length

            if (isCurrentLine) {
                ssb.append("▶  ")
            } else {
                ssb.append("    ")

