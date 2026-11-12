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
}
