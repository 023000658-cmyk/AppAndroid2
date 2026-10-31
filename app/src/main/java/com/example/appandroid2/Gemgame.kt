package com.example.appandroid2

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private const val GEM_PROGRESS_KEY = "gems_level"

/**
 * Niveles. Leyenda:
 *  #  pared          .  piso            P  inicio del explorador
 *  G  gema           R  roca (se empuja)
 *  K  llave          D  puerta (gasta una llave)
 *  ^  pinchos        E  salida (se abre al juntar todas las gemas)
 */
private val GEM_LEVELS: List<List<String>> = listOf(
    listOf(
        "#########",
        "#P..G...#",
        "#.#.#.#.#",
        "#G..#..G#",
        "#.#.#.#.#",
        "#...G...#",
        "#.#.#.#.#",
        "#G.....E#",
        "#########"
    ),
    listOf(
        "#########",
        "#P..#..G#",
        "#.R.#.#.#",
        "#...^.#.#",
        "#.#.#.#.#",
        "#G..R..G#",
        "#.#...#.#",
        "#..G...E#",
        "#########"
    ),
    listOf(
        "#########",
        "#P..#G..#",
        "#.R.#.#.#",
        "#...#.#G#",
        "#.#.D.#.#",
        "#K..#...#",
        "#.#.#.R.#",
        "#G..^..E#",
        "#########"
    ),
    listOf(
        "#########",
        "#P.G#..G#",
        "#.R.#.R.#",
        "#...D...#",
        "#.#.#.#.#",
        "#K.R..^.#",
        "#.#.#.#.#",
        "#G...G.E#",
        "#########"
    ),
    listOf(
        "#########",
        "#P..R..G#",
        "#.#.#.#.#",
        "#G.^R...#",
        "#.#.#.#.#",
        "#K..D.R.#",
        "#.#.#.#.#",
        "#G..^.G.E",
        "#########"
    )
)

private data class GemState(val grid: List<String>, val px: Int, val py: Int, val keys: Int)

/** result: 0 = se movió, 1 = cayó en pinchos, 2 = nivel completo */
private data class GemMove(val state: GemState, val result: Int)

private fun loadGemLevel(index: Int): GemState {
    var px = 1
    var py = 1
    val grid = GEM_LEVELS[index].mapIndexed { y, row ->
        val x = row.indexOf('P')
        if (x >= 0) {
            px = x
            py = y
            row.replace('P', '.')
        } else {
            row
        }
    }
    return GemState(grid, px, py, 0)
}

private fun List<String>.tileAt(x: Int, y: Int): Char =
    if (y in indices && x in 0 until this[y].length) this[y][x] else ' '

private fun List<String>.withTile(x: Int, y: Int, c: Char): List<String> =
    mapIndexed { i, row -> if (i == y) row.substring(0, x) + c + row.substring(x + 1) else row }

/** Aplica un movimiento. Devuelve null si algo lo bloquea. */
private fun applyGemMove(s: GemState, dx: Int, dy: Int): GemMove? {
    val nx = s.px + dx
    val ny = s.py + dy
    val t = s.grid.tileAt(nx, ny)
    var g = s.grid
    var keys = s.keys

    when (t) {
        '#', ' ' -> return null
        'R' -> {
            // Empujar la roca: solo si detrás hay piso libre o pinchos (los tapa)
            val bx = nx + dx
            val by = ny + dy
            when (g.tileAt(bx, by)) {
                '.' -> g = g.withTile(bx, by, 'R')
                '^' -> g = g.withTile(bx, by, '.')
                else -> return null
            }
            g = g.withTile(nx, ny, '.')
        }
        'D' -> {
            if (keys == 0) return null
            keys--
            g = g.withTile(nx, ny, '.')
        }
        'K' -> {
            keys++
            g = g.withTile(nx, ny, '.')
        }
        'G' -> g = g.withTile(nx, ny, '.')
        'E' -> if (g.any { 'G' in it }) return null
        '^' -> return GemMove(GemState(g, nx, ny, keys), 1)
    }
    return GemMove(GemState(g, nx, ny, keys), if (t == 'E') 2 else 0)
}

