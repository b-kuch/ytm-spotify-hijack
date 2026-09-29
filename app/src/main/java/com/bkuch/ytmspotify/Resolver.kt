package com.bkuch.ytmspotify

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where we ended up sending the user. */
data class SpotifyTarget(
    /** spotify:track:..., spotify:album:... or spotify:search:... */
    val uri: String,
    /** Browser fallback when the Spotify app isn't installed. */
    val webUrl: String,
    /** Human readable description shown in the dialog. */
    val label: String,
    /** false when we only managed to build a search query. */
    val exact: Boolean,
)

sealed class Resolution {
    data class Found(val target: SpotifyTarget) : Resolution()
    /** A regular youtube.com video that doesn't look like music. */
    data class NotMusic(val title: String?) : Resolution()
    data class Failed(val message: String) : Resolution()
}

/** Blocking network code; call from a background thread. */
class Resolver(private val prefs: Prefs) {

    fun resolve(link: YouTubeLink): Resolution {
        // 1. Odesli (song.link) maps YouTube / YT Music ids straight to Spotify ids.
        val odesli = try {
            odesli(link)
        } catch (e: Exception) {
            null
        }
        if (odesli is OdesliResult.Found) return Resolution.Found(odesli.target)

        // 2. Fall back to the YouTube title + a Spotify search.
        val meta = try {
            metadata(link)
        } catch (e: Exception) {
            null
        } ?: return Resolution.Failed("Couldn't read the YouTube link (offline?)")

        if (!link.isMusic && !SearchQuery.looksLikeMusic(meta.title, meta.author)) {
            return Resolution.NotMusic(meta.title)
        }

        val kind = when (link) {
            is YouTubeLink.Video -> "track"
            is YouTubeLink.Playlist -> if (link.isAlbum) "album" else "playlist"
            is YouTubeLink.Browse -> if (link.isAlbum) "album" else "playlist"
        }
        val query = if (kind == "track") SearchQuery.forTrack(meta.title, meta.author)
        else SearchQuery.forAlbum(meta.title, meta.author)
        if (query.isBlank()) return Resolution.Failed("Couldn't work out what to search for")

        // 3. With API credentials we can jump straight to the best match.
        if (kind != "playlist" && prefs.hasSpotifyCredentials) {
            try {
                spotifySearch(query, kind)?.let { return Resolution.Found(it) }
            } catch (e: Exception) {
                // fall through to a plain search
            }
        }

        val encoded = encodePath(query)
        return Resolution.Found(
            SpotifyTarget(
                uri = "spotify:search:$encoded",
                webUrl = "https://open.spotify.com/search/$encoded",
                label = "Search: $query",
                exact = false,
            )
        )
    }

    // ---------------------------------------------------------------- Odesli

    private sealed class OdesliResult {
        data class Found(val target: SpotifyTarget) : OdesliResult()
        object NoMatch : OdesliResult()
    }

