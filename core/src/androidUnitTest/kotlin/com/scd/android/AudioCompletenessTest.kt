package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class AudioCompletenessTest {
    @Test fun rejectsPreviewsEvenWhenTheFileIsLarge() {
        assertNotNull(AudioCompleteness.rejection(10_000, 4_000_000, 121_000))
        assertNotNull(AudioCompleteness.rejection(30_000, 4_000_000, 154_000))
    }
    @Test fun acceptsCompleteAudioWithSmallTimingDifferences() {
        assertNull(AudioCompleteness.rejection(119_000, 4_000_000, 121_000))
        assertNull(AudioCompleteness.rejection(15_000, 80_000, 15_000))
    }
    @Test fun unknownDurationStillRejectsTinyFiles() {
        assertNotNull(AudioCompleteness.rejection(null, 16_000, 121_000))
        assertNull(AudioCompleteness.rejection(null, 4_000_000, 121_000))
    }
}
