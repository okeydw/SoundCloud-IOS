package com.scd.android

actual object StreamInfo {
    actual fun wasHq(urn: String?): Boolean = false
}

actual object BlurSupport {
    actual val available: Boolean = true
}

actual object AudioFx {
    actual var info: EqInfo? = null
    actual fun levels(preset: Int): List<Int> = emptyList()
    actual fun parseBands(csv: String): List<Int> = csv.split(',').mapNotNull { it.trim().toIntOrNull() }
}

actual object AudioOutput {
    actual val AUTO: String = "auto"
    actual val SPEAKER: String = "speaker"
    actual val WIRED: String = "wired"
    actual val BLUETOOTH: String = "bt"
    actual val USB: String = "usb"
    actual val HDMI: String = "hdmi"
    actual fun outputs(context: PlatformContext): List<OutputDevice> = emptyList()
}

actual object Updater {
    actual var asset: ApkAsset? = null
    actual var state: UpdateState = UpdateState.Idle
    actual var progress: Float = 0f
    actual fun pick(context: PlatformContext, assets: List<ApkAsset>): ApkAsset? = null
    actual fun start(context: PlatformContext) {}
    actual fun cancel() {}
    actual fun install(context: PlatformContext) {}
}

actual object Importer {
    actual val YOUTUBE: String = "youtube"
    actual val SPOTIFY: String = "spotify"
    actual val YANDEX: String = "yandex"
    actual var state: ImportState? = null
    actual val busy: Boolean = false
    actual fun detect(link: String): String? = null
    actual fun cancel() {}
    actual fun clear() {}
    actual fun youtube(context: PlatformContext, link: String, asPlaylist: Boolean, similar: Boolean, download: Boolean) {}
    actual fun spotify(context: PlatformContext, link: String, asPlaylist: Boolean, download: Boolean) {}
    actual fun yandex(context: PlatformContext, link: String?, token: String?, asPlaylist: Boolean, download: Boolean) {}
}

actual suspend fun platformArtHash(track: Track): Long? = null
