package tv.own.owntv.features.setup

import okio.Buffer
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uKodiPropOrderTest {

    private fun reorder(text: String): String =
        M3uKodiPropOrder.reorder(Buffer().writeUtf8(text)).buffer().readUtf8()

    @Test
    fun `moves a KODIPROP block that sits above EXTINF to below it`() {
        val input = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.manifest_type=dash
            #EXTINF:-1 group-title="1-Playlist-Activation",Activate Playlist
            https://example.test/activation.mpd
        """.trimIndent() + "\n"

        val expected = """
            #EXTM3U
            #EXTINF:-1 group-title="1-Playlist-Activation",Activate Playlist
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.manifest_type=dash
            https://example.test/activation.mpd
        """.trimIndent() + "\n"

        assertEquals(expected, reorder(input))
    }

    @Test
    fun `leaves a block already below EXTINF exactly where it is`() {
        val input = """
            #EXTM3U
            #EXTINF:-1,TSN 1
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=14ee:17d2
            https://example.test/tsn1.mpd
        """.trimIndent() + "\n"

        assertEquals(input, reorder(input))
    }

    @Test
    fun `fixes one entry without disturbing its neighbours`() {
        val input = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_key=https://example.test/key?id=1
            #EXTINF:-1,First
            https://example.test/1.mpd
            #EXTINF:-1,Second
            #KODIPROP:inputstream.adaptive.license_key=aa:bb
            https://example.test/2.mpd
        """.trimIndent() + "\n"

        val expected = """
            #EXTM3U
            #EXTINF:-1,First
            #KODIPROP:inputstream.adaptive.license_key=https://example.test/key?id=1
            https://example.test/1.mpd
            #EXTINF:-1,Second
            #KODIPROP:inputstream.adaptive.license_key=aa:bb
            https://example.test/2.mpd
        """.trimIndent() + "\n"

        assertEquals(expected, reorder(input))
    }

    @Test
    fun `a body that is not a playlist passes through byte for byte`() {
        val input = "<html><body>landing page</body></html>\n#KODIPROP:x\n#EXTINF:-1,No\n"

        assertEquals(input, reorder(input))
    }

    @Test
    fun `CRLF line endings survive, so the byte count does not change`() {
        val input = "#EXTM3U\r\n#KODIPROP:a=b\r\n#EXTINF:-1,One\r\nhttps://example.test/1.mpd\r\n"

        val output = reorder(input)

        assertEquals("#EXTM3U\r\n#EXTINF:-1,One\r\n#KODIPROP:a=b\r\nhttps://example.test/1.mpd\r\n", output)
        assertEquals(input.length, output.length)
    }

    @Test
    fun `a UTF-8 BOM does not hide the playlist header`() {
        val input = "\uFEFF#EXTM3U\n#KODIPROP:a=b\n#EXTINF:-1,One\nhttps://example.test/1.mpd\n"

        val output = reorder(input)

        assertEquals("\uFEFF#EXTM3U\n#EXTINF:-1,One\n#KODIPROP:a=b\nhttps://example.test/1.mpd\n", output)
    }

    @Test
    fun `a leading blank does not hide a directive`() {
        val input = "#EXTM3U\n  #KODIPROP:a=b\n\t#EXTINF:-1,One\nhttps://example.test/1.mpd\n"

        val output = reorder(input)

        assertEquals("#EXTM3U\n\t#EXTINF:-1,One\n  #KODIPROP:a=b\nhttps://example.test/1.mpd\n", output)
    }
}
