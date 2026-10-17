package com.scd.android

object LocalRadioBackup : WaveBackup {
    override suspend fun batch(limit: Int): List<Track> = LocalRadio.batch(limit)
    override fun reset() = LocalRadio.resetWave()
    override fun likeSeed(): String? = Likes.urns.firstOrNull()
}
