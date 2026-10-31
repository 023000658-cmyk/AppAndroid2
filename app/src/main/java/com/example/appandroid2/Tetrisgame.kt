package com.example.appandroid2

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val BOARD_W = 10
private const val BOARD_H = 20
private const val TETRIS_BEST_KEY = "best_tetris"

/** Puntos por 0, 1, 2, 3 y 4 líneas a la vez (se multiplican por el nivel). */
private val LINE_POINTS = listOf(0, 100, 300, 500, 800)

private val PIECE_COLORS = listOf(
    Color(0xFF26C6DA), // barra
    Color(0xFFFDD835), // cuadro
    Color(0xFFAB47BC), // T
    Color(0xFF66BB6A), // S
    Color(0xFFEF5350), // Z
    Color(0xFF42A5F5), // J
    Color(0xFFFFA726)  // L
)

/** Las 7 formas, como 4 casillas (x, y) alrededor de un punto de giro. */
private val BASE_SHAPES: List<List<Pair<Int, Int>>> = listOf(
    listOf(-1 to 0, 0 to 0, 1 to 0, 2 to 0),    // barra
    listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),     // cuadro
    listOf(-1 to 0, 0 to 0, 1 to 0, 0 to -1),   // T
    listOf(0 to 0, 1 to 0, -1 to 1, 0 to 1),    // S
    listOf(-1 to 0, 0 to 0, 0 to 1, 1 to 1),    // Z
    listOf(-1 to -1, -1 to 0, 0 to 0, 1 to 0),  // J
    listOf(1 to -1, -1 to 0, 0 to 0, 1 to 0)    // L
)

private data class FallingPiece(val type: Int, val rot: Int, val x: Int, val y: Int)

/** Casillas de una pieza según su giro (0..3). El cuadro no gira. */
private fun pieceCells(type: Int, rot: Int): List<Pair<Int, Int>> {
    var c: List<Pair<Int, Int>> = BASE_SHAPES[type]
    if (type != 1) {
        repeat(rot % 4) { c = c.map { (x, y) -> -y to x } }
    }
    return c
}

/** ¿Cabe la pieza? Dentro de las paredes, sobre el piso y sin chocar con otras piezas. */
private fun pieceFits(grid: List<List<Int>>, p: FallingPiece): Boolean =
    pieceCells(p.type, p.rot).all { (dx, dy) ->
        val x = p.x + dx
        val y = p.y + dy
        x in 0 until BOARD_W && y < BOARD_H && (y < 0 || grid[y][x] == 0)
    }

private fun emptyGrid(): List<List<Int>> = List(BOARD_H) { List(BOARD_W) { 0 } }

/** Pega la pieza en el tablero (0 = vacío, 1..7 = color de la pieza). */
private fun mergePiece(grid: List<List<Int>>, p: FallingPiece): List<List<Int>> {
    val g = grid.map { it.toMutableList() }
    pieceCells(p.type, p.rot).forEach { (dx, dy) ->
        val x = p.x + dx
        val y = p.y + dy
        if (y in 0 until BOARD_H && x in 0 until BOARD_W) g[y][x] = p.type + 1
    }
    return g
}

/** Borra las filas completas y baja lo de arriba. Devuelve el tablero nuevo y cuántas borró. */
private fun clearLines(grid: List<List<Int>>): Pair<List<List<Int>>, Int> {
    val kept = grid.filter { row -> row.any { it == 0 } }
    val cleared = BOARD_H - kept.size
    val newRows = List(cleared) { List(BOARD_W) { 0 } }
    return (newRows + kept) to cleared
}

private fun DrawScope.drawBlock(color: Color, col: Int, row: Int, cell: Float) {
    val pad = cell * 0.06f
    drawRoundRect(
        color = color,
        topLeft = Offset(col * cell + pad, row * cell + pad),
        size = Size(cell - 2 * pad, cell - 2 * pad),
        cornerRadius = CornerRadius(cell * 0.2f)
    )
}

/**
 * Bloques que caen (tipo Tetris).
 *  - ◀ ▶ mueven, ⟳ gira, ▼ baja una casilla y ▼▼ la deja caer de golpe.
 *  - Completa filas para borrarlas. Más líneas = más nivel = más velocidad.
 *  - Pierdes cuando las piezas llegan hasta arriba. El récord se guarda en el teléfono.
 */
