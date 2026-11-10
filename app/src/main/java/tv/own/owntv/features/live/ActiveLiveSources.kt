package tv.own.owntv.features.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository

/**
 * Core's active sources, narrowed to the playlists chosen in the top-bar picker. An empty choice
 * (the default) leaves core's "All playlists" view untouched; a selection filters the source list
 * before the Live screen derives categories, channel counts and page queries from it.
 */
internal fun activeLiveProfileSources(
    settings: SettingsRepository,
    sourceDao: SourceDao,
): Flow<ActiveProfileSources> =
    activeProfileSources(settings, sourceDao)
        .combine(LiveUiStore.playlistIds) { aps, selected ->
            if (selected.isEmpty()) aps else aps.copy(sources = aps.sources.filter { it.id in selected })
        }
        .distinctUntilChanged()

/** The same narrowing for the suspend call sites (the guide's queries and the startup-channel search). */
internal suspend fun activeLiveSourceIds(
    settings: SettingsRepository,
    sourceDao: SourceDao,
    profileId: Long,
): List<Long> {
    val ids = activeSourceIds(settings, sourceDao, profileId, MediaType.LIVE)
    val selected = LiveUiStore.playlistIds.value
    return if (selected.isEmpty()) ids else ids.filter { it in selected }
}
