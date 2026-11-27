package app.calorietraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val weightStep = BigDecimal("0.1")
private val weightMax = BigDecimal(500)

/** Вес из текста табло: больше нуля и не больше 500 кг. */
internal fun String.scaleWeight(): BigDecimal? = amount()?.takeIf { it.signum() == 1 && it <= weightMax }

/** Следующее значение табло: шаг 0,1 кг в `BigDecimal`, за пределы 0..500 не выходит. */
internal fun String.stepWeight(direction: Int): String {
    val next = ((amount() ?: BigDecimal.ZERO) + weightStep.multiply(BigDecimal(direction)))
        .max(BigDecimal.ZERO).min(weightMax)
    return next.display()
}

/** Весы: слева «−», справа «+», в табло вес; нажатие на число открывает ввод с клавиатуры. */
@Composable
internal fun WeightScale(text: String, onText: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(text)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Уменьшить на 0,1", enabled) { onText(current.stepWeight(-1)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            ScaleBody(current, onText, enabled, Modifier.widthIn(max = 220.dp).fillMaxWidth())
            Text("кг", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StepButton("+", "Увеличить на 0,1", enabled) { onText(current.stepWeight(1)) }
    }
}

@Composable
private fun ScaleBody(text: String, onText: (String) -> Unit, enabled: Boolean, modifier: Modifier) {
    val accent = LocalDiaryAccents.current.workout
    val scheme = MaterialTheme.colorScheme
    val body = accent.copy(alpha = .18f).compositeOver(scheme.surfaceContainerHigh)
    val screen = scheme.background
    // Число крупное, но на большом шрифте системы не выходит из табло.
    val density = LocalDensity.current
    val readoutSize = (30f * minOf(density.fontScale, 1.3f) / density.fontScale).sp
    Box(modifier.aspectRatio(170f / 150f), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.matchParentSize()) {
            val k = size.width / 170f
            fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) = Triple(Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(r * k))
            rect(8f, 6f, 154f, 136f, 26f).let { (o, s, r) ->
                drawRoundRect(body, o, s, r)
                drawRoundRect(accent, o, s, r, style = Stroke(2f * k))
            }
            rect(17f, 15f, 136f, 118f, 19f).let { (o, s, r) -> drawRoundRect(accent.copy(alpha = .3f), o, s, r, style = Stroke(1f * k)) }
            rect(22f, 24f, 126f, 44f, 11f).let { (o, s, r) ->
                drawRoundRect(screen, o, s, r)
                drawRoundRect(accent.copy(alpha = .7f), o, s, r, style = Stroke(1f * k))
            }
            listOf(32f, 98f).forEach { x ->
                rect(x, 78f, 40f, 46f, 14f).let { (o, s, r) -> drawRoundRect(accent.copy(alpha = .3f), o, s, r) }
            }
        }
        BoxWithConstraints(Modifier.matchParentSize()) {
            val scale = maxWidth.value / 170f
            BasicTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.offset(22.dp * scale, 24.dp * scale).size(126.dp * scale, 44.dp * scale).testTag("weight-readout")
                    .semantics { contentDescription = "Вес, кг" },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = scheme.onSurface, fontSize = readoutSize, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(accent),
                decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } },
            )
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onStep: () -> Unit) {
    val accent = LocalDiaryAccents.current.workout
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(accent.copy(alpha = .2f)).border(1.dp, accent, CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick { if (enabled) step(); enabled }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    step()
                    // Пауза перед повтором, чтобы короткое нажатие давало ровно один шаг.
                    val repeat = scope.launch {
                        delay(450)
                        while (true) {
                            step()
                            delay(90)
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        repeat.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
