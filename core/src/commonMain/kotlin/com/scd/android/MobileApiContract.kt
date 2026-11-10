package com.scd.android
import kotlinx.serialization.json.*
internal object MobileApiContract {
    private val json = Json { ignoreUnknownKeys = true }

    fun objectOf(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    fun errorCode(body: String): String? = objectOf(body)?.text("code")
    fun sessionAuthenticated(body: String): Boolean? =
        (objectOf(body)?.get("authenticated") as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    fun reconnectRequired(body: String): Boolean {
        val root = objectOf(body) ?: return false
        return root.text("code") in setOf("soundcloud_reauthorization_required", "soundcloud_not_connected") ||
            root.text("state") in setOf("reauthorization_required", "not_connected") ||
            (root["sync"] as? JsonObject)?.text("errorCode") == "auth_required"
    }

    fun retryHost(status: Int, body: String): Boolean {
        val code = errorCode(body)
        return status >= 500 && code?.endsWith("_refresh_pending") != true && code?.startsWith("soundcloud_") != true
    }

    fun collectionFailure(body: String): ApiHttpException? {
        if (!pendingCollection(body)) return null
        val sync = objectOf(body)?.get("sync") as? JsonObject
        val error = sync?.text("errorCode")
        val code = when (error) {
            "auth_required" -> "soundcloud_reauthorization_required"
            "not_found", "forbidden" -> "collection_$error"
            else -> "collection_refresh_pending"
        }
        val collection = objectOf(body)?.get("collection") as? JsonArray
        if (code == "collection_refresh_pending" && !collection.isNullOrEmpty()) return null
        val retryAfter = (sync?.get("retryAfterSeconds") as? JsonPrimitive)?.longOrNull
        return ApiHttpException(503, """{"code":"$code","message":"The collection is not ready"}""", retryAfter)
    }

    fun pendingCollection(body: String): Boolean {
        val root = objectOf(body) ?: return false
        if (root["collection"] !is JsonArray) return false
        val sync = root["sync"] as? JsonObject ?: return false
        return !json.decodeFromJsonElement(CollectionSync.serializer(), sync).snapshotReady
    }

}
