package dev.nncx.folders

import dev.nncx.apps.AppEntry
import dev.nncx.index.Rank
import dev.nncx.index.UseLog

/**
 * How often a folder's answer can change. There is no HOT tier: a folder whose
 * contents move because someone else sent you a message is a feed, not a
 * filing system, and the notification shade already exists.
 */
enum class Tier { COLD, WARM }

/** Everything a rule is allowed to look at. */
data class AppFacts(
    val entry: AppEntry,
    val rank: Rank,
    val signedLikeMe: Boolean,
    val installedDaysAgo: Long,
    val logSpanDays: Long,
    /** Wall clock of the last foreground moment, or 0 if never / unknown. */
    val lastUsedMs: Long = 0L,
) {
    val packageName: String get() = entry.packageName
}

/**
 * A folder is a query, not a container. Nothing is filed and nothing can rot,
 * because membership is derived every time it is asked for.
 *
 * `exit` is the hysteresis half: an app already inside stays until `exit` says
 * otherwise, so one sitting exactly on the threshold doesn't flicker. Leave it
 * null and the folder is a plain predicate.
 */
class Folder(
    val id: String,
    val name: String,
    val rule: String,
    val tier: Tier,
    val enter: (AppFacts) -> Boolean,
    val exit: ((AppFacts) -> Boolean)? = null,
    /**
     * Most folders answer "how often", so most-used-first is right. A folder
     * about the last day answers "how recently", and wants the other order.
     */
    val byRecency: Boolean = false,
)

data class FolderView(
    val folder: Folder,
    val members: List<AppFacts>,
) {
    val size: Int get() = members.size
}

object FolderEngine {

    /**
     * Membership is persisted only so hysteresis has a yesterday to compare
     * against — it is never the source of truth. Wipe the table and every
     * folder rebuilds itself correctly on the next pass.
     */
    fun evaluate(
        log: UseLog,
        folders: List<Folder>,
        facts: List<AppFacts>,
    ): List<FolderView> = folders.map { folder ->
        val previous = log.members(folder.id)

        val members = facts.filter { f ->
            if (f.packageName in previous) {
                folder.exit?.let { !it(f) } ?: folder.enter(f)
            } else {
                folder.enter(f)
            }
        }

        log.setMembers(folder.id, members.mapTo(HashSet()) { it.packageName })

        FolderView(
            folder = folder,
            // Within a folder, most used first — the same comparator as everywhere.
            members = if (folder.byRecency) {
                members.sortedByDescending { it.lastUsedMs }
            } else {
                members.sortedWith(
                    compareByDescending<AppFacts> { it.rank.total14 }.thenBy { it.entry.sortKey }
                )
            },
        )
    }
}
