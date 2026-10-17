package com.scd.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.scd.android.ui.R

@Composable
actual fun rememberCoverPicker(urn: String): () -> Unit {
    val context = LocalContext.current
    var source by remember(urn) { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) source = uri
    }
    source?.let { uri ->
        CropDialog(
            source = uri,
            onDismiss = { source = null },
            aspect = 1f,
            save = { PlaylistCovers.set(urn, it) },
        ) { ok ->
            source = null
            showToast(context, context.getString(if (ok) R.string.cover_saved else R.string.background_failed))
        }
    }
    return { runCatching { launcher.launch("image/*") } }
}
