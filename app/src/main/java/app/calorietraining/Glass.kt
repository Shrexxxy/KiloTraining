package app.calorietraining

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
internal fun DiaryBackground(training: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDiaryDark.current
    val colors = MaterialTheme.colorScheme
    val accents = LocalDiaryAccents.current
    val backgroundTop = if (dark) DarkDiaryBackgroundTop else colors.background
    val flat = trainingBackground(dark)
    Box(Modifier.fillMaxSize().testTag("diary-background").drawWithCache {
        if (training) {
            val (top, bottom) = trainingGlows(dark, accents)
            val radius = hypot(size.width, size.height) * .6f
            val spots = listOf(
                Brush.radialGradient(listOf(top, Color.Transparent), Offset(size.width, 0f), radius),
                Brush.radialGradient(listOf(bottom, Color.Transparent), Offset(0f, size.height * .9f), radius),
            )
            return@drawWithCache onDrawBehind {
                drawRect(flat)
                spots.forEach { drawRect(it) }
            }
        }
        val base = Brush.verticalGradient(listOf(backgroundTop, colors.surface))
        val (carbs, kcal, fat, protein) = diaryGlows(dark, accents)
        val glows = listOf(
            Brush.radialGradient(listOf(carbs, Color.Transparent), Offset(0f, size.height * .30f), size.width * .55f),
            Brush.radialGradient(listOf(kcal, Color.Transparent), Offset(size.width, 0f), size.width * .60f),
            Brush.radialGradient(listOf(fat, Color.Transparent), Offset(size.width, size.height * .75f), size.width * .65f),
            Brush.radialGradient(listOf(protein, Color.Transparent), Offset(0f, size.height), size.width * .60f),
        )
        onDrawBehind {
            drawRect(base)
            glows.forEach { drawRect(it) }
        }
    }) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}

/** Цвета цветных пятен фона вместе с прозрачностью: углеводы, ккал, жиры, белки. */
internal fun diaryGlows(dark: Boolean, accents: DiaryAccents): List<Color> = listOf(
    accents.carbs.copy(alpha = if (dark) .11f else .12f),
    accents.kcal.copy(alpha = if (dark) .13f else .14f),
    accents.fat.copy(alpha = .10f),
    accents.protein.copy(alpha = if (dark) .11f else .12f),
)

/** Стальные пятна фона вкладки «Тренировки»: сверху справа и снизу слева. */
internal fun trainingGlows(dark: Boolean, accents: DiaryAccents): List<Color> =
    listOf(accents.workout.copy(alpha = if (dark) .20f else .12f), accents.workout.copy(alpha = if (dark) .11f else .07f))

/** Заливка стекла без бликов; лежит поверх фона. */
internal fun glassFill(accent: Color, dark: Boolean, surfaceContainer: Color): Color {
    val start = if (dark) DarkGlassStart.copy(alpha = .42f) else surfaceContainer.copy(alpha = .70f)
    val end = if (dark) DarkGlassEnd.copy(alpha = .38f) else surfaceContainer.copy(alpha = .58f)
    return lerp(accent.copy(alpha = .10f).compositeOver(start), end, .5f)
}

@Composable
internal fun GlassCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = LocalDiaryDark.current
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.colorScheme
    val white = if (dark) colors.onSurface else colors.surfaceContainer
    val edge = accent.copy(alpha = .40f).compositeOver(white.copy(alpha = if (dark) .10f else .90f))
    val frame = Modifier.testTag("glass-card")
    val fill = glassFill(accent, dark, colors.surfaceContainer)
    val reflection = Brush.verticalGradient(
        0f to white.copy(alpha = if (dark) .06f else .60f),
        .40f to Color.Transparent,
    )
    val inside = @Composable {
        Box(Modifier.background(fill).background(reflection)
            .border(1.dp, edge, shape)
            .border(1.dp, Brush.verticalGradient(0f to white.copy(alpha = .12f), .22f to Color.Transparent), shape)) {
            content()
        }
    }
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.matchParentSize()
            .drawWithCache {
                val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
                onDrawWithContent {
                    // Полупрозрачная заливка иначе пропускает тень внутри контура.
                    clipPath(outline, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                }
            }
            .dropShadow(shape, Shadow(3.dp, Color.Black.copy(alpha = .24f), offset = DpOffset(0.dp, 2.dp))))
        if (onClick == null) {
            Surface(frame, shape = shape, color = Color.Transparent, contentColor = colors.onSurface, content = inside)
        } else {
            Surface(onClick, frame, enabled = enabled, shape = shape, color = Color.Transparent,
                contentColor = colors.onSurface, content = inside)
        }
    }
}

@Composable
internal fun glassAccent(meal: Meal): Color {
    val accents = LocalDiaryAccents.current
    return when (meal) {
        Meal.BREAKFAST -> accents.carbs
        Meal.LUNCH -> accents.protein
        Meal.DINNER -> accents.fat
        Meal.SNACK -> accents.kcal
        Meal.UNSPECIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
internal fun GlassTonalButton(
    onClick: () -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = contentPadding, enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = LocalDiaryAccents.current.workout.copy(alpha = .16f),
            contentColor = colors.onSurface,
        ),
        border = BorderStroke(1.dp, colors.outlineVariant),
        content = content,
    )
}
