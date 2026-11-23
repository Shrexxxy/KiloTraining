package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import java.time.LocalDate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Показывает разделы дневника и передаёт выбор оформления в профиль. */
@Composable
fun DiaryApp(mode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Питание") }
    val training = tab == "Тренировки"
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val backup: BackupViewModel = viewModel { BackupViewModel(app) }
    val backupState by backup.uiState.collectAsStateWithLifecycle()
    var copying by rememberSaveable { mutableStateOf(false) }
    if (copying) {
        BackupScreen(backup) { copying = false }
        return
    }
    val settings = remember(app) { OnboardingSettings(app) }
    var onboardingRevision by rememberSaveable { mutableIntStateOf(0) }
    val revision = "${backupState.revision}-$onboardingRevision"
    val onboardingData = remember(revision) {
        FoodStore(app).use { it.onboardingData(LocalDate.now()) }
    }
    var questionnaire by rememberSaveable(backupState.revision) {
        mutableStateOf(!settings.skipped && onboardingData.profile == null && !onboardingData.hasGoals)
    }
    var questionnaireWeight by remember { mutableStateOf<Measurement?>(null) }
    if (questionnaire) {
        OnboardingScreen(
            onboardingData.copy(measurement = questionnaireWeight ?: onboardingData.measurement),
            save = { profile, measurement, goals ->
                FoodStore(app).use { it.saveOnboarding(profile, measurement, goals) }
            },
            onSaved = {
                onboardingRevision++
                questionnaire = false
                tab = "Питание"
            },
            onBack = { questionnaire = false },
            onSkip = {
                settings.skipped = true
                questionnaire = false
            },
        )
        return
    }
    val food: FoodViewModel = viewModel(key = "food-$revision") { FoodViewModel(app) }
    val foodState by food.uiState.collectAsStateWithLifecycle()
    val workout: WorkoutViewModel? = if (training) viewModel(key = "workout-${backupState.revision}") { WorkoutViewModel(app) } else null
    val workoutState = workout?.uiState?.collectAsStateWithLifecycle()?.value
    val editing = if(training) workoutState?.draft != null else foodState.draft != null || foodState.scanning || foodState.recipes != null
    var fromHistory by rememberSaveable { mutableStateOf(false) }
    var requestedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSession by rememberSaveable { mutableStateOf<Long?>(null) }
    val historyHolder = rememberSaveableStateHolder()
    fun selectTab(title: String) {
        fromHistory = false
        requestedDay = null
        requestedSession = null
        tab = title
    }
    fun openDiary(title: String, date: LocalDate, session: Long? = null) {
        fromHistory = true
        requestedDay = date.toString()
        requestedSession = session
        tab = title
    }
    BackHandler(enabled = fromHistory && !editing && !training) { selectTab("История") }
    LaunchedEffect(requestedDay, requestedSession, tab, foodState.busy, workoutState?.busy) {
        val session = requestedSession
        if (training && session != null && workout != null && workoutState?.busy == false) {
            workout.event(WorkoutEvent.Open(session))
            requestedSession = null
            requestedDay = null
        } else if (!training && !foodState.busy) {
            val day = requestedDay?.let(LocalDate::parse) ?: return@LaunchedEffect
            food.onEvent(FoodEvent.Day(day))
            requestedDay = null
        }
    }
    DiaryBackground {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (tab == "История") historyHolder.SaveableStateProvider("history") {
                    HistoryScreen(viewModel { HistoryViewModel(app) }, onBackup = { copying = true },
                        onFoodDay = { openDiary("Питание", it) },
                        onWorkout = { openDiary("Тренировки", it.date, it.id) })
                }
                else if(workout != null && workoutState != null) {
                    LaunchedEffect(workout) { workout.event(WorkoutEvent.Refresh) }
                    WorkoutContent(workoutState, { event ->
                        workout.event(event)
                        if (fromHistory && event == WorkoutEvent.BackToList) selectTab("История")
                    })
                }
                else if (tab == "Профиль") ProfileScreen(
                    viewModel(key = "profile-$revision") { ProfileViewModel(app) }, mode, onThemeChange,
                    onboardingData.profile, onboardingData.measurement,
                    onRecalculate = { measurement ->
                        questionnaireWeight = measurement
                        questionnaire = true
                    }
                )
                else FoodContent(foodState,food::onEvent)
            }
            if (!editing) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    listOf("Питание" to R.drawable.ic_nav_food, "Тренировки" to R.drawable.ic_nav_workout,
                        "История" to R.drawable.ic_nav_history, "Профиль" to R.drawable.ic_nav_profile).forEach { (title, icon) ->
                        NavigationBarItem(selected = tab == title, onClick = { selectTab(title) },
                            icon = { Icon(painterResource(icon), contentDescription = null) },
                            label = { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerScreen(state: FoodUiState, onEvent: (FoodEvent)->Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if(code != null && validBarcode(code)) onEvent(FoodEvent.Barcode(code))
        else message = if(code == null) "Сканирование закрыто. Можно ввести цифры вручную." else "Нужен товарный штрихкод с упаковки."
    }
    BackHandler { onEvent(FoodEvent.Close) }
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Штрихкод продукта",style=MaterialTheme.typography.headlineMedium)
            Button(onClick={
                launcher.launch(ScanOptions().setDesiredBarcodeFormats(listOf("EAN_8","EAN_13","UPC_A","UPC_E","ITF"))
                    .setBeepEnabled(false).setOrientationLocked(false).setPrompt("Наведите камеру на штрихкод"))
            }) { Text("Открыть камеру") }
            DiaryTextField(digits,{ digits=it },singleLine=true,label={Text("Цифры штрихкода")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedButton(enabled=validBarcode(digits),onClick={ onEvent(FoodEvent.Barcode(digits)) }) { Text("Найти продукт") }
            if(state.busy) Text("Ищем продукт…")
            if(message.isNotBlank()) Text(message)
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Text("Каталог: Open Food Facts · ODbL",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={onEvent(FoodEvent.Close)}) { Text("Назад") }
        }
    }
}
