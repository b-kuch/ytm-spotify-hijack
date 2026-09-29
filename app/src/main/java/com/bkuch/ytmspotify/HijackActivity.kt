package com.bkuch.ytmspotify

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Receives YouTube / YouTube Music links (VIEW or share), looks the song/album up on Spotify and
 * opens it there. While it works it shows a small dialog with an "Open in YouTube" escape hatch.
 */
class HijackActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var url: String
    private lateinit var link: YouTubeLink

    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var youtubeButton: Button
    private lateinit var spotifyButton: Button

    private var target: SpotifyTarget? = null
    private var done = false
    private var startedAt = 0L
    private var countdown: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val incoming = when (intent?.action) {
            Intent.ACTION_SEND -> YouTubeLink.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            else -> intent?.dataString
        }
        if (incoming == null) {
            Toast.makeText(this, R.string.no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        url = incoming

        val parsed = YouTubeLink.parse(incoming)
        if (parsed == null) {
            if (intent?.action == Intent.ACTION_SEND) {
                Toast.makeText(this, R.string.not_youtube, Toast.LENGTH_SHORT).show()
            } else {
                // A YouTube link we don't understand (channel page etc.): pass it on untouched.
                Launchers.openInYouTube(this, incoming, incoming.contains("music.youtube.com"))
            }
            finish()
            return
        }
        link = parsed

        // Shared links are an explicit request, so ignore the master switch for them.
        val explicit = intent?.action == Intent.ACTION_SEND
        if (!explicit && (!prefs.enabled || (!link.isMusic && !prefs.handleRegularYouTube))) {
            escapeToYouTube()
            return
        }

        setContentView(R.layout.activity_hijack)
        setFinishOnTouchOutside(true)
        status = findViewById(R.id.status)
        detail = findViewById(R.id.detail)
        progress = findViewById(R.id.progress)
        youtubeButton = findViewById(R.id.open_youtube)
        spotifyButton = findViewById(R.id.open_spotify)

        detail.text = incoming
        youtubeButton.setOnClickListener { escapeToYouTube() }
        spotifyButton.isEnabled = false
        spotifyButton.setOnClickListener { target?.let { launchSpotify(it) } }

        startedAt = SystemClock.uptimeMillis()
        val resolver = Resolver(prefs)
        thread(name = "resolve") {
            val result = resolver.resolve(parsed)
            handler.post { onResolved(result) }
        }
    }

    private fun onResolved(result: Resolution) {
        if (done || isFinishing) return
        when (result) {
            is Resolution.Found -> {
                target = result.target
                progress.visibility = View.INVISIBLE
                spotifyButton.isEnabled = true
                detail.text = result.target.label
                val remaining = prefs.delayMs - (SystemClock.uptimeMillis() - startedAt)
                if (remaining <= 0) {
                    launchSpotify(result.target)
                } else {
                    tick(result.target, SystemClock.uptimeMillis() + remaining)
                }
            }
            is Resolution.NotMusic -> {
                Toast.makeText(this, R.string.not_music, Toast.LENGTH_SHORT).show()
                escapeToYouTube()
            }
            is Resolution.Failed -> {
                progress.visibility = View.INVISIBLE
                status.text = result.message
            }
        }
    }

    /** Updates the "Opening Spotify in N s" text and fires when the deadline passes. */
    private fun tick(target: SpotifyTarget, deadline: Long) {
        val left = deadline - SystemClock.uptimeMillis()
        if (left <= 0) {
            launchSpotify(target)
            return
        }
        val seconds = (left + 999) / 1000
        status.text = getString(
            if (target.exact) R.string.opening_in else R.string.searching_in,
            seconds,
        )
        val r = Runnable { tick(target, deadline) }
        countdown = r
        handler.postDelayed(r, minOf(left, 250L))
    }

    private fun launchSpotify(target: SpotifyTarget) {
        if (done) return
        done = true
        cancelCountdown()
        Launchers.openInSpotify(this, target)
        finish()
    }

    private fun escapeToYouTube() {
        if (done) return
        done = true
        cancelCountdown()
        Launchers.openInYouTube(this, url, link.isMusic)
        finish()
    }

    private fun cancelCountdown() {
        countdown?.let { handler.removeCallbacks(it) }
        countdown = null
    }

    override fun onStop() {
        super.onStop()
        // User backed out / switched away before we opened anything: don't jump to Spotify later.
        if (!done) {
            done = true
            cancelCountdown()
            finish()
        }
    }

    override fun onDestroy() {
        cancelCountdown()
        super.onDestroy()
    }
}
