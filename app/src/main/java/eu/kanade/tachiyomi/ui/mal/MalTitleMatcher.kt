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
     * The score of two titles that only differ in their season.
     */
    private const val SCORE_DIFFERENT_SEASON = 15

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
        "movie", "series", "season", "part", "second", "third", "new", "final", "ova", "ona",
        "dub", "sub", "english", "japanese", "episode", "episodes", "first", "fourth", "fifth",
    )

    /**
     * "3rd season", "2nd part", "volume 5"
     */
    private val SEASON_BEFORE = Regex("(\\d+)\\s*(?:st|nd|rd|th)?\\s*(?:season|part|volume|vol)")

    /**
     * "season 3", "part 2", "vol 5"
     */
    private val SEASON_AFTER = Regex("(?:season|part|volume|vol)\\s*(\\d+)")

    /**
     * More than one space in a row.
     */
    private val WHITESPACE = Regex("\\s+")

    /**
     * Minimum score for a title to be considered the same anime.
     */
    const val MIN_SCORE = 30

    /**
     * Words that mark a season, a part or a volume. They tell two entries of the same show
     * apart, so a title that only adds one of them is a different entry.
     */
    private val SEASON_MARKERS = listOf(
        "season", "part", "volume", "vol", "2nd", "3rd", "4th", "5th", "6th", "7th",
        "second", "third", "fourth", "fifth", "sixth", "seventh",
    )

    /**
     * A candidate with the score it reached.
     */
    data class Match<T>(val item: T, val title: String, val score: Int)

    /**
     * Score of [query] against [candidate]. Zero means they don't look alike at all.
     */
    fun score(query: String, candidate: String): Int =
        scoreNormalized(normalize(query), normalize(candidate))

    /**
     * The score of two titles that are already normalized.
     *
     * The list of the user can have thousands of entries, so the titles are normalized only
     * once and compared with this.
     */
    fun scoreNormalized(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0

        // The seasons of a show are different entries, even when the titles are nearly the
        // same - "X 3rd Season" is not "X Season 4".
        val seasonA = seasonNumber(a)
        val seasonB = seasonNumber(b)
        if (seasonA != null && seasonB != null) {
            if (seasonA != seasonB) {
                return SCORE_DIFFERENT_SEASON
            }
            // The same season, spelled differently - "One Piece Season 3" and
            // "One Piece 3rd Season" are the same entry.
            val base = withoutSeason(a)
            if (base.isNotEmpty() && base == withoutSeason(b)) {
                return SCORE_EXACT
            }
        }

        if (a == b) return SCORE_EXACT
        if (b.startsWith(a)) return prefixScore(a, b)
        if (a.startsWith(b)) return prefixScore(b, a)
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
     * The number of the season of a title, when it has one.
     *
     * Understands "3rd Season", "Season 3", "Part 2" and "Volume 5".
     */
    private fun seasonNumber(title: String): Int? {
        SEASON_BEFORE.find(title)?.let { return it.groupValues[1].toIntOrNull() }
        SEASON_AFTER.find(title)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    /**
     * The title without the season it mentions.
     *
     * "One Piece 3rd Season" becomes "One Piece" - what is left has to be the same for two
     * titles of the same season.
     */
    private fun withoutSeason(title: String): String {
        val match = SEASON_BEFORE.find(title) ?: SEASON_AFTER.find(title) ?: return title
        return title.removeRange(match.range)
            .replace(WHITESPACE, " ")
            .trim()
    }

    /**
     * The words of a title, lower case and without punctuation.
     *
     * Used to find the candidates that are worth comparing - comparing every title with
     * every other one is far too slow when the list of the user is big.
     */
    fun tokens(title: String): Set<String> =
        normalize(title).split(' ').filter { it.length > 1 }.toSet()

    /**
     * The words of a title that is already normalized.
     */
    fun tokensNormalized(title: String): Set<String> =
        title.split(' ').filter { it.length > 1 }.toSet()

    /**
     * The score of a title that starts with the other one.
     *
     * "Oshi no Ko" and "Oshi no Ko 3rd Season" are different entries, so the longer one only
     * scores when it doesn't add a season marker.
     */
    private fun prefixScore(shorter: String, longer: String): Int {
        val remainder = longer.removePrefix(shorter).trim()
        if (remainder.isEmpty()) return SCORE_EXACT
        if (hasSeasonMarker(remainder)) return SCORE_DIFFERENT_SEASON
        return SCORE_PREFIX
    }

    private fun hasSeasonMarker(text: String): Boolean {
        val tokens = text.split(' ')
        return tokens.any { token -> SEASON_MARKERS.any { marker -> token.contains(marker) } }
    }

    /**
     * The best matching candidate, or null when nothing matches well enough.
     */
    fun <T> best(query: String, candidates: List<Pair<T, String>>): Pair<T, String>? =
        bestMatch(query, candidates)?.let { it.item to it.title }

    /**
     * The best matching candidate with the score it reached.
     */
    fun <T> bestMatch(query: String, candidates: List<Pair<T, String>>): Match<T>? {
        var best: Match<T>? = null
        for (candidate in candidates) {
            val score = score(query, candidate.second)
            if (best == null || score > best.score) {
                best = Match(candidate.first, candidate.second, score)
            }
        }
        return best?.takeIf { it.score >= MIN_SCORE }
    }

    private fun isDistinctive(token: String): Boolean =
        token.length >= MIN_DISTINCTIVE_TOKEN_LENGTH && token !in STOP_WORDS

    /**
     * Lower case, without punctuation and with collapsed spaces.
     */
    fun normalized(title: String): String = normalize(title)

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
