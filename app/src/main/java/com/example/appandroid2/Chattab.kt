package com.example.appandroid2

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val API_KEY_PREF = "ai_api_key"

private val SUGGESTIONS = listOf(
    "Resuelve 2x+3=7",
    "x^2-5x+6=0",
    "(3+4)*2^2-5",
    "Derivada de x^3+2x",
    "20% de 150",
    "Explícame la fórmula general"
)

private fun cleanChatText(text: String): String =
    text.replace("**", "")
        .replace("`", "")
        .replace(Regex("(?m)^#+\\s*"), "")


@Composable
fun ChatTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculadora", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var apiKey by remember { mutableStateOf(prefs.getString(API_KEY_PREF, "") ?: "") }
    var showKeyDialog by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || loading) return
        if (apiKey.isBlank()) {
            // Sin clave de la API: responde el tutor que funciona sin internet
            messages.add(ChatMessage(fromUser = true, text = t))
            messages.add(ChatMessage(fromUser = false, text = OfflineTutor.answer(t)))
            input = ""
            return
        }
        messages.add(ChatMessage(fromUser = true, text = t))
        input = ""
        loading = true
        scope.launch {
            val result = MathTutorApi.ask(apiKey, messages.toList())
            result
                .onSuccess { messages.add(ChatMessage(fromUser = false, text = it)) }
                .onFailure {
                    messages.add(ChatMessage(fromUser = false, text = it.message ?: "Error", isError = true))
                }
            loading = false
        }
    }

    // Baja al último mensaje
    LaunchedEffect(messages.size, loading) {
        listState.animateScrollToItem(messages.size + if (loading) 1 else 0)
    }

    val welcome = if (apiKey.isBlank()) {
        "¡Hola! Soy tu asistente inteligente de matemáticas. Puedo resolver operaciones y " +
                "ecuaciones paso a paso, derivar e integrar polinomios, factorizar, sacar porcentajes y " +
                "explicarte temas básicos. Escribe tu duda o toca un ejemplo."
    } else {
        "¡Hola! Soy tu asistente inteligente de matemáticas. Pregúntame cómo resolver una ecuación, " +
                "qué significa una fórmula o pídeme ayuda paso a paso con un ejercicio."
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Encabezado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (apiKey.isBlank()) "¡Hola! Soy MATHIA" else "¡Hola! Soy MATHIA",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { messages.clear() }, enabled = messages.isNotEmpty() && !loading) {
                Text("🗑 Borrar", fontSize = 13.sp)
            }
        }

        // Mensajes
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { ChatBubble(ChatMessage(fromUser = false, text = welcome)) }
            items(messages) { m -> ChatBubble(m) }
            if (loading) {
                item {
                    Text(
                        "Pensando…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        // Preguntas de ejemplo
        if (messages.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUGGESTIONS.forEach { s ->
                    AssistChip(onClick = { send(s) }, label = { Text(s, fontSize = 13.sp) })
                }
            }
        }

        // Escribir
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe tu duda de matemáticas…") },
                maxLines = 3
            )
            Button(
                onClick = { send(input) },
                enabled = input.isNotBlank() && !loading
            ) { Text("Enviar") }
        }
    }

}

@Composable
private fun ChatBubble(m: ChatMessage) {
    val colors = MaterialTheme.colorScheme
    val bg = when {
        m.isError -> colors.errorContainer
        m.fromUser -> colors.primaryContainer
        else -> colors.surfaceVariant
    }
    val fg = when {
        m.isError -> colors.onErrorContainer
        m.fromUser -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        SelectionContainer {
            Text(
                text = cleanChatText(m.text),
                color = fg,
                fontSize = 15.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}