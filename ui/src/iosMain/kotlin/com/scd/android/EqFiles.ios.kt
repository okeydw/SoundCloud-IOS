package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberEqFilePicker(onText: (String?) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onText)
    return {
        IosFiles.pick { bytes, _ ->
            latest(if (bytes.size <= 2 * 1024 * 1024) runCatching { EqTransfer.decode(bytes) }.getOrNull() else null)
        }
    }
}

@Composable
actual fun rememberEqFileSaver(onDone: (Boolean) -> Unit): (name: String, text: String) -> Unit =
    { name, text -> IosFiles.share(name, text.encodeToByteArray()) }
