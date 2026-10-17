package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PendingLink {
    var token by mutableStateOf<String?>(null)
    var pull by mutableStateOf(false)
    var fromScanner by mutableStateOf(false)

    fun set(token: String, pull: Boolean, fromScanner: Boolean = false) {
        this.pull = pull
        this.fromScanner = fromScanner
        this.token = token
    }

    fun clear() {
        token = null
        pull = false
        fromScanner = false
    }
}

object StreamStatus {

    var protectedUrn by mutableStateOf<String?>(null)
        private set

    fun begin(urn: String) {
        protectedUrn = urn
    }

    fun end(urn: String) {
        if (protectedUrn == urn) protectedUrn = null
    }

    fun isProtected(urn: String?): Boolean = urn != null && protectedUrn == urn
}
