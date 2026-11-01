package com.friends.ships.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.friends.ships.game.SIZE

private val Water = Color(0xFF0F3A66)
private val Line = Color(0xFF1E5A96)
private val ShipC = Color(0xFF90A4AE)

/**
 * 10x10 maydon. ships = ko'rinadigan kemalar (faqat o'zimizniki),
 * marks = cell -> MISS/HIT/SUNK, selected = nishon katak.
 */
@Composable
fun BoardView(
    modifier: Modifier = Modifier,
    ships: Set<Int> = emptySet(),
    marks: Map<Int, String> = emptyMap(),
    selected: Int = -1,
    onTap: ((Int) -> Unit)? = null,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(onTap) {
                if (onTap != null) {
                    detectTapGestures { p ->
                        val cs = size.width / SIZE.toFloat()
                        val c = (p.x / cs).toInt().coerceIn(0, SIZE - 1)
                        val r = (p.y / cs).toInt().coerceIn(0, SIZE - 1)
                        onTap(r * SIZE + c)
                    }
                }
            },
    ) {
        val cs = size.width / SIZE
        drawRect(Water)
        for (cell in 0 until SIZE * SIZE) {
            val x = (cell % SIZE) * cs
            val y = (cell / SIZE) * cs
            if (cell in ships) {
                drawRect(ShipC, Offset(x + 1, y + 1), Size(cs - 2, cs - 2))
            }
            when (marks[cell]) {
                "MISS" -> drawCircle(Color.White, cs * 0.12f, Offset(x + cs / 2, y + cs / 2))
                "HIT" -> drawCircle(Color(0xFFE53935), cs * 0.32f, Offset(x + cs / 2, y + cs / 2))
                "SUNK" -> {
                    drawRect(Color(0xFF7F0000), Offset(x + 1, y + 1), Size(cs - 2, cs - 2))
                    drawLine(Color.White, Offset(x + cs * 0.25f, y + cs * 0.25f), Offset(x + cs * 0.75f, y + cs * 0.75f), 3f)
                    drawLine(Color.White, Offset(x + cs * 0.75f, y + cs * 0.25f), Offset(x + cs * 0.25f, y + cs * 0.75f), 3f)
                }
            }
        }
        for (i in 0..SIZE) {
            drawLine(Line, Offset(i * cs, 0f), Offset(i * cs, size.height), 1.5f)
            drawLine(Line, Offset(0f, i * cs), Offset(size.width, i * cs), 1.5f)
        }
        if (selected in 0 until SIZE * SIZE) {
            drawRect(
                Color(0xFF26C6DA),
                Offset((selected % SIZE) * cs, (selected / SIZE) * cs),
                Size(cs, cs),
                style = Stroke(width = 5f),
            )
        }
    }
}
