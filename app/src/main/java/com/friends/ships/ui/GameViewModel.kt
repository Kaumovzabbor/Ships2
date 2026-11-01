package com.friends.ships.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.friends.ships.R
import com.friends.ships.game.ShipPos
import com.friends.ships.game.canPlace
import com.friends.ships.game.fleetSizes
import com.friends.ships.game.placementValid
import com.friends.ships.game.randomShipPositions
import com.friends.ships.game.rotated
import com.friends.ships.game.toShipPos
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, JOIN, LOBBY, PLACE, BATTLE, OVER, HOWTO, SETTINGS }

data class Room(val svc: String, val name: String, val host: String, val port: Int)

/** Jurnal yozuvi: matn UI'da joriy tilda yasaladi. kind: MISS, HIT, SUNK, LEFT */
data class LogEntry(val kind: String, val who: String, val tgt: String = "", val cell: Int = -1)

/** Oxirgi o'q animatsiyasi uchun (key o'zgarganda animatsiya qayta boshlanadi). */
data class Pulse(val target: Int, val cell: Int, val res: String, val key: Int)

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
    // host sozlamalari
    val fleetType: String = "CLASSIC",
    val noTouch: Boolean = false,
    val extra: Boolean = true,
    val timer: Int = 30,
    // joylashtirish
    val ships: List<ShipPos> = emptyList(),
    val iAmReady: Boolean = false,
    val myFleet: List<List<Int>> = emptyList(),
    val placeSecs: Int = 0,
    val placeKey: Int = 0,
    // jang
    val turn: Int = -1,
    val turnSecs: Int = 0,
    val turnKey: Int = 0,
    val marks: Map<Int, Map<Int, String>> = emptyMap(),
    val sunkLens: Map<Int, List<Int>> = emptyMap(),
    val log: List<LogEntry> = emptyList(),
    val pulse: Pulse? = null,
    val winner: Int = -1,
    val shots: Int = 0,
    val hits: Int = 0,
    val sunkCount: Int = 0,
    // ilova sozlamalari
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val theme: Int = 2,
    val lang: String = "",
) {
    val sizes: List<Int> get() = fleetSizes(fleetType)
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val feedback = Feedback(app)
    private val _s = MutableStateFlow(
        UiState(
            name = prefs.name, sound = prefs.sound, vibration = prefs.vibration,
            theme = prefs.theme, lang = prefs.lang,
        ),
    )
    val state: StateFlow<UiState> = _s

    private var server: GameServer? = null
    private var serverScope: CoroutineScope? = null
    private var client: GameClient? = null
    private var collectJob: Job? = null
    private var connected = false
    private var soloPending = false
    private val discovery = Discovery(app)

    private fun fx(kind: String) = _s.value.let { feedback.play(kind, it.sound, it.vibration) }

    // ---------- ilova sozlamalari ----------

    fun setSound(v: Boolean) {
        prefs.sound = v
        _s.update { it.copy(sound = v) }
    }

    fun setVibration(v: Boolean) {
        prefs.vibration = v
        _s.update { it.copy(vibration = v) }
    }

    fun setTheme(v: Int) {
        prefs.theme = v
        _s.update { it.copy(theme = v) }
    }

    fun setLang(v: String) {
        prefs.lang = v
        _s.update { it.copy(lang = v) }
    }

    fun setName(n: String) {
        val t = n.take(16)
        prefs.name = t
        _s.update { it.copy(name = t, error = null) }
    }

    fun go(s: Screen) = _s.update { it.copy(screen = s, error = null) }

    // ---------- ulanish ----------

    fun createRoom(solo: Boolean = false) {
        val name = _s.value.name.trim()
        if (name.isEmpty()) return _s.update { it.copy(error = R.string.err_name_empty) }
        _s.update { it.copy(busy = true, error = null) }
        soloPending = solo
        viewModelScope.launch {
            val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val srv = GameServer(sc)
            val ok = withContext(Dispatchers.IO) { srv.start() }
            if (!ok) {
                sc.cancel()
                soloPending = false
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
        soloPending = false
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
        _s.update { UiState(name = it.name, sound = it.sound, vibration = it.vibration, theme = it.theme, lang = it.lang) }
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

    /** Faqat host: sozlamalarni yuboradi, server hammaga tarqatadi. */
    fun sendSettings(fleet: String, noTouch: Boolean, extra: Boolean, timer: Int) {
        client?.send(Msg("SETTINGS", fleetType = fleet, noTouch = noTouch || fleet == "RUSSIAN", extra = extra, timer = timer))
    }

    // ---------- joylashtirish ----------

    fun movePlacement(idx: Int, pos: ShipPos) = _s.update {
        if (it.iAmReady || idx !in it.ships.indices || !canPlace(it.ships, idx, pos, it.noTouch)) it
        else it.copy(ships = it.ships.toMutableList().also { l -> l[idx] = pos })
    }

    fun rotateShip(idx: Int) = _s.update {
        if (it.iAmReady || idx !in it.ships.indices) it
        else rotated(it.ships, idx, it.noTouch)?.let { p ->
            it.copy(ships = it.ships.toMutableList().also { l -> l[idx] = p })
        } ?: it
    }

    fun randomize() = _s.update {
        if (it.iAmReady) it else it.copy(ships = randomShipPositions(it.sizes, it.noTouch))
    }

    fun ready() {
        val s = _s.value
        if (s.iAmReady || !placementValid(s.ships, s.noTouch)) return
        val fleet = s.ships.map { it.cells()!! }
        client?.send(Msg("READY", fleet = fleet))
        _s.update { it.copy(iAmReady = true, myFleet = fleet) }
    }

    // ---------- jang ----------

    fun fire(target: Int, cell: Int) {
        client?.send(Msg("FIRE", target = target, cell = cell))
    }

    // ---------- server xabarlari ----------

    private fun UiState.withCfg(m: Msg) = copy(fleetType = m.fleetType, noTouch = m.noTouch, extra = m.extra, timer = m.timer)

    private fun handle(m: Msg) {
        when (m.t) {
            "WELCOME" -> {
                connected = true
                _s.update { it.withCfg(m).copy(busy = false, screen = Screen.LOBBY, myId = m.id, players = m.players) }
                if (soloPending) {
                    soloPending = false
                    client?.send(Msg("ADDBOT"))
                    client?.send(Msg("START"))
                }
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

            "LOBBY" -> _s.update { it.withCfg(m).copy(players = m.players) }

            "REMATCH" -> _s.update {
                it.withCfg(m).copy(
                    screen = Screen.LOBBY, players = m.players, winner = -1, marks = emptyMap(),
                    sunkLens = emptyMap(), log = emptyList(), ships = emptyList(), iAmReady = false, pulse = null,
                )
            }

            "PLACE" -> _s.update {
                val c = it.withCfg(m)
                c.copy(
                    screen = Screen.PLACE, players = m.players, ships = randomShipPositions(c.sizes, c.noTouch),
                    iAmReady = false, myFleet = emptyList(), placeSecs = m.secs, placeKey = it.placeKey + 1,
                    marks = emptyMap(), sunkLens = emptyMap(), log = emptyList(), pulse = null,
                    shots = 0, hits = 0, sunkCount = 0, winner = -1,
                )
            }

            // vaqt tugadi: server flotni o'zi belgiladi
            "FLEET" -> _s.update {
                it.copy(myFleet = m.fleet, iAmReady = true, ships = m.fleet.map { f -> toShipPos(f) })
            }

            "BATTLE" -> {
                _s.update {
                    it.withCfg(m).copy(
                        screen = Screen.BATTLE, players = m.players, turn = m.turn,
                        turnSecs = m.secs, turnKey = it.turnKey + 1,
                        myFleet = if (it.myFleet.isEmpty()) it.ships.mapNotNull { s -> s.cells() } else it.myFleet,
                    )
                }
                if (m.turn == _s.value.myId) fx("TURN")
            }

            "RESULT" -> onResult(m)

            "LEFT" -> {
                _s.update {
                    val who = it.players.firstOrNull { p -> p.id == m.id }?.name ?: "?"
                    it.copy(
                        players = m.players, turn = m.turn, turnKey = it.turnKey + 1,
                        log = it.log + LogEntry("LEFT", who),
                        winner = m.winner, screen = if (m.winner != -1) Screen.OVER else it.screen,
                    )
                }
                if (m.winner == _s.value.myId) fx("WIN")
            }

            "DISCONNECTED" -> if (connected) {
                cleanup()
                _s.update {
                    UiState(
                        name = it.name, error = R.string.err_host_left, sound = it.sound,
                        vibration = it.vibration, theme = it.theme, lang = it.lang,
                    )
                }
            }
        }
    }

    private fun onResult(m: Msg) {
        val s = _s.value
        val cells = HashMap(s.marks[m.target] ?: emptyMap())
        cells[m.cell] = if (m.res == "MISS") "MISS" else "HIT"
        if (m.res == "SUNK") {
            m.sunk.forEach { cells[it] = "SUNK" }
            m.around.forEach { if (it !in cells) cells[it] = "MISS" }
        }
        val marks = s.marks + (m.target to cells)
        val sunkLens = if (m.res == "SUNK") s.sunkLens + (m.target to (s.sunkLens[m.target].orEmpty() + m.sunk.size)) else s.sunkLens

        val who = s.players.firstOrNull { it.id == m.id }?.name ?: "?"
        val tgt = s.players.firstOrNull { it.id == m.target }?.name ?: "?"
        val mine = m.id == s.myId
        _s.update {
            it.copy(
                marks = marks,
                sunkLens = sunkLens,
                players = m.players,
                turn = m.turn,
                turnSecs = m.secs,
                turnKey = it.turnKey + 1,
                log = it.log + LogEntry(m.res, who, tgt, m.cell),
                pulse = Pulse(m.target, m.cell, m.res, (it.pulse?.key ?: 0) + 1),
                shots = it.shots + if (mine) 1 else 0,
                hits = it.hits + if (mine && m.res != "MISS") 1 else 0,
                sunkCount = it.sunkCount + if (mine && m.res == "SUNK") 1 else 0,
                winner = m.winner,
                screen = if (m.winner != -1) Screen.OVER else it.screen,
            )
        }
        if (mine || m.target == s.myId) fx(m.res)
        if (m.winner == s.myId) fx("WIN") else if (m.turn == s.myId && !mine) fx("TURN")
    }

    override fun onCleared() {
        cleanup()
        feedback.release()
        super.onCleared()
    }
}
