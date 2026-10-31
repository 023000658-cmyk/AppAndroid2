package com.example.appandroid2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Las imágenes viven en res/drawable-nodpi (puzzle_atardecer.jpg y puzzle_cabana.jpg). */
private val JIGSAW_IMAGES = listOf(
    "🌅 Atardecer" to R.drawable.puzzle_atardecer,
    "🏔️ Cabaña" to R.drawable.puzzle_cabana
)

private fun jigsawBestKey(img: Int, n: Int) = "jigsaw_best_${img}_$n"

/**
 * Uniones entre piezas. h[r][c] es la unión de arriba de la pieza (r, c) y v[r][c] la de su izquierda.
 * +1 / -1 dicen cuál de las dos piezas tiene el saliente; 0 = borde recto del rompecabezas.
 */
private class JigsawEdges(val h: List<List<Int>>, val v: List<List<Int>>)

private fun makeJigsawEdges(n: Int): JigsawEdges {
    fun sign() = if (Random.nextBoolean()) 1 else -1
    val h = List(n + 1) { r -> List(n) { if (r == 0 || r == n) 0 else sign() } }
    val v = List(n) { List(n + 1) { c -> if (c == 0 || c == n) 0 else sign() } }
    return JigsawEdges(h, v)
}

/**
 * Dibuja una arista de pieza de rompecabezas, del punto (x0,y0) al (x1,y1).
 * (nx, ny) apunta hacia afuera de la pieza. bulge: +1 saliente, -1 hueco, 0 recta.
 */
private fun Path.jigsawEdge(
    x0: Float, y0: Float, x1: Float, y1: Float,
    nx: Float, ny: Float, bulge: Int, s: Float
) {
    if (bulge == 0) {
        lineTo(x1, y1)
        return
    }
    val dx = x1 - x0
    val dy = y1 - y0
    val len = hypot(dx, dy)
    val ux = dx / len
    val uy = dy / len
    val sg = bulge.toFloat()
    val c = len / 2f

    // l = distancia sobre la arista, w = altura hacia afuera (en unidades de s)
    fun px(l: Float, w: Float) = x0 + ux * l + nx * w * s * sg
    fun py(l: Float, w: Float) = y0 + uy * l + ny * w * s * sg

    cubicTo(
        px(0.2f * len, 0f), py(0.2f * len, 0f),
        px(c, -0.1f), py(c, -0.1f),
        px(c - 0.1f * s, 0.1f), py(c - 0.1f * s, 0.1f)
    )
    cubicTo(
        px(c - 0.2f * s, 0.3f), py(c - 0.2f * s, 0.3f),
        px(c + 0.2f * s, 0.3f), py(c + 0.2f * s, 0.3f),
        px(c + 0.1f * s, 0.1f), py(c + 0.1f * s, 0.1f)
    )
    cubicTo(
        px(c, -0.1f), py(c, -0.1f),
        px(0.8f * len, 0f), py(0.8f * len, 0f),
        x1, y1
    )
}

/** Contorno de la pieza (fila r, columna c), con su esquina superior izquierda en (0, 0). */
private fun jigsawPiecePath(r: Int, c: Int, e: JigsawEdges, pw: Float, ph: Float): Path {
    val s = min(pw, ph)
    val top = -e.h[r][c]
    val bottom = e.h[r + 1][c]
    val left = -e.v[r][c]
    val right = e.v[r][c + 1]
    return Path().apply {
        moveTo(0f, 0f)
        jigsawEdge(0f, 0f, pw, 0f, 0f, -1f, top, s)
        jigsawEdge(pw, 0f, pw, ph, 1f, 0f, right, s)
        jigsawEdge(pw, ph, 0f, ph, 0f, 1f, bottom, s)
        jigsawEdge(0f, ph, 0f, 0f, -1f, 0f, left, s)
        close()
    }
}

/** Dibuja una pieza con la parte de la imagen que le toca. imageOrigin = dónde cae la esquina de la imagen completa. */
private fun DrawScope.drawJigsawPiece(
    image: ImageBitmap,
    path: Path,
    pos: Offset,
    imageOrigin: Offset,
    lifted: Boolean
) {
    translate(left = pos.x, top = pos.y) {
        if (lifted) {
            translate(left = 4f, top = 6f) {
                drawPath(path, Color.Black.copy(alpha = 0.3f))
            }
        }
        clipPath(path) {
            drawImage(image, topLeft = imageOrigin)
        }
        drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(width = 2f))
    }
}

