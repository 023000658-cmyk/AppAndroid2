package com.example.appandroid2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Un mensaje del chat. isError = aviso de la app (no se manda a la IA). */
data class ChatMessage(val fromUser: Boolean, val text: String, val isError: Boolean = false)

/**
 * Cliente de la API de Claude (https://docs.claude.com) para el tutor de matemáticas.
 * Usa solo HttpURLConnection, así que no necesita librerías extra.
 */
object MathTutorApi {
    /** Modelo rápido y barato. Puedes cambiarlo, por ejemplo por "claude-sonnet-5-5" para respuestas más detalladas. */
    const val MODEL = "claude-haiku-5-5"

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"

    private const val SYSTEM_PROMPT =
        "Eres un tutor de matemáticas amable dentro de una app de calculadora. " +
                "Responde siempre en español, de forma clara y breve. " +
                "Explica paso a paso, sin saltarte pasos importantes, y al final da el resultado. " +
                "No uses LaTeX ni formato markdown: escribe las fórmulas en texto plano " +
                "(por ejemplo x^2, sqrt(x), a/b). " +
                "Si te piden solo la respuesta de un ejercicio, dásela con una explicación corta. " +
                "Si la pregunta no es de matemáticas, di amablemente que solo ayudas con matemáticas."

    /** Manda la conversación y devuelve la respuesta, o un error con un mensaje listo para mostrar. */
    suspend fun ask(apiKey: String, history: List<ChatMessage>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 1024)
                    .put("system", SYSTEM_PROMPT)
                    .put("messages", buildApiMessages(history))

                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()

                if (code in 200..299) {
                    val content = JSONObject(text).getJSONArray("content")
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") sb.append(block.optString("text"))
                    }
                    val answer = sb.toString().trim()
                    if (answer.isEmpty()) {
                        Result.failure(Exception("La IA no devolvió respuesta. Intenta de nuevo."))
                    } else {
                        Result.success(answer)
                    }
                } else {
                    val apiMessage = runCatching {
                        JSONObject(text).getJSONObject("error").getString("message")
                    }.getOrDefault(text.take(200))
                    Result.failure(Exception(friendlyError(code, apiMessage)))
                }
            } catch (e: Exception) {
                Result.failure(Exception("No se pudo conectar. Revisa tu internet e intenta de nuevo."))
            }
        }

    /** La API pide roles alternados que empiecen con "user": se junta lo consecutivo y se descartan los avisos. */
    private fun buildApiMessages(history: List<ChatMessage>): JSONArray {
        val merged = mutableListOf<Pair<String, String>>()
        for (m in history.takeLast(20)) {
            if (m.isError) continue
            val role = if (m.fromUser) "user" else "assistant"
            if (merged.isNotEmpty() && merged.last().first == role) {
                merged[merged.lastIndex] = role to (merged.last().second + "\n\n" + m.text)
            } else {
                merged.add(role to m.text)
            }
        }
        while (merged.isNotEmpty() && merged.first().first != "user") merged.removeAt(0)

        val arr = JSONArray()
        merged.forEach { (role, text) ->
            arr.put(JSONObject().put("role", role).put("content", text))
        }
        return arr
    }

    private fun friendlyError(code: Int, apiMessage: String): String = when (code) {
        401 -> "La clave de la API no es válida. Revísala con el botón 🔑 Clave."
        403 -> "Esta clave no tiene permiso para usar la API."
        429 -> "Demasiadas solicitudes seguidas. Espera un momento e intenta de nuevo."
        529 -> "El servicio está saturado ahora mismo. Intenta de nuevo en un momento."
        else -> "Error $code: $apiMessage"
    }
}