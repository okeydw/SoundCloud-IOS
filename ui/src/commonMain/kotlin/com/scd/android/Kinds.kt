package com.scd.android

import com.scd.android.res.*
import org.jetbrains.compose.resources.StringResource

fun albumKindText(kind: String): StringResource = when (kind) {
    "ep" -> Res.string.kind_ep
    "single" -> Res.string.kind_single
    "compilation" -> Res.string.kind_compilation
    else -> Res.string.kind_album
}

fun Playlist.kindLabelText(): StringResource = when (kind) {
    "album" -> Res.string.kind_album
    "ep" -> Res.string.kind_ep
    "single" -> Res.string.kind_single
    "compilation" -> Res.string.kind_compilation
    else -> Res.string.playlist_label
}
