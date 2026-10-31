package com.example.appandroid2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * El gato (tres en raya).
 *  - "Vs. máquina": juegas con X y la máquina con O.
 *  - "2 jugadores": se turnan en el mismo teléfono.
 *  - Lleva la cuenta de victorias y empates mientras estés en esta pantalla.
 */
@Composable
fun TicTacToeGame(modifier: Modifier = Modifier) {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var xTurn by remember { mutableStateOf(true) }
    var winLine by remember { mutableStateOf<List<Int>?>(null) }
    var draw by remember { mutableStateOf(false) }
    var vsComputer by remember { mutableStateOf(true) }
    var winsX by remember { mutableIntStateOf(0) }
    var winsO by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }

    val over = winLine != null || draw

    fun newRound() {
        board = List(9) { "" }
        xTurn = true
        winLine = null
        draw = false
    }

    fun resetAll(computer: Boolean) {
        vsComputer = computer
        winsX = 0
        winsO = 0
        draws = 0
        newRound()
    }

    fun place(i: Int) {
        if (winLine != null || draw || board[i].isNotEmpty()) return
        val mark = if (xTurn) "X" else "O"
        val newBoard = board.toMutableList().also { it[i] = mark }
        board = newBoard
        val line = findWin(newBoard)
        when {
            line != null -> {
                winLine = line
                if (xTurn) winsX++ else winsO++
            }
            newBoard.all { it.isNotEmpty() } -> {
                draw = true
                draws++
            }
            else -> xTurn = !xTurn
        }
    }

    // Turno de la máquina
    LaunchedEffect(xTurn, vsComputer, over) {
        if (vsComputer && !xTurn && !over) {
            delay(450)
            place(aiMove(board))
        }
    }

    val colors = MaterialTheme.colorScheme
    val wl = winLine
    val status = when {
        wl != null -> {
            val w = board[wl[0]]
            if (vsComputer) {
                if (w == "X") "🎉 ¡Ganaste!" else "🤖 Ganó la máquina"
            } else {
                "🎉 ¡Ganó $w!"
            }
        }
        draw -> "🤝 ¡Empate!"
        vsComputer -> if (xTurn) "Tu turno (X)" else "Pensando…"
        else -> "Turno de ${if (xTurn) "X" else "O"}"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modo de juego
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vsComputer,
                onClick = { resetAll(true) },
                label = { Text("Vs. máquina") }
            )
            FilterChip(
                selected = !vsComputer,
                onClick = { resetAll(false) },
                label = { Text("2 jugadores") }
            )
        }

        // Marcador
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScoreBox(if (vsComputer) "Tú (X)" else "X", winsX, colors.primary)
            ScoreBox("Empates", draws, colors.onSurfaceVariant)
            ScoreBox(if (vsComputer) "Máquina (O)" else "O", winsO, colors.tertiary)
        }

        Text(status, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // Tablero
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.aspectRatio(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until 3) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in 0 until 3) {
                            val i = r * 3 + c
                            val mark = board[i]
                            val highlighted = wl != null && i in wl
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (highlighted) colors.primaryContainer
                                        else colors.surfaceVariant
                                    )
                                    .clickable {
                                        // No deja tocar mientras piensa la máquina
                                        if (!(vsComputer && !xTurn)) place(i)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mark,
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (mark == "X") colors.primary else colors.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { newRound() },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = if (over) "Jugar otra vez" else "Reiniciar",
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun ScoreBox(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private val LINES = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // filas
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // columnas
    listOf(0, 4, 8), listOf(2, 4, 6)                   // diagonales
)

/** Devuelve la línea ganadora, o null si todavía nadie gana. */
private fun findWin(b: List<String>): List<Int>? =
    LINES.firstOrNull { l ->
        b[l[0]].isNotEmpty() && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]
    }

/** Movimiento de la máquina (O): gana si puede, bloquea a X, y si no prefiere centro y esquinas. */
private fun aiMove(b: List<String>): Int {
    val empty = b.indices.filter { b[it].isEmpty() }

    fun winningMove(mark: String): Int? = empty.firstOrNull { i ->
        val t = b.toMutableList()
        t[i] = mark
        findWin(t) != null
    }

    winningMove("O")?.let { return it }
    winningMove("X")?.let { return it }
    if (4 in empty) return 4
    val corners = listOf(0, 2, 6, 8).filter { it in empty }
    if (corners.isNotEmpty()) return corners.random()
    return empty.random()
}