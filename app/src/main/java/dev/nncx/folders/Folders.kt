package dev.nncx.folders

/**
 * The rules, in Kotlin, in source.
 *
 * No parser, no settings screen, no config format. Editing your folders means
 * editing code — which in a clone-and-build launcher is the point. A text
 * format is a weekend of parsing and validation for a config only you will
 * ever write.
 */
data class FolderConfig(
    val workPrefixes: List<String>,
    val logSpanDays: Long,
    /** Wall clock at the moment the folders were evaluated. */
    val nowMs: Long,
)

object Folders {

    /** Display order. First match is an app's primary folder. */
    fun all(cfg: FolderConfig): List<Folder> = listOf(

        Folder(
            id = "daily",
            name = "Daily",
            rule = "launches/day > 5 · leaves below 3",
            tier = Tier.WARM,
            enter = { it.rank.perDay(it.logSpanDays) > 5 },
            exit = { it.rank.perDay(it.logSpanDays) < 3 },
        ),

        // Replaces "Morning". A share-of-the-morning rule was a statement about
        // habit; this is a statement about the last few hours, which is what
        // you actually reach for mid-day. Sorted by recency rather than by
        // count, because that is the question it answers.
        Folder(
            id = "lastday",
            name = "Last day",
            rule = "opened in the last 24h",
            tier = Tier.WARM,
            byRecency = true,
            enter = {
                it.lastUsedMs > 0 && cfg.nowMs - it.lastUsedMs <= 24L * 60 * 60 * 1000
            },
        ),

        Folder(
            id = "work",
            name = "Work",
            rule = "signed with my key, or starts with " + cfg.workPrefixes.joinToString(", "),
            tier = Tier.COLD,
            enter = { f ->
                f.signedLikeMe || cfg.workPrefixes.any { f.packageName.startsWith(it) }
            },
        ),

        Folder(
            id = "new",
            name = "New",
            rule = "installed < 7d ago",
            tier = Tier.COLD,
            enter = { it.installedDaysAgo in 0..6 },
        ),

        // Stays empty until the log is actually long enough to mean it. An
        // answer you cannot back up is worse than no answer, especially here.
        Folder(
            id = "dead",
            name = "Dead",
            rule = if (cfg.logSpanDays >= 14) {
                "0 launches in 14d · installed > 30d ago"
            } else {
                "needs 14d of log — have ${cfg.logSpanDays}d"
            },
            tier = Tier.WARM,
            enter = {
                it.logSpanDays >= 14 && it.rank.total14 == 0 && it.installedDaysAgo > 30
            },
        ),
    )
}
