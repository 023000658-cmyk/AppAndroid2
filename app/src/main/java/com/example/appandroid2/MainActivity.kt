package com.example.appandroid2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appandroid2.ui.theme.AppAndroid2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDark) }

            // Ajusta los iconos de la barra de estado/navegación al tema elegido
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkTheme }
                )
                onDispose { }
            }

            // Si tu tema tiene otro nombre, cámbialo aquí (míralo en ui/theme/Theme.kt)
            AppAndroid2Theme(darkTheme = darkTheme) {
                CalculatorApp(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme }
                )
            }
        }
    }
}

private val calcRows = listOf(
    listOf("sin", "cos", "tan", "√"),
    listOf("ln", "π", "^", "⌫"),
    listOf("C", "(", ")", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "="),
)

private val equationRows = listOf(
    listOf("x", "=", "(", ")"),
    listOf("sin", "cos", "√", "^"),
    listOf("C", "⌫", "π", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "Resolver"),
)

private val graphRows = listOf(
    listOf("x", "(", ")", "^"),
    listOf("sin", "cos", "tan", "√"),
    listOf("C", "⌫", "π", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "ln", "e"),
)

private val functionKeys = setOf("sin", "cos", "tan", "√", "ln")
private val operatorKeys = setOf("÷", "×", "−", "+", "^")

