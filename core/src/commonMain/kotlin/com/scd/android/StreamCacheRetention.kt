package com.scd.android

internal data class StreamCacheFile(val path: String, val modified: Double, val bytes: Long)

internal fun pruneStreamCache(
    files: List<StreamCacheFile>,
    limitBytes: Long,
    expiresBefore: Double?,
    clear: Boolean,
    protected: (String) -> Boolean,
    remove: (String) -> Boolean,
) {
    var total = files.sumOf { it.bytes }
    for (file in files.sortedBy { it.modified }) {
        val expired = expiresBefore != null && file.modified > 0 && file.modified < expiresBefore
        if (!clear && !expired && (limitBytes <= 0L || total <= limitBytes)) continue
        if (!protected(file.path) && remove(file.path)) total -= file.bytes
    }
}
