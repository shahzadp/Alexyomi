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
        val shared = tokensA.intersect(tokensB).size
        if (shared == 0) return 0

        return SCORE_TOKEN_BASE * shared / maxOf(tokensA.size, tokensB.size)
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
        // A single shared word out of many isn't a match
        return best?.takeIf { bestScore >= 30 }
    }

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
