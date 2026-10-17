package com.scd.android

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

private object QueuedTracks {
    private const val LIMIT = 6000
    private val map = java.util.LinkedHashMap<String, Track>(256, 0.75f, true)

    fun put(track: Track) {
        synchronized(map) {
            map[track.urn] = track
            if (map.size > LIMIT) {
                val eldest = map.keys.iterator()
                eldest.next()
                eldest.remove()
            }
        }
    }

    fun get(urn: String): Track? = synchronized(map) { map[urn] }
}

fun Track.toMediaItem(): MediaItem {
    QueuedTracks.put(this)
    val local = if (Downloads.isDownloaded(urn)) Downloads.fileFor(urn) else null
    val gp = goPlus
    if (gp) ScDataSource.markGoPlus(urn)
    ScDataSource.noteStorageQuality(urn, scdMeta?.storage_quality)
    ScDataSource.expectDuration(urn, displayDuration)
    val localArt = Downloads.artPathOrNull(urn)
    val artwork = localArt?.let { File(it).toUri() } ?: Api.artworkUrl(artwork_url)?.toUri()
    return MediaItem.Builder()
        .setMediaId(urn)
        .setUri(local?.toUri() ?: Api.streamUrl(urn).toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(user?.username)
                .setArtworkUri(artwork)
                .apply {
                    if (artwork == null) {
                        NoteArtwork.bytes?.let {
                            setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                }
                .setExtras(
                    Bundle().apply {
                        putString("artist_urn", user?.urn)
                        putString("artwork_url", artwork_url)
                        putString("waveform_url", waveform_url)
                        putString("permalink_url", permalink_url)
                        putString("genre", genre)
                        putLong("duration", displayDuration)
                        putBoolean("go_plus", gp)
                    }
                )
                .build()
        )
        .build()
}

fun MediaItem.toTrackOrNull(): Track? {
    if (mediaId.isEmpty()) return null
    QueuedTracks.get(mediaId)?.let { return it }
    val md = mediaMetadata
    val title = md.title?.toString() ?: return null
    return Track(
        urn = mediaId,
        title = title,
        monetization_model = if (md.extras?.getBoolean("go_plus") == true) "SUB_HIGH_TIER" else null,
        duration = md.extras?.getLong("duration") ?: 0L,
        artwork_url = md.extras?.getString("artwork_url"),
        waveform_url = md.extras?.getString("waveform_url"),
        permalink_url = md.extras?.getString("permalink_url"),
        genre = md.extras?.getString("genre"),
        user = TrackUser(
            urn = md.extras?.getString("artist_urn"),
            username = md.artist?.toString() ?: "",
        ),
    )
}


actual fun hasNetwork(context: PlatformContext): Boolean = NetMonitor.hasTransport(context)

actual fun openInBrowser(context: PlatformContext, url: String) {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val browsers = pm.queryIntentActivities(probe, 0)
        .map { it.activityInfo.packageName }
        .filter { it != context.packageName }
        .distinct()
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    val pkg = browsers.firstOrNull { it.contains("chrome") }
        ?: browsers.firstOrNull { it.contains("browser") || it.contains("firefox") || it.contains("opera") }
        ?: browsers.firstOrNull()
    if (pkg != null) view.setPackage(pkg)
    runCatching { context.startActivity(view) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            )
        }
    }
}

fun openCamera(context: android.content.Context) {
    val camera = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    val chooser = Intent.createChooser(camera, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(chooser) }.onFailure {
        runCatching {
            context.startActivity(camera.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}
