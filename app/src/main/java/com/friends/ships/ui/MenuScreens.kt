package com.friends.ships.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.friends.ships.R
import com.friends.ships.network.GameServer

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun ErrorText(s: UiState) {
    s.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}

@Composable
fun NameField(s: UiState, vm: GameViewModel) {
    OutlinedTextField(
        value = s.name,
        onValueChange = vm::setName,
        label = { Text(stringResource(R.string.name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun HomeScreen(s: UiState, vm: GameViewModel) = Page {
    Spacer(Modifier.height(32.dp))
    Text("Ships", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    NameField(s, vm)
    ErrorText(s)
    Button(onClick = { vm.createRoom() }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(R.string.home_create))
    }
    Button(
        onClick = { vm.go(Screen.JOIN) },
        enabled = !s.busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(stringResource(R.string.home_join)) }
    OutlinedButton(
        onClick = { vm.createRoom(solo = true) },
        enabled = !s.busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(stringResource(R.string.home_solo)) }
    OutlinedButton(onClick = { vm.go(Screen.HOWTO) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.home_howto))
    }
    OutlinedButton(onClick = { vm.go(Screen.SETTINGS) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.home_settings))
    }
}

@Composable
fun HowToScreen(vm: GameViewModel) = Page {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.home_howto), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.howto_text), lineHeight = 24.sp)
    }
    Button(onClick = { vm.go(Screen.HOME) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(s: UiState, vm: GameViewModel) = Page {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.set_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        SwitchRow(stringResource(R.string.set_sound), s.sound) { vm.setSound(it) }
        SwitchRow(stringResource(R.string.set_vibration), s.vibration) { vm.setVibration(it) }
        Text(stringResource(R.string.set_theme))
        ChipRow {
            listOf(0 to R.string.theme_system, 1 to R.string.theme_light, 2 to R.string.theme_dark).forEach { (v, label) ->
                FilterChip(selected = s.theme == v, onClick = { vm.setTheme(v) }, label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(R.string.set_language))
        ChipRow {
            listOf("" to R.string.lang_system, "uz" to R.string.lang_uz, "ru" to R.string.lang_ru, "en" to R.string.lang_en)
                .forEach { (v, label) ->
                    FilterChip(selected = s.lang == v, onClick = { vm.setLang(v) }, label = { Text(stringResource(label)) })
                }
        }
    }
    Button(onClick = { vm.go(Screen.HOME) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
}

@Composable
fun JoinScreen(s: UiState, vm: GameViewModel) = Page {
    DisposableEffect(Unit) {
        vm.startDiscovery()
        onDispose { vm.stopDiscovery() }
    }
    var ip by remember { mutableStateOf("") }
    Text(stringResource(R.string.home_join), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    NameField(s, vm)
    ErrorText(s)
    Text(stringResource(R.string.join_scan))
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.rooms) { r ->
            Button(
                onClick = { vm.joinRoom(r.host, r.port) },
                enabled = !s.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("${r.name}  (${r.host})") }
        }
    }
    OutlinedTextField(
        value = ip,
        onValueChange = { ip = it },
        label = { Text(stringResource(R.string.join_manual)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { vm.joinRoom(ip, GameServer.DEFAULT_PORT) },
        enabled = !s.busy && ip.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(stringResource(R.string.join_connect)) }
    OutlinedButton(onClick = { vm.go(Screen.HOME) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.back))
    }
}

/** Host qoidalarni o'zgartiradi, boshqalar faqat ko'radi. */
@Composable
private fun RulesCard(s: UiState, vm: GameViewModel) {
    val host = s.isHost
    val russian = s.fleetType == "RUSSIAN"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.rules_title), fontWeight = FontWeight.Bold)
        ChipRow {
            FilterChip(
                selected = !russian, enabled = host,
                onClick = { vm.sendSettings("CLASSIC", s.noTouch, s.extra, s.timer) },
                label = { Text(stringResource(R.string.fleet_classic)) },
            )
            FilterChip(
                selected = russian, enabled = host,
                onClick = { vm.sendSettings("RUSSIAN", true, true, s.timer) },
                label = { Text(stringResource(R.string.fleet_russian)) },
            )
        }
        SwitchRow(stringResource(R.string.rule_notouch), s.noTouch, enabled = host && !russian) {
            vm.sendSettings(s.fleetType, it, s.extra, s.timer)
        }
        SwitchRow(stringResource(R.string.rule_extra), s.extra, enabled = host) {
            vm.sendSettings(s.fleetType, s.noTouch, it, s.timer)
        }
        Text(stringResource(R.string.rule_timer))
        ChipRow {
            listOf(0, 15, 30, 60).forEach { t ->
                FilterChip(
                    selected = s.timer == t, enabled = host,
                    onClick = { vm.sendSettings(s.fleetType, s.noTouch, s.extra, t) },
                    label = { Text(if (t == 0) stringResource(R.string.timer_off) else stringResource(R.string.timer_fmt, t)) },
                )
            }
        }
    }
}

@Composable
fun LobbyScreen(s: UiState, vm: GameViewModel) = Page {
    Text(stringResource(R.string.lobby_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    if (s.isHost) Text(stringResource(R.string.lobby_addr, s.hostIp, GameServer.DEFAULT_PORT))
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        s.players.forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(p.name + if (p.id == s.myId) " ★" else "", fontSize = 18.sp)
                if (p.bot) Text("BOT")
            }
        }
        RulesCard(s, vm)
    }
    if (s.isHost) {
        if (s.players.size < 2) Text(stringResource(R.string.lobby_need))
        OutlinedButton(
            onClick = vm::addBot,
            enabled = s.players.size < GameServer.MAX_PLAYERS,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.lobby_add_bot)) }
        Button(
            onClick = vm::start,
            enabled = s.players.size >= 2,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(R.string.lobby_start)) }
    } else {
        Text(stringResource(R.string.lobby_wait_host))
    }
    OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.leave)) }
}