/**
 * Cazagemas: un juego de explorar cuevas.
 *  - Junta todas las gemas y llega a la salida.
 *  - Empuja las rocas para abrir camino. Una roca empujada sobre unos pinchos los tapa.
 *  - Las llaves abren puertas (cada llave abre una).
 *  - Si pisas pinchos pierdes el intento: deshaz el paso o reinicia el nivel.
 *  - Avanzas de nivel y el progreso se guarda en el teléfono.
 */
@Composable
fun GemGame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculadora", Context.MODE_PRIVATE) }

    var level by remember {
        mutableIntStateOf(prefs.getInt(GEM_PROGRESS_KEY, 0).coerceIn(0, GEM_LEVELS.lastIndex))
    }
    var state by remember { mutableStateOf(loadGemLevel(level)) }
    var history by remember { mutableStateOf(emptyList<GemState>()) }
    var moves by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableIntStateOf(0) } // 0 = jugando, 1 = pinchos, 2 = nivel completo
    var facing by remember { mutableIntStateOf(1) }   // 1 = mira a la derecha, -1 = a la izquierda

    fun loadLevel(i: Int) {
        level = i
        state = loadGemLevel(i)
        history = emptyList()
        moves = 0
        outcome = 0
        facing = 1
    }

    fun step(dx: Int, dy: Int) {
        if (outcome != 0) return
        if (dx != 0) facing = dx
        val r = applyGemMove(state, dx, dy) ?: return
        history = history + state
        state = r.state
        moves++
        outcome = r.result
        if (r.result == 2) {
            val unlocked = (level + 1).coerceAtMost(GEM_LEVELS.lastIndex)
            if (unlocked > prefs.getInt(GEM_PROGRESS_KEY, 0)) {
                prefs.edit().putInt(GEM_PROGRESS_KEY, unlocked).apply()
            }
        }
    }

    fun undo() {
        if (history.isEmpty()) return
        state = history.last()
        history = history.dropLast(1)
        moves = (moves - 1).coerceAtLeast(0)
        outcome = 0
    }

    // Animaciones: el explorador se desliza y los brillos pulsan
    val animX by animateFloatAsState(state.px.toFloat(), tween(110), label = "x")
    val animY by animateFloatAsState(state.py.toFloat(), tween(110), label = "y")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val colors = MaterialTheme.colorScheme
    val gemsLeft = state.grid.sumOf { row -> row.count { it == 'G' } }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Información
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Nivel ${level + 1}/${GEM_LEVELS.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("💎 $gemsLeft", fontSize = 16.sp)
            Text("🔑 ${state.keys}", fontSize = 16.sp)
            Text("Pasos: $moves", fontSize = 16.sp)
        }
        Text(
            text = when {
                outcome == 1 -> "💥 ¡Pinchos! Deshaz el último paso o reinicia."
                gemsLeft == 0 -> "¡Ya tienes todas! Ve a la salida"
                else -> "Junta todas las gemas y llega a la salida"
            },
            fontSize = 14.sp,
            color = if (outcome == 1) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Tablero
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val rows = state.grid.size
            val cols = state.grid[0].length
            val cell = minOf(maxWidth / cols, maxHeight / rows)

            Box(
                modifier = Modifier
                    .size(cell * cols, cell * rows)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                if (abs(total.x) > 40f || abs(total.y) > 40f) {
                                    if (abs(total.x) > abs(total.y)) {
                                        step(if (total.x > 0) 1 else -1, 0)
                                    } else {
                                        step(0, if (total.y > 0) 1 else -1)
                                    }
                                    total = Offset.Zero
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = size.width / cols

                    // Suelo y paredes
                    for (y in 0 until rows) {
                        for (x in 0 until cols) {
                            if (state.grid[y][x] == '#') drawWallTile(x, y, c) else drawFloorTile(x, y, c)
                        }
                    }

                    // Objetos
                    for (y in 0 until rows) {
                        for (x in 0 until cols) {
                            when (state.grid[y][x]) {
                                'G' -> drawGem(x, y, c, pulse)
                                'R' -> drawBoulder(x, y, c)
                                'K' -> drawKey(x, y, c, pulse)
                                'D' -> drawDoor(x, y, c)
                                '^' -> drawSpikes(x, y, c)
                                'E' -> drawExit(x, y, c, gemsLeft == 0, pulse)
                            }
                        }
                    }

                    // Explorador
                    drawExplorer(animX, animY, c, facing, pulse, outcome == 1)

                    // Penumbra de cueva
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.maxDimension * 0.72f
                        )
                    )
                    if (outcome == 1) drawRect(Color.Red.copy(alpha = 0.22f))
                }

                // Nivel completo
                if (outcome == 2) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surface.copy(alpha = 0.9f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (level < GEM_LEVELS.lastIndex) {
                            Text("⭐ ¡Nivel completo!", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Lo lograste en $moves pasos",
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Button(onClick = { loadLevel(level + 1) }) {
                                Text("Siguiente nivel", fontSize = 18.sp)
                            }
                        } else {
                            Text(
                                text = "🏆 ¡Completaste\ntodos los niveles!",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Último nivel en $moves pasos",
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Button(onClick = { loadLevel(0) }) {
                                Text("Volver al nivel 1", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }

        // Deshacer / reiniciar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { undo() },
                enabled = history.isNotEmpty() && outcome != 2,
                modifier = Modifier.weight(1f)
            ) { Text("↶ Deshacer") }
            FilledTonalButton(
                onClick = { loadLevel(level) },
                modifier = Modifier.weight(1f)
            ) { Text("⟲ Reiniciar") }
        }

        // Flechas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GemKey("◀", Modifier.weight(1f)) { step(-1, 0) }
            GemKey("▲", Modifier.weight(1f)) { step(0, -1) }
            GemKey("▼", Modifier.weight(1f)) { step(0, 1) }
            GemKey("▶", Modifier.weight(1f)) { step(1, 0) }
        }
    }
}

