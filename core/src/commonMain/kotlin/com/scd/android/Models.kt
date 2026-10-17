package com.scd.android

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PagedTracks(
    val collection: List<Track> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
)

data class ApkAsset(val name: String, val url: String, val size: Long)

data class ReleaseInfo(val tag: String, val url: String, val assets: List<ApkAsset>)

@Serializable
data class Track(
    val id: Long = 0,
    val urn: String,
    val title: String = "",
    val duration: Long = 0,
    val full_duration: Long? = null,
    val artwork_url: String? = null,
    val waveform_url: String? = null,
    val genre: String? = null,
    val permalink_url: String? = null,
    val user: TrackUser? = null,
    val access: String? = null,
    val policy: String? = null,
    val monetization_model: String? = null,
    @SerialName("_scd_meta") val scdMeta: ScdMeta? = null,
    val enrichment: TrackEnrichment? = null,
    val playback_count: Long? = null,
    val likes_count: Long? = null,
    val favoritings_count: Long? = null,
    val reposts_count: Long? = null,
    val comment_count: Long? = null,
) {
    val catalogArtist: EnrichmentArtist? get() = enrichment?.primary_artist?.takeIf { it.id.isNotBlank() }

    val isPreview: Boolean get() = access == "preview"

    val goPlus: Boolean
        get() = access == "preview" ||
            policy == "SNIP" ||
            monetization_model == "SUB_HIGH_TIER"

    val starLocked: Boolean get() = access == "preview" && !Prefs.star

    val unavailable: Boolean get() = access == "blocked"
}

@Serializable
data class ScdMeta(
    val storage_state: String? = null,
    val storage_quality: String? = null,
)

@Serializable
data class TrackUser(
    val urn: String? = null,
    val username: String = "",
    val avatar_url: String? = null,
)

@Serializable
data class PagedUsers(
    val collection: List<Artist> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class Artist(
    val urn: String,
    val username: String = "",
    val avatar_url: String? = null,
    val followers_count: Int? = null,
    val track_count: Int? = null,
    val city: String? = null,
    val country_code: String? = null,
)

@Serializable
data class PagedPlaylists(
    val collection: List<Playlist> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class Playlist(
    val urn: String,
    val title: String = "",
    val artwork_url: String? = null,
    val track_count: Int = 0,
    val user: TrackUser? = null,
    val kind: String? = null,
    val sharing: String? = null,
    val last_modified: String? = null,
) {
    val isAlbum: Boolean
        get() = kind == "album" || kind == "ep" || kind == "single" || kind == "compilation"
}

data class CreatedPlaylist(val urn: String?, val queued: Boolean)

@Serializable
data class AuthStatus(
    val authenticated: Boolean = false,
    val username: String? = null,
)

@Serializable
data class LinkCreate(
    val linkRequestId: String = "",
    val claimToken: String = "",
    val expiresAt: String? = null,
)

@Serializable
data class LinkStatus(
    val status: String = "",
    val mode: String = "",
    val sessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class LinkClaim(
    @SerialName("sessionId") val sessionId: String? = null,
    val mode: String = "",
)

@Serializable
data class MeProfile(
    val username: String = "",
    val avatar_url: String? = null,
    val urn: String? = null,
    val permalink_url: String? = null,
    val followers_count: Int? = null,
    val followings_count: Int? = null,
    val track_count: Int? = null,
)

@Serializable
data class Subscription(
    val premium: Boolean = false,
)

@Serializable
data class DownloadResponse(
    val track_urn: String = "",
    val candidates: List<DownloadCandidate> = emptyList(),
)

@Serializable
data class DownloadCandidate(
    val kind: String = "",
    val quality: String? = null,
    val preset: String? = null,
    val mime: String? = null,
    val url: String? = null,
    val manifest_url: String? = null,
    val content_type: String? = null,
    val init_base64: String? = null,
    val segments: List<String> = emptyList(),
    val key_base64: String? = null,
)

@Serializable
data class ResolveResult(
    val urn: String = "",
    val kind: String? = null,
    val title: String? = null,
) {
    val type: String
        get() = when {
            urn.startsWith("soundcloud:tracks:") -> "track"
            urn.startsWith("soundcloud:playlists:") || urn.startsWith("soundcloud:system-playlists:") -> "playlist"
            urn.startsWith("soundcloud:users:") -> "user"
            kind == "system-playlist" -> "playlist"
            else -> kind.orEmpty()
        }
}

@Serializable
data class LyricsResponse(
    val syncedLrc: String? = null,
    val plainText: String? = null,
    val source: String? = null,
    val language: String? = null,
)

data class LyricLine(val timeMs: Long, val text: String)

@Serializable
data class VibeSearchResponse(
    val items: List<Track> = emptyList(),
    val status: String? = null,
)

fun parseLrc(lrc: String): List<LyricLine> {
    val re = Regex("^\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})]\\s*(.*)")
    return lrc.lines().mapNotNull { raw ->
        val m = re.find(raw.trim()) ?: return@mapNotNull null
        val (mm, ss, frac, text) = m.destructured
        if (text.isBlank()) return@mapNotNull null
        val ms = mm.toLong() * 60_000L + ss.toLong() * 1000L + frac.padEnd(3, '0').take(3).toLong()
        LyricLine(ms, text.trim())
    }
}

@Serializable
data class DislikeIds(
    val ids: List<String> = emptyList(),
)

@Serializable
data class WaveformJson(
    val width: Int = 0,
    val height: Int = 140,
    val samples: List<Int> = emptyList(),
)

@Serializable
data class LoginResponse(
    val url: String,
    val loginRequestId: String,
)

@Serializable
data class LoginStatus(
    val status: String,
    val step: String? = null,
    val sessionId: String? = null,
    val username: String? = null,
    val error: String? = null,
)

@Serializable
data class WavePayload(
    val tracks: List<WaveRec> = emptyList(),
    val cursor: String = "",
)

@Serializable
data class WaveRec(
    val id: JsonPrimitive,
    val score: Double? = null,
)

@Serializable
data class HistoryPage(
    val collection: List<HistoryEntry> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class HistoryEntry(
    val id: String = "",
    val scTrackId: String,
    val title: String = "",
    val artistName: String = "",
    val artistUrn: String? = null,
    val artworkUrl: String? = null,
    val duration: Long = 0,
    val playedAt: String = "",
) {
    fun toTrack(): Track = Track(
        urn = scTrackId,
        title = title,
        duration = duration,
        artwork_url = artworkUrl,
        user = TrackUser(urn = artistUrn, username = artistName),
    )
}
