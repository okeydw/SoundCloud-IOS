package com.scd.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

actual typealias PlatformContext = Context

actual fun showToast(context: PlatformContext, text: String) {
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
}

actual fun copyText(context: PlatformContext, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    runCatching { cm.setPrimaryClip(ClipData.newPlainText("text", text)) }
}

actual fun shareText(context: PlatformContext, text: String, title: String?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

actual fun appVersionName(context: PlatformContext): String? =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

actual fun appEdition(context: PlatformContext): String = if (context.packageName.endsWith(".full")) "full" else "lite"

fun androidStrings(context: Context): (String) -> String {
    val app = context.applicationContext
    return { name ->
        val id = app.resources.getIdentifier(name, "string", app.packageName)
        if (id == 0) name else app.getString(id)
    }
}
