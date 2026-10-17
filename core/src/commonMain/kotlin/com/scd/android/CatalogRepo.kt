package com.scd.android

object CatalogRepo {
    const val PAGE = 30

    suspend fun tracksPage(id: String, sort: String, page: Int, role: String = "any"): Pair<List<Track>, Boolean> {
        val res = Api.catalogArtistTracks(id, role, sort, page, PAGE)
        val real = res.collection.filter { !it.urn.startsWith("wanted:") }
        return real to (real.size >= PAGE)
    }

    fun scUserUrn(id: String) = "soundcloud:users:$id"

    fun findCatalogFor(userUrn: String, tracks: List<Track>): EnrichmentArtist? {
        val id = scIdOf(userUrn) ?: return null
        return tracks.asSequence().mapNotNull { it.catalogArtist }.firstOrNull { scIdOf(it.sc_user_id) == id }
    }

    fun formatListen(ms: Long): Pair<Long, Long> {
        val minutes = ms / 60_000
        return (minutes / 60) to (minutes % 60)
    }
}
