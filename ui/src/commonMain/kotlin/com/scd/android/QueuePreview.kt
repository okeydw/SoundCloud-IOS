package com.scd.android

import androidx.compose.runtime.*

data class QueuePreview(
    val previous: Track? = null,
    val next: Track? = null,
    val current: Track? = null,
    val currentId: String? = null,
    val currentIndex: Int = -1,
    val previousIndex: Int = -1,
    val nextIndex: Int = -1,
    val previousId: String? = null,
    val nextId: String? = null,
)

@Composable
fun rememberQueuePreview(controller: QueuePlayer?): QueuePreview {
    fun snapshot(): QueuePreview {
        val c = controller ?: return QueuePreview()
        fun at(index: Int) = index.takeIf { it in 0 until c.itemCount }?.let { c.trackAt(it) }
        return QueuePreview(
            at(c.previousIndex), at(c.nextIndex), c.currentTrack,
            c.currentUrn, c.currentIndex, c.previousIndex, c.nextIndex,
            c.previousIndex.takeIf { it in 0 until c.itemCount }?.let { c.urnAt(it) },
            c.nextIndex.takeIf { it in 0 until c.itemCount }?.let { c.urnAt(it) },
        )
    }
    var preview by remember(controller) { mutableStateOf(snapshot()) }
    DisposableEffect(controller) {
        val listener = object : QueueListener {
            override fun onChanged() { preview = snapshot() }
        }
        controller?.addListener(listener)
        preview = snapshot()
        onDispose { controller?.removeListener(listener) }
    }
    return preview
}
