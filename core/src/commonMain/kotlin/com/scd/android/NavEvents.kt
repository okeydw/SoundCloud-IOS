package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PlaylistEvents {
    var version by mutableStateOf(0)
    fun bump() { version++ }
}

object NavEvents {
    var playlistUrn by mutableStateOf<String?>(null)
    var playlistTitle by mutableStateOf<String?>(null)
    var pendingUrl by mutableStateOf<String?>(null)

    fun openPlaylist(urn: String, title: String) {
        playlistTitle = title
        playlistUrn = urn
    }

    fun consume() {
        playlistUrn = null
        playlistTitle = null
    }

    var route by mutableStateOf<String?>(null)
        private set
    var userUrn by mutableStateOf<String?>(null)
        private set

    const val STATS = "stats"
    const val CATALOG = "catalog:"
    const val ALBUM = "album:"

    fun openCatalog(id: String) {
        route = CATALOG + id
    }

    fun openAlbum(id: String) {
        route = ALBUM + id
    }

    fun openStats() {
        route = STATS
    }

    fun openUser(urn: String) {
        userUrn = urn
    }

    fun openScUser(scId: String) {
        userUrn = "soundcloud:users:$scId"
    }

    fun consumeRoute() {
        route = null
    }

    fun consumeUser() {
        userUrn = null
    }

    fun openUrl(url: String) {
        pendingUrl = url
    }

    fun consumeUrl() {
        pendingUrl = null
    }
}
