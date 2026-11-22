package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scd.android.res.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AlbumActions(key: String, title: String, cover: String?, info: SavedAlbumInfo? = null, onRemoved: () -> Unit = {}, shuffled: Boolean, shuffle: () -> Unit, load: suspend () -> List<Track>) {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    val saved = LocalPlaylists.find("local:$key")
    var busy by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    val downloading = key in Downloads.activeLists
    val complete = saved != null && saved.tracks.isNotEmpty() && saved.tracks.all { it.urn in Downloads.downloaded }
    fun save(download: Boolean) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                val tracks = load().filter { it.urn.isNotBlank() && !it.urn.startsWith("wanted:") }
                if (tracks.isEmpty()) { failed = true; return@launch }
                LocalPlaylists.saveAlbum(key, title, cover, tracks, info)
                if (download) Downloads.enqueue(context, tracks, listKey = key)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AlbumActionButton(if (saved != null) Res.drawable.ic_check else Res.drawable.ic_plus,
                stringResource(if (saved != null) Res.string.remove_album else Res.string.save_album), saved != null, !busy) {
                if (saved != null) { LocalPlaylists.delete(saved.id); onRemoved() } else save(false)
            }
            AlbumActionButton(if (downloading) Res.drawable.ic_clear else if (complete) Res.drawable.ic_check else Res.drawable.ic_download,
                stringResource(if (downloading) Res.string.cd_cancel_download else Res.string.cd_download),
                downloading || complete, !busy && (downloading || !Prefs.offline && !complete)) {
                if (downloading) Downloads.cancelList(context, key) else save(true)
            }
            AlbumActionButton(if (shuffled) Res.drawable.ic_shuffle_on else Res.drawable.ic_shuffle,
                stringResource(Res.string.notif_shuffle), shuffled, true, shuffle)
        }
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        if (failed) Text(stringResource(Res.string.error_network), style = MaterialTheme.typography.labelSmall)
    }
}
