package com.bkuch.ytmspotify

import java.net.URI
import java.net.URLDecoder

/** A parsed YouTube / YouTube Music link. Pure JVM code so it can be unit tested. */
sealed class YouTubeLink {
    abstract val originalUrl: String
    abstract val isMusic: Boolean

    /** URL in a form that Odesli and oEmbed understand. */
    abstract val canonicalUrl: String

    /** URL on www.youtube.com, which the oEmbed endpoint accepts. */
    abstract val oEmbedUrl: String?

    data class Video(
        override val originalUrl: String,
        override val isMusic: Boolean,
        val videoId: String,
    ) : YouTubeLink() {
        override val canonicalUrl: String
            get() = if (isMusic) "https://music.youtube.com/watch?v=$videoId"
            else "https://www.youtube.com/watch?v=$videoId"
        override val oEmbedUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
    }

    data class Playlist(
        override val originalUrl: String,
        override val isMusic: Boolean,
        val listId: String,
    ) : YouTubeLink() {
        /** YouTube Music albums/EPs/singles are auto-generated playlists with this prefix. */
        val isAlbum: Boolean get() = listId.startsWith("OLAK5uy_")

        override val canonicalUrl: String
            get() = if (isMusic) "https://music.youtube.com/playlist?list=$listId"
            else "https://www.youtube.com/playlist?list=$listId"
        override val oEmbedUrl: String get() = "https://www.youtube.com/playlist?list=$listId"
    }

    /** music.youtube.com/browse/MPREb_... (album pages) and similar. */
    data class Browse(
        override val originalUrl: String,
        val browseId: String,
    ) : YouTubeLink() {
        override val isMusic: Boolean get() = true
        val isAlbum: Boolean get() = browseId.startsWith("MPREb_")
        override val canonicalUrl: String get() = "https://music.youtube.com/browse/$browseId"
        override val oEmbedUrl: String? get() = null
    }

    companion object {
        private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")
        private val LIST_ID = Regex("""[A-Za-z0-9_-]{10,}""")
        private val BROWSE_ID = Regex("""[A-Za-z0-9_-]{5,}""")

        /** Pulls the first http(s) URL out of arbitrary shared text. */
        fun extractUrl(text: String?): String? =
            text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', '"', '\'', '>') }

        fun parse(url: String): YouTubeLink? {
            val uri = try {
                URI(url.trim())
            } catch (e: Exception) {
                return null
            }
            val host = uri.host?.lowercase() ?: return null
            val segments = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
            val query = parseQuery(uri.rawQuery)

            if (host == "youtu.be" || host == "www.youtu.be") {
                val id = segments.firstOrNull()?.takeIf { VIDEO_ID.matches(it) } ?: return null
                return Video(url, false, id)
            }

            val isMusic = when (host) {
                "music.youtube.com" -> true
                "youtube.com", "www.youtube.com", "m.youtube.com" -> false
                else -> return null
            }

            val v = query["v"]?.takeIf { VIDEO_ID.matches(it) }
            val list = query["list"]?.takeIf { LIST_ID.matches(it) }

            return when (segments.firstOrNull()) {
                "watch" -> when {
                    v != null -> Video(url, isMusic, v)
                    list != null -> Playlist(url, isMusic, list)
                    else -> null
                }
                "playlist" -> list?.let { Playlist(url, isMusic, it) }
                "shorts", "embed", "live", "v" ->
                    segments.getOrNull(1)?.takeIf { VIDEO_ID.matches(it) }?.let { Video(url, isMusic, it) }
                "browse" ->
                    if (isMusic) segments.getOrNull(1)?.takeIf { BROWSE_ID.matches(it) }?.let { Browse(url, it) }
                    else null
                else -> null
            }
        }

        private fun parseQuery(raw: String?): Map<String, String> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (part in raw.split('&')) {
                val idx = part.indexOf('=')
                if (idx <= 0) continue
                val key = decode(part.substring(0, idx))
                if (key !in out) out[key] = decode(part.substring(idx + 1))
            }
            return out
        }

        private fun decode(s: String): String = try {
            URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) {
            s
        }
    }
}
