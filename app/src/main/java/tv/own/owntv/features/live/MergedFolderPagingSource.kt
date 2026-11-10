package tv.own.owntv.features.live

import androidx.paging.PagingSource
import androidx.paging.PagingState
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * The channels of several provider folders at once — the "News" of three playlists, shown as one
 * category.
 *
 * Core's paging queries read one folder at a time, so a merged category cannot be a plain query.
 * This takes a bounded snapshot of each folder in the group and pages through the merged list.
 * The snapshot is bounded on purpose: a merged category is a convenience view, not the place to
 * hold a whole playlist in memory.
 */
internal class MergedFolderPagingSource(
    private val categoryIds: List<Long>,
    private val channelDao: ChannelDao,
    private val profileId: Long,
    /** The manual-order context of the group; only the first folder has rows in it. */
    private val contextKey: String,
    private val alpha: Boolean,
    private val query: String,
) : PagingSource<Int, ChannelEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> = try {
        val wanted = params.loadSize.coerceIn(1, 600)
        val all = ArrayList<ChannelEntity>()
        for (id in categoryIds) {
            val rows = channelDao.snapshotByCategoryManual(id, profileId, contextKey, alpha, wanted)
            all.addAll(rows)
            if (all.size >= wanted) break
        }
        val q = query.trim()
        val data = if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) }
        LoadResult.Page(data = data.take(wanted), prevKey = null, nextKey = null)
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? = null
}