@Composable
private fun GemKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(label, fontSize = 22.sp)
    }
}

// ───────────────────────── Gráficos ─────────────────────────

private val GOLD = Color(0xFFFFC107)

private fun poly(vararg p: Pair<Float, Float>): Path = Path().apply {
    moveTo(p[0].first, p[0].second)
    for (i in 1 until p.size) lineTo(p[i].first, p[i].second)
    close()
}

/** Suelo de tierra con piedritas en lugares fijos. */
private fun DrawScope.drawFloorTile(x: Int, y: Int, cell: Float) {
    val base = if ((x + y) % 2 == 0) Color(0xFF6D4C41) else Color(0xFF634338)
    drawRect(base, Offset(x * cell, y * cell), Size(cell, cell))
    val h = (x * 73856093) xor (y * 19349663)
    for (i in 0 until 3) {
        val a = (h shr (i * 5)) and 31
        val b = (h shr (i * 5 + 3)) and 31
        drawCircle(
            color = Color.Black.copy(alpha = 0.16f),
            radius = cell * 0.035f,
            center = Offset(
                x * cell + cell * (0.15f + 0.7f * (a / 31f)),
                y * cell + cell * (0.15f + 0.7f * (b / 31f))
            )
        )
    }
}

/** Pared de roca con luz arriba y grietas. */
private fun DrawScope.drawWallTile(x: Int, y: Int, cell: Float) {
    val left = x * cell
    val top = y * cell
    val pad = cell * 0.05f
    val inner = cell - 2 * pad
    drawRect(Color(0xFF263238), Offset(left, top), Size(cell, cell))
    drawRoundRect(Color(0xFF546E7A), Offset(left + pad, top + pad), Size(inner, inner), CornerRadius(cell * 0.18f))
    drawRoundRect(Color(0xFF78909C), Offset(left + pad, top + pad), Size(inner, inner * 0.42f), CornerRadius(cell * 0.18f))
    val h = (x * 31 + y * 17) % 3
    val crack = Color(0xFF263238)
    val sw = cell * 0.03f
    drawLine(crack, Offset(left + cell * (0.25f + 0.1f * h), top + cell * 0.55f), Offset(left + cell * (0.45f + 0.1f * h), top + cell * 0.72f), sw)
    drawLine(crack, Offset(left + cell * (0.45f + 0.1f * h), top + cell * 0.72f), Offset(left + cell * (0.5f + 0.1f * h), top + cell * 0.88f), sw)
}

