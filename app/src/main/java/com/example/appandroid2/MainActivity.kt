package com.example.appandroid2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appandroid2.ui.theme.AppAndroid2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Si tu tema tiene otro nombre, cámbialo aquí (míralo en ui/theme/Theme.kt)
            AppAndroid2Theme {
                CalculatorApp()
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

private val functionKeys = setOf("sin", "cos", "tan", "√", "ln")
private val operatorKeys = setOf("÷", "×", "−", "+", "^")

@Composable
fun CalculatorApp() {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = Calculadora, 1 = Ecuaciones
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }

    fun onKey(key: String) {
        when (key) {
            "C" -> { expression = ""; result = "" }
            "⌫" -> {
                expression = expression.replace(Regex("(sin\\(|cos\\(|tan\\(|sqrt\\(|ln\\(|log\\(|.)$"), "")
                result = ""
            }
            "=" -> {
                if (mode == 0) {
                    try {
                        val value = ExpressionParser(expression).parse()
                        expression = formatNumber(value)
                        result = ""
                    } catch (e: Exception) {
                        result = "Error: ${e.message}"
                    }
                } else {
                    expression += "="
                }
            }
            "Resolver" -> {
                result = try {
                    EquationSolver.solve(expression)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            "√" -> { expression += "sqrt("; result = "" }
            "sin", "cos", "tan", "ln", "log" -> { expression += "$key("; result = "" }
            "π" -> { expression += "pi"; result = "" }
            else -> { expression += key; result = "" }
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
            PrimaryTabRow(selectedTabIndex = mode) {
                Tab(
                    selected = mode == 0,
                    onClick = { mode = 0; expression = ""; result = "" },
                    text = { Text("Calculadora") }
                )
                Tab(
                    selected = mode == 1,
                    onClick = { mode = 1; expression = ""; result = "" },
                    text = { Text("Ecuaciones") }
                )
            }

            // Pantalla
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
                    text = expression.ifEmpty { if (mode == 0) "0" else "Ej: 2x+3=7  o  x^2-5x+6=0" },
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
            }

            // Teclado
            val rows = if (mode == 0) calcRows else equationRows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val wide = key == "=" && mode == 0 || key == "Resolver"
                            CalcButton(
                                label = key,
                                modifier = Modifier.weight(if (wide) 2f else 1f),
                                onClick = { onKey(key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (label) {
        "=", "Resolver" -> colors.primary to colors.onPrimary
        "C", "⌫" -> colors.errorContainer to colors.onErrorContainer
        in operatorKeys -> colors.secondaryContainer to colors.onSecondaryContainer
        in functionKeys, "π", "x", "(", ")" -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
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