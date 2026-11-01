package com.friends.ships.game

import kotlin.random.Random

const val SIZE = 10

/** Klassik (Hasbro): 5-4-3-3-2. */
val CLASSIC = listOf(5, 4, 3, 3, 2)

/** Rus "Морской бой": 1x4, 2x3, 3x2, 4x1; kemalar bir-biriga tegmaydi. */
val RUSSIAN = listOf(4, 3, 3, 2, 2, 2, 1, 1, 1, 1)

fun fleetSizes(type: String): List<Int> = if (type == "RUSSIAN") RUSSIAN else CLASSIC

/** Kema kataklari (index = qator * 10 + ustun) yoki joy yetmasa null. */
fun shipCells(len: Int, r: Int, c: Int, horizontal: Boolean): List<Int>? {
    val endR = if (horizontal) r else r + len - 1
    val endC = if (horizontal) c + len - 1 else c
    if (r < 0 || c < 0 || endR >= SIZE || endC >= SIZE) return null
    return (0 until len).map { if (horizontal) r * SIZE + c + it else (r + it) * SIZE + c }
}

/** Kema joyi: boshi (r, c), uzunligi va yo'nalishi. */
data class ShipPos(val len: Int, val r: Int, val c: Int, val h: Boolean) {
    fun cells(): List<Int>? = shipCells(len, r, c, h)
}

fun toShipPos(cells: List<Int>): ShipPos {
    val a = cells.sorted()
    return ShipPos(a.size, a[0] / SIZE, a[0] % SIZE, a.size == 1 || a[1] - a[0] == 1)
}

private fun isLine(s: List<Int>): Boolean {
    val c = s.sorted()
    val horiz = c.all { it / SIZE == c[0] / SIZE } && c.zipWithNext().all { (a, b) -> b - a == 1 }
    val vert = c.zipWithNext().all { (a, b) -> b - a == SIZE }
    return horiz || vert
}

fun touches(a: List<Int>, b: List<Int>): Boolean = a.any { x ->
    b.any { y -> Math.abs(x / SIZE - y / SIZE) <= 1 && Math.abs(x % SIZE - y % SIZE) <= 1 }
}

/** Kemani o'rab turgan 8 tomonlama qo'shni kataklar (kemaning o'zi chiqarilgan). */
fun around(ship: List<Int>): Set<Int> {
    val out = HashSet<Int>()
    for (x in ship) for (dr in -1..1) for (dc in -1..1) {
        val r = x / SIZE + dr
        val c = x % SIZE + dc
        if (r in 0 until SIZE && c in 0 until SIZE) out.add(r * SIZE + c)
    }
    out.removeAll(ship.toSet())
    return out
}

fun isValidFleet(fleet: List<List<Int>>, sizes: List<Int> = CLASSIC, noTouch: Boolean = false): Boolean {
    if (fleet.map { it.size }.sorted() != sizes.sorted()) return false
    val seen = HashSet<Int>()
    for (s in fleet) {
        if (s.any { it !in 0 until SIZE * SIZE }) return false
        if (!isLine(s)) return false
        for (c in s) if (!seen.add(c)) return false
    }
    if (noTouch) {
        for (i in fleet.indices) for (j in i + 1 until fleet.size) {
            if (touches(fleet[i], fleet[j])) return false
        }
    }
    return true
}

/** ships[idx] ni cand bilan almashtirsak, qolganlar bilan to'qnashmaydimi? */
fun canPlace(ships: List<ShipPos>, idx: Int, cand: ShipPos, noTouch: Boolean): Boolean {
    val cells = cand.cells() ?: return false
    for ((i, o) in ships.withIndex()) {
        if (i == idx) continue
        val oc = o.cells() ?: return false
        if (if (noTouch) touches(oc, cells) else oc.any { it in cells }) return false
    }
    return true
}

fun placementValid(ships: List<ShipPos>, noTouch: Boolean): Boolean =
    ships.indices.all { canPlace(ships, it, ships[it], noTouch) }

/** Tasodifiy flot; natija sizes tartibida qaytadi. */
fun randomFleet(
    sizes: List<Int> = CLASSIC,
    noTouch: Boolean = false,
    rnd: Random = Random.Default,
): List<List<Int>> {
    val byLen = sizes.indices.sortedByDescending { sizes[it] }
    while (true) {
        val res = arrayOfNulls<List<Int>>(sizes.size)
        var ok = true
        for (i in byLen) {
            var placed = false
            var tries = 0
            while (!placed && tries < 300) {
                tries++
                val cells = shipCells(sizes[i], rnd.nextInt(SIZE), rnd.nextInt(SIZE), rnd.nextBoolean()) ?: continue
                val clash = res.filterNotNull().any { f -> if (noTouch) touches(f, cells) else f.any { it in cells } }
                if (!clash) {
                    res[i] = cells
                    placed = true
                }
            }
            if (!placed) {
                ok = false
                break
            }
        }
        if (ok) return res.map { it!! }
    }
}

fun randomShipPositions(sizes: List<Int>, noTouch: Boolean): List<ShipPos> =
    randomFleet(sizes, noTouch).map { toShipPos(it) }

fun coordName(cell: Int): String = "${'A' + cell % SIZE}${cell / SIZE + 1}"

/**
 * Kemani joyida 90° aylantiradi. Sig'masa kemani surib ko'radi.
 * Hech qanday variant mos kelmasa null.
 */
fun rotated(ships: List<ShipPos>, idx: Int, noTouch: Boolean): ShipPos? {
    val s = ships[idx]
    if (s.len == 1) return null
    val h = !s.h
    for (k in 0 until s.len) {
        val cand = if (h) ShipPos(s.len, s.r, s.c - k, true) else ShipPos(s.len, s.r - k, s.c, false)
        if (canPlace(ships, idx, cand, noTouch)) return cand
    }
    return null
}
