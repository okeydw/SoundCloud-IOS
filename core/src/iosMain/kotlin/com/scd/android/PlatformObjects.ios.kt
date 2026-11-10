package com.scd.android

actual object StreamInfo {
    actual fun wasHq(urn: String?): Boolean = false
}

actual object BlurSupport {
    actual val available: Boolean = true
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

actual suspend fun platformArtHash(track: Track): Long? = null
