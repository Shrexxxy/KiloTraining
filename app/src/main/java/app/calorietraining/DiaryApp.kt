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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun DiaryApp() {
    var tab by rememberSaveable { mutableStateOf("Питание") }
    val training = tab == "Тренировки"
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val backup: BackupViewModel = viewModel { BackupViewModel(app) }
    val backupState by backup.uiState.collectAsStateWithLifecycle()
    var restUntil by rememberSaveable(backupState.revision) { mutableLongStateOf(0L) }
    var copying by rememberSaveable { mutableStateOf(false) }
    if (copying) {
        BackupScreen(backup) { copying = false }
        return
    }
    val food: FoodViewModel = viewModel(key = "food-${backupState.revision}") { FoodViewModel(app) }
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
    BackHandler(enabled = fromHistory && !editing) { selectTab("История") }
    LaunchedEffect(requestedDay, tab, foodState.busy, workoutState?.busy) {
        val day = requestedDay?.let(LocalDate::parse) ?: return@LaunchedEffect
        if (training && workout != null && workoutState?.busy == false) {
            workout.event(WorkoutEvent.Day(day))
            requestedDay = null
        } else if (!training && !foodState.busy) {
            food.onEvent(FoodEvent.Day(day))
            requestedDay = null
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (tab == "История") historyHolder.SaveableStateProvider("history") {
                HistoryScreen(viewModel { HistoryViewModel(app) }, onBackup = { copying = true },
                    onFoodDay = { openDiary("Питание", it) },
                    onWorkout = { openDiary("Тренировки", it.date, it.id) })
            }
            else if(workout != null && workoutState != null) WorkoutContent(workoutState,workout::event,
                focusSession = requestedSession, onFocused = { requestedSession = null }) {
                RestTimer(restUntil, onDeadlineChanged = { restUntil = it })
            }
            else if (tab == "Профиль") ProfileScreen(viewModel(key = "profile-${backupState.revision}") { ProfileViewModel(app) })
            else FoodContent(foodState,food::onEvent)
        }
        if(!editing) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("Питание", "Тренировки", "История", "Профиль").forEach { title ->
                NavigationBarItem(selected = tab == title, onClick = { selectTab(title) }, icon = { Text(title) })
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
    Scaffold(containerColor=MaterialTheme.colorScheme.surface) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Штрихкод продукта",style=MaterialTheme.typography.headlineMedium)
            Button(enabled=!state.busy,onClick={
                launcher.launch(ScanOptions().setDesiredBarcodeFormats(listOf("EAN_8","EAN_13","UPC_A","UPC_E","ITF"))
                    .setBeepEnabled(false).setOrientationLocked(false).setPrompt("Наведите камеру на штрихкод"))
            }) { Text("Открыть камеру") }
            OutlinedTextField(digits,{ digits=it },singleLine=true,label={Text("Цифры штрихкода")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=!state.busy)
            OutlinedButton(enabled=validBarcode(digits) && !state.busy,onClick={ onEvent(FoodEvent.Barcode(digits)) }) { Text("Найти продукт") }
            if(state.busy) Text("Ищем продукт…")
            if(message.isNotBlank()) Text(message)
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Text("Каталог: Open Food Facts · ODbL",style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!state.busy,onClick={onEvent(FoodEvent.Close)}) { Text("Назад") }
        }
    }
}
