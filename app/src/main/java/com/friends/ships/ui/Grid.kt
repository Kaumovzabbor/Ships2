package com.friends.ships.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.friends.ships.game.SIZE
import com.friends.ships.game.ShipPos
import com.friends.ships.game.canPlace
import kotlin.math.floor

private val Water = Color(0xFF0F3A66)
private val Line = Color(0xFF1E5A96)
private val ShipC = Color(0xFF90A4AE)
private val ShipEdge = Color(0xFFCFD8DC)
private val Good = Color(0xFF66BB6A)
private val Bad = Color(0xFFEF5350)
private val Teal = Color(0xFF26C6DA)

/** Maydon o'lchamlari: koordinata yozuvlari bo'lsa bir katak chekka qoladi. */
private class Geom(val width: Float, val labels: Boolean) {
    val cs = width / (SIZE + if (labels) 1 else 0)
    val o = if (labels) cs else 0f
    fun cellAt(p: Offset): Int? {
        val c = floor((p.x - o) / cs).toInt()
        val r = floor((p.y - o) / cs).toInt()
        return if (r in 0 until SIZE && c in 0 until SIZE) r * SIZE + c else null
    }
    fun x(c: Int) = o + c * cs
    fun y(r: Int) = o + r * cs
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberLabelPainter(): (DrawScope, Geom, Color) -> Unit {
    val tm = rememberTextMeasurer()
    return { scope, g, color ->
        if (g.labels) with(scope) {
            val style = TextStyle(color = color, fontSize = (g.cs * 0.42f).toSp())
            for (i in 0 until SIZE) {
                val letter = tm.measure(('A' + i).toString(), style)
                drawText(letter, topLeft = Offset(g.x(i) + (g.cs - letter.size.width) / 2f, (g.cs - letter.size.height) / 2f))
                val num = tm.measure((i + 1).toString(), style)
                drawText(num, topLeft = Offset((g.cs - num.size.width) / 2f, g.y(i) + (g.cs - num.size.height) / 2f))
            }
        }
    }
}

private fun DrawScope.drawWater(g: Geom) {
    drawRect(Water, Offset(g.o, g.o), Size(g.cs * SIZE, g.cs * SIZE))
    for (i in 0..SIZE) {
        drawLine(Line, Offset(g.x(i), g.o), Offset(g.x(i), g.o + g.cs * SIZE), 1.5f)
        drawLine(Line, Offset(g.o, g.y(i)), Offset(g.o + g.cs * SIZE, g.y(i)), 1.5f)
    }
}

private fun DrawScope.drawShip(g: Geom, p: ShipPos, fill: Color, edge: Color, alpha: Float = 1f) {
    val w = (if (p.h) p.len else 1) * g.cs
    val h = (if (p.h) 1 else p.len) * g.cs
    val pad = g.cs * 0.08f
    drawRoundRect(
        fill.copy(alpha = alpha), Offset(g.x(p.c) + pad, g.y(p.r) + pad), Size(w - 2 * pad, h - 2 * pad),
        CornerRadius(g.cs * 0.3f),
    )
    drawRoundRect(
        edge.copy(alpha = alpha), Offset(g.x(p.c) + pad, g.y(p.r) + pad), Size(w - 2 * pad, h - 2 * pad),
        CornerRadius(g.cs * 0.3f), style = Stroke(2.5f),
    )
}

/**
 * 10x10 maydon (otish uchun). ships = ko'rinadigan kemalar (faqat o'zimizniki),
 * marks = cell -> MISS/HIT/SUNK, selected = nishon katak, pulse = oxirgi o'q animatsiyasi.
 */
@Composable
fun BoardView(
    modifier: Modifier = Modifier,
    ships: List<List<Int>> = emptyList(),
    marks: Map<Int, String> = emptyMap(),
    selected: Int = -1,
    labels: Boolean = true,
    pulse: Pulse? = null,
    onTap: ((Int) -> Unit)? = null,
) {
    val painter = rememberLabelPainter()
    val anim = remember { Animatable(1f) }
    LaunchedEffect(pulse?.key) {
        if (pulse != null) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(650))
        }
    }
    val labelColor = Color(0xFF90CAF9)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(onTap, labels) {
                if (onTap != null) {
                    detectTapGestures { p -> Geom(size.width.toFloat(), labels).cellAt(p)?.let(onTap) }
                }
            },
    ) {
        val g = Geom(size.width, labels)
        painter(this, g, labelColor)
        drawWater(g)
        for (s in ships) toPos(s)?.let { drawShip(g, it, ShipC, ShipEdge) }
        for ((cell, kind) in marks) {
            val x = g.x(cell % SIZE)
            val y = g.y(cell / SIZE)
            val cx = x + g.cs / 2
            val cy = y + g.cs / 2
            when (kind) {
                "MISS" -> drawCircle(Color.White, g.cs * 0.12f, Offset(cx, cy))
                "HIT" -> {
                    drawCircle(Color(0xFFE53935), g.cs * 0.32f, Offset(cx, cy))
                    drawCircle(Color(0xFFFFCDD2), g.cs * 0.12f, Offset(cx, cy))
                }
                "SUNK" -> {
                    drawRect(Color(0xFF7F0000), Offset(x + 1, y + 1), Size(g.cs - 2, g.cs - 2))
                    drawLine(Color.White, Offset(x + g.cs * 0.25f, y + g.cs * 0.25f), Offset(x + g.cs * 0.75f, y + g.cs * 0.75f), 3f)
                    drawLine(Color.White, Offset(x + g.cs * 0.75f, y + g.cs * 0.25f), Offset(x + g.cs * 0.25f, y + g.cs * 0.75f), 3f)
                }
            }
        }
        if (pulse != null && pulse.cell in 0 until SIZE * SIZE && anim.value < 1f) {
            val t = anim.value
            val cx = g.x(pulse.cell % SIZE) + g.cs / 2
            val cy = g.y(pulse.cell / SIZE) + g.cs / 2
            val col = if (pulse.res == "MISS") Color(0xFF80DEEA) else Color(0xFFFF8A65)
            drawCircle(col.copy(alpha = 1f - t), g.cs * (0.3f + 1.4f * t), Offset(cx, cy), style = Stroke(4f))
            if (pulse.res == "SUNK") {
                drawCircle(Color(0xFFFF5252).copy(alpha = 0.5f * (1f - t)), g.cs * (0.5f + 2.2f * t), Offset(cx, cy))
            }
        }
        if (selected in 0 until SIZE * SIZE) {
            drawRect(
                Teal,
                Offset(g.x(selected % SIZE), g.y(selected / SIZE)),
                Size(g.cs, g.cs),
                style = Stroke(width = 5f),
            )
        }
    }
}

