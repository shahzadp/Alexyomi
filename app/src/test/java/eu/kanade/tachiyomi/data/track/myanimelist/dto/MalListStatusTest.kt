package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * MyAnimeList only returns the fields that were asked for, so the parsers have to survive a
 * partial response. The list status is read with a narrow field selection, which is where the
 * dates are missing.
 */
class MalListStatusTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `anime list status without the dates is parsed`() {
        val response = """
            {
              "my_list_status": {
                "status": "watching",
                "score": 8,
                "num_episodes_watched": 12,
                "is_rewatching": false
              }
            }
        """.trimIndent()

        val status = json.decodeFromString<MALAnimeListStatusResponse>(response).myListStatus!!

        assertEquals("watching", status.status)
        assertEquals(12, status.numEpisodesWatched.toInt())
        assertEquals(8, status.score)
    }

    @Test
    fun `manga list status without the dates is parsed`() {
        val response = """
            {
              "my_list_status": {
                "status": "reading",
                "score": 7,
                "num_chapters_read": 34,
                "is_rereading": false
              }
            }
        """.trimIndent()

        val status = json.decodeFromString<MALMangaListStatusResponse>(response).myListStatus!!

        assertEquals("reading", status.status)
        assertEquals(34, status.numChaptersRead.toInt())
        assertEquals(7, status.score)
    }

    @Test
    fun `a full list status is parsed as well`() {
        val response = """
            {
              "my_list_status": {
                "status": "completed",
                "score": 10,
                "num_episodes_watched": 26,
                "is_rewatching": false,
                "start_date": "2024-01-05",
                "finish_date": "2024-03-01",
                "updated_at": "2024-03-02T00:00:00+00:00"
              }
            }
        """.trimIndent()

        val status = json.decodeFromString<MALAnimeListStatusResponse>(response).myListStatus!!

        assertEquals("completed", status.status)
        assertEquals("2024-01-05", status.startDate)
        assertEquals("2024-03-01", status.finishDate)
    }
}
