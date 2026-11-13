package eu.kanade.tachiyomi.data.track.anilist

import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseResponse
import eu.kanade.tachiyomi.data.track.anilist.dto.ALMediaDetailsResponse
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/**
 * Anonymous read-only access to AniList's GraphQL API (https://graphql.anilist.co/).
 * Used for the MAL tab's browse sections (season, top, search) so they work
 * without a sign-in. MyAnimeList account list reads/writes go through the
 * official OAuth API in [eu.kanade.tachiyomi.data.track.myanimelist].
 *
 * AniList is free, supports anonymous queries and has generous rate limits
 * (90 req/min), unlike scraping-based MAL proxies.
 */
class AnilistBrowseApi(
    private val client: OkHttpClient,
) {

    private val json: Json by injectLazy()

    suspend fun searchAnime(query: String, page: Int = 1, includeAdult: Boolean = false): AnilistBrowsePage {
        val payload = buildPayload(
            "Search",
            """
            query Search(${'$'}search: String, ${'$'}page: Int) {
              Page(perPage: $PAGE_SIZE, page: ${'$'}page) {
                pageInfo { hasNextPage }
                media(type: ANIME, search: ${'$'}search, isAdult: ${'$'}includeAdult, sort: SEARCH_MATCH) {
                  $MEDIA_FIELDS
                }
              }
            }
            """.trimIndent(),
            buildJsonObject {
                put("search", query.take(150))
                put("page", page)
            },
        )
        return fetch(payload)
    }

    /** Browsing one specific AniList season (e.g. FALL 2026). */
    suspend fun seasonal(
        season: String,
        seasonYear: Int,
        page: Int = 1,
    ): AnilistBrowsePage {
        val payload = buildPayload(
            "Seasonal",
            """
            query Seasonal(${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}page: Int) {
              Page(perPage: $PAGE_SIZE, page: ${'$'}page) {
                pageInfo { hasNextPage }
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}seasonYear, isAdult: false, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
            """.trimIndent(),
            buildJsonObject {
                put("season", season)
                put("seasonYear", seasonYear)
                put("page", page)
            },
        )
        return fetch(payload)
    }

    suspend fun topAnime(filter: TopFilter, page: Int = 1): AnilistBrowsePage {
        val (sort, status) = when (filter) {
            TopFilter.BY_SCORE -> "SCORE_DESC" to null
            TopFilter.BY_POPULARITY -> "POPULARITY_DESC" to null
            TopFilter.AIRING -> "POPULARITY_DESC" to "RELEASING"
        }
        val payload = buildPayload(
            "Top",
            """
            query Top(${'$'}sort: [MediaSort], ${'$'}status: MediaStatus, ${'$'}page: Int) {
              Page(perPage: $PAGE_SIZE, page: ${'$'}page) {
                pageInfo { hasNextPage }
                media(type: ANIME, isAdult: false, sort: ${'$'}sort, status: ${'$'}status) {
                  $MEDIA_FIELDS
                }
              }
            }
            """.trimIndent(),
            buildJsonObject {
                putJsonArray("sort") { add(JsonPrimitive(sort)) }
                status?.let { put("status", it) }
                put("page", page)
            },
        )
        return fetch(payload)
    }

    /**
     * Full details for one anime, looked up either by AniList id (browse results)
     * or by MyAnimeList id (My List rows). Throws on API/GraphQL errors.
     */
    suspend fun animeDetails(
        anilistId: Int?,
        malId: Int?,
    ): AnilistAnimeDetails {
        // AniList rejects operations whose variables are never used, so the
        // signature declares only the one we pass.
        val (signature, argument, variables) = when {
            anilistId != null ->
                Triple("\$id: Int", "id: \$id", buildJsonObject { put("id", anilistId) })
            malId != null ->
                Triple("\$idMal: Int", "idMal: \$idMal", buildJsonObject { put("idMal", malId) })
            else -> error("animeDetails requires anilistId or malId")
        }
        val query = """
            query Details($signature) {
              Media($argument, type: ANIME) {
                id idMal
                title { romaji english native }
                coverImage { large medium }
                bannerImage
                description(asHtml: false)
                episodes duration status format averageScore popularity
                genres
                season seasonYear
                studios(isMain: true) { nodes { name isAnimationStudio } }
                nextAiringEpisode { airingAt episode }
                siteUrl
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("operationName", "Details")
            put("query", query)
            put("variables", variables)
        }.toString()

        val response = withIOContext {
            with(json) {
                client.newCall(
                    POST(
                        API_URL,
                        body = payload.toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<ALMediaDetailsResponse>()
            }
        }
        response.errors.firstOrNull()?.let { throw IllegalStateException(it.message) }
        val media = response.data?.Media ?: throw IllegalStateException("Anime not found")
        return AnilistAnimeDetails(
            anilistId = media.id,
            malId = media.idMal,
            titleRomaji = media.title.romaji.orEmpty(),
            titleEnglish = media.title.english?.takeIf { it.isNotBlank() },
            titleNative = media.title.native?.takeIf { it.isNotBlank() },
            coverUrl = media.coverImage.large ?: media.coverImage.medium.orEmpty(),
            bannerUrl = media.bannerImage,
            description = stripHtml(media.description),
            episodes = media.episodes,
            duration = media.duration,
            status = media.status,
            format = media.format,
            averageScore = media.averageScore,
            popularity = media.popularity,
            genres = media.genres,
            season = media.season,
            seasonYear = media.seasonYear,
            studios = media.studios.nodes.filter { it.isAnimationStudio }.map { it.name }
                .ifEmpty { media.studios.nodes.map { it.name } },
            nextAiringEpisode = media.nextAiringEpisode?.episode,
            airingAt = media.nextAiringEpisode?.airingAt,
            siteUrl = media.siteUrl,
        )
    }

    /**
     * Full details for one manga, looked up either by AniList id or by MAL id.
     * Mirrors [animeDetails] but queries MANGA entries and returns chapters/volumes.
     */
    suspend fun mangaDetails(
        anilistId: Int?,
        malId: Int?,
    ): AnilistAnimeDetails {
        val (signature, argument, variables) = when {
            anilistId != null ->
                Triple("\$id: Int", "id: \$id", buildJsonObject { put("id", anilistId) })
            malId != null ->
                Triple("\$idMal: Int", "idMal: \$idMal", buildJsonObject { put("idMal", malId) })
            else -> error("mangaDetails requires anilistId or malId")
        }
        val query = """
            query Details($signature) {
              Media($argument, type: MANGA) {
                id idMal
                title { romaji english native }
                coverImage { large medium }
                bannerImage
                description(asHtml: false)
                chapters volumes status format averageScore popularity
                genres
                startDate { year month day }
                siteUrl
              }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("operationName", "Details")
            put("query", query)
            put("variables", variables)
        }.toString()

        val response = withIOContext {
            with(json) {
                client.newCall(
                    POST(
                        API_URL,
                        body = payload.toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<ALMediaDetailsResponse>()
            }
        }
        response.errors.firstOrNull()?.let { throw IllegalStateException(it.message) }
        val media = response.data?.Media ?: throw IllegalStateException("Manga not found")
        val startYear = media.startDate?.year
        return AnilistAnimeDetails(
            anilistId = media.id,
            malId = media.idMal,
            titleRomaji = media.title.romaji.orEmpty(),
            titleEnglish = media.title.english?.takeIf { it.isNotBlank() },
            titleNative = media.title.native?.takeIf { it.isNotBlank() },
            coverUrl = media.coverImage.large ?: media.coverImage.medium.orEmpty(),
            bannerUrl = media.bannerImage,
            description = stripHtml(media.description),
            episodes = media.chapters,
            duration = media.volumes,
            status = media.status,
            format = media.format,
            averageScore = media.averageScore,
            popularity = media.popularity,
            genres = media.genres,
            season = null,
            seasonYear = startYear,
            studios = emptyList(),
            siteUrl = media.siteUrl,
            malType = "manga",
            nextAiringEpisode = null,
            airingAt = null,
        )
    }

    /** Batch-fetches chapter counts and status for manga by MAL id. */
    suspend fun mangaChaptersByMalId(malIds: List<Int>): Map<Int, AnilistMediaStatus> {
        if (malIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, AnilistMediaStatus>()
        malIds.distinct().chunked(50).forEach { chunk ->
            val payload = buildPayload(
                "MangaListStatus",
                """
                query MangaListStatus(${'$'}ids: [Int]) {
                  Page(perPage: 50) {
                    media(idMal_in: ${'$'}ids, type: MANGA, isAdult: false) {
                      id idMal chapters status format season seasonYear
                    }
                  }
                }
                """.trimIndent(),
                buildJsonObject {
                    putJsonArray("ids") { chunk.forEach { add(JsonPrimitive(it)) } }
                },
            )
            val response = withIOContext {
                with(json) {
                    client.newCall(
                        POST(
                            API_URL,
                            body = payload.toRequestBody(jsonMime),
                        ),
                    )
                        .awaitSuccess()
                        .parseAs<ALBrowseResponse>()
                }
            }
            response.data?.Page?.media.orEmpty().forEach { media ->
                val id = media.idMal ?: return@forEach
                result[id] = AnilistMediaStatus(
                    episodes = media.chapters,
                    airingStatus = when (media.status?.uppercase()) {
                        "FINISHED" -> "finished"
                        "NOT_YET_RELEASED" -> "not_yet_published"
                        else -> "currently_publishing"
                    },
                    format = media.format,
                    season = media.season?.lowercase()?.replaceFirstChar { it.uppercase() },
                    seasonYear = media.seasonYear,
                )
            }
        }
        return result
    }

    /**
     * Batch-fetches episode count and airing status (and format/season) by
     * MAL id — used to fill in what MyAnimeList's list endpoint can't be
     * relied on to return. Safe to call with any missing-value ids.
     */
    suspend fun listStatusByMalId(malIds: List<Int>): Map<Int, AnilistMediaStatus> {
        if (malIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, AnilistMediaStatus>()
        malIds.distinct().chunked(50).forEach { chunk ->
            val payload = buildPayload(
                "ListStatus",
                """
                query ListStatus(${'$'}ids: [Int]) {
                  Page(perPage: 50) {
                    media(idMal_in: ${'$'}ids, type: ANIME, isAdult: false) {
                      id idMal episodes status format season seasonYear
                      title { romaji english }
                    }
                  }
                }
                """.trimIndent(),
                buildJsonObject {
                    putJsonArray("ids") { chunk.forEach { add(JsonPrimitive(it)) } }
                },
            )
            val response = withIOContext {
                with(json) {
                    client.newCall(
                        POST(
                            API_URL,
                            body = payload.toRequestBody(jsonMime),
                        ),
                    )
                        .awaitSuccess()
                        .parseAs<ALBrowseResponse>()
                }
            }
            response.data?.Page?.media.orEmpty().forEach { media ->
                val id = media.idMal ?: return@forEach
                result[id] = AnilistMediaStatus(
                    episodes = media.episodes?.takeIf { it > 0 },
                    airingStatus = when (media.status?.uppercase()) {
                        "FINISHED" -> "finished_airing"
                        "NOT_YET_RELEASED" -> "not_yet_aired"
                        else -> "currently_airing"
                    },
                    format = media.format,
                    season = media.season?.lowercase()?.replaceFirstChar { it.uppercase() },
                    seasonYear = media.seasonYear,
                    titleRomaji = media.title?.romaji.orEmpty(),
                    titleEnglish = media.title?.english.orEmpty(),
                )
            }
        }
        return result
    }

    private suspend fun fetch(payload: String): AnilistBrowsePage {
        val response = withIOContext {
            with(json) {
                client.newCall(
                    POST(
                        API_URL,
                        body = payload.toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<ALBrowseResponse>()
            }
        }
        val page = response.data?.Page
        return AnilistBrowsePage(
            items = page?.media.orEmpty().map { it.toBrowseItem() },
            hasNextPage = page?.pageInfo?.hasNextPage == true,
        )
    }

    private fun eu.kanade.tachiyomi.data.track.anilist.dto.ALMedia.toBrowseItem(): AnilistBrowseItem {
        return AnilistBrowseItem(
            anilistId = id,
            malId = idMal,
            title = title.english?.takeIf { it.isNotBlank() } ?: title.romaji.orEmpty(),
            // The extensions usually show the romaji title, so it is kept for the lookup
            titleRomaji = title.romaji.orEmpty(),
            coverUrl = coverImage.large ?: coverImage.medium.orEmpty(),
            score = averageScore,
            episodes = episodes,
            format = format,
        )
    }

    private fun buildPayload(
        operationName: String,
        query: String,
        variables: kotlinx.serialization.json.JsonObject,
    ): String {
        return buildJsonObject {
            put("operationName", operationName)
            put("query", query)
            put("variables", variables)
        }.toString()
    }

    enum class TopFilter { BY_SCORE, BY_POPULARITY, AIRING }

    companion object {
        private const val API_URL = "https://graphql.anilist.co/"
        private const val PAGE_SIZE = 25
        private const val MEDIA_FIELDS =
            "id idMal title { romaji english } coverImage { large medium } averageScore episodes format status"
    }
}

data class AnilistBrowsePage(
    val items: List<AnilistBrowseItem>,
    val hasNextPage: Boolean,
)

data class AnilistBrowseItem(
    val anilistId: Int,
    val malId: Int?,
    val title: String,
    /** The romaji title, the extensions usually show that one. */
    val titleRomaji: String = "",
    val coverUrl: String,
    /** AniList score, 0–100. Null when unknown. */
    val score: Int?,
    val episodes: Int?,
    val format: String?,
) {
    val scoreText: String?
        get() = score?.let { "%.1f".format(it / 10.0) }

    /** Prefer the MAL page; fall back to the AniList page. */
    val webUrl: String
        get() = malId?.let { "https://myanimelist.net/anime/$it" }
            ?: "https://anilist.co/anime/$anilistId"
}

data class AnilistMediaStatus(
    val episodes: Int?,
    val airingStatus: String,
    val format: String?,
    val season: String?,
    val seasonYear: Int?,
    /** The romaji title, the extensions usually show that one. */
    val titleRomaji: String = "",
    val titleEnglish: String = "",
)

data class AnilistAnimeDetails(
    val anilistId: Int,
    val malId: Int?,
    val titleRomaji: String,
    val titleEnglish: String?,
    val titleNative: String?,
    val coverUrl: String,
    val bannerUrl: String?,
    val description: String,
    val episodes: Int?,
    val duration: Int?,
    val status: String?,
    val format: String?,
    val averageScore: Int?,
    val popularity: Int?,
    val genres: List<String>,
    val season: String?,
    val seasonYear: Int?,
    val studios: List<String>,
    val nextAiringEpisode: Int?,
    val airingAt: Long?,
    val siteUrl: String?,
    val malType: String = "anime",
) {
    val displayTitle: String
        get() = titleEnglish ?: titleRomaji

    val webUrl: String
        get() = malId?.let { "https://myanimelist.net/$malType/$it" }
            ?: siteUrl
            ?: if (malType == "manga") "https://anilist.co/manga/$anilistId" else "https://anilist.co/anime/$anilistId"
}

private fun stripHtml(html: String?): String {
    return html
        ?.replace(Regex("<br\\s*/?>"), "\n")
        ?.replace(Regex("<[^>]+>"), "")
        ?.replace("&quot;", "\"")
        ?.replace("&#039;", "'")
        ?.replace("&amp;", "&")
        ?.replace("&lt;", "<")
        ?.replace("&gt;", ">")
        ?.replace("&nbsp;", " ")
        ?.trim()
        .orEmpty()
}
