package com.scd.android

import com.scd.android.res.*
import org.jetbrains.compose.resources.StringResource

fun noConnectionText(): StringResource = if (offlineByChoice) Res.string.offline_mode_on else Res.string.no_internet

fun networkErrorText(): StringResource = if (offlineByChoice) Res.string.offline_mode_on else Res.string.error_network
