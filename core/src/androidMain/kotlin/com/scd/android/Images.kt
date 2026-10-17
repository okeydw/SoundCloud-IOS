package com.scd.android

object Images {
    @Volatile
    var disk: coil.disk.DiskCache? = null

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clear() {
        runCatching { disk?.clear() }
    }
}
