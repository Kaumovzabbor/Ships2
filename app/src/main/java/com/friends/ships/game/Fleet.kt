package com.friends.ships.game

import kotlin.random.Random

const val SIZE = 10
val FLEET = listOf(5, 4, 3, 3, 2)

/** Kema kataklari (index = qator * 10 + ustun) yoki joy yetmasa null. */
fun shipCells(len: Int, r: Int, c: Int, horizontal: Boolean): List<Int>? {
    val endR = if (horizontal) r else r + len - 1
    val endC = if (horizontal) c + len - 1 else c
    if (r < 0 || c < 0 || endR >= SIZE || endC >= SIZE) return null
    return (0 until len).map { if (horizontal) r * SIZE + c + it else (r + it) * SIZE + c }
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

fun isValidFleet(fleet: List<List<Int>>, noTouch: Boolean = false): Boolean {
    if (fleet.map { it.size }.sorted() != FLEET.sorted()) return false
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

/** Tasodifiy flot; ro'yxat tartibi FLEET tartibi bilan bir xil. */
fun randomFleet(rnd: Random = Random.Default, noTouch: Boolean = false): List<List<Int>> {
    while (true) {
        val fleet = ArrayList<List<Int>>()
        var ok = true
        for (len in FLEET) {
            var placed = false
            var tries = 0
            while (!placed && tries < 300) {
                tries++
                val cells = shipCells(len, rnd.nextInt(SIZE), rnd.nextInt(SIZE), rnd.nextBoolean()) ?: continue
                val clash = fleet.any { f -> if (noTouch) touches(f, cells) else f.any { it in cells } }
                if (!clash) {
                    fleet.add(cells)
                    placed = true
                }
            }
            if (!placed) {
                ok = false
                break
            }
        }
        if (ok) return fleet
    }
}

fun coordName(cell: Int): String = "${'A' + cell % SIZE}${cell / SIZE + 1}"
