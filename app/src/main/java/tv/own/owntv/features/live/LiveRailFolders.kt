package tv.own.owntv.features.live

import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.live.LiveBuiltIns

/** The rail's inputs, gathered so the category build can read them in one place. */
internal data class RailBase(
    val cats: List<CategoryEntity>,
    val cust: SectionCustomizations,
    val alphabetical: Boolean,
    val kids: Boolean,
    val catchup: Boolean,
    val builtIns: LiveBuiltIns,
    /** The playlist name of each source, when more than one playlist is merged. */
    val sourceNames: Map<Long, String>,
)

/** One row of the category rail: a provider folder, or a user's own combined category. */
internal data class RailFolder(
    val categoryId: Long?,
    val customId: String?,
    val displayName: String,
    /** The playlist the folder came from (-1 for a user's own category). */
    val sourceId: Long,
    val locked: Boolean,
    val sealed: Boolean,
)

/**
 * Folds the provider folders that share a name across the playlists into one row each.
 *
 * Three playlists that all publish "News" would otherwise fill the list with three News rows,
 * each with a third of the channels. Merged, there is one News row holding all three sets of
 * channels. Only names shared by *different* playlists are folded: two folders with the same name
 * inside one playlist are two different categories that happen to be called the same, and folding
 * them would lose the difference.
 *
 * The folded row keeps the first folder's id, and the map says which other ids it stands for.
 */
internal fun mergeRailFolders(
    folders: List<RailFolder>,
    mergeSameName: Boolean,
): Pair<List<RailFolder>, Map<Long, List<Long>>> {
    if (!mergeSameName) return folders to emptyMap()
    val shared = folders
        .filter { it.categoryId != null }
        .groupBy { it.displayName.trim().lowercase() }
        .filterValues { list -> list.map { it.sourceId }.distinct().size > 1 }
    if (shared.isEmpty()) return folders to emptyMap()
    val merged = HashMap<Long, List<Long>>()
    val out = ArrayList<RailFolder>(folders.size)
    val folded = HashSet<String>()
    for (f in folders) {
        val group = if (f.customId == null) shared[f.displayName.trim().lowercase()] else null
        if (group == null) {
            out.add(f)
            continue
        }
        // The first folder of the name stands for the group; the rest fold into it.
        if (!folded.add(f.displayName.trim().lowercase())) continue
        merged[f.categoryId!!] = group.mapNotNull { it.categoryId }
        out.add(f)
    }
    return out to merged
}
