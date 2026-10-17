package com.scd.android

actual object StreamInfo {
    actual fun wasHq(urn: String?): Boolean = ScDataSource.wasHq(urn)
}
