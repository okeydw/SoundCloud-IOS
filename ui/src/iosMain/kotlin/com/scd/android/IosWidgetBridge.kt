package com.scd.android

import androidx.compose.runtime.snapshotFlow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.writeToURL

@OptIn(ExperimentalForeignApi::class)
object IosWidgetBridge {
    var reload: () -> Unit = {}

    private const val DEFAULT_GROUP = "group.com.scd.ios"
    private const val ART = "widget_art.jpg"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var artFor: Pair<String?, String?>? = null

    val group: String
        get() {
            val bundle = NSBundle.mainBundle
            val alt = (bundle.objectForInfoDictionaryKey("ALTAppGroups") as? List<*>)?.firstOrNull() as? String
            return alt ?: bundle.objectForInfoDictionaryKey("SoundCloudAppGroup") as? String ?: DEFAULT_GROUP
        }

    private data class Snapshot(val urn: String?, val title: String, val artist: String, val playing: Boolean, val art: String?, val accent: Int)

    private fun snapshot() = Snapshot(NowPlaying.urn, NowPlaying.title, NowPlaying.artist, NowPlaying.isPlaying, NowPlaying.artworkUri, Prefs.accent)

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            snapshotFlow { snapshot() }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest { publish(it) }
        }
    }

    private fun publishMetadata(s: Snapshot): NSUserDefaults {
        val defaults = NSUserDefaults(suiteName = group)
        defaults.setObject(s.title, forKey = "title")
        defaults.setObject(s.artist, forKey = "artist")
        defaults.setBool(s.playing, forKey = "playing")
        defaults.setObject(s.urn ?: "", forKey = "urn")
        defaults.setObject(Strings.get("widget_idle"), forKey = "idle")
        defaults.setObject(Strings.get("widget_open"), forKey = "open")
        defaults.setInteger(s.accent.toLong() and 0xFFFFFFFFL, forKey = "accent")
        if ((s.urn to s.art) != artFor) defaults.setObject("", forKey = "art")
        runCatching { reload() }
        return defaults
    }

    private suspend fun publish(s: Snapshot) {
        val defaults = publishMetadata(s)
        val artKey = s.urn to s.art
        if (artKey != artFor) {
            val bytes = s.urn?.let { artwork(it, s.art) }
            withContext(Dispatchers.Default) { writeArt(bytes) }
            defaults.setObject(if (bytes != null) ART else "", forKey = "art")
            defaults.setDouble(nowMillis().toDouble(), forKey = "artStamp")
            artFor = artKey
        }
        runCatching { reload() }
    }

    private suspend fun artwork(urn: String, art: String?): ByteArray? = withContext(IoDispatcher) {
        Downloads.artPath(urn)?.let { Fs.readAtomic(it) }?.let { return@withContext it }
        val raw = art ?: return@withContext null
        runCatching {
            if (raw.startsWith("/") || raw.startsWith("file:")) return@runCatching Fs.readAtomic(raw.removePrefix("file://"))
            val res = if (raw.contains("sndcdn.com")) {
                val big = Api.artworkUrl(raw) ?: raw
                val (n, v) = Api.imageProxyTarget(big)
                Http.execute(HttpRequest("${Api.IMAGES_BASE}/?t=${formEncode(v)}", headers = listOf(n to v)))
            } else {
                Http.execute(HttpRequest(raw))
            }
            res.body.takeIf { res.isSuccessful && it.isNotEmpty() }
        }.getOrElse { if (it is CancellationException) throw it; null }
    }

    private fun writeArt(bytes: ByteArray?) {
        val dir = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(group) ?: return
        val url = dir.URLByAppendingPathComponent(ART) ?: return
        if (bytes == null || bytes.isEmpty()) {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
            return
        }
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToURL(url, atomically = true)
    }

    fun markStopped() {
        NSUserDefaults(suiteName = group).setBool(false, forKey = "playing")
        runCatching { reload() }
    }

    fun command(name: String) {
        val p = PlayerHub.player
        when (name) {
            "toggle" -> when {
                p == null || p.itemCount == 0 -> NowPlaying.openPlayerRequest = true
                p.isPlaying || p.playWhenReady -> p.pause()
                else -> p.play()
            }
            "next" -> p?.next()
            "previous" -> p?.previous()
            "open" -> NowPlaying.openPlayerRequest = true
        }
        if (p != null) NowPlaying.sync(p)
        publishMetadata(snapshot())
    }
}
