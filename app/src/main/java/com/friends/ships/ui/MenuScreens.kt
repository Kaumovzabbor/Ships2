package com.friends.ships.ui

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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
    Spacer(Modifier.height(40.dp))
    Text("Ships", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    NameField(s, vm)
    ErrorText(s)
    Button(onClick = vm::createRoom, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(R.string.home_create))
    }
    Button(
        onClick = { vm.go(Screen.JOIN) },
        enabled = !s.busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(stringResource(R.string.home_join)) }
    OutlinedButton(onClick = { vm.go(Screen.HOWTO) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(R.string.home_howto))
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

@Composable
fun LobbyScreen(s: UiState, vm: GameViewModel) = Page {
    Text(stringResource(R.string.lobby_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    if (s.isHost) Text(stringResource(R.string.lobby_addr, s.hostIp, GameServer.DEFAULT_PORT))
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.players.forEach { p ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(p.name + if (p.id == s.myId) " ★" else "", fontSize = 18.sp)
                if (p.bot) Text("BOT")
            }
        }
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
