package com.friends.ships

import com.friends.ships.game.Engine
import com.friends.ships.game.FLEET
import com.friends.ships.game.isValidFleet
import com.friends.ships.game.randomFleet
import com.friends.ships.game.shipCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun fleetAt(rowStart: Int) = FLEET.mapIndexed { i, len -> shipCells(len, rowStart + i * 2, 0, true)!! }

    @Test fun shipOutsideGridIsRejected() {
        assertNull(shipCells(5, 0, 7, true))
        assertNull(shipCells(5, 7, 0, false))
        assertNotNull(shipCells(5, 0, 5, true))
    }

    @Test fun randomFleetsAreValid() {
        repeat(200) {
            assertTrue(isValidFleet(randomFleet()))
            assertTrue(isValidFleet(randomFleet(noTouch = true), noTouch = true))
        }
    }

    @Test fun overlapIsInvalid() {
        val f = fleetAt(0).toMutableList()
        f[1] = f[0]
        assertFalse(isValidFleet(f))
    }

    @Test fun fireResolution() {
        val fleets = mapOf(0 to fleetAt(0), 1 to fleetAt(0))
        val e = Engine(listOf(0, 1), fleets, extraTurn = false)
        val shooter = e.current
        val other = 1 - shooter
        val miss = e.fire(shooter, other, 99)!!
        assertEquals("MISS", miss.result)
        assertEquals(other, e.current)
        e.advance() // qaytib shooter'ga
        val destroyer = fleets[other]!!.last()
        val hit = e.fire(shooter, other, destroyer[0])!!
        assertEquals("HIT", hit.result)
        e.advance()
        val sunk = e.fire(shooter, other, destroyer[1])!!
        assertEquals("SUNK", sunk.result)
        assertEquals(destroyer, sunk.sunk)
    }

    @Test fun cannotFireTwiceOrOutOfTurn() {
        val fleets = mapOf(0 to fleetAt(0), 1 to fleetAt(0))
        val e = Engine(listOf(0, 1), fleets, extraTurn = true)
        val shooter = e.current
        val other = 1 - shooter
        assertNull(e.fire(other, shooter, 0))
        e.fire(shooter, other, 0) // 0-katak kema: hit, extra turn
        assertNull(e.fire(shooter, other, 0))
    }

    @Test fun extraTurnOnHitOnly() {
        val fleets = mapOf(0 to fleetAt(0), 1 to fleetAt(0))
        val e = Engine(listOf(0, 1), fleets, extraTurn = true)
        val s = e.current
        e.fire(s, 1 - s, 0)
        assertEquals(s, e.current)
        e.fire(s, 1 - s, 99)
        assertEquals(1 - s, e.current)
    }

    @Test fun eliminationAndWinner() {
        val fleets = mapOf(0 to fleetAt(0), 1 to fleetAt(0), 2 to fleetAt(0))
        val e = Engine(listOf(0, 1, 2), fleets, extraTurn = true)
        val shooter = e.current
        val victim = (shooter + 1) % 3
        var last: com.friends.ships.game.FireOutcome? = null
        fleets[victim]!!.flatten().forEach { last = e.fire(shooter, victim, it) }
        assertTrue(last!!.eliminated)
        assertFalse(e.isAlive(victim))
        assertEquals(-1, e.winner())
        // turn tartibida yo'q qilingan o'yinchi o'tkazib yuboriladi
        e.advance()
        assertTrue(e.current != victim)
        val third = e.alive.first { it != shooter }
        e.remove(third)
        assertEquals(shooter, e.winner())
    }
}
