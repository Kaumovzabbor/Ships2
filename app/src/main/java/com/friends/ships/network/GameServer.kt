package com.friends.ships.network

import com.friends.ships.game.Engine
import com.friends.ships.game.SIZE
import com.friends.ships.game.isValidFleet
import com.friends.ships.game.randomFleet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket

/**
 * Host telefonida ishlaydigan avtoritar server.
 * Kemalar joylashuvi faqat shu yerda saqlanadi; mijozlarga faqat natijalar yuboriladi.
 */
class GameServer(private val scope: CoroutineScope, private val port: Int = DEFAULT_PORT) {

    companion object {
        const val DEFAULT_PORT = 8765
        const val TURN_SECS = 30
        const val PLACE_SECS = 90
        const val MAX_PLAYERS = 6
    }

    private class Conn(val sock: Socket) {
        private val w = sock.getOutputStream().bufferedWriter()
        fun send(m: Msg) {
            try {
                synchronized(w) {
                    w.write(json.encodeToString(Msg.serializer(), m))
                    w.write("\n")
                    w.flush()
                }
            } catch (_: Exception) {
            }
        }

        fun close() {
            try {
                sock.close()
            } catch (_: Exception) {
            }
        }
    }

    private class P(val id: Int, val name: String, val conn: Conn?, val bot: Boolean) {
        var ready = false
        var fleet: List<List<Int>>? = null
        var alive = true
    }

    private val lock = Any()
    private val players = LinkedHashMap<Int, P>()
    private var nextId = 0
    private var hostId = -1
    private var phase = "LOBBY" // LOBBY, PLACE, BATTLE, OVER
    private var engine: Engine? = null
    private var server: ServerSocket? = null
    private var turnJob: Job? = null
    private var placeJob: Job? = null
    private val lastHit = HashMap<Int, Pair<Int, Int>>()
    private var botCount = 0