/** Gema con facetas, sombra y destello que pulsa. El color cambia según la casilla. */
private fun DrawScope.drawGem(x: Int, y: Int, cell: Float, pulse: Float) {
    val (light, mid, dark) = when ((x * 7 + y * 3) % 3) {
        0 -> Triple(Color(0xFFB2EBF2), Color(0xFF26C6DA), Color(0xFF00838F))
        1 -> Triple(Color(0xFFF8BBD0), Color(0xFFEC407A), Color(0xFFAD1457))
        else -> Triple(Color(0xFFFFF59D), Color(0xFFFFCA28), Color(0xFFF57F17))
    }
    val cx = x * cell + cell / 2
    val cy = y * cell + cell * 0.52f

    drawCircle(mid.copy(alpha = 0.12f + 0.12f * pulse), cell * 0.42f, Offset(cx, cy))
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(cx - cell * 0.26f, cy + cell * 0.26f), Size(cell * 0.52f, cell * 0.1f))

    val w = cell * 0.6f
    val h = cell * 0.54f
    val l = cx - w / 2
    val t = cy - h / 2
    val crown = h * 0.36f

    drawPath(poly(l + w * 0.2f to t, cx to t, cx to t + crown, l to t + crown), light)
    drawPath(poly(cx to t, l + w * 0.8f to t, l + w to t + crown, cx to t + crown), mid.copy(alpha = 0.85f))
    drawPath(poly(l to t + crown, cx to t + crown, cx to t + h), mid)
    drawPath(poly(cx to t + crown, l + w to t + crown, cx to t + h), dark)
    drawPath(
        poly(l + w * 0.2f to t, l + w * 0.8f to t, l + w to t + crown, cx to t + h, l to t + crown),
        dark,
        style = Stroke(cell * 0.03f)
    )

    // Destello
    val sx = l + w * 0.3f
    val sy = t + crown * 0.5f
    val s = cell * 0.09f * (0.5f + pulse)
    drawLine(Color.White, Offset(sx - s, sy), Offset(sx + s, sy), cell * 0.025f)
    drawLine(Color.White, Offset(sx, sy - s), Offset(sx, sy + s), cell * 0.025f)
}

/** Roca redonda con luz y sombra. */
private fun DrawScope.drawBoulder(x: Int, y: Int, cell: Float) {
    val cx = x * cell + cell / 2
    val cy = y * cell + cell * 0.48f
    val r = cell * 0.4f
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - cell * 0.36f, cy + cell * 0.28f), Size(cell * 0.72f, cell * 0.16f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFBCAAA4), Color(0xFF8D6E63), Color(0xFF4E342E)),
            center = Offset(cx - r * 0.3f, cy - r * 0.35f),
            radius = r * 1.6f
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(Color(0xFF3E2723), r, Offset(cx, cy), style = Stroke(cell * 0.03f))
    drawLine(Color(0xFF3E2723).copy(alpha = 0.6f), Offset(cx + r * 0.1f, cy - r * 0.2f), Offset(cx + r * 0.4f, cy + r * 0.1f), cell * 0.03f)
    drawLine(Color(0xFF3E2723).copy(alpha = 0.6f), Offset(cx - r * 0.5f, cy + r * 0.25f), Offset(cx - r * 0.2f, cy + r * 0.5f), cell * 0.03f)
}

/** Llave dorada con aura. */
private fun DrawScope.drawKey(x: Int, y: Int, cell: Float, pulse: Float) {
    val cx = x * cell + cell / 2
    val cy = y * cell + cell / 2
    drawCircle(Color(0xFFFFEB3B).copy(alpha = 0.12f + 0.14f * pulse), cell * 0.42f, Offset(cx, cy))
    drawCircle(GOLD, cell * 0.14f, Offset(cx, cy - cell * 0.18f), style = Stroke(cell * 0.07f))
    drawRect(GOLD, Offset(cx - cell * 0.035f, cy - cell * 0.05f), Size(cell * 0.07f, cell * 0.34f))
    drawRect(GOLD, Offset(cx, cy + cell * 0.14f), Size(cell * 0.15f, cell * 0.06f))
    drawRect(GOLD, Offset(cx, cy + cell * 0.24f), Size(cell * 0.1f, cell * 0.06f))
}

