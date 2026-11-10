package com.scd.android

import com.scd.android.res.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun QualitySettingsPanel() {
    val context = LocalAppContext.current
    var upgradeCount by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QualityChoices(stringResource(Res.string.stream_quality), Prefs.streamQuality, Prefs::changeStreamQuality)
        Spacer(Modifier.height(8.dp))
        QualityChoices(stringResource(Res.string.download_quality), Prefs.downloadQuality, Prefs::changeDownloadQuality)
        if (Downloads.downloaded.isNotEmpty()) {
            val upgrading = Downloads.UPGRADE_KEY in Downloads.activeLists
            GlassChip(stringResource(if (upgrading) Res.string.upgrade_quality_stop else Res.string.upgrade_quality), active = upgrading) {
                if (upgrading) Downloads.cancelList(context, Downloads.UPGRADE_KEY)
                else if (!Prefs.offline) Downloads.upgradeAll(context) { upgradeCount = it }
            }
            upgradeCount?.let { count ->
                Text(if (count == 0) stringResource(Res.string.upgrade_quality_none) else stringResource(Res.string.upgrade_quality_started, count), style = GlassType.small, color = LocalGlass.current.text3)
            }
        }
        if (!Prefs.star) Text(stringResource(Res.string.stream_quality_locked), style = GlassType.small, color = LocalGlass.current.text3)
    }
}

@Composable
private fun QualityChoices(title: String, selected: String, onSelect: (String) -> Unit) {
    Text(title, style = GlassType.rowTitle, color = LocalGlass.current.text)
    ArtistActions {
        Prefs.streamQualities.forEach { quality ->
            val label = when (quality) {
                Prefs.QUALITY_AUTO -> stringResource(Res.string.stream_quality_auto)
                else -> quality.uppercase()
            }
            GlassChip(label, active = selected == quality, modifier = Modifier.heightIn(min = 48.dp)) { onSelect(quality) }
        }
    }
}
