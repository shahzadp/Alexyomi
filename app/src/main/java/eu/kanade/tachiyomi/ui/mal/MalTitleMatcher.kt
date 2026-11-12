package eu.kanade.tachiyomi.ui.mal

/**
 * Matches the title of a local anime against the titles of MyAnimeList entries.
 *
 * MyAnimeList and the extensions spell the same anime in different ways
 * ("Sousou no Frieren" / "Frieren: Beyond Journey's End"), so an exact comparison is not
 * enough. The score is a rough ranking: the higher the better, zero means no match.
 */
object MalTitleMatcher {

    private const val SCORE_EXACT = 100
    private const val SCORE_PREFIX = 80
    private const val SCORE_SUFFIX = 70
    private const val SCORE_CONTAINS = 60
    private const val SCORE_TOKEN_BASE = 50

    /**
     * A shared word that is specific enough to identify an anime on its own.
     */
    private const val DISTINCTIVE_TOKEN_BONUS = 25
    private const val MIN_DISTINCTIVE_TOKEN_LENGTH = 4

    /**
     * Words that are too common to identify an anime.
     */
    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "of", "a", "an", "to", "in", "on", "at", "no", "tv",
        "movie", "series", "season", "part", "second", "third", "new", "final", "OVA", "ONA",
        "dub", "sub", "english", "japanese", "episode", "episodes",
    )

    /**
     * Minimum score for a title to be considered the same anime.
     */
    const val MIN_SCORE = 30

    /**
     * Score of [query] against [candidate]. Zero means they don't look alike at all.
     */
    fun score(query: String, candidate: String): Int {
        val a = normalize(query)
        val b = normalize(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return SCORE_EXACT
        if (b.startsWith(a)) return SCORE_PREFIX
        if (a.startsWith(b)) return SCORE_SUFFIX
        if (b.contains(a)) return SCORE_CONTAINS
        if (a.contains(b)) return SCORE_CONTAINS - 5

        // Token overlap, so "Frieren Beyond Journey End" still matches
        // "Beyond Journey's End: Frieren"
        val tokensA = a.split(' ').filter { it.length > 1 }.toSet()
        val tokensB = b.split(' ').filter { it.length > 1 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0
        val shared = tokensA.intersect(tokensB)
        if (shared.isEmpty()) return 0

        var score = SCORE_TOKEN_BASE * shared.size / maxOf(tokensA.size, tokensB.size)

        // A shared word that is specific enough identifies the anime on its own, even when
        // the rest of the titles have nothing in common ("sousou no frieren" /
        // "frieren beyond journey's end").
        if (shared.any { isDistinctive(it) }) {
            score += DISTINCTIVE_TOKEN_BONUS
        }

        return score
    }

    /**
     * The best matching candidate, or null when nothing matches well enough.
     */
    fun best(query: String, candidates: List<Pair<Int, String>>): Pair<Int, String>? {
        var best: Pair<Int, String>? = null
        var bestScore = 0
        for (candidate in candidates) {
            val score = score(query, candidate.second)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best?.takeIf { bestScore >= MIN_SCORE }
    }

    private fun isDistinctive(token: String): Boolean =
        token.length >= MIN_DISTINCTIVE_TOKEN_LENGTH && token !in STOP_WORDS

    /**
     * Lower case, without punctuation and with collapsed spaces.
     */
    private fun normalize(title: String): String {
        val sb = StringBuilder(title.length)
        var lastWasSpace = true
        for (char in title.lowercase()) {
            if (char.isLetterOrDigit()) {
                sb.append(char)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        }
        return sb.toString().trim()
    }
}
