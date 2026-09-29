package com.bkuch.ytmspotify

/** Turns YouTube titles/channel names into something Spotify search copes with. Pure JVM. */
object SearchQuery {
    private val IGNORE = RegexOption.IGNORE_CASE

    private const val NOISE_WORDS =
        "official|video|audio|lyrics?|visuali[sz]er|hd|hq|4k|m/?v|explicit|clip|remaster(ed)?|full album"

    /** "(Official Music Video)", "[HD]", "【MV】" ... */
    private val NOISE_BRACKETS =
        Regex("""\s*[(\[【][^)\]】]*\b($NOISE_WORDS)\b[^)\]】]*[)\]】]""", IGNORE)

    /** "Song - Official Video", "Song | Lyrics" at the end. */
    private val TRAILING_NOISE =
        Regex("""\s*[-|:]\s*(official\s+)?(music\s+)?(video|audio|lyrics?|lyric video|visuali[sz]er)\s*$""", IGNORE)

    private val ALBUM_PREFIX = Regex("""^\s*(album|ep|single)\s*[-:–]\s*""", IGNORE)

    private val MULTI_SPACE = Regex("""\s+""")

    fun cleanTitle(title: String): String {
        var t = title
        t = NOISE_BRACKETS.replace(t, "")
        t = TRAILING_NOISE.replace(t, "")
        t = t.replace('|', ' ')
        return MULTI_SPACE.replace(t, " ").trim()
    }

    fun cleanArtist(author: String): String {
        var a = author.trim()
        a = a.removeSuffix(" - Topic")
        a = Regex("""\s*VEVO$""", IGNORE).replace(a, "")
        a = Regex("""\s*(official)?\s*(channel)?$""", IGNORE).replace(a, "")
        return MULTI_SPACE.replace(a, " ").trim()
    }

    fun forTrack(title: String, author: String): String {
        val t = cleanTitle(title)
        val a = cleanArtist(author)
        // "Artist - Song" titles already contain the artist; the channel is often a label.
        val dash = Regex("""\s+[-–—]\s+""")
        return when {
            dash.containsMatchIn(t) -> dash.replace(t, " ")
            a.isEmpty() -> t
            t.contains(a, ignoreCase = true) -> t
            else -> "$a $t"
        }.let { MULTI_SPACE.replace(it, " ").trim() }
    }

    fun forAlbum(title: String, author: String): String {
        val t = cleanTitle(ALBUM_PREFIX.replace(title, ""))
        val a = cleanArtist(author)
        return if (a.isEmpty() || t.contains(a, ignoreCase = true)) t else "$a $t"
    }

    /** Heuristic used for plain youtube.com links when Odesli can't tell us. */
    fun looksLikeMusic(title: String, author: String): Boolean =
        author.endsWith(" - Topic") ||
            author.trim().endsWith("VEVO", ignoreCase = true) ||
            Regex("""official\s+(music\s+)?(video|audio)|lyric|visuali[sz]er|\(audio\)""", IGNORE)
                .containsMatchIn(title)
}

/** Converts https://open.spotify.com/(intl-xx/)track/ID?si=... to spotify:track:ID. Pure JVM. */
object SpotifyUris {
    private val TYPES = setOf("track", "album", "artist", "playlist", "episode", "show")

    fun fromWebUrl(url: String): String? {
        val path = url.substringAfter("open.spotify.com/", "").substringBefore('?').substringBefore('#')
        val segs = path.split('/').filter { it.isNotEmpty() && !it.startsWith("intl-") }
        if (segs.size < 2 || segs[0] !in TYPES) return null
        return "spotify:${segs[0]}:${segs[1]}"
    }

    fun toWebUrl(uri: String): String? {
        val parts = uri.split(':')
        if (parts.size != 3 || parts[0] != "spotify" || parts[1] !in TYPES) return null
        return "https://open.spotify.com/${parts[1]}/${parts[2]}"
    }
}