    private fun odesli(link: YouTubeLink): OdesliResult {
        val res = get(
            "https://api.song.link/v1-alpha.1/links?userCountry=${prefs.country}&url=" +
                enc(link.canonicalUrl)
        )
        if (res.code != 200) {
            throw IOException("Odesli HTTP ${res.code}")
        }
        val json = JSONObject(res.body)
        val spotify = json.optJSONObject("linksByPlatform")?.optJSONObject("spotify")
            ?: return OdesliResult.NoMatch
        val webUrl = spotify.optString("url")
        val uri = SpotifyUris.fromWebUrl(webUrl) ?: return OdesliResult.NoMatch

        val entity = json.optJSONObject("entitiesByUniqueId")
            ?.optJSONObject(spotify.optString("entityUniqueId"))
        val title = entity?.optString("title").orEmpty()
        val artist = entity?.optString("artistName").orEmpty()
        val label = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" — ")
            .ifEmpty { uri }
        return OdesliResult.Found(SpotifyTarget(uri, webUrl, label, exact = true))
    }

    // ------------------------------------------------------------- metadata

    private data class Meta(val title: String, val author: String)

    private fun metadata(link: YouTubeLink): Meta? {
        link.oEmbedUrl?.let { url ->
            val res = get("https://www.youtube.com/oembed?format=json&url=" + enc(url))
            if (res.code == 200) {
                val json = JSONObject(res.body)
                val title = json.optString("title")
                if (title.isNotBlank()) return Meta(title, json.optString("author_name"))
            }
        }
        // Album "browse" pages have no oEmbed; scrape og:title instead.
        val res = get(link.canonicalUrl, mapOf("Cookie" to "SOCS=CAI"))
        if (res.code != 200) return null
        val title = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")
            .find(res.body)?.groupValues?.get(1)?.let(::unescapeHtml)
            ?: Regex("""<title>([^<]+)</title>""").find(res.body)?.groupValues?.get(1)
                ?.let(::unescapeHtml)?.removeSuffix(" - YouTube Music")?.removeSuffix(" - YouTube")
            ?: return null
        if (title.isBlank() || title == "YouTube Music" || title == "YouTube") return null
        return Meta(title, "")
    }

    // ------------------------------------------------------- Spotify Web API

    private fun spotifySearch(query: String, type: String): SpotifyTarget? {
        val token = spotifyToken() ?: return null
        val res = get(
            "https://api.spotify.com/v1/search?limit=1&type=$type&market=${prefs.country}&q=" + enc(query),
            mapOf("Authorization" to "Bearer $token"),
        )
        if (res.code != 200) return null
        val item = JSONObject(res.body).optJSONObject("${type}s")?.optJSONArray("items")
            ?.optJSONObject(0) ?: return null
        val uri = item.optString("uri").takeIf { it.startsWith("spotify:") } ?: return null
        val artist = item.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
        val label = listOf(item.optString("name"), artist).filter { it.isNotBlank() }.joinToString(" — ")
        val webUrl = item.optJSONObject("external_urls")?.optString("spotify")
            ?.takeIf { it.isNotBlank() } ?: SpotifyUris.toWebUrl(uri) ?: return null
        return SpotifyTarget(uri, webUrl, label, exact = true)
    }

    private fun spotifyToken(): String? {
        synchronized(tokenLock) {
            val now = System.currentTimeMillis()
            if (cachedToken != null && now < tokenExpiry && cachedTokenKey == prefs.credentialsKey) {
                return cachedToken
            }
            val basic = Base64.encodeToString(
                "${prefs.spotifyClientId}:${prefs.spotifyClientSecret}".toByteArray(),
                Base64.NO_WRAP,
            )
            val res = post(
                "https://accounts.spotify.com/api/token",
                "grant_type=client_credentials",
                mapOf("Authorization" to "Basic $basic"),
            )
            if (res.code != 200) return null
            val json = JSONObject(res.body)
            cachedToken = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
            tokenExpiry = now + (json.optLong("expires_in", 3600) - 60) * 1000
            cachedTokenKey = prefs.credentialsKey
            return cachedToken
        }
    }

    // ------------------------------------------------------------------ http

    private data class Response(val code: Int, val body: String)

    private fun get(url: String, headers: Map<String, String> = emptyMap()) =
        request("GET", url, null, headers)

    private fun post(url: String, form: String, headers: Map<String, String>) =
        request("POST", url, form, headers + ("Content-Type" to "application/x-www-form-urlencoded"))

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "en")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Response(code, text)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Spotify search URIs want %20 rather than '+'. */
    private fun encodePath(s: String) = enc(s).replace("+", "%20")

    private fun unescapeHtml(s: String) = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0 Safari/537.36"

        private val tokenLock = Any()
        private var cachedToken: String? = null
        private var cachedTokenKey: String? = null
        private var tokenExpiry = 0L
    }
}
