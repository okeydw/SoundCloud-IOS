package com.scd.android

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackTitleStartupTest {
    @Test
    fun widgetCanFormatAnEmptyQueueAtLaunch() {
        assertEquals("", formattedTrackTitle("", ""))
    }

    @Test
    fun artistHandlesAcceptLettersAndAllUnicodeNumberCategories() {
        for (artist in listOf("Я", "٢guest", "Ⅳ", "²artist", "D’Angelo", "DJ_9")) {
            assertEquals("Song", withoutArtistCredits("Song (feat. $artist)", emptyList()), artist)
        }
        assertEquals("Song (feat. !artist)", withoutArtistCredits("Song (feat. !artist)", emptyList()))
    }
}
