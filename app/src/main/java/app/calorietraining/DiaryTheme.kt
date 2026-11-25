package app.calorietraining

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp

/** Определяет, какую палитру показывает дневник. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Хранит выбранное оформление на устройстве. */
class ThemeSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Выбранный режим или системная тема при первом запуске. */
    var mode: ThemeMode
        get() {
            val name = preferences.getString("theme_mode", null)
            return ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM
        }
        set(value) { preferences.edit().putString("theme_mode", value.name).apply() }
}

/** Цвета показателей питания и тренировки в текущей теме. */
data class DiaryAccents(
    val kcal: Color, val protein: Color, val fat: Color, val carbs: Color, val workout: Color,
    val onWorkout: Color = Color.White,
)

private val darkColors = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF1C1C1F),
    onSurface = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB4B4BC),
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF26262A),
    primary = Color(0xFFB4F03C),
    onPrimary = Color(0xFF0B1400),
    primaryContainer = Color(0xFF2A3A0C),
    onPrimaryContainer = Color(0xFFD8FF8A),
    secondary = Color(0xFF3DDC97),
    onSecondary = Color(0xFF06080C),
    tertiary = Color(0xFFFF8A4C),
    onTertiary = Color(0xFF06080C),
    error = Color(0xFFFF8A8A),
    secondaryContainer = Color(0xFF0A2E22),
    onSecondaryContainer = Color(0xFF3DDC97),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerHighest = Color(0xFF26262A),
    surfaceVariant = Color(0xFF141416),
)

private val lightColors = lightColorScheme(
    background = Color(0xFFF2F2F5),
    surface = Color(0xFFF2F2F5),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101012),
    onBackground = Color(0xFF101012),
    onSurfaceVariant = Color(0xFF55555E),
    outline = Color(0xFF9A9AA4),
    outlineVariant = Color(0xFFE2E2E7),
    primary = Color(0xFF487800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F5C8),
    onPrimaryContainer = Color(0xFF1E3300),
    secondary = Color(0xFF08785A),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB8480F),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFB71C1C),
    secondaryContainer = Color(0xFFD2F0E6),
    onSecondaryContainer = Color(0xFF06382A),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE2E2E7),
    surfaceVariant = Color(0xFFFFFFFF),
)

internal val DarkDiaryBackgroundTop = Color(0xFF07090B)
internal val DarkGlassStart = Color(0xFF1C1E24)
internal val DarkGlassEnd = Color(0xFF121317)
internal val LocalDiaryDark = staticCompositionLocalOf { false }

private val darkAccents = with(darkColors) {
    DiaryAccents(primary, secondary, tertiary, Color(0xFFFFC83D), Color(0xFF5B95FF), Color(0xFF06080C))
}
private val lightAccents = with(lightColors) {
    DiaryAccents(primary, secondary, tertiary, Color(0xFF8A5F00), Color(0xFF1B55C4), Color.White)
}
private val darkTrainingAccents = darkAccents.copy(workout = Color(0xFF9DB9D6))
private val lightTrainingAccents = lightAccents.copy(workout = Color(0xFF3B556F))

/** Цвета показателей; на вкладке «Тренировки» цвет занятий другой. */
internal fun diaryAccents(dark: Boolean, training: Boolean = false): DiaryAccents = when {
    dark && training -> darkTrainingAccents
    dark -> darkAccents
    training -> lightTrainingAccents
    else -> lightAccents
}

/** Фон вкладки «Тренировки»; у остальных экранов фон с цветными пятнами. */
internal fun trainingBackground(dark: Boolean): Color = if (dark) Color(0xFF08090B) else Color(0xFFEFF1F3)

/** Включает палитру вкладки «Тренировки» для вложенных экранов. */
@Composable
internal fun TrainingPalette(content: @Composable () -> Unit) {
    val accents = diaryAccents(LocalDiaryDark.current, training = true)
    val scheme = trainingColorScheme(MaterialTheme.colorScheme, accents, LocalDiaryDark.current)
    CompositionLocalProvider(LocalDiaryAccents provides accents) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

/** Стальные основные цвета для кнопок, чипов, переключателей и полей вкладки «Тренировки». */
internal fun trainingColorScheme(base: ColorScheme, accents: DiaryAccents, dark: Boolean): ColorScheme {
    val tint = accents.workout.copy(alpha = if (dark) .36f else .26f)
    val container = tint.compositeOver(base.surface)
    val onContainer = if (dark) accents.workout else Color(0xFF22384D)
    return base.copy(
        primary = accents.workout, onPrimary = accents.onWorkout,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = accents.workout, onSecondary = accents.onWorkout,
        secondaryContainer = container, onSecondaryContainer = onContainer,
    )
}

/** Предоставляет цвета показателей вложенным компонентам. */
val LocalDiaryAccents = staticCompositionLocalOf { lightAccents }

@OptIn(ExperimentalTextApi::class)
private val exo2 = FontFamily(listOf(400, 500, 600, 700, 800).map { weight ->
    Font(R.font.exo2, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
})

private val diaryTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = exo2, fontWeight = FontWeight.ExtraBold),
        displayMedium = displayMedium.copy(fontFamily = exo2, fontWeight = FontWeight.ExtraBold),
        displaySmall = displaySmall.copy(fontFamily = exo2, fontWeight = FontWeight.ExtraBold),
        headlineLarge = headlineLarge.copy(fontFamily = exo2, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = exo2, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = exo2, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = exo2, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = exo2, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = exo2, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = exo2, fontWeight = FontWeight.Normal),
        bodyMedium = bodyMedium.copy(fontFamily = exo2, fontWeight = FontWeight.Normal),
        bodySmall = bodySmall.copy(fontFamily = exo2, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontFamily = exo2, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = exo2, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = exo2, fontWeight = FontWeight.SemiBold),
    )
}

private val diaryShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

internal fun ComponentActivity.applyDiaryBars(dark: Boolean) {
    val barStyle = SystemBarStyle.auto(lightColors.surface.toArgb(), darkColors.surface.toArgb()) { dark }
    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
}

/** Применяет оформление дневника и подбирает контрастные значки системных панелей. */
@Composable
fun DiaryTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkColors else lightColors
    val activity = LocalActivity.current as? ComponentActivity
    var previousDark by remember(activity) { mutableStateOf(dark) }
    LaunchedEffect(activity, dark) {
        if (previousDark != dark) {
            activity?.applyDiaryBars(dark)
            previousDark = dark
        }
    }
    CompositionLocalProvider(LocalDiaryDark provides dark, LocalDiaryAccents provides if (dark) darkAccents else lightAccents) {
        MaterialTheme(colorScheme = colors, typography = diaryTypography, shapes = diaryShapes, content = content)
    }
}
