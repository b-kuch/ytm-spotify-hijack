package com.bkuch.ytmspotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParsingTest {

    @Test
    fun musicWatch() {
        val link = YouTubeLink.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc&list=RDAMVM")
        assertEquals(YouTubeLink.Video("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc&list=RDAMVM", true, "dQw4w9WgXcQ"), link)
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", link!!.canonicalUrl)
    }

    @Test
    fun regularAndShortLinks() {
        assertEquals("dQw4w9WgXcQ", (YouTubeLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ") as YouTubeLink.Video).videoId)
        assertEquals("dQw4w9WgXcQ", (YouTubeLink.parse("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ") as YouTubeLink.Video).videoId)
        assertEquals("dQw4w9WgXcQ", (YouTubeLink.parse("https://youtu.be/dQw4w9WgXcQ?si=xyz") as YouTubeLink.Video).videoId)
        assertEquals("dQw4w9WgXcQ", (YouTubeLink.parse("https://youtube.com/shorts/dQw4w9WgXcQ") as YouTubeLink.Video).videoId)
        assertFalse(YouTubeLink.parse("https://youtu.be/dQw4w9WgXcQ")!!.isMusic)
    }

    @Test
    fun albums() {
        val album = YouTubeLink.parse("https://music.youtube.com/playlist?list=OLAK5uy_nMr9h2VlS-2PULNz3M3XVXQj_P3C2bqaY&si=1")
        assertTrue(album is YouTubeLink.Playlist && album.isAlbum && album.isMusic)
        assertEquals("https://www.youtube.com/playlist?list=OLAK5uy_nMr9h2VlS-2PULNz3M3XVXQj_P3C2bqaY", album!!.oEmbedUrl)

        val browse = YouTubeLink.parse("https://music.youtube.com/browse/MPREb_BQZvl3BFGay")
        assertTrue(browse is YouTubeLink.Browse && browse.isAlbum)

        val userPlaylist = YouTubeLink.parse("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI")
        assertTrue(userPlaylist is YouTubeLink.Playlist && !userPlaylist.isAlbum)
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(YouTubeLink.parse("https://www.youtube.com/@somechannel"))
        assertNull(YouTubeLink.parse("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLink.parse("https://www.youtube.com/watch"))
        assertNull(YouTubeLink.parse("not a url"))
    }

    @Test
    fun extractsUrlFromSharedText() {
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=a",
            YouTubeLink.extractUrl("Check this out: https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=a."),
        )
        assertNull(YouTubeLink.extractUrl("nothing here"))
    }

    @Test
    fun trackQueries() {
        assertEquals("Rick Astley Never Gonna Give You Up",
            SearchQuery.forTrack("Rick Astley - Never Gonna Give You Up (Official Music Video)", "Rick Astley"))
        assertEquals("Daft Punk Get Lucky",
            SearchQuery.forTrack("Get Lucky", "Daft Punk - Topic"))
        assertEquals("Adele Hello",
            SearchQuery.forTrack("Hello [Official Video]", "AdeleVEVO"))
        assertEquals("Queen Bohemian Rhapsody",
            SearchQuery.forTrack("Queen – Bohemian Rhapsody | Lyrics", "Some Lyrics Channel"))
        assertEquals("Radiohead Creep",
            SearchQuery.forTrack("Creep", "Radiohead Official"))
    }

    @Test
    fun albumQueries() {
        assertEquals("Daft Punk Random Access Memories",
            SearchQuery.forAlbum("Album - Random Access Memories", "Daft Punk - Topic"))
        assertEquals("Pink Floyd The Dark Side of the Moon",
            SearchQuery.forAlbum("The Dark Side of the Moon (Remastered)", "Pink Floyd"))
    }

    @Test
    fun musicHeuristic() {
        assertTrue(SearchQuery.looksLikeMusic("Get Lucky", "Daft Punk - Topic"))
        assertTrue(SearchQuery.looksLikeMusic("Hello", "AdeleVEVO"))
        assertTrue(SearchQuery.looksLikeMusic("Song (Official Music Video)", "Band"))
        assertFalse(SearchQuery.looksLikeMusic("I built a house in 24 hours", "SomeVlogger"))
    }

    @Test
    fun spotifyUris() {
        assertEquals("spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            SpotifyUris.fromWebUrl("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=123"))
        assertEquals("spotify:album:4m2880jivSbbyEGAKfITCa",
            SpotifyUris.fromWebUrl("https://open.spotify.com/intl-pl/album/4m2880jivSbbyEGAKfITCa"))
        assertNull(SpotifyUris.fromWebUrl("https://example.com/track/x"))
        assertEquals("https://open.spotify.com/track/abc", SpotifyUris.toWebUrl("spotify:track:abc"))
    }
}
