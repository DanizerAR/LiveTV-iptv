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
import okio.ByteString.Companion.toByteString
import okio.Source
import okio.Timeout
import okio.buffer

/**
 * Kodi's `#KODIPROP` directives, translated into the dialect core's `M3uParser` reads. The parser
 * ships, final, inside the published `core` AAR, so it cannot be corrected; the stream is corrected
 * instead, on its way in. Two differences, both measured on a real provider (drmlive):
 *
 * **1. Where the block sits.** The parser reads `#KODIPROP` only between `#EXTINF` and the URL: its
 * `#EXTINF` branch drops whatever accumulated before it. Kodi accepts the block on either side, and
 * the provider writes it above:
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
 * opened as a plain progressive stream and fails on the encrypted bytes. Each run of `#KODIPROP`
 * lines that sits directly above an `#EXTINF` is therefore emitted just after it.
 *
 * **2. How DASH is spelled.** The same provider declares `manifest_type=dash` — Kodi's spelling —
 * while core's manifest tokens are `mpd`, `hls` and `ism`, so `dash` decodes to nothing. Without a
 * declaration an entry falls to the progressive route and is only re-opened as DASH by the engine's
 * response sniff, one round trip after the manifest was fetched anyway. The value is rewritten to
 * `mpd` in place.
 *
 * Lines are moved as whole byte strings and values are rewritten in place, in a streaming fashion (a
 * few held lines, never the whole playlist), and only once the body's first line proves it is a
 * playlist (`#EXTM3U`). A body that is not a playlist is passed through byte for byte. Nothing ever
 * changes the length: the same lines in a different order, or one value padded with a space (the
 * parser trims every line) — so the response's `Content-Length` still describes the body.
 */
class M3uKodiPropsInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response
        val body = response.body ?: return response
        val type = body.contentType()
        // Playlists are text; the M3U media types are the accurate tell, `text/plain` catches the
        // panels that label them plainly. Nothing binary (segments, images, archives) is buffered.
        if (!type.isPlaylistText()) return response
        return response.newBuilder()
            .body(ReorderedBody(body, M3uKodiProps.rewrite(body.source())))
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

/** The translation itself, split out so it can be exercised without a server or a player. */
internal object M3uKodiProps {

    fun rewrite(upstream: BufferedSource): Source = RewritingSource(upstream)

    private class RewritingSource(private val upstream: BufferedSource) : Source {

        private val out = Buffer()
        private val held = ArrayDeque<ByteString>()
        private var checkedHeader = false
        private var passthrough = false
        private var eof = false
        private var reportedReorder = false
        private var reportedManifest = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (out.size == 0L && !eof) fill()
            if (out.size == 0L) return -1L
            return out.read(sink, minOf(byteCount, out.size))
        }

