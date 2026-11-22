package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.scd.android.res.*
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.stringResource
import platform.AVKit.AVRoutePickerView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformOutputDialog(onDismiss: () -> Unit) {
    AppDialog(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.output_title))
            UIKitView(factory = { AVRoutePickerView() }, modifier = Modifier.size(64.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cd_close)) }
        }
    }
}
