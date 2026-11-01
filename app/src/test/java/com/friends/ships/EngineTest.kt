package com.friends.ships

import com.friends.ships.game.CLASSIC
import com.friends.ships.game.Engine
import com.friends.ships.game.FireOutcome
import com.friends.ships.game.RUSSIAN
import com.friends.ships.game.ShipPos
import com.friends.ships.game.around
import com.friends.ships.game.canPlace
import com.friends.ships.game.isValidFleet
import com.friends.ships.game.placementValid
import com.friends.ships.game.randomFleet
import com.friends.ships.game.shipCells
import com.friends.ships.game.toShipPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun fleetAt() = CLASSIC.mapIndexed { i, len -> shipCells(len, i * 2, 0, true)!! }

    @Test fun shipOutsideGridIsRejected() {
        assertNull(shipCells(5, 0, 7, true))
        assertNull(shipCells(5, 7, 0, false))
        assertNotNull(shipCells(5, 0, 5, true))
    }

    @Test fun randomFleetsAreValid() {
        repeat(200) {
            assertTrue(isValidFleet(randomFleet()))
            assertTrue(isValidFleet(randomFleet(noTouch = true), noTouch = true))
            assertTrue(isValidFleet(randomFleet(RUSSIAN, true), RUSSIAN, true))
        }
    }

    @Test fun russianFleetHasTenShips() {
        assertEquals(20, RUSSIAN.sum())
        assertEquals(10, randomFleet(RUSSIAN, true).size)
    }

    @Test fun overlapIsInvalid() {
        val f = fleetAt().toMutableList()
        f[1] = f[0]
        assertFalse(isValidFleet(f))
    }

    @Test fun touchingShipsRejectedOnlyWhenNoTouch() {
        val f = fleetAt().toMutableList()
        f[1] = shipCells(4, 1, 0, true)!! // 0-qatorga yopishgan
        assertTrue(isValidFleet(f, CLASSIC, false))
        assertFalse(isValidFleet(f, CLASSIC, true))
    }

    @Test fun diagonalTouchIsNotAllowedWithNoTouch() {
        val a = shipCells(2, 0, 0, true)!!
        val b = shipCells(2, 1, 2, true)!! // (0,1) va (1,2) diagonal
        assertFalse(isValidFleet(listOf(a, b), listOf(2, 2), true))
        assertTrue(isValidFleet(listOf(a, b), listOf(2, 2), false))
    }

    @Test fun dragPlacementChecks() {
        val ships = CLASSIC.mapIndexed { i, len -> ShipPos(len, i * 2, 0, true) }
        assertTrue(placementValid(ships, true))
        assertFalse(canPlace(ships, 0, ShipPos(5, 0, 6, true), true)) // maydondan chiqadi
        assertFalse(canPlace(ships, 0, ShipPos(5, 1, 0, true), true)) // 2-qatordagi kemaga tegadi
        assertTrue(canPlace(ships, 0, ShipPos(5, 0, 5, true), true))
        assertEquals(ships[2], toShipPos(ships[2].cells()!!))
    }

    @Test fun fireResolution() {
        val fleets = mapOf(0 to fleetAt(), 1 to fleetAt())
        val e = Engine(listOf(0, 1), fleets, extraTurn = false)
        val shooter = e.current
        val other = 1 - shooter
        assertEquals("MISS", e.fire(shooter, other, 99)!!.result)
        assertEquals(other, e.current)
        e.advance()
        val destroyer = fleets[other]!!.last()
        assertEquals("HIT", e.fire(shooter, other, destroyer[0])!!.result)
        e.advance()
        val sunk = e.fire(shooter, other, destroyer[1])!!
        assertEquals("SUNK", sunk.result)
        assertEquals(destroyer, sunk.sunk)
        assertTrue(sunk.around.isEmpty()) // markAround o'chiq
    }

    @Test fun sunkShipMarksWaterAroundWhenEnabled() {
        val fleets = mapOf(0 to fleetAt(), 1 to fleetAt())
        val e = Engine(listOf(0, 1), fleets, extraTurn = true, markAround = true)
        val shooter = e.current
        val other = 1 - shooter
        val destroyer = fleets[other]!!.last() // 8-qator, 0 va 1-ustun
        e.fire(shooter, other, destroyer[0])
        val sunk = e.fire(shooter, other, destroyer[1])!!
        assertEquals(around(destroyer).size, sunk.around.size)
        assertEquals(7, sunk.around.size)
        // atrofdagi katakka qayta otib bo'lmaydi
        assertNull(e.fire(shooter, other, sunk.around.first()))
    }

    @Test fun cannotFireTwiceOrOutOfTurn() {
        val fleets = mapOf(0 to fleetAt(), 1 to fleetAt())
        val e = Engine(listOf(0, 1), fleets, extraTurn = true)
        val shooter = e.current
        val other = 1 - shooter
        assertNull(e.fire(other, shooter, 0))
        assertNotNull(e.fire(shooter, other, 0))
        assertNull(e.fire(shooter, other, 0))
        assertNull(e.fire(shooter, shooter, 5))
    }

    @Test fun extraTurnOnHitOnly() {
        val fleets = mapOf(0 to fleetAt(), 1 to fleetAt())
        val e = Engine(listOf(0, 1), fleets, extraTurn = true)
        val s = e.current
        e.fire(s, 1 - s, 0)
        assertEquals(s, e.current)
        e.fire(s, 1 - s, 99)
        assertEquals(1 - s, e.current)
    }

    @Test fun eliminationSkipAndWinner() {
        val fleets = mapOf(0 to fleetAt(), 1 to fleetAt(), 2 to fleetAt())
        val e = Engine(listOf(0, 1, 2), fleets, extraTurn = true)
        val shooter = e.current
        val victim = (shooter + 1) % 3
        var last: FireOutcome? = null
        fleets[victim]!!.flatten().forEach { last = e.fire(shooter, victim, it) }
        assertTrue(last!!.eliminated)
        assertFalse(e.isAlive(victim))
        assertEquals(-1, e.winner())
        e.advance()
        assertTrue(e.current != victim)
        val third = e.alive.first { it != shooter }
        e.remove(third)
        assertEquals(shooter, e.winner())
    }
}

class RotateTest {
    @Test fun rotateInPlaceAndShiftNearEdge() {
        val ships = listOf(ShipPos(4, 0, 0, true), ShipPos(3, 5, 7, true))
        // 4-kema (0,0) dan vertikal bo'ladi
        assertEquals(ShipPos(4, 0, 0, false), com.friends.ships.game.rotated(ships, 0, true))
        // 3-kema (5,7) gorizontal: vertikalga o'tadi
        assertEquals(ShipPos(3, 5, 7, false), com.friends.ships.game.rotated(ships, 1, true))
        // pastki chetda vertikal -> gorizontalga o'tishda siljiydi
        val edge = listOf(ShipPos(3, 7, 9, false))
        assertEquals(ShipPos(3, 7, 7, true), com.friends.ships.game.rotated(edge, 0, true))
        // bir kataklik kema aylanmaydi
        assertNull(com.friends.ships.game.rotated(listOf(ShipPos(1, 3, 3, true)), 0, true))
    }
}