        private fun fill() {
            while (out.size == 0L && !eof) {
                val raw = readLine() ?: run {
                    flushHeld()
                    eof = true
                    return
                }
                if (!checkedHeader) {
                    checkedHeader = true
                    passthrough = !startsWithDirective(withoutBom(raw), EXT_M3U)
                }
                if (passthrough) {
                    out.write(raw)
                    continue
                }
                val line = normalizeManifestType(raw)
                if (startsWithDirective(line, KODI_PROP)) {
                    held.addLast(line)
                    continue
                }
                if (held.isNotEmpty() && startsWithDirective(line, EXT_INF)) {
                    // The parser clears its accumulator on #EXTINF, so the block has to follow it.
                    out.write(line)
                    flushHeld()
                    if (!reportedReorder) {
                        reportedReorder = true
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

        /**
         * `#KODIPROP:…manifest_type=dash` → `…manifest_type=mpd `, the token core's parser knows.
         * Rewritten in place, so the line — and with it the whole body — keeps its byte count. Every
         * other value (`mpd`, `hls`, `ism`) and every other property is returned untouched.
         */
        private fun normalizeManifestType(line: ByteString): ByteString {
            val directiveAt = directiveOffset(line, KODI_PROP)
            if (directiveAt < 0) return line
            val keyFrom = directiveAt + KODI_PROP.size
            val equals = indexOf(line, EQUALS, keyFrom, line.size)
            if (equals < 0) return line
            val keyTo = lastNonBlank(line, keyFrom, equals)
            if (keyTo < keyFrom) return line
            if (!isManifestKey(asciiLower(line.substring(keyFrom, keyTo + 1)))) return line
            val lineEnd = lineTerminatorAt(line)
            val valueFrom = firstNonBlank(line, equals + 1, lineEnd)
            val valueTo = lastNonBlank(line, equals + 1, lineEnd)
            if (valueFrom < 0 || valueTo < valueFrom) return line
            if (asciiLower(line.substring(valueFrom, valueTo + 1)) != DASH) return line
            val padding = (valueTo - valueFrom + 1) - MPD.size
            if (padding < 0) return line
            if (!reportedManifest) {
                reportedManifest = true
                Log.i(TAG, "playlist: manifest_type=dash stored as mpd, the token core's parser knows")
            }
            val rewritten = Buffer()
            rewritten.write(line.substring(0, valueFrom))
            rewritten.write(MPD)
            repeat(padding) { rewritten.writeByte(SPACE.toInt()) }
            rewritten.write(line.substring(valueTo + 1))
            return rewritten.readByteString()
        }

        /** The parser's own rule: the part after the last dot has to be `manifest_type`. */
        private fun isManifestKey(key: ByteString): Boolean {
            if (!key.endsWith(MANIFEST_TYPE)) return false
            if (key.size == MANIFEST_TYPE.size) return true
            return key[key.size - MANIFEST_TYPE.size - 1] == DOT
        }

        /** One line including its terminator, so every byte (and CRLF) survives the rewrite. */
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
        private fun startsWithDirective(line: ByteString, directive: ByteString): Boolean =
            directiveOffset(line, directive) >= 0

        private fun directiveOffset(line: ByteString, directive: ByteString): Int {
            val at = firstNonBlank(line, 0, line.size)
            return if (matchesAt(line, at, directive)) at else -1
        }

        private fun matchesAt(line: ByteString, at: Int, prefix: ByteString): Boolean {
            if (at < 0 || line.size - at < prefix.size) return false
            for (i in 0 until prefix.size) {
                if (line[at + i] != prefix[i]) return false
            }
            return true
        }

        private fun firstNonBlank(line: ByteString, from: Int, to: Int): Int {
            var at = from
            while (at < to && (line[at] == SPACE || line[at] == TAB)) at++
            return if (at < to) at else -1
        }

        private fun lastNonBlank(line: ByteString, from: Int, to: Int): Int {
            var at = to - 1
            while (at >= from && (line[at] == SPACE || line[at] == TAB)) at--
            return at
        }

        private fun indexOf(line: ByteString, byte: Byte, from: Int, to: Int): Int {
            for (i in from until to) {
                if (line[i] == byte) return i
            }
            return -1
        }

        private fun lineTerminatorAt(line: ByteString): Int {
            for (i in 0 until line.size) {
                if (line[i] == NEWLINE || line[i] == CARRIAGE_RETURN) return i
            }
            return line.size
        }

        private fun asciiLower(bytes: ByteString): ByteString {
            val lowered = ByteArray(bytes.size)
            for (i in 0 until bytes.size) {
                val byte = bytes[i]
                lowered[i] = if (byte in UPPER_A..UPPER_Z) (byte + 32).toByte() else byte
            }
            return lowered.toByteString()
        }

        override fun timeout(): Timeout = upstream.timeout()

        override fun close() = upstream.close()

        private companion object {
            const val TAG = "OwnTV-Playlist"
            val NEWLINE = '\n'.code.toByte()
            val CARRIAGE_RETURN = '\r'.code.toByte()
            val SPACE = ' '.code.toByte()
            val TAB = '\t'.code.toByte()
            val EQUALS = '='.code.toByte()
            val DOT = '.'.code.toByte()
            val UPPER_A = 'A'.code.toByte()
            val UPPER_Z = 'Z'.code.toByte()
            val BOM_0 = 0xEF.toByte()
            val BOM_1 = 0xBB.toByte()
            val BOM_2 = 0xBF.toByte()
            val EXT_M3U = "#EXTM3U".encodeUtf8()
            val EXT_INF = "#EXTINF".encodeUtf8()
            val KODI_PROP = "#KODIPROP:".encodeUtf8()
            val MANIFEST_TYPE = "manifest_type".encodeUtf8()
            val DASH = "dash".encodeUtf8()
            val MPD = "mpd".encodeUtf8()
        }
    }
}
