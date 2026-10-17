package com.scd.android

data class EqInfo(
    val bands: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val centerFreqsHz: List<Int>,
    val presets: List<String>,
)

data class OutputDevice(val key: String, val kind: String, val name: String)

enum class UpdateState { Idle, Downloading, Ready, Failed, ForeignKey, NeedsPermission }

enum class ImportStage { Reading, Matching, Saving, Done, Failed }

data class ImportState(
    val source: String,
    val title: String = "",
    val stage: ImportStage = ImportStage.Reading,
    val total: Int = 0,
    val done: Int = 0,
    val found: Int = 0,
    val missed: List<String> = emptyList(),
    val error: String? = null,
    val playlists: Int = 0,
    val partial: Boolean = false,
)

expect object Downloads {
    val UPGRADE_KEY: String
    var downloaded: Set<String>
    var inProgress: Set<String>
    var queued: Set<String>
    var activeLists: Set<String>
    fun isDownloaded(urn: String): Boolean
    fun tracks(): List<Track>
    fun cachedQuality(urn: String): String?
    suspend fun quality(urn: String): String?
    fun enqueue(
        context: PlatformContext,
        tracks: List<Track>,
        playlistUrn: String? = null,
        playlistTitle: String? = null,
        listKey: String? = playlistUrn,
    )
    fun cancelList(context: PlatformContext, key: String)
    suspend fun remove(urn: String)
    suspend fun removeAll(urns: Collection<String>)
    fun artPath(urn: String): String?
    fun localPath(urn: String): String?
    fun upgradeAll(context: PlatformContext, onResult: (Int) -> Unit)
    suspend fun backfillArt()
}

expect object StreamInfo {
    fun wasHq(urn: String?): Boolean
}

expect object BlurSupport {
    val available: Boolean
}

expect object AudioFx {
    var info: EqInfo?
    fun levels(preset: Int): List<Int>
    fun parseBands(csv: String): List<Int>
}

expect object AudioOutput {
    val AUTO: String
    val SPEAKER: String
    val WIRED: String
    val BLUETOOTH: String
    val USB: String
    val HDMI: String
    fun outputs(context: PlatformContext): List<OutputDevice>
}

expect object Updater {
    var asset: ApkAsset?
    var state: UpdateState
    var progress: Float
    fun pick(context: PlatformContext, assets: List<ApkAsset>): ApkAsset?
    fun start(context: PlatformContext)
    fun cancel()
    fun install(context: PlatformContext)
}

expect object Importer {
    val YOUTUBE: String
    val SPOTIFY: String
    val YANDEX: String
    var state: ImportState?
    val busy: Boolean
    fun detect(link: String): String?
    fun cancel()
    fun clear()
    fun youtube(context: PlatformContext, link: String, asPlaylist: Boolean, similar: Boolean = false, download: Boolean = true)
    fun spotify(context: PlatformContext, link: String, asPlaylist: Boolean, download: Boolean)
    fun yandex(context: PlatformContext, link: String?, token: String?, asPlaylist: Boolean, download: Boolean)
}

expect suspend fun platformArtHash(track: Track): Long?
