package com.scd.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scd.android.res.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun rememberSleepClock(): Long {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(SleepTimer.endsAt) {
        while (SleepTimer.endsAt > 0L) {
            now = nowMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    return now
}

@Composable
fun sleepTimerStatus(): String? {
    val now = rememberSleepClock()
    return when {
        SleepTimer.endsAt > 0L -> stringResource(Res.string.sleep_timer_left, SleepTimer.format(SleepTimer.remainingMs(now)))
        SleepTimer.endOfTrack -> stringResource(Res.string.sleep_timer_after_track)
        else -> null
    }
}

@Composable
fun sleepTimerShort(): String? {
    val now = rememberSleepClock()
    return when {
        SleepTimer.endsAt > 0L -> SleepTimer.format(SleepTimer.remainingMs(now))
        SleepTimer.endOfTrack -> stringResource(Res.string.sleep_timer_end_of_track)
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val p = LocalGlass.current
    GlassSheet(onDismiss, stringResource(Res.string.sleep_timer), Res.drawable.ic_timer) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val status = sleepTimerStatus()
            Text(
                status ?: stringResource(Res.string.sleep_timer_hint),
                style = GlassType.body,
                color = if (status != null) p.accent else p.text2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SleepTimer.MINUTES.forEach { m ->
                    GlassChip(stringResource(Res.string.sleep_timer_minutes, m)) {
                        SleepTimer.start(m)
                        onDismiss()
                    }
                }
                GlassChip(stringResource(Res.string.sleep_timer_end_of_track), active = SleepTimer.endOfTrack) {
                    SleepTimer.startEndOfTrack()
                    onDismiss()
                }
            }
            if (SleepTimer.active) {
                Spacer(Modifier.height(4.dp))
                if (SleepTimer.endsAt > 0L) {
                    SheetAction(Res.drawable.ic_plus, stringResource(Res.string.sleep_timer_extend, SleepTimer.EXTEND_MINUTES)) { SleepTimer.extend() }
                }
                SheetAction(Res.drawable.ic_clear, stringResource(Res.string.sleep_timer_off)) {
                    SleepTimer.cancel()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun SleepEyebrow() {
    val p = LocalGlass.current
    val text = sleepTimerShort() ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(Res.drawable.ic_timer), stringResource(Res.string.sleep_timer), tint = p.accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Eyebrow(text, color = p.accent)
    }
}
