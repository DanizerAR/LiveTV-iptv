package tv.own.owntv.features.setup

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.Source
import okio.Timeout
import okio.buffer

/**
 * Core's playlist parser reads `#KODIPROP` only between `#EXTINF` and the channel's URL: the
 * `#EXTINF` branch drops whatever accumulated before it. Kodi itself accepts the block on either
 * side, and a real provider (drmlive) writes it **above** the `#EXTINF`:
 *
 * ```
 * #KODIPROP:inputstream.adaptive.license_type=clearkey
 * #KODIPROP:inputstream.adaptive.manifest_type=dash
 * #KODIPROP:inputstream.adaptive.license_key=https://…/key.php?id=…
 * #EXTINF:-1 group-title="1-Playlist-Activation",Activate Playlist
 * https://…/drmlive.mpd?token=…
 * ```
 *
 * Parsed as-is, that entry reaches the database with no licence and no manifest type, so it is
 * opened as a plain progressive stream and fails on the encrypted bytes. Nothing can be done on the
 * app side at parse time — `M3uParser` lives in the published `core` AAR and is final — so this
 * rewrites the *stream*: each run of `#KODIPROP` lines that sits directly above an `#EXTINF` is
 * emitted just after it, where the parser does read it.
 *
 * Lines are moved as whole byte strings, in a streaming fashion (a few held lines, never the whole
 * playlist), and only once the body's first line proves it is a playlist (`#EXTM3U`). A body that
 * is not a playlist is passed through byte for byte. The returned byte count is unchanged: the same
 * lines in a different order.
 */
class M3uKodiPropOrderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response
        val body = response.body ?: return response
        val type = body.contentType()
        // Playlists are text; the M3U media types are the accurate tell, `text/plain` catches the
        // panels that label them plainly. Nothing binary (segments, images, archives) is buffered.
        if (!type.isPlaylistText()) return response
        return response.newBuilder()
            .body(ReorderedBody(body, M3uKodiPropOrder.reorder(body.source())))
            .build()
    }

    private fun MediaType?.isPlaylistText(): Boolean = when {
        this == null -> false
        subtype.endsWith(MPEGURL, ignoreCase = true) -> true
        type.equals(TEXT, ignoreCase = true) && subtype.equals(PLAIN, ignoreCase = true) -> true
        else -> false
    }

    private class ReorderedBody(
        private val original: ResponseBody,
        source: Source,
    ) : ResponseBody() {
        private val buffered = source.buffer()
        override fun contentType(): MediaType? = original.contentType()
        override fun contentLength(): Long = original.contentLength()
        override fun source(): BufferedSource = buffered
    }

    private companion object {
        const val MPEGURL = "mpegurl"
        const val TEXT = "text"
        const val PLAIN = "plain"
    }
}

/** The reordering itself, split out so it can be exercised without a server or a player. */
internal object M3uKodiPropOrder {

    fun reorder(upstream: BufferedSource): Source = ReorderingSource(upstream)

    private class ReorderingSource(private val upstream: BufferedSource) : Source {

        private val out = Buffer()
        private val held = ArrayDeque<ByteString>()
        private var checkedHeader = false
        private var passthrough = false
        private var eof = false
        private var reported = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (out.size == 0L && !eof) fill()
            if (out.size == 0L) return -1L
            return out.read(sink, minOf(byteCount, out.size))
        }

        private fun fill() {
            while (out.size == 0L && !eof) {
                val line = readLine() ?: run {
                    flushHeld()
                    eof = true
                    return
                }
                if (!checkedHeader) {
                    checkedHeader = true
                    passthrough = !startsWithDirective(withoutBom(line), EXT_M3U)
                }
                if (passthrough) {
                    out.write(line)
                    continue
                }
                if (startsWithDirective(line, KODI_PROP)) {
                    held.addLast(line)
                    continue
                }
                if (held.isNotEmpty() && startsWithDirective(line, EXT_INF)) {
                    // The parser clears its accumulator on #EXTINF, so the block has to follow it.
                    out.write(line)
                    flushHeld()
                    if (!reported) {
                        reported = true
                        Log.i(TAG, "playlist: moved #KODIPROP below #EXTINF so the parser keeps it")
                    }
                    continue
                }
                flushHeld()
                out.write(line)
            }
        }

        private fun flushHeld() {
            while (held.isNotEmpty()) out.write(held.removeFirst())
        }

        /** One line including its terminator, so every byte (and CRLF) survives the reorder. */
        private fun readLine(): ByteString? {
            if (upstream.exhausted()) return null
            val end = upstream.indexOf(NEWLINE)
            return if (end == -1L) upstream.readByteString() else upstream.readByteString(end + 1)
        }

        private fun withoutBom(line: ByteString): ByteString =
            if (line.size >= 3 && line[0] == BOM_0 && line[1] == BOM_1 && line[2] == BOM_2) {
                line.substring(3)
            } else {
                line
            }

        /** The parser trims every line, so leading blanks must not hide a directive either. */
        private fun startsWithDirective(line: ByteString, directive: ByteString): Boolean {
            var at = 0
            while (at < line.size && (line[at] == SPACE || line[at] == TAB)) at++
            if (line.size - at < directive.size) return false
            for (i in 0 until directive.size) {
                if (line[at + i] != directive[i]) return false
            }
            return true
        }

        override fun timeout(): Timeout = upstream.timeout()

        override fun close() = upstream.close()

        private companion object {
            const val TAG = "OwnTV-Playlist"
            val NEWLINE = '\n'.code.toByte()
            val SPACE = ' '.code.toByte()
            val TAB = '\t'.code.toByte()
            val BOM_0 = 0xEF.toByte()
            val BOM_1 = 0xBB.toByte()
            val BOM_2 = 0xBF.toByte()
            val EXT_M3U = "#EXTM3U".encodeUtf8()
            val EXT_INF = "#EXTINF".encodeUtf8()
            val KODI_PROP = "#KODIPROP:".encodeUtf8()
        }
    }
}
