package com.example.appandroid2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Grafica y = f(x).
 *  - Arrastra con un dedo para mover la gráfica, pellizca para hacer zoom.
 *  - Marca con puntos los cruces con el eje X (las raíces visibles).
 */
@Composable
fun FunctionGraph(expression: String, modifier: Modifier = Modifier) {
    var scale by rememberSaveable { mutableFloatStateOf(50f) } // píxeles por unidad
    var cx by rememberSaveable { mutableFloatStateOf(0f) }      // centro (coordenada x)
    var cy by rememberSaveable { mutableFloatStateOf(0f) }      // centro (coordenada y)

    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    val valid = remember(expression) {
        expression.isNotBlank() && runCatching { ExpressionParser(expression, 0.0).parse() }.isSuccess
    }

    val gridColor = colors.outlineVariant.copy(alpha = 0.5f)
    val axisColor = colors.onSurface.copy(alpha = 0.7f)
    val curveColor = colors.primary
    val rootColor = colors.tertiary
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val rootStyle = TextStyle(fontSize = 11.sp, color = colors.tertiary)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(2f, 3000f)
                    cx -= pan.x / scale
                    cy += pan.y / scale
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val sc = scale
            val ox = cx
            val oy = cy

            fun toSx(x: Double): Float = (w / 2 + (x - ox) * sc).toFloat()
            fun toSy(y: Double): Float = (h / 2 - (y - oy) * sc).toFloat()

            val step = niceStep(sc)
            val xMin = ox - w / 2 / sc
            val xMax = ox + w / 2 / sc
            val yMin = oy - h / 2 / sc
            val yMax = oy + h / 2 / sc
            val axisY = toSy(0.0).coerceIn(0f, h - 16f)
            val axisX = toSx(0.0).coerceIn(0f, w - 44f)

            // Cuadrícula y marcas
            for (i in ceil(xMin / step).toLong()..floor(xMax / step).toLong()) {
                val gx = i * step
                val sx = toSx(gx)
                drawLine(gridColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1f)
                if (i != 0L) {
                    drawText(textMeasurer, formatNumber(gx), Offset(sx + 3f, axisY + 2f), labelStyle)
                }
            }
            for (j in ceil(yMin / step).toLong()..floor(yMax / step).toLong()) {
                val gy = j * step
                val sy = toSy(gy)
                drawLine(gridColor, Offset(0f, sy), Offset(w, sy), strokeWidth = 1f)
                if (j != 0L) {
                    drawText(textMeasurer, formatNumber(gy), Offset(axisX + 4f, sy + 2f), labelStyle)
                }
            }

            // Ejes
            val y0 = toSy(0.0)
            if (y0 in 0f..h) drawLine(axisColor, Offset(0f, y0), Offset(w, y0), strokeWidth = 2f)
            val x0 = toSx(0.0)
            if (x0 in 0f..w) drawLine(axisColor, Offset(x0, 0f), Offset(x0, h), strokeWidth = 2f)

            // Curva
            if (valid) {
                val path = Path()
                val roots = mutableListOf<Double>()
                var pen = false
                var prevSy = 0f
                var prevX = 0.0
                var prevY = Double.NaN
                var px = 0f
                while (px <= w) {
                    val x = ox + (px - w / 2) / sc.toDouble()
                    val y = evalAt(expression, x)
                    if (y.isFinite()) {
                        val sy = toSy(y)
                        if (abs(sy) > 50f * h) {
                            pen = false
                        } else {
                            val connected = pen && abs(sy - prevSy) <= h
                            if (connected) {
                                path.lineTo(px, sy)
                                if (prevY.isFinite() && prevY * y < 0 && roots.size < 12) {
                                    roots.add(prevX - prevY * (x - prevX) / (y - prevY))
                                }
                            } else {
                                path.moveTo(px, sy)
                            }
                            pen = true
                            prevSy = sy
                        }
                    } else {
                        pen = false
                    }
                    prevX = x
                    prevY = y
                    px += 2f
                }
                drawPath(
                    path = path,
                    color = curveColor,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Raíces (cruces con el eje X)
                for (r in roots) {
                    val center = Offset(toSx(r), toSy(0.0))
                    drawCircle(rootColor, radius = 6f, center = center)
                    drawText(
                        textMeasurer,
                        "x≈${formatNumber(r)}",
                        Offset(center.x + 8f, center.y - 20f),
                        rootStyle
                    )
                }
            }
        }

        if (expression.isBlank()) {
            Text(
                text = "Escribe una función de x\nEj: x^2-4  o  sin(x)",
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (!valid) {
            Text(
                text = "Expresión incompleta",
                color = colors.error,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GraphButton("+") { scale = (scale * 1.5f).coerceAtMost(3000f) }
            GraphButton("−") { scale = (scale / 1.5f).coerceAtLeast(2f) }
            GraphButton("Centrar") { scale = 50f; cx = 0f; cy = 0f }
        }
    }
}

@Composable
private fun GraphButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(38.dp),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Text(label, fontSize = 14.sp)
    }
}

private fun evalAt(expression: String, x: Double): Double =
    try {
        ExpressionParser(expression, x).parse()
    } catch (e: Exception) {
        Double.NaN
    }

/** Elige un paso de cuadrícula "bonito" (1, 2, 5 × 10^n) con ~70 px entre líneas. */
private fun niceStep(scale: Float): Double {
    val raw = 70.0 / scale
    val pow = 10.0.pow(floor(log10(raw)))
    val m = raw / pow
    val nice = when {
        m <= 1.0 -> 1.0
        m <= 2.0 -> 2.0
        m <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * pow
}