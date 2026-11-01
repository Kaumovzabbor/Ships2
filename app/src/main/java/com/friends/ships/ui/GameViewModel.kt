package com.friends.ships.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.friends.ships.R
import com.friends.ships.game.FLEET
import com.friends.ships.game.SIZE
import com.friends.ships.game.coordName
import com.friends.ships.game.randomFleet
import com.friends.ships.game.shipCells
import com.friends.ships.network.Discovery
import com.friends.ships.network.GameClient
import com.friends.ships.network.GameServer
import com.friends.ships.network.Msg
import com.friends.ships.network.PlayerInfo
import com.friends.ships.network.localIp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, JOIN, LOBBY, PLACE, BATTLE, OVER, HOWTO }

data class Room(val svc: String, val name: String, val host: String, val port: Int)

data class UiState(
    val screen: Screen = Screen.HOME,
    val name: String = "",
    val busy: Boolean = false,
    val error: Int? = null,
    val myId: Int = -1,
    val isHost: Boolean = false,
    val hostIp: String = "",
    val players: List<PlayerInfo> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val placed: Map<Int, List<Int>> = emptyMap(),
    val horizontal: Boolean = true,
    val iAmReady: Boolean = false,
    val myFleet: List<List<Int>> = emptyList(),
    val placeSecs: Int = 0,
    val placeKey: Int = 0,
    val turn: Int = -1,
    val turnSecs: Int = 0,
    val turnKey: Int = 0,
    val marks: Map<Int, Map<Int, String>> = emptyMap(),
    val log: List<String> = emptyList(),
    val winner: Int = -1,
    val shots: Int = 0,
    val hits: Int = 0,
    val sunkCount: Int = 0,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val _s = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _s

    private var server: GameServer? = null
    private var serverScope: CoroutineScope? = null
    private var client: GameClient? = null
    private var collectJob: Job? = null
    private var connected = false
    private val discovery = Discovery(app)

    private fun str(id: Int, vararg a: Any): String = getApplication<Application>().getString(id, *a)

    private fun nameOf(id: Int) = _s.value.players.firstOrNull { it.id == id }?.name ?: "?"

    fun setName(n: String) = _s.update { it.copy(name = n.take(16), error = null) }

    fun go(s: Screen) = _s.update { it.copy(screen = s, error = null) }

    // ---------- ulanish ----------

    fun createRoom() {
        val name = _s.value.name.trim()
        if (name.isEmpty()) return _s.update { it.copy(error = R.string.err_name_empty) }
        _s.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val srv = GameServer(sc)
            val ok = withContext(Dispatchers.IO) { srv.start() }
            if (!ok) {
                sc.cancel()
                _s.update { it.copy(busy = false, error = R.string.err_port) }
                return@launch
            }
            server = srv
            serverScope = sc
            if (connectTo("127.0.0.1", GameServer.DEFAULT_PORT, name)) {
                discovery.register(name, GameServer.DEFAULT_PORT)
                _s.update { it.copy(isHost = true, hostIp = localIp()) }
            }
        }
    }

    fun joinRoom(host: String, port: Int = GameServer.DEFAULT_PORT) {
        val name = _s.value.name.trim()
        if (name.isEmpty()) return _s.update { it.copy(error = R.string.err_name_empty) }
        if (host.isBlank()) return
        _s.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            discovery.stopDiscovery()
            connectTo(host.trim(), port, name)
        }
    }

    private suspend fun connectTo(host: String, port: Int, name: String): Boolean {
        val c = GameClient(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        client = c
        collectJob?.cancel()
        collectJob = viewModelScope.launch { for (m in c.incoming) handle(m) }
        val ok = c.connect(host, port, name)
        if (!ok) {
            cleanup()
            _s.update { it.copy(busy = false, error = R.string.err_connect) }
        }
        return ok
    }

    private fun cleanup() {
        connected = false
        collectJob?.cancel()
        client?.close()
        client = null
        server?.stop()
        serverScope?.cancel()
        server = null
        serverScope = null
        discovery.unregister()
    }

    fun leave() {
        cleanup()
        _s.update { UiState(name = it.name) }
    }

    // ---------- xonalarni qidirish ----------

    fun startDiscovery() {
        _s.update { it.copy(rooms = emptyList()) }
        discovery.discover(
            onFound = { svc, host, port ->
                _s.update { s ->
                    s.copy(rooms = s.rooms.filter { it.host != host } + Room(svc, svc.removePrefix(Discovery.PREFIX), host, port))
                }
            },
            onLost = { svc -> _s.update { s -> s.copy(rooms = s.rooms.filter { it.svc != svc }) } },
        )
    }

    fun stopDiscovery() = discovery.stopDiscovery()

    // ---------- lobbi ----------

    fun addBot() = client?.send(Msg("ADDBOT")) ?: Unit
    fun start() = client?.send(Msg("START")) ?: Unit
    fun rematch() = client?.send(Msg("REMATCH")) ?: Unit

    // ---------- joylashtirish ----------

    fun toggleDir() = _s.update { it.copy(horizontal = !it.horizontal) }
    fun clearPlacement() = _s.update { if (it.iAmReady) it else it.copy(placed = emptyMap()) }

    fun randomize() = _s.update {
        if (it.iAmReady) it else it.copy(placed = randomFleet().mapIndexed { i, f -> i to f }.toMap())
    }

    fun placeAt(cell: Int) {
        val s = _s.value
        if (s.iAmReady) return
        val owner = s.placed.entries.firstOrNull { cell in it.value }
        if (owner != null) return _s.update { it.copy(placed = it.placed - owner.key) }
        val idx = FLEET.indices.firstOrNull { it !in s.placed } ?: return
        val cells = shipCells(FLEET[idx], cell / SIZE, cell % SIZE, s.horizontal) ?: return
        if (s.placed.values.any { o -> o.any { it in cells } }) return
        _s.update { it.copy(placed = it.placed + (idx to cells)) }
    }

    fun ready() {
        val s = _s.value
        if (s.placed.size != FLEET.size || s.iAmReady) return
        val fleet = FLEET.indices.map { s.placed.getValue(it) }
        client?.send(Msg("READY", fleet = fleet))
        _s.update { it.copy(iAmReady = true, myFleet = fleet) }
    }

    // ---------- jang ----------

    fun fire(target: Int, cell: Int) {
        client?.send(Msg("FIRE", target = target, cell = cell))
    }

    // ---------- server xabarlari ----------

    private fun handle(m: Msg) {
        when (m.t) {
            "WELCOME" -> {
                connected = true
                _s.update { it.copy(busy = false, screen = Screen.LOBBY, myId = m.id, players = m.players) }
            }

            "REJECT" -> {
                val e = when (m.text) {
                    "FULL" -> R.string.err_full
                    "STARTED" -> R.string.err_started
                    else -> R.string.err_name
                }
                cleanup()
                _s.update { it.copy(busy = false, error = e) }
            }

            "LOBBY" -> _s.update { it.copy(players = m.players) }

            "REMATCH" -> _s.update {
                it.copy(
                    screen = Screen.LOBBY, players = m.players, winner = -1, marks = emptyMap(),
                    log = emptyList(), placed = emptyMap(), iAmReady = false,
                )
            }

            "PLACE" -> _s.update {
                it.copy(
                    screen = Screen.PLACE, players = m.players, placed = emptyMap(), iAmReady = false,
                    placeSecs = m.secs, placeKey = it.placeKey + 1, marks = emptyMap(), log = emptyList(),
                    shots = 0, hits = 0, sunkCount = 0, winner = -1,
                )
            }

            "FLEET" -> _s.update { it.copy(myFleet = m.fleet, iAmReady = true) }

            "BATTLE" -> _s.update {
                it.copy(
                    screen = Screen.BATTLE, players = m.players, turn = m.turn,
                    turnSecs = m.secs, turnKey = it.turnKey + 1,
                )
            }

            "RESULT" -> onResult(m)

            "LEFT" -> _s.update {
                val line = str(R.string.log_left, it.players.firstOrNull { p -> p.id == m.id }?.name ?: "?")
                it.copy(
                    players = m.players, turn = m.turn, turnKey = it.turnKey + 1, log = it.log + line,
                    winner = m.winner, screen = if (m.winner != -1) Screen.OVER else it.screen,
                )
            }

            "DISCONNECTED" -> if (connected) {
                cleanup()
                _s.update { UiState(name = it.name, error = R.string.err_host_left) }
            }
        }
    }

    private fun onResult(m: Msg) {
        val s = _s.value
        val cells = HashMap(s.marks[m.target] ?: emptyMap())
        cells[m.cell] = if (m.res == "MISS") "MISS" else "HIT"
        if (m.res == "SUNK") m.sunk.forEach { cells[it] = "SUNK" }
        val marks = s.marks + (m.target to cells)

        val who = s.players.firstOrNull { it.id == m.id }?.name ?: "?"
        val tgt = s.players.firstOrNull { it.id == m.target }?.name ?: "?"
        val line = when (m.res) {
            "SUNK" -> str(R.string.log_sunk, who, tgt, coordName(m.cell))
            "HIT" -> str(R.string.log_hit, who, tgt, coordName(m.cell))
            else -> str(R.string.log_miss, who, tgt, coordName(m.cell))
        }
        val mine = m.id == s.myId
        _s.update {
            it.copy(
                marks = marks,
                players = m.players,
                turn = m.turn,
                turnSecs = m.secs,
                turnKey = it.turnKey + 1,
                log = it.log + line,
                shots = it.shots + if (mine) 1 else 0,
                hits = it.hits + if (mine && m.res != "MISS") 1 else 0,
                sunkCount = it.sunkCount + if (mine && m.res == "SUNK") 1 else 0,
                winner = m.winner,
                screen = if (m.winner != -1) Screen.OVER else it.screen,
            )
        }
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }
}
