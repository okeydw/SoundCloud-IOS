package com.scd.android

import com.scd.android.core.R

fun albumKindRes(kind: String): Int = when (kind) {
    "album" -> R.string.kind_album
    "ep" -> R.string.kind_ep
    "single" -> R.string.kind_single
    "compilation" -> R.string.kind_compilation
    else -> R.string.kind_album
}
