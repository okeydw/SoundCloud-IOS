package com.scd.android

object CatalogRepo {
    const val PAGE = 30

    suspend fun tracksPage(id: String, sort: String, page: Int, role: String = "any"): Pair<List<Track>, Boolean> {
        val res = Api.catalogArtistTracks(id, role, sort, page, PAGE)
        val real = res.collection.filter { !it.urn.startsWith("wanted:") }
        return real to res.collection.isNotEmpty()
    }

    fun scUserUrn(id: String) = soundCloudUserId(id)?.let { "soundcloud:users:$it" }

    fun findCatalogFor(userUrn: String, tracks: List<Track>): EnrichmentArtist? {
        val id = soundCloudUserId(userUrn) ?: return null
        return tracks.asSequence().flatMap { track ->
            sequenceOf(track.enrichment?.primary_artist) + track.enrichment?.participants.orEmpty().asSequence().map { it.artist }
        }.filterNotNull().firstOrNull { it.id.isNotBlank() && soundCloudUserId(it.sc_user_id) == id }
    }

    fun formatListen(ms: Long): Pair<Long, Long> {
        val minutes = ms / 60_000
        return (minutes / 60) to (minutes % 60)
    }
}
