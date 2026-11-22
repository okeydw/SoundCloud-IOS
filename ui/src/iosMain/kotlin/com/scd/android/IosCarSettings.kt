package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scd.android.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CarOptions() {
    var editing by remember { mutableStateOf<CarScenario?>(null) }
    val current = editing
    if (current != null) {
        var conflict by remember(current.id) { mutableStateOf(false) }
        Panel {
            OutlinedTextField(current.name, { editing = current.copy(name = it.take(64)) }, singleLine = true,
                label = { Text(stringResource(Res.string.car_scenario_name)) }, modifier = Modifier.padding(12.dp))
            Label(stringResource(Res.string.car_devices))
            Choice(stringResource(Res.string.car_devices_any), current.anyDevice) { editing = current.copy(devices = emptyMap()) }
            val devices = current.devices + listOfNotNull(IosCar.device).toMap()
            devices.forEach { (id, name) ->
                Choice(name, id in current.devices) {
                    editing = current.copy(devices = if (id in current.devices) current.devices - id else current.devices + (id to name))
                }
            }
            Label(stringResource(Res.string.car_source))
            val sources = mapOf("liked" to stringResource(Res.string.liked), "wave" to stringResource(Res.string.tab_wave),
                "downloads" to stringResource(Res.string.storage_downloaded)) + LocalPlaylists.visible.associate { "playlist:" + it.id to it.title }
            OptionMenu(sources, current.source) { editing = current.copy(source = it, sourceTitle = sources[it].orEmpty()) }
            Toggle(stringResource(Res.string.car_autostart), "", current.autostart) { editing = current.copy(autostart = it) }
            Toggle(stringResource(Res.string.car_shuffle), "", current.shuffle) { editing = current.copy(shuffle = it) }
            Label(stringResource(Res.string.car_eq))
            val presets = mapOf(EqPresets.NONE to stringResource(Res.string.car_eq_keep), EqPresets.OFF to stringResource(Res.string.car_eq_off)) +
                EqPresets.builtin.associate { EqPresets.presetKey(it.id) to it.id } + EqPresets.custom.associate { EqPresets.customKey(it.id) to it.name }
            OptionMenu(presets, current.eq) { editing = current.copy(eq = it) }
            if (conflict) Hint(if (current.anyDevice) stringResource(Res.string.car_scenario_any_taken)
                else stringResource(Res.string.car_scenario_taken, CarScenarios.conflict(current)?.name.orEmpty()))
            Row {
                TextButton(onClick = { if (CarScenarios.save(current)) editing = null else conflict = true }) { Text(stringResource(Res.string.save)) }
                TextButton(onClick = { editing = null }) { Text(stringResource(Res.string.cancel)) }
            }
            if (CarScenarios.all.any { it.id == current.id }) IosRow(Res.drawable.ic_trash, stringResource(Res.string.delete)) {
                CarScenarios.delete(current.id)
                editing = null
            }
        }
    } else Panel {
        Toggle(stringResource(Res.string.car_autoplay), "", Prefs.carAutoplay) {
            Prefs.changeCarAutoplay(it)
            if (!it) IosCar.disable()
        }
        if (Prefs.carAutoplay) Hint(stringResource(Res.string.ios_car_automation))
        Label(stringResource(Res.string.car_scenarios))
        CarScenarios.all.forEachIndexed { index, scenario ->
            IosRow(Res.drawable.ic_speaker, scenario.name.ifBlank { stringResource(Res.string.car_scenario_number, index + 1) }) { editing = scenario }
        }
        IosRow(Res.drawable.ic_plus, stringResource(Res.string.car_scenario_add)) { editing = CarScenario(CarScenarios.newId()) }
    }
}
