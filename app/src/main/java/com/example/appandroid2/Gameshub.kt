package com.example.appandroid2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pestaña "Juego": un selector arriba para elegir entre los juegos. */
@Composable
fun GamesHub(modifier: Modifier = Modifier) {
    var game by rememberSaveable { mutableIntStateOf(0) } // 0 = Viborita, 1 = Gato, 2 = Tetris, 3 = Gemas, 4 = Rompecabezas

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = game == 0,
                onClick = { game = 0 },
                label = { Text("🐍 Viborita") }
            )
            FilterChip(
                selected = game == 1,
                onClick = { game = 1 },
                label = { Text("❌⭕ Gato") }
            )
            FilterChip(
                selected = game == 2,
                onClick = { game = 2 },
                label = { Text("🧱 Tetris") }
            )
            FilterChip(
                selected = game == 3,
                onClick = { game = 3 },
                label = { Text("💎 Gemas") }
            )
            FilterChip(
                selected = game == 4,
                onClick = { game = 4 },
                label = { Text("🧩 Rompecabezas") }
            )
        }

        val gameModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        when (game) {
            0 -> SnakeGame(gameModifier)
            1 -> TicTacToeGame(gameModifier)
            2 -> TetrisGame(gameModifier)
            3 -> GemGame(gameModifier)
            else -> JigsawGame(gameModifier)
        }
    }
}