package com.bkuch.ytmspotify

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable

object Launchers {
    const val SPOTIFY = "com.spotify.music"
    const val YOUTUBE = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    fun openInSpotify(context: Context, target: SpotifyTarget) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target.uri))
            .setPackage(SPOTIFY)
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Spotify not installed: open.spotify.com in a browser.
            openInBrowser(context, target.webUrl)
        }
    }

    /**
     * The escape hatch. Opens the link in the YouTube Music / YouTube app explicitly so the
     * system doesn't bounce it straight back to us.
     */
    fun openInYouTube(context: Context, url: String, preferMusic: Boolean) {
        val packages = if (preferMusic) listOf(YOUTUBE_MUSIC, YOUTUBE) else listOf(YOUTUBE, YOUTUBE_MUSIC)
        for (pkg in packages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // try the next one
            }
        }
        openInBrowser(context, url)
    }

    /** Opens a URL with anything except this app. */
    private fun openInBrowser(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val chooser = Intent.createChooser(view, null)
            .putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf<Parcelable>(ComponentName(context, HijackActivity::class.java)),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
