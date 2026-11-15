package tv.own.owntv.features.setup

import android.util.Log
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.parser.XtreamM3uLink

/**
 * Finds the User-Agent a playlist endpoint is willing to answer.
 *
 * Some providers serve their playlist only to a set of player identities and answer everyone else
 * with a redirect to a landing page (measured on one: VLC, curl, browsers and media players got a
 * 302 to a Telegram channel, while "OTT Navigator", "Android" and a bare `okhttp` were served the
 * real 2213-channel M3U). Because the redirect ends on an HTML page, the import does not fail — it
 * "succeeds" with zero channels, which looks exactly like the app refusing the link.
 *
 * The syncer's VLC identity is what most panels expect, so that is asked first and nothing changes
 * for them. Only when it is refused is a short list of generic identities tried. The one that
 * answers a real `#EXTM3U` is stored on the source, so the syncer and the player then use it for
 * every request.
 */
class PlaylistUserAgent(private val http: HttpClient) {

    /**
     * The User-Agent to store on the source: [requested] when the person set one, otherwise the
     * default (`""`, which the syncer turns into its VLC identity) or a fallback that this endpoint
     * actually serves.
     */
    suspend fun resolve(url: String, requested: String): String {
        val chosen = requested.trim()
        if (chosen.isNotEmpty()) return chosen
        // A local file is read from disk and a get.php "M3U" is an Xtream account imported (and
        // fetched) as one — neither spends a request on the default identity, so neither is probed.
        if (!url.startsWith("http", ignoreCase = true)) return ""
        if (XtreamM3uLink.parse(url) != null) return ""
        if (isPlaylist(url, userAgent = null)) return ""
        for (candidate in CANDIDATES) {
            if (isPlaylist(url, candidate)) {
                Log.i(TAG, "playlist refused the default User-Agent; using \"$candidate\"")
                return candidate
            }
        }
        return ""
    }

    /**
     * The first bytes only: a playlist says `#EXTM3U` on its first line, and the landing pages these
     * panels redirect to do not. Reading 4 KB is cheap even for a 700 KB catalogue.
     */
    private suspend fun isPlaylist(url: String, userAgent: String?): Boolean = runCatching {
        http.get(XtreamM3uLink.clean(url), userAgent, maxAttempts = 1) { input ->
            val buffer = ByteArray(PROBE_BYTES)
            val read = input.read(buffer).coerceAtLeast(0)
            // U+FEFF is the byte-order mark, written as an escape: a literal one in the source is
            // both invisible to read and a lint error (ByteOrderMark).
            String(buffer, 0, read, Charsets.UTF_8)
                .trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                .startsWith("#EXTM3U", ignoreCase = true)
        }
    }.getOrDefault(false)

    private companion object {
        const val TAG = "PlaylistUserAgent"
        const val PROBE_BYTES = 4 * 1024

        /**
         * Tried in order after the default is refused. A real IPTV player first (bare names are
         * enough for the panels that filter on them), then two generic identities that no known
         * blocklist targets.
         */
        val CANDIDATES = listOf("OTT Navigator", "Android", "okhttp/5.5.0")
    }
}
