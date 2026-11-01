package com.friends.ships.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.friends.ships.game.FLEET
import kotlinx.coroutines.delay

@Composable
private fun Countdown(key: Int, secs: Int): Int {
    var left by remember(key) { mutableIntStateOf(secs) }
    LaunchedEffect(key) {
        left = secs
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    return left
}

@Composable
fun PlaceScreen(s: UiState, vm: GameViewModel) {
    val left = Countdown(s.placeKey, s.placeSecs)
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.place_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.place_time, left))
        Text(stringResource(R.string.place_hint), fontSize = 13.sp)
        val shown = if (s.iAmReady && s.myFleet.isNotEmpty()) s.myFleet.flatten().toSet() else s.placed.values.flatten().toSet()
        BoardView(ships = shown, onTap = if (s.iAmReady) null else vm::placeAt)
        val next = FLEET.indices.firstOrNull { it !in s.placed }
        if (next != null && !s.iAmReady) Text("▮".repeat(FLEET[next]))
        if (s.iAmReady) {
            Text(stringResource(R.string.place_wait))
        } else {
            OutlinedButton(onClick = vm::toggleDir, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (s.horizontal) R.string.place_dir_h else R.string.place_dir_v))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::randomize, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.place_random))
                }
                OutlinedButton(onClick = vm::clearPlacement, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.place_clear))
                }
            }
            Button(
                onClick = vm::ready,
                enabled = s.placed.size == FLEET.size,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.place_ready)) }
        }
    }
}

@Composable
fun BattleScreen(s: UiState, vm: GameViewModel) {
    val left = Countdown(s.turnKey, s.turnSecs)
    val opponents = s.players.filter { it.id != s.myId && it.alive }
    var target by remember { mutableIntStateOf(-1) }
    var cell by remember { mutableIntStateOf(-1) }
    val me = s.players.firstOrNull { it.id == s.myId }
    val myTurn = s.turn == s.myId && me?.alive == true
    if (opponents.none { it.id == target }) target = opponents.firstOrNull()?.id ?: -1
    LaunchedEffect(target, s.turnKey) { cell = -1 }

    Column(
        Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val turnName = s.players.firstOrNull { it.id == s.turn }?.name ?: "?"
        Text(
            if (me?.alive == false) stringResource(R.string.battle_eliminated)
            else if (myTurn) stringResource(R.string.battle_your_turn)
            else stringResource(R.string.battle_turn_of, turnName),
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = if (myTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text("$left s")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            opponents.forEach { p ->
                FilterChip(selected = p.id == target, onClick = { target = p.id }, label = { Text(p.name) })
            }
        }
        val tried = s.marks[target] ?: emptyMap()
        BoardView(
            marks = tried,
            selected = cell,
            onTap = if (myTurn) { c -> if (c !in tried) cell = c } else null,
        )
        Button(
            onClick = { vm.fire(target, cell); cell = -1 },
            enabled = myTurn && cell != -1 && target != -1,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.battle_fire)) }

        Text(stringResource(R.string.battle_my_fleet), fontSize = 13.sp)
        val incoming = s.marks[s.myId] ?: emptyMap()
        Column(Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally)) {
            BoardView(ships = s.myFleet.flatten().toSet(), marks = incoming)
        }
        s.log.takeLast(6).reversed().forEach { Text(it, fontSize = 12.sp) }
    }
}

@Composable
fun OverScreen(s: UiState, vm: GameViewModel) {
    val name = s.players.firstOrNull { it.id == s.winner }?.name ?: "?"
    val acc = if (s.shots == 0) 0 else s.hits * 100 / s.shots
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.over_winner, name), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.over_stats, s.shots, s.hits, acc, s.sunkCount))
        if (s.isHost) {
            Button(onClick = vm::rematch, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.over_rematch))
            }
        }
        OutlinedButton(onClick = vm::leave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.over_menu))
        }
    }
}