@Composable
fun CalculatorApp(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    // 0 = Calculadora, 1 = Ecuaciones, 2 = Gráfica
    var mode by rememberSaveable { mutableIntStateOf(0) }

    var calcExpr by rememberSaveable { mutableStateOf("") }
    var calcResult by rememberSaveable { mutableStateOf("") }
    var eqExpr by rememberSaveable { mutableStateOf("") }
    var eqResult by rememberSaveable { mutableStateOf("") }
    var graphExpr by rememberSaveable { mutableStateOf("") }

    // Historial (se guarda en el teléfono)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calculadora", Context.MODE_PRIVATE) }
    val history = remember { HistoryItem.load(prefs).toMutableStateList() }
    var showHistory by remember { mutableStateOf(false) }

    fun addHistory(item: HistoryItem) {
        history.add(0, item)
        while (history.size > HistoryItem.MAX_ITEMS) history.removeAt(history.size - 1)
        HistoryItem.save(prefs, history)
    }

    val expression = when (mode) {
        0 -> calcExpr
        1 -> eqExpr
        else -> graphExpr
    }
    val result = when (mode) {
        0 -> calcResult
        1 -> eqResult
        else -> ""
    }

    fun setExpression(value: String) {
        when (mode) {
            0 -> calcExpr = value
            1 -> eqExpr = value
            else -> graphExpr = value
        }
    }

    fun setResult(value: String) {
        when (mode) {
            0 -> calcResult = value
            1 -> eqResult = value
        }
    }

    fun onKey(key: String) {
        when (key) {
            "C" -> { setExpression(""); setResult("") }
            "⌫" -> {
                setExpression(
                    expression.replace(Regex("(sin\\(|cos\\(|tan\\(|sqrt\\(|ln\\(|log\\(|.)$"), "")
                )
                setResult("")
            }
            "=" -> {
                if (mode == 0) {
                    if (expression.isNotBlank()) {
                        try {
                            val value = ExpressionParser(expression).parse()
                            if (value.isNaN() || value.isInfinite()) {
                                setResult("Error: resultado no definido")
                            } else {
                                val out = formatNumber(value)
                                addHistory(HistoryItem("calc", expression, out))
                                setExpression(out)
                                setResult("")
                            }
                        } catch (e: Exception) {
                            setResult("Error: ${e.message}")
                        }
                    }
                } else {
                    setExpression("$expression=")
                }
            }
            "Resolver" -> {
                if (expression.isNotBlank()) {
                    try {
                        val out = EquationSolver.solve(expression)
                        addHistory(HistoryItem("eq", expression, out))
                        setResult(out)
                    } catch (e: Exception) {
                        setResult("Error: ${e.message}")
                    }
                }
            }
            "√" -> { setExpression("${expression}sqrt("); setResult("") }
            "sin", "cos", "tan", "ln", "log" -> { setExpression("$expression$key("); setResult("") }
            "π" -> { setExpression("${expression}pi"); setResult("") }
            else -> { setExpression(expression + key); setResult("") }
        }
    }

    // Vista previa del resultado mientras escribes (solo en modo calculadora)
    val preview = if (mode == 0 && expression.isNotBlank()) {
        runCatching { formatNumber(ExpressionParser(expression).parse()) }.getOrNull()
    } else null

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showHistory = true }) {
                    Text("🕘 Historial")
                }
                TextButton(onClick = onToggleTheme) {
                    Text(if (darkTheme) "☀️ Modo claro" else "🌙 Modo oscuro")
                }
            }

            PrimaryTabRow(selectedTabIndex = mode) {
                listOf("Calculadora", "Ecuaciones", "Gráfica").forEachIndexed { index, title ->
                    Tab(
                        selected = mode == index,
                        onClick = { mode = index },
                        text = { Text(title, maxLines = 1, fontSize = 14.sp) }
                    )
                }
            }

            if (mode == 2) {
                // Gráfica
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = if (graphExpr.isEmpty()) "y = ?" else "y = $graphExpr",
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    FunctionGraph(
                        expression = graphExpr,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            } else {
                // Pantalla de calculadora / ecuaciones
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expression.ifEmpty {
                            if (mode == 0) "0" else "Ej: 2x+3=7  o  x^2-5x+6=0"
                        },
                        fontSize = if (expression.length > 14) 28.sp else 40.sp,
                        color = if (expression.isEmpty()) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val secondary = result.ifEmpty { preview ?: "" }
                    if (secondary.isNotEmpty()) {
                        Text(
                            text = secondary,
                            fontSize = 24.sp,
                            color = if (result.startsWith("Error")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                    if (mode == 1 && result.isNotEmpty() && !result.startsWith("Error")) {
                        TextButton(onClick = {
                            val parts = eqExpr.split("=")
                            if (parts.size == 2) {
                                graphExpr = "(${parts[0]})-(${parts[1]})"
                                mode = 2
                            }
                        }) {
                            Text("📈 Ver gráfica")
                        }
                    }
                }
            }

            // Teclado
            val rows = when (mode) {
                0 -> calcRows
                1 -> equationRows
                else -> graphRows
            }
            val keyHeight = if (mode == 2) 46.dp else 56.dp
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val wide = (key == "=" && mode == 0) || key == "Resolver"
                            CalcButton(
                                label = key,
                                height = keyHeight,
                                modifier = Modifier.weight(if (wide) 2f else 1f),
                                onClick = { onKey(key) }
                            )
                        }
                    }
                }
            }
        }

        if (showHistory) {
            HistoryDialog(
                entries = history,
                onSelect = { item ->
                    if (item.kind == "calc") {
                        calcExpr = item.input
                        calcResult = ""
                        mode = 0
                    } else {
                        eqExpr = item.input
                        eqResult = ""
                        mode = 1
                    }
                    showHistory = false
                },
                onClear = {
                    history.clear()
                    HistoryItem.save(prefs, history)
                },
                onDismiss = { showHistory = false }
            )
        }
    }
}

@Composable
private fun CalcButton(label: String, height: Dp, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (label) {
        "=", "Resolver" -> colors.primary to colors.onPrimary
        "C", "⌫" -> colors.errorContainer to colors.onErrorContainer
        in operatorKeys -> colors.secondaryContainer to colors.onSecondaryContainer
        in functionKeys, "π", "x", "e", "(", ")" -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        )
    ) {
        Text(text = label, fontSize = 20.sp)
    }
}