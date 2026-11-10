package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.scd.android.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun eqPresetName(id: String): String = when (id) {
    "flat" -> stringResource(Res.string.eq_p_flat)
    "bassBoost" -> stringResource(Res.string.eq_p_bass_boost)
    "bassDestroyer" -> stringResource(Res.string.eq_p_bass_destroyer)
    "trebleBoost" -> stringResource(Res.string.eq_p_treble_boost)
    "vocal" -> stringResource(Res.string.eq_p_vocal)
    "rock" -> stringResource(Res.string.eq_p_rock)
    "electronic" -> stringResource(Res.string.eq_p_electronic)
    "classical" -> stringResource(Res.string.eq_p_classical)
    "loudness" -> stringResource(Res.string.eq_p_loudness)
    "vShape" -> stringResource(Res.string.eq_p_v_shape)
    "nightMode" -> stringResource(Res.string.eq_p_night)
    EqPresets.CUSTOM -> stringResource(Res.string.eq_custom)
    else -> EqPresets.customById(id)?.name ?: stringResource(Res.string.eq_custom)
}

sealed interface EqImportOutcome {
    class Done(val result: EqImport) : EqImportOutcome
    data object Empty : EqImportOutcome
    data object Unreadable : EqImportOutcome
}

fun eqImportText(text: String): EqImportOutcome {
    val result = runCatching { EqTransfer.import(text) }.getOrNull() ?: return EqImportOutcome.Empty
    EqPresets.applyImport(result)
    return EqImportOutcome.Done(result)
}

@Composable
expect fun rememberEqFilePicker(onText: (String?) -> Unit): () -> Unit

@Composable
expect fun rememberEqFileSaver(onDone: (Boolean) -> Unit): (name: String, text: String) -> Unit

@Suppress("DEPRECATION")
@Composable
fun rememberEqCopier(): (String) -> Boolean {
    val clipboard = LocalClipboardManager.current
    return { text -> runCatching { clipboard.setText(AnnotatedString(text)) }.isSuccess }
}

fun eqExportName(format: EqFormat): String = if (format == EqFormat.APO) "soundcloud-eq.txt" else "soundcloud-graphiceq.txt"

fun eqExportText(format: EqFormat): String {
    val gains = EqCurve.bandGains(Prefs.eqGains)
    return EqTransfer.export(gains, EqCurve.resolvePreamp(gains, Prefs.eqPreamp), format)
}

@Composable
fun CappedFontScale(max: Float = 1.15f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(d.density, max)) { content() }
    }
}
