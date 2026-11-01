package com.friends.ships.game

class FireOutcome(
    val result: String, // MISS, HIT, SUNK
    val sunk: List<Int>,
    val around: List<Int>, // SUNK bo'lsa va markAround yoqilgan bo'lsa: kema atrofidagi suv kataklari
    val next: Int,
    val winner: Int,
    val eliminated: Boolean,
)

/** Sof Kotlin o'yin qoidalari: Android'ga bog'liq emas. */
class Engine(
    val order: List<Int>,
    private val fleets: Map<Int, List<List<Int>>>,
    private val extraTurn: Boolean = true,
    private val markAround: Boolean = false,
) {
    private val shots: Map<Int, MutableSet<Int>> = order.associateWith { HashSet<Int>() }
    private val aliveList = order.toMutableList()

    var current: Int = order.random()
        private set

    val alive: List<Int> get() = aliveList.toList()

    fun isAlive(id: Int) = id in aliveList

    fun tried(target: Int): Set<Int> = shots[target] ?: emptySet()

    fun winner(): Int = if (aliveList.size == 1) aliveList[0] else -1

    fun fire(shooter: Int, target: Int, cell: Int): FireOutcome? {
        if (aliveList.size < 2) return null
        if (shooter != current || target == shooter || target !in aliveList) return null
        if (cell !in 0 until SIZE * SIZE) return null
        val got = shots.getValue(target)
        if (!got.add(cell)) return null

        val ship = fleets.getValue(target).firstOrNull { cell in it }
        var res = "MISS"
        var sunk = emptyList<Int>()
        var ring = emptyList<Int>()
        var eliminated = false
        if (ship != null) {
            if (ship.all { it in got }) {
                res = "SUNK"
                sunk = ship
                if (markAround) {
                    ring = around(ship).filter { it !in got }
                    got.addAll(ring)
                }
                if (fleets.getValue(target).all { s -> s.all { it in got } }) {
                    eliminated = true
                    aliveList.remove(target)
                }
            } else {
                res = "HIT"
            }
        }
        val win = winner()
        if (win == -1 && !(extraTurn && ship != null)) advance()
        return FireOutcome(res, sunk, ring, current, win, eliminated)
    }

    fun advance() {
        if (aliveList.size < 2) return
        var i = order.indexOf(current)
        repeat(order.size) {
            i = (i + 1) % order.size
            if (order[i] in aliveList) {
                current = order[i]
                return
            }
        }
    }

    /** O'yinchi chiqib ketsa; g'olib id yoki -1. */
    fun remove(id: Int): Int {
        val wasCurrent = current == id
        aliveList.remove(id)
        if (wasCurrent) advance()
        return winner()
    }
}
