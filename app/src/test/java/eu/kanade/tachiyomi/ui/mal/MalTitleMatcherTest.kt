package eu.kanade.tachiyomi.ui.mal

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The title matching decides which MyAnimeList entry belongs to which local anime. Seasons of
 * the same show have very similar titles, so they must not be mixed up.
 */
class MalTitleMatcherTest {

    @Test
    fun `the same title matches`() {
        assertTrue(MalTitleMatcher.score("One Piece", "One Piece") >= 100)
    }

    @Test
    fun `a season is not the first season`() {
        val score = MalTitleMatcher.score("Oshi no Ko", "[Oshi no Ko] 4th Season")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `the same season matches`() {
        val score = MalTitleMatcher.score("Oshi no Ko 4th Season", "[Oshi no Ko] 4th Season")
        assertTrue(score >= 100) { "score was $score" }
    }

    @Test
    fun `a different season number does not match`() {
        val score = MalTitleMatcher.score("One Piece 3rd Season", "One Piece Season 4")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `a different season number does not match the other way round`() {
        val score = MalTitleMatcher.score("One Piece Season 6", "One Piece 3rd Season")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `the same season number matches`() {
        val score = MalTitleMatcher.score("One Piece Season 3", "One Piece 3rd Season")
        assertTrue(score >= 100) { "score was $score" }
    }

    @Test
    fun `a different part does not match`() {
        val score = MalTitleMatcher.score("Re:Zero Part 2", "Re:Zero Part 3")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `a different volume does not match`() {
        val score = MalTitleMatcher.score("Berserk Volume 5", "Berserk Volume 3")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `the right season wins over the wrong ones`() {
        val local = listOf(
            1L to "One Piece Season 4",
            2L to "One Piece Season 6",
            3L to "One Piece Season 3",
        )

        val match = MalTitleMatcher.bestMatch("One Piece 3rd Season", local)
        assertTrue(match != null)
        assertTrue(match!!.item == 3L) { "matched ${match.item} ('${match.title}')" }
    }

    @Test
    fun `a different season does not win over the right one`() {
        val local = listOf(
            1L to "Oshi no Ko",
            2L to "Oshi no Ko 2nd Season",
            3L to "Oshi no Ko 3rd Season",
        )

        val match = MalTitleMatcher.bestMatch("[Oshi no Ko] 3rd Season", local)
        assertTrue(match != null)
        assertTrue(match!!.item == 3L) { "matched ${match.item} ('${match.title}')" }
    }

    @Test
    fun `a different title spelling still matches`() {
        val score = MalTitleMatcher.score("Sousou no Frieren", "Frieren: Beyond Journey's End")
        assertTrue(score >= MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `an unrelated title does not match`() {
        val score = MalTitleMatcher.score("One Piece", "Bleach")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `the english title of a romaji one does not match`() {
        // The extensions show "Shingeki no Kyojin", MyAnimeList "Attack on Titan" - the title
        // comparison alone cannot bridge that, the AniList title is needed.
        val score = MalTitleMatcher.score("Shingeki no Kyojin", "Attack on Titan")
        assertTrue(score < MalTitleMatcher.MIN_SCORE) { "score was $score" }
    }

    @Test
    fun `the romaji title of an english one matches`() {
        val score = MalTitleMatcher.score("Shingeki no Kyojin", "Shingeki no Kyojin")
        assertTrue(score >= 100) { "score was $score" }
    }

    @Test
    fun `the words of a title are found`() {
        val tokens = MalTitleMatcher.tokens("Frieren: Beyond Journey's End")
        assertTrue(tokens.contains("frieren")) { "tokens were $tokens" }
        assertTrue(tokens.contains("beyond")) { "tokens were $tokens" }
    }
}
