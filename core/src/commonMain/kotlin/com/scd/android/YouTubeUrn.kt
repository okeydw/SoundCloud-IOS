package com.scd.android

object YouTubeUrn {
    const val PREFIX = "youtube:tracks:"
    const val SOURCE = "youtube"
    const val SINGLES = "youtube-singles"

    fun matches(urn: String?): Boolean = urn?.startsWith(PREFIX) == true
}
