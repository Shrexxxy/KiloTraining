package app.calorietraining

import android.database.SQLException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun OnboardingScreen(
    data: OnboardingData,
    save: (UserProfile, Measurement, DailyGoals) -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val today = LocalDate.now()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableStateOf(data.profile?.kind) }
    var sex by rememberSaveable { mutableStateOf(data.profile?.sex) }
    var birth by rememberSaveable { mutableStateOf(data.profile?.birthYear?.toString().orEmpty()) }
    var height by rememberSaveable { mutableStateOf(data.profile?.heightCm?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(data.measurement?.weight.orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(data.measurement?.fat.orEmpty()) }
    var activity by rememberSaveable { mutableStateOf(data.profile?.activity) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val age = birth.toIntOrNull()?.let { today.year.toLong() - it }
    val birthValid = age != null && age in 14L..100L
    val heightValid = height.toIntOrNull() in 120..230
    val weightValue = weight.trim().amount()?.toDouble()
    val weightValid = weightValue != null && weightValue in 30.0..300.0
    val fatValue = fat.trim().amount()?.toDouble()
    val fatValid = fat.isBlank() || (fatValue != null && fatValue in 3.0..60.0)
    val currentKind = kind
    val currentSex = sex
    val currentActivity = activity
    val input = if (currentKind != null && currentSex != null && currentActivity != null &&
        birthValid && heightValid && weightValid && fatValid) {
        GoalInput(currentSex, birth.toInt(), height.toInt(), weightValue, fatValue, currentActivity, currentKind, today.year)
    } else null
    val plan = remember(input) { input?.let(::calculateGoals) }
    val canContinue = when (step) {
        0 -> kind != null
        1 -> sex != null && birthValid && heightValid
        2 -> weightValid && fatValid
        else -> input != null
    }
    fun back() {
        if (saving) return
        if (step > 0) step-- else onBack()
    }
    fun commit(goals: DailyGoals) {
        val form = input ?: return
        if (saving) return
        saving = true
        error = null
        val profile = UserProfile(form.sex, form.birthYear, form.heightCm, form.activity, form.kind)
        val measurement = Measurement(today, weight.trim(), fat.trim())
        scope.launch {
            try {
                withContext(Dispatchers.IO) { save(profile, measurement, goals) }
                onSaved()
            } catch (_: SQLException) {
                error = "Не удалось сохранить цель. Попробуйте ещё раз."
            } finally {
                saving = false
            }
        }
    }
    BackHandler { back() }
    OnboardingContent(step, canContinue, saving, error, ::back, onSkip, { step++ },
        save = { plan?.goals?.let(::commit) }, edit = { manual = true }) {
        when (step) {
            0 -> OnboardingChoices(GoalKind.entries, kind, GoalKind::label) { kind = it }
            1 -> {
                OnboardingChoices(Sex.entries, sex, Sex::label) { sex = it }
                OnboardingField("Год рождения", birth, { birth = it }, birthValid, "Возраст должен быть от 14 до 100 лет")
                OnboardingField("Рост, см", height, { height = it }, heightValid, "Допустимый рост: 120–230 см")
            }
            2 -> {
                OnboardingField("Вес, кг", weight, { weight = it }, weightValid, "Допустимый вес: 30–300 кг", decimal = true)
                OnboardingField("% жира", fat, { fat = it }, fatValid, "Допустимое значение: 3–60 %",
                    decimal = true, hint = "Если не знаете — оставьте пустым")
            }
            3 -> OnboardingChoices(ActivityLevel.entries, activity, ActivityLevel::label) { activity = it }
            4 -> if (plan != null && input != null) OnboardingResult(plan, input.activity, input.kind)
        }
    }
    if (manual && plan != null) {
        GoalDialog(today, plan.goals, { manual = false }, onboarding = true) { kcal, macros ->
            manual = false
            commit(DailyGoals(kcal, macros))
        }
    }
}

@Composable
private fun OnboardingContent(
    step: Int, canContinue: Boolean, saving: Boolean, error: String?,
    back: () -> Unit, skip: () -> Unit, next: () -> Unit, save: () -> Unit, edit: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val accents = LocalDiaryAccents.current
    val palette = MaterialTheme.colorScheme
    val titles = listOf("Ваша цель", "О вас", "Вес и состав тела", "Активность", "Ваша цель на день")
    DiaryBackground {
        Column(modifier.fillMaxSize().safeDrawingPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(key(step) { rememberScrollState() }).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Шаг ${step + 1} из 5", style = MaterialTheme.typography.labelLarge, color = palette.onSurfaceVariant)
                LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth())
                Text(titles[step], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                GlassCard(accents.kcal, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
                }
                if (error != null) Text(error, color = palette.error)
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                if (step == 4) {
                    Button(save, Modifier.fillMaxWidth(), enabled = canContinue && !saving) {
                        Text(if (saving) "Сохраняем…" else "Сохранить цель")
                    }
                    OutlinedButton(edit, Modifier.fillMaxWidth(), enabled = canContinue && !saving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onSurface),
                        border = BorderStroke(1.dp, palette.outline)) {
                        Text("Изменить вручную")
                    }
                } else {
                    Button(next, Modifier.fillMaxWidth(), enabled = canContinue) { Text("Далее") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(back, enabled = !saving) { Text("Назад") }
                    TextButton(skip, enabled = !saving) { Text("Пропустить") }
                }
            }
        }
    }
}

@Composable
private fun <T> OnboardingChoices(options: List<T>, selected: T?, label: (T) -> String, choose: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(option == selected, role = Role.RadioButton, onClick = { choose(option) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(option == selected, onClick = null)
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun OnboardingField(
    label: String, value: String, change: (String) -> Unit, valid: Boolean, error: String,
    decimal: Boolean = false, hint: String? = null,
) {
    val invalid = value.isNotEmpty() && !valid
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        isError = invalid, supportingText = if (invalid || hint != null) ({ Text(if (invalid) error else hint.orEmpty()) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}

@Composable
private fun OnboardingResult(plan: GoalPlan, activity: ActivityLevel, kind: GoalKind) {
    val accents = LocalDiaryAccents.current
    val style = MaterialTheme.typography
    val macros = plan.goals.macros
    val calories = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(plan.goals.kcal)
    Text("$calories ккал", style = style.displaySmall, color = accents.kcal, fontWeight = FontWeight.ExtraBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Б ${macros.protein} г", color = accents.protein, style = style.titleMedium)
        Text("Ж ${macros.fat} г", color = accents.fat, style = style.titleMedium)
        Text("У ${macros.carbs} г", color = accents.carbs, style = style.titleMedium)
    }
    HorizontalDivider()
    val activityFactor = activity.factor.toString().replace('.', ',')
    val goalAdjustment = when (kind) {
        GoalKind.LOSE -> "−15 %"
        GoalKind.MAINTAIN -> "0 %"
        GoalKind.GAIN -> "+10 %"
    }
    Text("${plan.formula} · ×$activityFactor · $goalAdjustment", style = style.bodySmall)
    Text("Расчёт ориентировочный и не заменяет консультацию врача", style = style.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun OnboardingResultPreview() {
    val input = GoalInput(Sex.MALE, 1996, 180, 80.0, null, ActivityLevel.MODERATE, GoalKind.MAINTAIN, 2026)
    DiaryTheme(ThemeMode.DARK) {
        OnboardingContent(4, true, false, null, {}, {}, {}, {}, {}) {
            OnboardingResult(calculateGoals(input), input.activity, input.kind)
        }
    }
}
