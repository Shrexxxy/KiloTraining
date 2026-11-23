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

@Composable
internal fun DiaryBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDiaryDark.current
    val colors = MaterialTheme.colorScheme
    val accents = LocalDiaryAccents.current
    val backgroundTop = if (dark) DarkDiaryBackgroundTop else colors.background
    Box(Modifier.fillMaxSize().testTag("diary-background").drawWithCache {
        val base = Brush.verticalGradient(listOf(backgroundTop, colors.surface))
        val glows = listOf(
            Brush.radialGradient(listOf(accents.carbs.copy(alpha = if (dark) .22f else .28f), Color.Transparent),
                Offset(0f, size.height * .30f), size.width * .55f),
            Brush.radialGradient(listOf(accents.kcal.copy(alpha = if (dark) .28f else .30f), Color.Transparent),
                Offset(size.width, 0f), size.width * .60f),
            Brush.radialGradient(listOf(accents.fat.copy(alpha = if (dark) .20f else .18f), Color.Transparent),
                Offset(size.width, size.height * .75f), size.width * .65f),
            Brush.radialGradient(listOf(accents.protein.copy(alpha = .22f), Color.Transparent),
                Offset(0f, size.height), size.width * .60f),
        )
        onDrawBehind {
            drawRect(base)
            glows.forEach { drawRect(it) }
        }
    }) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
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
    val glassStart = if (dark) DarkGlassStart.copy(alpha = .42f) else colors.surfaceContainer.copy(alpha = .62f)
    val glassEnd = if (dark) DarkGlassEnd.copy(alpha = .38f) else colors.surfaceContainer.copy(alpha = .48f)
    val white = if (dark) colors.onSurface else colors.surfaceContainer
    val edge = accent.copy(alpha = .40f).compositeOver(white.copy(alpha = if (dark) .10f else .90f))
    val glow = accent.copy(alpha = .40f)
    val frame = Modifier.testTag("glass-card")
    val fill = lerp(accent.copy(alpha = .16f).compositeOver(glassStart), glassEnd, .5f)
    val reflection = Brush.verticalGradient(
        0f to white.copy(alpha = if (dark) .09f else .60f),
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
            .dropShadow(shape, Shadow(3.dp, Color.Black.copy(alpha = .24f), offset = DpOffset(0.dp, 2.dp)))
            .dropShadow(shape, Shadow(7.dp, glow, offset = DpOffset(0.dp, 2.dp))))
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
            containerColor = LocalDiaryAccents.current.protein.copy(alpha = .16f),
            contentColor = colors.onSurface,
        ),
        border = BorderStroke(1.dp, colors.outlineVariant),
        content = content,
    )
}
