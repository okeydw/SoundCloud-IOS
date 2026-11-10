package com.scd.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberEqFilePicker(onText: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.takeIf { it.size <= 2 * 1024 * 1024 }
                ?.let(EqTransfer::decode)
        }.getOrNull()
        latest(text)
    }
    return { runCatching { launcher.launch(arrayOf("text/*", "application/octet-stream")) } }
}

@Composable
actual fun rememberEqFileSaver(onDone: (Boolean) -> Unit): (name: String, text: String) -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onDone)
    val pending = remember { java.util.concurrent.atomic.AtomicReference("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(pending.get().toByteArray()) } != null
        }.getOrDefault(false)
        latest(ok)
    }
    return { name, text ->
        pending.set(text)
        runCatching { launcher.launch(name) }.onFailure { latest(false) }
    }
}
