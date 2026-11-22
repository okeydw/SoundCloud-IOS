package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class TrackEqBindingTest {
    @Test fun speedAndPitchAreBoundToTheTrackAndExplicitlyResaved() {
        Prefs.init(MemorySettings())
        val rate = Prefs.playbackRate
        val mode = Prefs.pitchMode
        val pitch = Prefs.pitchSemitones
        try {
            Prefs.changePlaybackRate(0.9f)
            Prefs.changePitchMode(SpeedPitch.MODE_AUTO)
            Prefs.changePitchSemitones(1f)
            Prefs.activateTrackEq("pitch-a")
            Prefs.changePlaybackRate(1.35f)
            Prefs.changePitchMode(SpeedPitch.MODE_MANUAL)
            Prefs.changePitchSemitones(-3.5f)
            Prefs.bindTrackEq("pitch-a", true)
            assertTrue(Prefs.trackEqSaved)
            Prefs.changePlaybackRate(1.5f)
            assertFalse(Prefs.trackEqSaved)
            Prefs.activateTrackEq("pitch-b")
            assertEquals(0.9f, Prefs.playbackRate, 0f)
            assertEquals(SpeedPitch.MODE_AUTO, Prefs.pitchMode)
            assertEquals(1f, Prefs.pitchSemitones, 0f)
            Prefs.bindTrackEq("pitch-b", true)
            Prefs.activateTrackEq("pitch-a")
            assertEquals(1.35f, Prefs.playbackRate, 0f)
            assertEquals(SpeedPitch.MODE_MANUAL, Prefs.pitchMode)
            assertEquals(-3.5f, Prefs.pitchSemitones, 0f)
            Prefs.changePitchMode(SpeedPitch.MODE_AUTO)
            assertFalse(Prefs.trackEqSaved)
            Prefs.changePitchMode(SpeedPitch.MODE_MANUAL)
            assertTrue(Prefs.trackEqSaved)
            Prefs.changePitchSemitones(5f)
            assertFalse(Prefs.trackEqSaved)
            Prefs.setRoomPlaybackRate(1.75f)
            Prefs.bindTrackEq("pitch-a", true)
            Prefs.activateTrackEq("pitch-b")
            assertEquals(1.75f, Prefs.playbackRate, 0f)
            Prefs.setRoomPlaybackRate(null)
            assertEquals(0.9f, Prefs.playbackRate, 0f)
            assertEquals(1f, Prefs.pitchSemitones, 0f)
            Prefs.activateTrackEq("pitch-a")
            assertEquals(1.35f, Prefs.playbackRate, 0f)
            assertEquals(5f, Prefs.pitchSemitones, 0f)
            Prefs.bindTrackEq("pitch-a", false)
            assertEquals(0.9f, Prefs.playbackRate, 0f)
        } finally {
            Prefs.setRoomPlaybackRate(null)
            for (urn in listOf("pitch-a", "pitch-b")) {
                Prefs.activateTrackEq(urn)
                Prefs.bindTrackEq(urn, false)
            }
            Prefs.activateTrackEq(null)
            Prefs.changePlaybackRate(rate)
            Prefs.changePitchMode(mode)
            Prefs.changePitchSemitones(pitch)
        }
    }

    @Test fun changingTracksRestoresTheirOwnCurvesAndTheGlobalSettings() {
        Prefs.init(MemorySettings())
        val original = TrackEqSetting(Prefs.eqEnabled, Prefs.eqGains, Prefs.eqPresetId, Prefs.eqPreamp)
        try {
            Prefs.activateTrackEq("a")
            EqPresets.select("rock")
            EqPresets.setEnabled(false)
            Prefs.bindTrackEq("a", true)
            assertTrue(Prefs.trackEqSaved)
            EqPresets.select("electronic")
            assertFalse(Prefs.trackEqSaved)
            Prefs.activateTrackEq("b")
            Prefs.activateTrackEq("a")
            assertEquals("rock", Prefs.eqPresetId)
            EqPresets.select("electronic")
            EqPresets.setPreamp(-3.0)
            EqPresets.setEnabled(true)
            Prefs.bindTrackEq("a", true)
            assertTrue(Prefs.trackEqSaved)
            Prefs.activateTrackEq("b")
            assertFalse(Prefs.trackEqActive)
            assertFalse(Prefs.eqEnabled)
            assertEquals("rock", Prefs.eqPresetId)
            assertNull(Prefs.eqPreamp)
            Prefs.activateTrackEq("a")
            assertTrue(Prefs.trackEqActive)
            assertTrue(Prefs.eqEnabled)
            assertEquals(EqPresets.builtinById("electronic")!!.gains, Prefs.eqGains)
            assertEquals(-3.0, Prefs.eqPreamp)
            Prefs.bindTrackEq("a", false)
            assertEquals("rock", Prefs.eqPresetId)
            Prefs.activateTrackEq("b")
            Prefs.activateTrackEq("a")
            assertFalse(Prefs.trackEqActive)
        } finally {
            Prefs.activateTrackEq(null)
            Prefs.changeEqCurve(original.gains, original.preset, original.preamp)
            Prefs.changeEqEnabled(original.enabled)
        }
    }

}