private fun toPos(cells: List<Int>): ShipPos? =
    if (cells.isEmpty()) null else com.friends.ships.game.toShipPos(cells)

/**
 * Kemalarni joylashtirish maydoni: kemani bosib turib sudrang (katakka yopishadi),
 * bir marta bossangiz aylanadi. Yaroqsiz joyga qo'ysangiz, kema qaytib o'z joyiga tushadi.
 */
@Composable
fun PlacementBoard(
    ships: List<ShipPos>,
    noTouch: Boolean,
    enabled: Boolean,
    onMove: (Int, ShipPos) -> Unit,
    onRotate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberLabelPainter()
    val cur by rememberUpdatedState(ships)
    val nt by rememberUpdatedState(noTouch)
    var dragIdx by remember { mutableStateOf(-1) }
    var cand by remember { mutableStateOf<ShipPos?>(null) }

    fun shipAt(cell: Int): Int = cur.indexOfFirst { s -> s.cells()?.contains(cell) == true }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures { p ->
                        Geom(size.width.toFloat(), true).cellAt(p)?.let { c ->
                            val i = shipAt(c)
                            if (i >= 0) onRotate(i)
                        }
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var pos = Offset.Zero
                var grabR = 0
                var grabC = 0
                detectDragGestures(
                    onDragStart = { p ->
                        val g = Geom(size.width.toFloat(), true)
                        val cell = g.cellAt(p)
                        val i = if (cell == null) -1 else shipAt(cell)
                        if (i >= 0) {
                            dragIdx = i
                            pos = p
                            val s = cur[i]
                            grabR = cell!! / SIZE - s.r
                            grabC = cell % SIZE - s.c
                            cand = s
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragIdx >= 0) {
                            change.consume()
                            pos += amount
                            val g = Geom(size.width.toFloat(), true)
                            val c = floor((pos.x - g.o) / g.cs).toInt()
                            val r = floor((pos.y - g.o) / g.cs).toInt()
                            cand = cur[dragIdx].copy(r = r - grabR, c = c - grabC)
                        }
                    },
                    onDragEnd = {
                        val i = dragIdx
                        val k = cand
                        if (i >= 0 && k != null && canPlace(cur, i, k, nt)) onMove(i, k)
                        dragIdx = -1
                        cand = null
                    },
                    onDragCancel = {
                        dragIdx = -1
                        cand = null
                    },
                )
            },
    ) {
        val g = Geom(size.width, true)
        painter(this, g, Color(0xFF90CAF9))
        drawWater(g)
        for ((i, s) in ships.withIndex()) {
            val lifted = i == dragIdx
            val ok = canPlace(ships, i, s, noTouch)
            drawShip(g, s, if (ok) ShipC else Bad, if (ok) ShipEdge else Color.White, if (lifted) 0.35f else 1f)
        }
        val k = cand
        if (dragIdx >= 0 && k != null) {
            val ok = canPlace(ships, dragIdx, k, noTouch)
            drawShip(g, k, if (ok) Good else Bad, Color.White, 0.9f)
        }
    }
}
