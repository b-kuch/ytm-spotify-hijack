package com.bkuch.ytmspotify

import android.content.Context
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Master switch. When off every link goes straight to YouTube. */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** Also hijack plain youtube.com links (only ones that turn out to be music). */
    var handleRegularYouTube: Boolean
        get() = sp.getBoolean("handle_regular", true)
        set(v) = sp.edit().putBoolean("handle_regular", v).apply()

    /** How long the "Open in YouTube instead" escape hatch stays up before Spotify opens. */
    var delayMs: Int
        get() = sp.getInt("delay_ms", 1500)
        set(v) = sp.edit().putInt("delay_ms", v).apply()

    var spotifyClientId: String
        get() = sp.getString("spotify_client_id", "").orEmpty()
        set(v) = sp.edit().putString("spotify_client_id", v.trim()).apply()

    var spotifyClientSecret: String
        get() = sp.getString("spotify_client_secret", "").orEmpty()
        set(v) = sp.edit().putString("spotify_client_secret", v.trim()).apply()

    val hasSpotifyCredentials: Boolean
        get() = spotifyClientId.isNotBlank() && spotifyClientSecret.isNotBlank()

    val credentialsKey: String
        get() = "$spotifyClientId:$spotifyClientSecret"

    val country: String
        get() = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
}