    fun start(): Boolean {
        try {
            server = ServerSocket(port)
        } catch (e: Exception) {
            return false
        }
        scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val s = server!!.accept()
                    launch { handle(s) }
                }
            } catch (_: Exception) {
            }
        }
        return true
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        synchronized(lock) {
            turnJob?.cancel()
            placeJob?.cancel()
            players.values.forEach { it.conn?.close() }
        }
    }

    private suspend fun handle(sock: Socket) = withContext(Dispatchers.IO) {
        val conn = Conn(sock)
        var id = -1
        try {
            sock.keepAlive = true
            val reader = sock.getInputStream().bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                val m = try {
                    json.decodeFromString(Msg.serializer(), line)
                } catch (e: Exception) {
                    continue
                }
                if (id == -1) {
                    if (m.t != "JOIN") break
                    id = join(m.name.trim().take(16), conn, sock.inetAddress.isLoopbackAddress)
                    if (id == -1) break
                } else {
                    onMsg(id, m)
                }
            }
        } catch (_: Exception) {
        } finally {
            if (id != -1) onLeave(id)
            conn.close()
        }
    }

    private fun infos() = players.values.map { PlayerInfo(it.id, it.name, it.ready, it.alive, it.bot) }

    private fun broadcast(m: Msg) = players.values.forEach { it.conn?.send(m) }

    private fun join(name: String, conn: Conn, loopback: Boolean): Int = synchronized(lock) {
        val reject = when {
            name.isEmpty() || players.values.any { it.name.equals(name, ignoreCase = true) } -> "NAME"
            phase != "LOBBY" -> "STARTED"
            players.size >= MAX_PLAYERS -> "FULL"
            else -> null
        }
        if (reject != null) {
            conn.send(Msg("REJECT", text = reject))
            return -1
        }
        val id = nextId++
        if (loopback && hostId == -1) hostId = id
        players[id] = P(id, name, conn, false)
        conn.send(Msg("WELCOME", id = id, players = infos()))
        broadcast(Msg("LOBBY", players = infos()))
        id
    }

    private fun onMsg(id: Int, m: Msg) = synchronized(lock) {
        val p = players[id] ?: return@synchronized
        when (m.t) {
            "ADDBOT" -> if (id == hostId && phase == "LOBBY" && players.size < MAX_PLAYERS) {
                botCount++
                val bid = nextId++
                players[bid] = P(bid, "Bot $botCount", null, true)
                broadcast(Msg("LOBBY", players = infos()))
            }

            "START" -> if (id == hostId && phase == "LOBBY" && players.size >= 2) startPlacement()

            "READY" -> if (phase == "PLACE" && !p.ready && isValidFleet(m.fleet)) {
                p.fleet = m.fleet
                p.ready = true
                broadcast(Msg("LOBBY", players = infos()))
                if (players.values.all { it.ready }) startBattle()
            }

            "FIRE" -> if (phase == "BATTLE") doFire(id, m.target, m.cell)

            "REMATCH" -> if (id == hostId && phase == "OVER") {
                phase = "LOBBY"
                engine = null
                players.values.forEach {
                    it.ready = false
                    it.fleet = null
                    it.alive = true
                }
                broadcast(Msg("REMATCH", players = infos()))
            }
        }
    }

    private fun startPlacement() {
        phase = "PLACE"
        players.values.forEach {
            it.ready = it.bot
            it.fleet = if (it.bot) randomFleet() else null
            it.alive = true
        }
        broadcast(Msg("PLACE", secs = PLACE_SECS, players = infos()))
        placeJob?.cancel()
        placeJob = scope.launch {
            delay(PLACE_SECS * 1000L)
            synchronized(lock) {
                if (phase == "PLACE") {
                    players.values.filter { !it.ready }.forEach {
                        val f = randomFleet()
                        it.fleet = f
                        it.ready = true
                        it.conn?.send(Msg("FLEET", fleet = f))
                    }
                    startBattle()
                }
            }
        }
    }

    private fun startBattle() {
        placeJob?.cancel()
        phase = "BATTLE"
        lastHit.clear()
        players.values.forEach { it.alive = true }
        val e = Engine(players.keys.toList(), players.values.associate { it.id to it.fleet!! }, true)
        engine = e
        broadcast(Msg("BATTLE", turn = e.current, secs = TURN_SECS, players = infos()))
        scheduleTurn()
    }

    private fun pickShot(shooter: Int): Pair<Int, Int>? {
        val e = engine ?: return null
        lastHit[shooter]?.let { (t, c) ->
            if (e.isAlive(t)) {
                val nb = buildList {
                    if (c % SIZE > 0) add(c - 1)
                    if (c % SIZE < SIZE - 1) add(c + 1)
                    if (c >= SIZE) add(c - SIZE)
                    if (c < SIZE * (SIZE - 1)) add(c + SIZE)
                }.filter { it !in e.tried(t) }
                if (nb.isNotEmpty()) return t to nb.random()
            }
        }
        val targets = e.alive.filter { it != shooter }
        if (targets.isEmpty()) return null
        val t = targets.random()
        val free = (0 until SIZE * SIZE).filter { it !in e.tried(t) }
        if (free.isEmpty()) return null
        return t to free.random()
    }

    private fun doFire(shooter: Int, target: Int, cell: Int) {
        val e = engine ?: return
        val o = e.fire(shooter, target, cell) ?: return
        if (o.eliminated) players[target]?.alive = false
        when (o.result) {
            "HIT" -> lastHit[shooter] = target to cell
            "SUNK" -> lastHit.remove(shooter)
        }
        broadcast(
            Msg(
                "RESULT", id = shooter, target = target, cell = cell, res = o.result,
                sunk = o.sunk, turn = o.next, winner = o.winner, secs = TURN_SECS, players = infos(),
            ),
        )
        if (o.winner != -1) {
            phase = "OVER"
            turnJob?.cancel()
        } else {
            scheduleTurn()
        }
    }

    private fun scheduleTurn() {
        val e = engine ?: return
        turnJob?.cancel()
        val cur = e.current
        val bot = players[cur]?.bot == true
        turnJob = scope.launch {
            delay(if (bot) 900L else TURN_SECS * 1000L)
            synchronized(lock) {
                if (engine === e && e.current == cur && phase == "BATTLE") {
                    pickShot(cur)?.let { doFire(cur, it.first, it.second) }
                }
            }
        }
    }

    private fun onLeave(id: Int) = synchronized(lock) {
        val p = players[id] ?: return@synchronized
        when (phase) {
            "LOBBY", "OVER" -> {
                players.remove(id)
                broadcast(Msg("LOBBY", players = infos()))
            }

            "PLACE" -> {
                players.remove(id)
                if (players.size < 2) {
                    phase = "LOBBY"
                    placeJob?.cancel()
                    broadcast(Msg("REMATCH", players = infos()))
                } else {
                    broadcast(Msg("LOBBY", players = infos()))
                    if (players.values.all { it.ready }) startBattle()
                }
            }

            "BATTLE" -> {
                p.alive = false
                val e = engine ?: return@synchronized
                val win = e.remove(id)
                broadcast(Msg("LEFT", id = id, turn = e.current, winner = win, players = infos()))
                if (win != -1) {
                    phase = "OVER"
                    turnJob?.cancel()
                } else {
                    scheduleTurn()
                }
            }
        }
    }
}
