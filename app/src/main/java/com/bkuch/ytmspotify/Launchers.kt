package com.bkuch.ytmspotify

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser
import android.widget.Toast

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

    /**
     * Opens a URL in the browser (default one if set), never in this app.
     *
     * Once this app is approved for the YouTube domains, Android 12+ drops browsers from the
     * resolution of YouTube URLs, so an implicit intent or chooser finds nothing ("No apps can
     * perform this action"). We therefore resolve the browser's generic web handler with a
     * neutral URL and target that exact component, which skips domain filtering.
     */
    private fun openInBrowser(context: Context, url: String) {
        for (component in browserComponents(context)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setComponent(component)
                // Lets Chrome reuse one tab for links coming from this app.
                .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // try the next browser
            } catch (e: SecurityException) {
                // try the next browser
            }
        }
        Toast.makeText(context, R.string.no_browser, Toast.LENGTH_LONG).show()
    }

    /** Browser web-link activities: the default browser first, then Chrome, then the rest. */
    private fun browserComponents(context: Context): List<ComponentName> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val pm = context.packageManager
        val all = pm.queryIntentActivities(probe, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
        val default = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return all.sortedBy {
            when (it.packageName) {
                default -> 0
                "com.android.chrome" -> 1
                else -> 2
            }
        }.map { ComponentName(it.packageName, it.name) }
    }
}
