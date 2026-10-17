package com.scd.android

expect abstract class PlatformContext

expect fun hasNetwork(context: PlatformContext): Boolean

expect fun openInBrowser(context: PlatformContext, url: String)

expect fun showToast(context: PlatformContext, text: String)

expect fun copyText(context: PlatformContext, text: String)

expect fun shareText(context: PlatformContext, text: String, title: String? = null)

expect fun appVersionName(context: PlatformContext): String?

expect fun appEdition(context: PlatformContext): String

object Strings {
    var lookup: (String) -> String = { it }

    fun get(name: String): String = runCatching { lookup(name) }.getOrDefault(name)
}

fun interface AccountWiper {
    fun wipe(context: PlatformContext, player: QueuePlayer?)
}

object PlatformHooks {
    var wiper: AccountWiper = AccountWiper { _, _ -> }
    var forgetResolvedStreams: () -> Unit = {}
    var afterLikesSynced: (PlatformContext) -> Unit = {}
    var backfillArt: suspend () -> Unit = {}
    var offlineReady: (String) -> Boolean = { Downloads.isDownloaded(it) }
    var meteredBlocked: () -> Boolean = { false }
}
