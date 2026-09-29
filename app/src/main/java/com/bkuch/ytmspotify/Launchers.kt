package com.bkuch.ytmspotify

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.provider.Browser

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
     * system doesn't bounce it straight back to us, or in the browser when neither is installed.
     * [browserUrl] should not redirect (e.g. youtu.be -> youtube.com), since a redirect can make
     * the browser hand the link back to this app.
     */
    fun openInYouTube(context: Context, url: String, preferMusic: Boolean, browserUrl: String = url) {
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
        openInBrowser(context, browserUrl)
    }

    /** Opens a URL in the browser (default one if set), never in this app. */
    private fun openInBrowser(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        browserPackage(context)?.let { pkg ->
            val direct = Intent(view)
                .setPackage(pkg)
                // Lets Chrome reuse one tab for links coming from this app.
                .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(direct)
                return
            } catch (e: ActivityNotFoundException) {
                // fall back to the chooser
            }
        }
        val chooser = Intent.createChooser(view, null)
            .putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf<Parcelable>(ComponentName(context, HijackActivity::class.java)),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** The default browser, else Chrome, else any browser. Never this app. */
    private fun browserPackage(context: Context): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = context.packageManager
        val default = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        // With no default set this resolves to the system chooser ("android").
        if (default != null && default != "android" && default != context.packageName) return default
        val all = pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }
            .filter { it != context.packageName }
        return all.firstOrNull { it == "com.android.chrome" } ?: all.firstOrNull()
    }
}
