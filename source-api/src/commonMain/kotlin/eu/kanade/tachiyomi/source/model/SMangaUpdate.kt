package eu.kanade.tachiyomi.source.model

/**
 * Result of a combined manga details + chapter list refresh.
 *
 * @since tachiyomix 1.6
 */
class SMangaUpdate(
    val manga: SManga,
    val chapters: List<SChapter>,
)
