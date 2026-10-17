package com.scd.android

import androidx.compose.runtime.mutableStateListOf

object Logs {
    private const val MAX = 300
    val lines = mutableStateListOf<String>()

    fun add(tag: String, msg: String) {
        val t = clockText(nowMillis())
        val line = "[$t] $tag · $msg"
        locked(lines) {
            lines.add(0, line)
            while (lines.size > MAX) lines.removeAt(lines.size - 1)
        }
    }

    fun clear() = lines.clear()

    fun dump(): String = lines.joinToString("\n")
}