@Composable
fun TetrisGame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculadora", Context.MODE_PRIVATE) }

    var best by remember { mutableIntStateOf(prefs.getInt(TETRIS_BEST_KEY, 0)) }
    var state by remember { mutableIntStateOf(0) } // 0 = inicio, 1 = jugando, 2 = terminado
    var grid by remember { mutableStateOf(emptyGrid()) }
    var piece by remember { mutableStateOf(FallingPiece(0, 0, BOARD_W / 2 - 1, 1)) }
    var next by remember { mutableIntStateOf(Random.nextInt(7)) }
    var score by remember { mutableIntStateOf(0) }
    var lines by remember { mutableIntStateOf(0) }
    var newRecord by remember { mutableStateOf(false) }
    val level = lines / 10 + 1

    fun spawn(type: Int) = FallingPiece(type, 0, BOARD_W / 2 - 1, 1)

    fun endGame() {
        if (score > best) {
            best = score
            newRecord = true
            prefs.edit().putInt(TETRIS_BEST_KEY, score).apply()
        }
        state = 2
    }

    fun start() {
        grid = emptyGrid()
        piece = spawn(Random.nextInt(7))
        next = Random.nextInt(7)
        score = 0
        lines = 0
        newRecord = false
        state = 1
    }

    fun lockPiece() {
        val (cleared, n) = clearLines(mergePiece(grid, piece))
        grid = cleared
        if (n > 0) {
            score += LINE_POINTS[n] * (lines / 10 + 1)
            lines += n
        }
        val np = spawn(next)
        next = Random.nextInt(7)
        if (pieceFits(grid, np)) piece = np else endGame()
    }

    fun tryMove(dx: Int, dy: Int): Boolean {
        if (state != 1) return false
        val moved = piece.copy(x = piece.x + dx, y = piece.y + dy)
        if (pieceFits(grid, moved)) {
            piece = moved
            return true
        }
        return false
    }

    fun rotate() {
        if (state != 1) return
        // Si al girar choca con una pared, prueba empujarla un poco hacia los lados
        for (kick in listOf(0, -1, 1, -2, 2)) {
            val r = piece.copy(rot = (piece.rot + 1) % 4, x = piece.x + kick)
            if (pieceFits(grid, r)) {
                piece = r
                return
            }
        }
    }

    fun softDrop() {
        if (state != 1) return
        if (tryMove(0, 1)) score += 1 else lockPiece()
    }

    fun hardDrop() {
        if (state != 1) return
        while (tryMove(0, 1)) score += 2
        lockPiece()
    }

    // Caída automática: más rápida con cada nivel
    LaunchedEffect(state) {
        while (state == 1) {
            delay((700L - (lines / 10) * 60L).coerceAtLeast(120L))
            if (state == 1 && !tryMove(0, 1)) lockPiece()
        }
    }

    val colors = MaterialTheme.colorScheme
    val checker = colors.onSurfaceVariant.copy(alpha = 0.07f)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // Tablero
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(BOARD_W.toFloat() / BOARD_H)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = size.width / BOARD_W
                    for (r in 0 until BOARD_H) {
                        for (c in 0 until BOARD_W) {
                            val v = grid[r][c]
                            if (v != 0) {
                                drawBlock(PIECE_COLORS[v - 1], c, r, cell)
                            } else if ((r + c) % 2 == 0) {
                                drawRect(checker, Offset(c * cell, r * cell), Size(cell, cell))
                            }
                        }
                    }
                    if (state == 1) {
                        pieceCells(piece.type, piece.rot).forEach { (dx, dy) ->
                            val y = piece.y + dy
                            if (y >= 0) drawBlock(PIECE_COLORS[piece.type], piece.x + dx, y, cell)
                        }
                    }
                }

                // Pantalla de inicio / fin
                if (state != 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surface.copy(alpha = 0.88f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state == 0) {
                            Text("🧱 Tetris", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Completa filas\npara borrarlas.",
                                textAlign = TextAlign.Center,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else {
                            Text("Fin del juego", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$score",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = if (newRecord) "🎉 ¡Nuevo récord!" else "🏆 $best",
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(onClick = { start() }) {
                            Text(if (state == 0) "Jugar" else "Otra vez", fontSize = 16.sp)
                        }
                    }
                }
            }

            // Panel lateral
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Siguiente", fontSize = 13.sp, color = colors.onSurfaceVariant)
                Canvas(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant)
                ) {
                    val cell = size.width / 4
                    pieceCells(next, 0).forEach { (dx, dy) ->
                        drawBlock(PIECE_COLORS[next], dx + 1, dy + 1, cell)
                    }
                }
                StatText("Puntos", "$score", colors.primary)
                StatText("Líneas", "$lines", colors.onSurface)
                StatText("Nivel", "$level", colors.onSurface)
                StatText("🏆 Récord", "$best", colors.onSurface)
            }
        }

        // Controles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TetrisKey("◀", Modifier.weight(1f)) { tryMove(-1, 0) }
            TetrisKey("▶", Modifier.weight(1f)) { tryMove(1, 0) }
            TetrisKey("⟳", Modifier.weight(1f)) { rotate() }
            TetrisKey("▼", Modifier.weight(1f)) { softDrop() }
            TetrisKey("▼▼", Modifier.weight(1f)) { hardDrop() }
        }
    }
}

@Composable
private fun StatText(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TetrisKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 20.sp)
    }
}