/** Puerta de madera con candado. */
private fun DrawScope.drawDoor(x: Int, y: Int, cell: Float) {
    val l = x * cell + cell * 0.14f
    val t = y * cell + cell * 0.08f
    val w = cell * 0.72f
    val h = cell * 0.86f
    val pad = cell * 0.04f
    drawRoundRect(Color(0xFF37474F), Offset(l - pad, t - pad), Size(w + 2 * pad, h + pad), CornerRadius(w * 0.5f))
    drawRect(Color(0xFF37474F), Offset(l - pad, t + h * 0.5f), Size(w + 2 * pad, h * 0.5f))
    drawRoundRect(Color(0xFF8D6E63), Offset(l, t), Size(w, h), CornerRadius(w * 0.45f))
    drawRect(Color(0xFF8D6E63), Offset(l, t + h * 0.5f), Size(w, h * 0.5f))
    val plank = Color(0xFF5D4037)
    drawLine(plank, Offset(l + w / 3, t + h * 0.15f), Offset(l + w / 3, t + h), cell * 0.025f)
    drawLine(plank, Offset(l + 2 * w / 3, t + h * 0.15f), Offset(l + 2 * w / 3, t + h), cell * 0.025f)
    drawRect(plank, Offset(l, t + h * 0.45f), Size(w, cell * 0.04f))
    drawCircle(GOLD, cell * 0.075f, Offset(l + w * 0.72f, t + h * 0.62f))
    drawRect(Color(0xFF3E2723), Offset(l + w * 0.72f - cell * 0.012f, t + h * 0.62f - cell * 0.02f), Size(cell * 0.024f, cell * 0.06f))
}

/** Trampa de pinchos metálicos. */
private fun DrawScope.drawSpikes(x: Int, y: Int, cell: Float) {
    val left = x * cell
    val top = y * cell
    drawRoundRect(Color(0xFF263238), Offset(left + cell * 0.08f, top + cell * 0.62f), Size(cell * 0.84f, cell * 0.26f), CornerRadius(cell * 0.06f))
    val baseY = top + cell * 0.8f
    val tipY = top + cell * 0.2f
    for (i in 0 until 3) {
        val bx = left + cell * (0.12f + i * 0.29f)
        val bw = cell * 0.25f
        val mid = bx + bw / 2
        drawPath(poly(bx to baseY, mid to tipY, mid to baseY), Color(0xFFCFD8DC))
        drawPath(poly(mid to baseY, mid to tipY, bx + bw to baseY), Color(0xFF90A4AE))
        drawCircle(Color(0xFFE53935), cell * 0.03f, Offset(mid, tipY + cell * 0.03f))
    }
}

/** Salida: arco de piedra con candado; al abrirse brilla. */
private fun DrawScope.drawExit(x: Int, y: Int, cell: Float, open: Boolean, pulse: Float) {
    val cx = x * cell + cell / 2
    val l = x * cell + cell * 0.1f
    val t = y * cell + cell * 0.06f
    val w = cell * 0.8f
    val h = cell * 0.9f
    drawRoundRect(Color(0xFF607D8B), Offset(l, t), Size(w, h), CornerRadius(w * 0.5f))
    drawRect(Color(0xFF607D8B), Offset(l, t + h * 0.5f), Size(w, h * 0.5f))

    val il = l + cell * 0.07f
    val it = t + cell * 0.07f
    val iw = w - cell * 0.14f
    val ih = h - cell * 0.07f
    val cy = t + h * 0.55f
    if (open) {
        val brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFFFD54F), Color(0xFFFF8F00)),
            center = Offset(cx, cy),
            radius = cell * (0.45f + 0.08f * pulse)
        )
        drawRoundRect(brush, Offset(il, it), Size(iw, ih), CornerRadius(iw * 0.5f))
        drawRect(brush, Offset(il, it + ih * 0.5f), Size(iw, ih * 0.5f))
        val s = cell * 0.1f * (0.5f + pulse)
        drawLine(Color.White, Offset(cx - s, cy - cell * 0.1f), Offset(cx + s, cy - cell * 0.1f), cell * 0.025f)
        drawLine(Color.White, Offset(cx, cy - cell * 0.1f - s), Offset(cx, cy - cell * 0.1f + s), cell * 0.025f)
    } else {
        drawRoundRect(Color(0xFF1B2328), Offset(il, it), Size(iw, ih), CornerRadius(iw * 0.5f))
        drawRect(Color(0xFF1B2328), Offset(il, it + ih * 0.5f), Size(iw, ih * 0.5f))
        drawArc(GOLD, 180f, 180f, false, Offset(cx - cell * 0.08f, cy - cell * 0.22f), Size(cell * 0.16f, cell * 0.2f), style = Stroke(cell * 0.04f))
        drawRoundRect(GOLD, Offset(cx - cell * 0.13f, cy - cell * 0.08f), Size(cell * 0.26f, cell * 0.22f), CornerRadius(cell * 0.03f))
        drawCircle(Color(0xFF3E2723), cell * 0.025f, Offset(cx, cy + cell * 0.03f))
    }
}

