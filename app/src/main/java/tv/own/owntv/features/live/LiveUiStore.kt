package tv.own.owntv.features.live

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-wide choices that only this live-TV build needs and core's settings store does not know:
 * whether the left navigation rail is shown, and which playlists the Live screen merges.
 *
 * Kept in SharedPreferences rather than core's DataStore because it is a property of this television,
 * not of a viewer profile, and because the rail is read during the first composition, before a
 * DataStore read could land. Empty [playlistIds] means "All playlists", so the merged view is the
 * default and a selection of every playlist collapses to the same state.
 */
object LiveUiStore {

    private const val PREFS = "owntv_live_ui"
    private const val KEY_RAIL = "rail_visible"
    private const val KEY_PLAYLISTS = "playlist_ids"
    private const val KEY_THREE_PANEL_SEEDED = "three_panel_seeded"

    private lateinit var prefs: android.content.SharedPreferences

    private val _railVisible = MutableStateFlow(true)
    val railVisible: StateFlow<Boolean> = _railVisible.asStateFlow()

    private val _playlistIds = MutableStateFlow<Set<Long>>(emptySet())
    val playlistIds: StateFlow<Set<Long>> = _playlistIds.asStateFlow()

    private val _threePanelSeeded = MutableStateFlow(false)
    val threePanelSeeded: StateFlow<Boolean> = _threePanelSeeded.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _railVisible.value = prefs.getBoolean(KEY_RAIL, true)
        _playlistIds.value = parseIds(prefs.getString(KEY_PLAYLISTS, null))
        _threePanelSeeded.value = prefs.getBoolean(KEY_THREE_PANEL_SEEDED, false)
    }

    fun setRailVisible(visible: Boolean) {
        _railVisible.value = visible
        if (::prefs.isInitialized) prefs.edit { putBoolean(KEY_RAIL, visible) }
    }

    /** Empty means every playlist. */
    fun setPlaylistIds(ids: Set<Long>) {
        val clean = ids.filter { it > 0L }.toSet()
        _playlistIds.value = clean
        if (::prefs.isInitialized) {
            prefs.edit { putString(KEY_PLAYLISTS, clean.joinToString(",")) }
        }
    }

    fun markThreePanelSeeded() {
        _threePanelSeeded.value = true
        if (::prefs.isInitialized) prefs.edit { putBoolean(KEY_THREE_PANEL_SEEDED, true) }
    }

    private fun parseIds(raw: String?): Set<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0L }.toSet()
}
