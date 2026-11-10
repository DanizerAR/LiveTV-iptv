package tv.own.owntv.features.live

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the Settings window dims what is behind it: [dim] is how much black sits over the picture
 * (100 = opaque black, 0 = the picture untouched), and [glass] adds the window's frosted surface.
 */
data class SettingsBackdrop(val dim: Int, val glass: Boolean)

/**
 * Device-wide choices that only this live-TV build needs and core's settings store does not know:
 * whether the left navigation rail is shown, which playlists the Live screen merges, how the
 * categories are grouped, and how the Settings window is backed.
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
    private const val KEY_HIDE_EMPTY = "hide_empty_categories"
    private const val KEY_MERGE_SAME_NAME = "merge_same_name_categories"
    private const val KEY_BACKDROP = "settings_backdrop"
    private const val KEY_BACKDROP_DIM = "settings_backdrop_dim"
    private const val KEY_BACKDROP_GLASS = "settings_backdrop_glass"

    private lateinit var prefs: android.content.SharedPreferences

    private val _railVisible = MutableStateFlow(true)
    val railVisible: StateFlow<Boolean> = _railVisible.asStateFlow()

    private val _playlistIds = MutableStateFlow<Set<Long>>(emptySet())
    val playlistIds: StateFlow<Set<Long>> = _playlistIds.asStateFlow()

    private val _threePanelSeeded = MutableStateFlow(false)
    val threePanelSeeded: StateFlow<Boolean> = _threePanelSeeded.asStateFlow()

    /** A category with no channels left is not worth a row in the list. */
    private val _hideEmptyCategories = MutableStateFlow(false)
    val hideEmptyCategories: StateFlow<Boolean> = _hideEmptyCategories.asStateFlow()

    /** The same category name in two playlists is one category, holding both playlists' channels. */
    private val _mergeSameNameCategories = MutableStateFlow(false)
    val mergeSameNameCategories: StateFlow<Boolean> = _mergeSameNameCategories.asStateFlow()

    private val _settingsBackdrop = MutableStateFlow(SettingsBackdrop(dim = 78, glass = false))
    val settingsBackdrop: StateFlow<SettingsBackdrop> = _settingsBackdrop.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _railVisible.value = prefs.getBoolean(KEY_RAIL, true)
        _playlistIds.value = parseIds(prefs.getString(KEY_PLAYLISTS, null))
        _threePanelSeeded.value = prefs.getBoolean(KEY_THREE_PANEL_SEEDED, false)
        _hideEmptyCategories.value = prefs.getBoolean(KEY_HIDE_EMPTY, false)
        _mergeSameNameCategories.value = prefs.getBoolean(KEY_MERGE_SAME_NAME, false)
        _settingsBackdrop.value = SettingsBackdrop(
            dim = prefs.getInt(KEY_BACKDROP_DIM, 78).coerceIn(0, 100),
            glass = prefs.getBoolean(KEY_BACKDROP_GLASS, false),
        )
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

    fun setHideEmptyCategories(hide: Boolean) {
        _hideEmptyCategories.value = hide
        if (::prefs.isInitialized) prefs.edit { putBoolean(KEY_HIDE_EMPTY, hide) }
    }

    fun setMergeSameNameCategories(merge: Boolean) {
        _mergeSameNameCategories.value = merge
        if (::prefs.isInitialized) prefs.edit { putBoolean(KEY_MERGE_SAME_NAME, merge) }
    }

    /** How much black the window puts over the picture: 100 = opaque, 0 = the picture untouched. */
    fun setSettingsBackdropDim(dim: Int) {
        val clean = dim.coerceIn(0, 100)
        _settingsBackdrop.value = _settingsBackdrop.value.copy(dim = clean)
        if (::prefs.isInitialized) prefs.edit { putInt(KEY_BACKDROP_DIM, clean) }
    }

    /** The window's own frosted surface, over the dimming. */
    fun setSettingsBackdropGlass(glass: Boolean) {
        _settingsBackdrop.value = _settingsBackdrop.value.copy(glass = glass)
        if (::prefs.isInitialized) prefs.edit { putBoolean(KEY_BACKDROP_GLASS, glass) }
    }

    private fun parseIds(raw: String?): Set<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it > 0L }.toSet()
}