/** Explorador con sombrero y mochila. Mira hacia donde camina y rebota suavemente. */
private fun DrawScope.drawExplorer(px: Float, py: Float, cell: Float, facing: Int, bob: Float, dead: Boolean) {
    val cx = px * cell + cell / 2
    val by = py * cell + cell * 0.58f - bob * cell * 0.02f

    drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - cell * 0.24f, py * cell + cell * 0.86f), Size(cell * 0.48f, cell * 0.12f))

    // Piernas
    val pants = Color(0xFF5D4037)
    drawRoundRect(pants, Offset(cx - cell * 0.14f, by + cell * 0.16f), Size(cell * 0.11f, cell * 0.16f), CornerRadius(cell * 0.03f))
    drawRoundRect(pants, Offset(cx + cell * 0.03f, by + cell * 0.16f), Size(cell * 0.11f, cell * 0.16f), CornerRadius(cell * 0.03f))
    // Mochila (del lado contrario al que mira)
    drawRoundRect(Color(0xFF6D4C41), Offset(cx - facing * cell * 0.2f - cell * 0.06f, by - cell * 0.02f), Size(cell * 0.12f, cell * 0.2f), CornerRadius(cell * 0.04f))
    // Cuerpo
    drawRoundRect(Color(0xFFFB8C00), Offset(cx - cell * 0.18f, by - cell * 0.06f), Size(cell * 0.36f, cell * 0.28f), CornerRadius(cell * 0.08f))
    // Cabeza
    drawCircle(Color(0xFFFFCC80), cell * 0.19f, Offset(cx, by - cell * 0.2f))

    // Ojos
    val ex = facing * cell * 0.04f
    val eyeY = by - cell * 0.14f
    val eye = Color(0xFF3E2723)
    if (dead) {
        for (dx in listOf(-0.07f, 0.07f)) {
            val ex0 = cx + cell * dx + ex
            drawLine(eye, Offset(ex0 - cell * 0.025f, eyeY - cell * 0.025f), Offset(ex0 + cell * 0.025f, eyeY + cell * 0.025f), cell * 0.02f)
            drawLine(eye, Offset(ex0 - cell * 0.025f, eyeY + cell * 0.025f), Offset(ex0 + cell * 0.025f, eyeY - cell * 0.025f), cell * 0.02f)
        }
    } else {
        drawCircle(eye, cell * 0.028f, Offset(cx - cell * 0.07f + ex, eyeY))
        drawCircle(eye, cell * 0.028f, Offset(cx + cell * 0.07f + ex, eyeY))
    }

    // Sombrero
    val hat = Color(0xFF8D6E63)
    drawArc(hat, 180f, 180f, true, Offset(cx - cell * 0.17f, by - cell * 0.5f), Size(cell * 0.34f, cell * 0.3f))
    drawOval(Color(0xFF795548), Offset(cx - cell * 0.27f, by - cell * 0.36f), Size(cell * 0.54f, cell * 0.12f))
    drawRect(Color(0xFFD84315), Offset(cx - cell * 0.17f, by - cell * 0.39f), Size(cell * 0.34f, cell * 0.04f))
}