/**
 * Rompecabezas clásico con tus imágenes.
 *  - Arrastra las piezas hasta su lugar en el tablero: encajan solas al acercarlas.
 *  - Elige la imagen y la cantidad de piezas (3×3, 4×4 o 5×5).
 *  - La imagen de fondo se ve tenue como guía; "Ver imagen" la muestra más clara.
 *  - "Pista" coloca una pieza (con pistas ya no cuenta el récord).
 *  - Guarda tu mejor tiempo por imagen y tamaño.
 */
@Composable
fun JigsawGame(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculadora", Context.MODE_PRIVATE) }

    var img by remember { mutableIntStateOf(0) }
    var n by remember { mutableIntStateOf(3) }
    var gameId by remember { mutableIntStateOf(0) }
    var showImage by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    var newRecord by remember { mutableStateOf(false) }
    var hints by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt(jigsawBestKey(0, 3), 0)) }

    var edges by remember { mutableStateOf(makeJigsawEdges(3)) }
    var positions by remember { mutableStateOf(emptyList<Offset>()) }   // esquina de cada pieza suelta
    var placed by remember { mutableStateOf(emptyList<Boolean>()) }     // piezas ya encajadas
    var order by remember { mutableStateOf(emptyList<Int>()) }          // orden de dibujo (la última va encima)
    var dragging by remember { mutableIntStateOf(-1) }

    val bitmap: Bitmap = remember(img) {
        BitmapFactory.decodeResource(
            context.resources,
            JIGSAW_IMAGES[img].second,
            BitmapFactory.Options().apply { inScaled = false }
        )
    }

    fun newGame(newImg: Int = img, newN: Int = n) {
        img = newImg
        n = newN
        edges = makeJigsawEdges(newN)
        positions = emptyList()
        placed = emptyList()
        order = emptyList()
        started = false
        seconds = 0
        solved = false
        newRecord = false
        showImage = false
        hints = 0
        dragging = -1
        best = prefs.getInt(jigsawBestKey(newImg, newN), 0)
        gameId++
    }

    fun finishGame() {
        solved = true
        dragging = -1
        if (hints == 0) {
            val t = seconds.coerceAtLeast(1)
            if (best == 0 || t < best) {
                best = t
                newRecord = true
                prefs.edit().putInt(jigsawBestKey(img, n), t).apply()
            }
        }
    }

    // Cronómetro: empieza al mover la primera pieza
    LaunchedEffect(gameId, started, solved) {
        while (started && !solved) {
            delay(1000)
            seconds++
        }
    }

    val colors = MaterialTheme.colorScheme
    val time = "%d:%02d".format(seconds / 60, seconds % 60)
    val placedCount = placed.count { it }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Imagen y cantidad de piezas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JIGSAW_IMAGES.forEachIndexed { i, (name, _) ->
                FilterChip(
                    selected = img == i,
                    onClick = { newGame(newImg = i) },
                    label = { Text(name) }
                )
            }
            listOf(3, 4, 5).forEach { s ->
                FilterChip(
                    selected = n == s,
                    onClick = { newGame(newN = s) },
                    label = { Text("${s * s} piezas") }
                )
            }
        }

        // Marcador
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Piezas: $placedCount/${n * n}", fontSize = 15.sp)
            Text("⏱ $time", fontSize = 15.sp)
            Text(
                "🏆 ${if (best == 0) "—" else "%d:%02d".format(best / 60, best % 60)}",
                fontSize = 15.sp
            )
        }

        // Tablero y piezas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            val iw = bitmap.width.toFloat()
            val ih = bitmap.height.toFloat()

            // El tablero ocupa el ancho (o menos si la imagen es muy alta) y deja espacio abajo para las piezas
            val bw = min(wPx, hPx * 0.55f * iw / ih)
            val bh = bw * ih / iw
            val boardLeft = (wPx - bw) / 2f
            val boardTop = 4f
            val pw = bw / n
            val ph = bh / n

            val bwI = bw.roundToInt().coerceAtLeast(1)
            val bhI = bh.roundToInt().coerceAtLeast(1)
            val scaled: ImageBitmap = remember(img, bwI, bhI) {
                Bitmap.createScaledBitmap(bitmap, bwI, bhI, true).asImageBitmap()
            }
            val paths = remember(edges, n, bwI, bhI) {
                List(n * n) { i -> jigsawPiecePath(i / n, i % n, edges, pw, ph) }
            }

            // Reparte las piezas sueltas por la zona de abajo
            LaunchedEffect(gameId, wPx, hPx) {
                val total = n * n
                val trayTop = (boardTop + bh + 10f).coerceAtMost(hPx - ph)
                val trayBottom = (hPx - ph - 4f).coerceAtLeast(trayTop)
                positions = List(total) {
                    Offset(
                        Random.nextFloat() * (wPx - pw).coerceAtLeast(0f),
                        trayTop + Random.nextFloat() * (trayBottom - trayTop)
                    )
                }
                if (placed.size != total) placed = List(total) { false }
                if (order.size != total) order = List(total) { it }.shuffled()
            }

            fun targetOf(i: Int) = Offset(boardLeft + (i % n) * pw, boardTop + (i / n) * ph)

            fun finishDrag() {
                val i = dragging
                dragging = -1
                if (i < 0 || i >= positions.size) return
                val target = targetOf(i)
                val p = positions[i]
                if (hypot(p.x - target.x, p.y - target.y) < min(pw, ph) * 0.35f) {
                    placed = placed.toMutableList().also { it[i] = true }
                    if (placed.all { it }) finishGame()
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(n, bw, wPx) {
                        detectDragGestures(
                            onDragStart = { start ->
                                dragging = -1
                                if (!solved && positions.size == n * n && placed.size == n * n) {
                                    var hit = -1
                                    for (k in order.indices.reversed()) {
                                        val i = order[k]
                                        if (placed[i]) continue
                                        val p = positions[i]
                                        if (start.x >= p.x && start.x <= p.x + pw &&
                                            start.y >= p.y && start.y <= p.y + ph
                                        ) {
                                            hit = i
                                            break
                                        }
                                    }
                                    dragging = hit
                                    if (hit >= 0) {
                                        order = order.filter { it != hit } + hit
                                        started = true
                                    }
                                }
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() },
                            onDrag = { change, amount ->
                                val i = dragging
                                if (i >= 0 && i < positions.size) {
                                    change.consume()
                                    val moved = positions[i] + amount
                                    positions = positions.toMutableList().also {
                                        it[i] = Offset(
                                            moved.x.coerceIn(0f, (wPx - pw).coerceAtLeast(0f)),
                                            moved.y.coerceIn(0f, (hPx - ph).coerceAtLeast(0f))
                                        )
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Zona de piezas sueltas
                val trayY = boardTop + bh + 4f
                drawRect(
                    Color.Black.copy(alpha = 0.07f),
                    Offset(0f, trayY),
                    Size(wPx, (hPx - trayY).coerceAtLeast(0f))
                )

                // Tablero con la imagen guía
                drawRect(Color.Black.copy(alpha = 0.15f), Offset(boardLeft, boardTop), Size(bw, bh))
                drawImage(
                    scaled,
                    topLeft = Offset(boardLeft, boardTop),
                    alpha = if (solved) 1f else if (showImage) 0.85f else 0.2f
                )
                drawRect(
                    Color.White.copy(alpha = 0.6f),
                    Offset(boardLeft, boardTop),
                    Size(bw, bh),
                    style = Stroke(width = 2f)
                )

                if (!solved && positions.size == n * n && placed.size == n * n) {
                    // Piezas ya encajadas
                    for (i in 0 until n * n) {
                        if (placed[i]) {
                            drawJigsawPiece(
                                scaled, paths[i], targetOf(i),
                                Offset(-(i % n) * pw, -(i / n) * ph), false
                            )
                        }
                    }
                    // Piezas sueltas (la última de la lista queda encima)
                    for (i in order) {
                        if (!placed[i]) {
                            drawJigsawPiece(
                                scaled, paths[i], positions[i],
                                Offset(-(i % n) * pw, -(i / n) * ph), i == dragging
                            )
                        }
                    }
                }
            }
        }

        // Mensaje
        Text(
            text = when {
                solved && newRecord -> "🎉 ¡Completado en $time! ¡Nuevo récord!"
                solved -> "🎉 ¡Rompecabezas completado en $time!"
                else -> "Arrastra cada pieza hasta su lugar"
            },
            fontSize = 14.sp,
            color = if (solved) colors.primary else colors.onSurfaceVariant
        )

        // Botones
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = { newGame() },
                modifier = Modifier.weight(1f)
            ) { Text(if (solved) "Otra vez" else "🔀 Mezclar", fontSize = 13.sp, maxLines = 1) }
            FilledTonalButton(
                onClick = { showImage = !showImage },
                enabled = !solved,
                modifier = Modifier.weight(1f)
            ) { Text(if (showImage) "👁 Ocultar" else "👁 Ver imagen", fontSize = 13.sp, maxLines = 1) }
            FilledTonalButton(
                onClick = {
                    // Pista: encaja una pieza suelta al azar
                    val loose = placed.indices.filter { !placed[it] }
                    if (!solved && loose.isNotEmpty()) {
                        val pick = loose.random()
                        placed = placed.toMutableList().also { it[pick] = true }
                        hints++
                        started = true
                        if (placed.all { it }) finishGame()
                    }
                },
                enabled = !solved,
                modifier = Modifier.weight(1f)
            ) { Text("💡 Pista", fontSize = 13.sp, maxLines = 1) }
        }
    }
}