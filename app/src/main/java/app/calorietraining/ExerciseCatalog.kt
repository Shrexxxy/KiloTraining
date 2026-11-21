package app.calorietraining

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal data class ExercisePhoto(
    val name: String, val muscle: String, val keywords: String, val first: Int, val second: Int
)

internal val exerciseCatalog = listOf(
    ExercisePhoto("Тяга вертикального блока", "Спина", "верхний блок спина", R.drawable.ex_widegrip_lat_pulldown_0, R.drawable.ex_widegrip_lat_pulldown_1),
    ExercisePhoto("Жим штанги лёжа", "Грудь", "жим лежа штанга", R.drawable.ex_barbell_bench_press_medium_grip_0, R.drawable.ex_barbell_bench_press_medium_grip_1),
    ExercisePhoto("Жим гантелей лёжа", "Грудь", "гантели", R.drawable.ex_dumbbell_bench_press_0, R.drawable.ex_dumbbell_bench_press_1),
    ExercisePhoto("Жим гантелей на наклонной скамье", "Грудь", "гантели", R.drawable.ex_incline_dumbbell_press_0, R.drawable.ex_incline_dumbbell_press_1),
    ExercisePhoto("Тяга горизонтального блока", "Спина", "сидя нижний блок", R.drawable.ex_seated_cable_rows_0, R.drawable.ex_seated_cable_rows_1),
    ExercisePhoto("Тяга гантели одной рукой", "Спина", "гантели в наклоне", R.drawable.ex_onearm_dumbbell_row_0, R.drawable.ex_onearm_dumbbell_row_1),
    ExercisePhoto("Подтягивания", "Спина", "турник собственный вес", R.drawable.ex_pullups_0, R.drawable.ex_pullups_1),
    ExercisePhoto("Становая тяга", "Спина", "штанга", R.drawable.ex_barbell_deadlift_0, R.drawable.ex_barbell_deadlift_1),
    ExercisePhoto("Приседания со штангой", "Ноги", "присед", R.drawable.ex_barbell_squat_0, R.drawable.ex_barbell_squat_1),
    ExercisePhoto("Жим ногами", "Ноги", "тренажёр", R.drawable.ex_leg_press_0, R.drawable.ex_leg_press_1),
    ExercisePhoto("Разгибание ног сидя", "Ноги", "квадрицепс тренажёр", R.drawable.ex_leg_extensions_0, R.drawable.ex_leg_extensions_1),
    ExercisePhoto("Сгибание ног лёжа", "Ноги", "бицепс бедра тренажёр", R.drawable.ex_lying_leg_curls_0, R.drawable.ex_lying_leg_curls_1),
    ExercisePhoto("Жим гантелей сидя", "Плечи", "гантели вверх", R.drawable.ex_dumbbell_shoulder_press_0, R.drawable.ex_dumbbell_shoulder_press_1),
    ExercisePhoto("Подъём гантелей в стороны", "Плечи", "махи разведения гантели", R.drawable.ex_side_lateral_raise_0, R.drawable.ex_side_lateral_raise_1),
    ExercisePhoto("Сгибание рук молотком", "Руки", "молотки бицепс гантели", R.drawable.ex_hammer_curls_0, R.drawable.ex_hammer_curls_1),
    ExercisePhoto("Разгибание рук на блоке с канатом", "Руки", "трицепс канатная рукоять", R.drawable.ex_triceps_pushdown_rope_attachment_0, R.drawable.ex_triceps_pushdown_rope_attachment_1)
)

private fun String.searchKey() = trim().lowercase().replace('ё', 'е')
internal fun photoForExercise(name: String) = exerciseCatalog.firstOrNull {
    it.name.searchKey() == name.searchKey()
}

@Composable
internal fun ExercisePicker(onSelect: (ExercisePhoto) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        val words = query.searchKey().split(Regex("""\s+"""))
        exerciseCatalog.filter { item ->
            val text = "${item.name} ${item.muscle} ${item.keywords}".searchKey()
            words.all(text::contains)
        }
    }
    BackHandler(onBack = onClose)
    Column(modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Каталог упражнений", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Название или группа мышц") })
        LazyColumn(Modifier.weight(1f)) {
            if (matches.isEmpty()) item { Text("Совпадений нет", Modifier.padding(vertical = 16.dp)) }
            items(matches, key = { it.name }) { item ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(item.second), null, Modifier.size(84.dp, 66.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(item.muscle, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
        TextButton(onClick = onClose) { Text("Вернуться к вводу") }
    }
}

@Composable
internal fun ExerciseFrames(photo: ExercisePhoto, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(painterResource(photo.first), "${photo.name}, первое положение",
            Modifier.weight(1f).height(108.dp), contentScale = ContentScale.Fit)
        Image(painterResource(photo.second), "${photo.name}, второе положение",
            Modifier.weight(1f).height(108.dp), contentScale = ContentScale.Fit)
    }
}
