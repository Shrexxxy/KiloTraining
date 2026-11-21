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
data class DiaryAccents(val kcal: Color, val protein: Color, val fat: Color, val carbs: Color, val workout: Color)

private val darkColors = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF141416),
    surfaceContainerHigh = Color(0xFF1C1C1F),
    onSurface = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF9C9CA3),
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF26262A),
    primary = Color(0xFFB4F03C),
    onPrimary = Color(0xFF0B1400),
    primaryContainer = Color(0xFF2A3A0C),
    onPrimaryContainer = Color(0xFFD8FF8A),
    secondary = Color(0xFF38D3F0),
    onSecondary = Color(0xFF00262E),
    tertiary = Color(0xFFFF3D7F),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B),
    secondaryContainer = Color(0xFF00262E),
    onSecondaryContainer = Color(0xFF38D3F0),
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
    onSurfaceVariant = Color(0xFF62626B),
    outline = Color(0xFFC8C8D0),
    outlineVariant = Color(0xFFE2E2E7),
    primary = Color(0xFF4C8000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F5C8),
    onPrimaryContainer = Color(0xFF1E3300),
    secondary = Color(0xFF077A96),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFE0185F),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFC62828),
    secondaryContainer = Color(0xFFD5F2F8),
    onSecondaryContainer = Color(0xFF00262E),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE2E2E7),
    surfaceVariant = Color(0xFFFFFFFF),
)

private val darkAccents = with(darkColors) { DiaryAccents(primary, secondary, tertiary, Color(0xFFFFB020), tertiary) }
private val lightAccents = with(lightColors) { DiaryAccents(primary, secondary, tertiary, Color(0xFF9A6200), tertiary) }

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
    CompositionLocalProvider(LocalDiaryAccents provides if (dark) darkAccents else lightAccents) {
        MaterialTheme(colorScheme = colors, typography = diaryTypography, shapes = diaryShapes, content = content)
    }
}
