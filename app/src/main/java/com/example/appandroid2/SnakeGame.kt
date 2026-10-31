package com.example.appandroid2

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val COLS = 15
private const val ROWS = 15
private const val SNAKE_BEST_KEY = "best_snake"

/** Una casilla del tablero (también se usa como dirección: (1,0) = derecha, etc.). */
private data class Cell(val x: Int, val y: Int)

private val UP = Cell(0, -1)
private val DOWN = Cell(0, 1)
private val LEFT = Cell(-1, 0)
private val RIGHT = Cell(1, 0)

/**
 * La viborita (Snake).
 *  - Desliza el dedo sobre el tablero o usa las flechas para moverte.
 *  - Cada manzana suma 1 punto y la víbora se va haciendo más rápida.
 *  - Pierdes si chocas con la pared o con tu propio cuerpo.
 *  - El récord se guarda en el teléfono.
 */
@Composable
fun SnakeGame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculadora", Context.MODE_PRIVATE) }

    var best by remember { mutableIntStateOf(prefs.getInt(SNAKE_BEST_KEY, 0)) }
    var state by remember { mutableIntStateOf(0) } // 0 = inicio, 1 = jugando, 2 = terminado
    var snake by remember { mutableStateOf(initialSnake()) }
    var dir by remember { mutableStateOf(RIGHT) }     // dirección con la que se movió por última vez
    var pending by remember { mutableStateOf(RIGHT) } // dirección pedida para el siguiente paso
    var food by remember { mutableStateOf(Cell(11, 7)) }
    var score by remember { mutableIntStateOf(0) }
    var newRecord by remember { mutableStateOf(false) }

    fun start() {
        val s = initialSnake()
        snake = s
        dir = RIGHT
        pending = RIGHT
        food = randomFood(s)
        score = 0
        newRecord = false
        state = 1
    }

    // Evita dar la vuelta en U sobre sí misma
    fun turn(d: Cell) {
        if (d.x + dir.x != 0 || d.y + dir.y != 0) pending = d
    }

    // Ciclo del juego: un paso cada cierto tiempo, cada vez más rápido
    LaunchedEffect(state) {
        if (state == 1) {
            while (true) {
                delay((170L - score * 4L).coerceAtLeast(70L))
                dir = pending
                val head = snake.first()
                val next = Cell(head.x + dir.x, head.y + dir.y)
                val eats = next == food
                val body = if (eats) snake else snake.dropLast(1)

                val crashed = next.x !in 0 until COLS || next.y !in 0 until ROWS || next in body
                if (crashed) {
                    if (score > best) {
                        best = score
                        newRecord = true
                        prefs.edit().putInt(SNAKE_BEST_KEY, score).apply()
                    }
                    state = 2
                    break
                }

                snake = listOf(next) + body
                if (eats) {
                    score++
                    food = randomFood(snake)
                }
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    val boardColor = colors.surfaceVariant
    val checkerColor = colors.onSurfaceVariant.copy(alpha = 0.08f)
    val headColor = colors.primary
    val bodyColor = colors.primary.copy(alpha = 0.7f)
    val foodColor = colors.error

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Puntos: $score", fontSize = 20.sp, color = colors.primary)
            Text("🏆 Récord: $best", fontSize = 20.sp)
        }

        // Tablero
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(boardColor)
                    .pointerInput(Unit) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                if (abs(total.x) > 30f || abs(total.y) > 30f) {
                                    if (abs(total.x) > abs(total.y)) {
                                        turn(if (total.x > 0) RIGHT else LEFT)
                                    } else {
                                        turn(if (total.y > 0) DOWN else UP)
                                    }
                                    total = Offset.Zero
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = size.width / COLS
                    val pad = cell * 0.08f

                    // Fondo de cuadros
                    for (x in 0 until COLS) {
                        for (y in 0 until ROWS) {
                            if ((x + y) % 2 == 0) {
                                drawRect(checkerColor, Offset(x * cell, y * cell), Size(cell, cell))
                            }
                        }
                    }

                    // Manzana
                    drawCircle(
                        color = foodColor,
                        radius = cell * 0.38f,
                        center = Offset(food.x * cell + cell / 2, food.y * cell + cell / 2)
                    )

                    // Víbora
                    snake.forEachIndexed { i, c ->
                        drawRoundRect(
                            color = if (i == 0) headColor else bodyColor,
                            topLeft = Offset(c.x * cell + pad, c.y * cell + pad),
                            size = Size(cell - 2 * pad, cell - 2 * pad),
                            cornerRadius = CornerRadius(cell * 0.3f)
                        )
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
                            Text("🐍 Viborita", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Come manzanas y no choques.\nDesliza el dedo sobre el tablero\no usa las flechas.",
                                textAlign = TextAlign.Center,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        } else {
                            Text("Fin del juego", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$score",
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = if (newRecord) "🎉 ¡Nuevo récord!" else "🏆 Récord: $best",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(onClick = { start() }) {
                            Text(
                                text = if (state == 0) "Jugar" else "Jugar de nuevo",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Flechas
        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DirKey("▲") { turn(UP) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DirKey("◀") { turn(LEFT) }
                DirKey("▼") { turn(DOWN) }
                DirKey("▶") { turn(RIGHT) }
            }
        }
    }
}

@Composable
private fun DirKey(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(88.dp)
            .height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(label, fontSize = 22.sp)
    }
}

private fun initialSnake(): List<Cell> = listOf(Cell(7, 7), Cell(6, 7), Cell(5, 7))

/** Elige una casilla libre al azar para la manzana. */
private fun randomFood(snake: List<Cell>): Cell {
    val free = ArrayList<Cell>()
    for (x in 0 until COLS) {
        for (y in 0 until ROWS) {
            val c = Cell(x, y)
            if (c !in snake) free.add(c)
        }
    }
    return if (free.isEmpty()) Cell(-1, -1) else free.random()
}