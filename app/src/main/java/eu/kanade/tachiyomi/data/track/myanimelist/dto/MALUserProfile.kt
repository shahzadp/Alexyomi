package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full user info from `GET /v2/users/@me?fields=...`.
 *
 * The official MAL OpenAPI schema marks every field here as optional except
 * `id` and `name`; gender/birthday/location are only returned when the user
 * set them on their profile. `picture` is a plain URI string.
 */
@Serializable
data class MALUserProfile(
    val id: Int = 0,
    val name: String = "",
    val picture: String = "",
    val gender: String? = null,
    val birthday: String? = null,
    val location: String? = null,
    @SerialName("joined_at")
    val joinedAt: String? = null,
    @SerialName("is_supporter")
    val isSupporter: Boolean? = null,
)
