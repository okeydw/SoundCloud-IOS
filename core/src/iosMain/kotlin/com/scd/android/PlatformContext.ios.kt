package com.scd.android

import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

actual abstract class PlatformContext private constructor() {
    companion object INSTANCE : PlatformContext()
}

actual fun hasNetwork(context: PlatformContext): Boolean = true

actual fun openInBrowser(context: PlatformContext, url: String) {
    val u = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any>(), null)
}

actual fun showToast(context: PlatformContext, text: String) {
    Toasts.show(text)
}

actual fun copyText(context: PlatformContext, text: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun shareText(context: PlatformContext, text: String, title: String?) {
    Toasts.share(text)
}

actual fun appVersionName(context: PlatformContext): String? =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String

actual fun appEdition(context: PlatformContext): String = "ios"

object Toasts {
    var show: (String) -> Unit = {}
    var share: (String) -> Unit = {}
}
