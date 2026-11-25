package app.calorietraining

import android.database.SQLException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MuscleRole(val label: String) {
    Inactive("Не работает"),
    Primary("Основная"),
    Secondary("Дополнительная"),
}

@Composable
internal fun CustomMusclesScreen(name: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val accent = LocalDiaryAccents.current.workout
    var selection by rememberSaveable(name) { mutableStateOf<IntArray?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable(name) { mutableStateOf<String?>(null) }

    LaunchedEffect(name) {
        if (selection != null) return@LaunchedEffect
        try {
            val saved = withContext(Dispatchers.IO) {
                FoodStore(app).use { it.customMuscles()[name.searchKey()] }
            }
            selection = Muscle.entries.map { muscle ->
                when {
                    saved?.primary?.contains(muscle) == true -> MuscleRole.Primary.ordinal
                    saved?.secondary?.contains(muscle) == true -> MuscleRole.Secondary.ordinal
                    else -> MuscleRole.Inactive.ordinal
                }
            }.toIntArray()
            error = null
        } catch (e: SQLException) {
            error = "Не удалось загрузить мышцы"
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(name, onBack = onBack, backEnabled = !saving)
        val roles = selection
        if (roles != null) {
            val technique = ExerciseTechnique(
                primary = Muscle.entries.filter { roles[it.ordinal] == MuscleRole.Primary.ordinal },
                secondary = Muscle.entries.filter { roles[it.ordinal] == MuscleRole.Secondary.ordinal },
                steps = emptyList(),
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassCard(accent, Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(12.dp)) {
                        MuscleDiagram(technique, accent, accent.copy(alpha = 0.35f))
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Muscle.entries.forEach { muscle ->
                        val role = MuscleRole.entries[roles[muscle.ordinal]]
                        DiaryFilterChip(
                            selected = role != MuscleRole.Inactive,
                            enabled = !saving,
                            modifier = Modifier.semantics { stateDescription = role.label },
                            onClick = {
                                selection = roles.copyOf().also {
                                    it[muscle.ordinal] = (role.ordinal + 1) % MuscleRole.entries.size
                                }
                            },
                            label = {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(muscle.label)
                                    if (role != MuscleRole.Inactive) {
                                        Text(role.label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                enabled = !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                FoodStore(app).use { it.saveCustomMuscles(name, technique.primary, technique.secondary) }
                            }
                            onBack()
                        } catch (e: SQLException) {
                            error = "Не удалось сохранить мышцы"
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text("Сохранить") }
        } else {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
