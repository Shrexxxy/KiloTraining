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
    val name: String, val muscle: String, val keywords: String, val first: Int, val second: Int, val dumbbell: Boolean = false
)

internal val exerciseCatalog = listOf(
    ExercisePhoto("Тяга вертикального блока", "Спина", "верхний блок спина", R.drawable.ex_widegrip_lat_pulldown_0, R.drawable.ex_widegrip_lat_pulldown_1),
    ExercisePhoto("Жим штанги лёжа", "Грудь", "жим лежа штанга", R.drawable.ex_barbell_bench_press_medium_grip_0, R.drawable.ex_barbell_bench_press_medium_grip_1),
    ExercisePhoto("Жим гантелей лёжа", "Грудь", "гантели", R.drawable.ex_dumbbell_bench_press_0, R.drawable.ex_dumbbell_bench_press_1, dumbbell = true),
    ExercisePhoto("Жим гантелей на наклонной скамье", "Грудь", "гантели", R.drawable.ex_incline_dumbbell_press_0, R.drawable.ex_incline_dumbbell_press_1, dumbbell = true),
    ExercisePhoto("Тяга горизонтального блока", "Спина", "сидя нижний блок", R.drawable.ex_seated_cable_rows_0, R.drawable.ex_seated_cable_rows_1),
    ExercisePhoto("Тяга гантели одной рукой", "Спина", "гантели в наклоне", R.drawable.ex_onearm_dumbbell_row_0, R.drawable.ex_onearm_dumbbell_row_1, dumbbell = true),
    ExercisePhoto("Подтягивания", "Спина", "турник собственный вес", R.drawable.ex_pullups_0, R.drawable.ex_pullups_1),
    ExercisePhoto("Становая тяга", "Спина", "штанга", R.drawable.ex_barbell_deadlift_0, R.drawable.ex_barbell_deadlift_1),
    ExercisePhoto("Приседания со штангой", "Ноги", "присед", R.drawable.ex_barbell_squat_0, R.drawable.ex_barbell_squat_1),
    ExercisePhoto("Жим ногами", "Ноги", "тренажёр", R.drawable.ex_leg_press_0, R.drawable.ex_leg_press_1),
    ExercisePhoto("Разгибание ног сидя", "Ноги", "квадрицепс тренажёр", R.drawable.ex_leg_extensions_0, R.drawable.ex_leg_extensions_1),
    ExercisePhoto("Сгибание ног лёжа", "Ноги", "бицепс бедра тренажёр", R.drawable.ex_lying_leg_curls_0, R.drawable.ex_lying_leg_curls_1),
    ExercisePhoto("Жим гантелей сидя", "Плечи", "гантели вверх", R.drawable.ex_dumbbell_shoulder_press_0, R.drawable.ex_dumbbell_shoulder_press_1, dumbbell = true),
    ExercisePhoto("Подъём гантелей в стороны", "Плечи", "махи разведения гантели", R.drawable.ex_side_lateral_raise_0, R.drawable.ex_side_lateral_raise_1, dumbbell = true),
    ExercisePhoto("Сгибание рук молотком", "Руки", "молотки молоточки бицепс гантели", R.drawable.ex_hammer_curls_0, R.drawable.ex_hammer_curls_1, dumbbell = true),
    ExercisePhoto("Разгибание рук на блоке с канатом", "Руки", "трицепс канатная рукоять сверху вниз", R.drawable.ex_triceps_pushdown_rope_attachment_0, R.drawable.ex_triceps_pushdown_rope_attachment_1),

    ExercisePhoto("Сведение рук в кроссовере", "Грудь", "кроссовер бабочка блоки", R.drawable.ex_cable_crossover_0, R.drawable.ex_cable_crossover_1),
    ExercisePhoto("Жим штанги на наклонной скамье", "Грудь", "наклонная штанга верх груди", R.drawable.ex_barbell_incline_bench_press_medium_grip_0, R.drawable.ex_barbell_incline_bench_press_medium_grip_1),
    ExercisePhoto("Разводка гантелей лёжа", "Грудь", "разводка гантели", R.drawable.ex_dumbbell_flyes_0, R.drawable.ex_dumbbell_flyes_1, dumbbell = true),
    ExercisePhoto("Отжимания на брусьях на грудь", "Грудь", "собственный вес", R.drawable.ex_dips_chest_version_0, R.drawable.ex_dips_chest_version_1),
    ExercisePhoto("Отжимания от пола", "Грудь", "собственный вес", R.drawable.ex_pushups_0, R.drawable.ex_pushups_1),
    ExercisePhoto("Жим в тренажёре сидя", "Грудь", "тренажер хаммер", R.drawable.ex_leverage_chest_press_0, R.drawable.ex_leverage_chest_press_1),

    ExercisePhoto("Тяга штанги в наклоне", "Спина", "штанга наклон", R.drawable.ex_bent_over_barbell_row_0, R.drawable.ex_bent_over_barbell_row_1),
    ExercisePhoto("Т-тяга", "Спина", "т-гриф тбар тяга в наклоне", R.drawable.ex_tbar_row_with_handle_0, R.drawable.ex_tbar_row_with_handle_1),
    ExercisePhoto("Тяга верхнего блока узким хватом", "Спина", "верхний блок узкий", R.drawable.ex_closegrip_front_lat_pulldown_0, R.drawable.ex_closegrip_front_lat_pulldown_1),
    ExercisePhoto("Гиперэкстензия", "Спина", "поясница разгибание спины", R.drawable.ex_hyperextensions_back_extensions_0, R.drawable.ex_hyperextensions_back_extensions_1),
    ExercisePhoto("Пуловер на блоке прямыми руками", "Спина", "широчайшие прямые руки", R.drawable.ex_straightarm_pulldown_0, R.drawable.ex_straightarm_pulldown_1),
    ExercisePhoto("Шраги с гантелями", "Спина", "трапеции шраги", R.drawable.ex_dumbbell_shrug_0, R.drawable.ex_dumbbell_shrug_1, dumbbell = true),

    ExercisePhoto("Румынская тяга", "Ноги", "штанга бицепс бедра", R.drawable.ex_romanian_deadlift_0, R.drawable.ex_romanian_deadlift_1),
    ExercisePhoto("Выпады с гантелями", "Ноги", "выпады", R.drawable.ex_dumbbell_lunges_0, R.drawable.ex_dumbbell_lunges_1, dumbbell = true),
    ExercisePhoto("Болгарские сплит-приседания", "Ноги", "болгарские сплит выпады гантели", R.drawable.ex_split_squat_with_dumbbells_0, R.drawable.ex_split_squat_with_dumbbells_1, dumbbell = true),
    ExercisePhoto("Подъём на носки стоя", "Ноги", "икры голень тренажер", R.drawable.ex_standing_calf_raises_0, R.drawable.ex_standing_calf_raises_1),
    ExercisePhoto("Подъём на носки сидя", "Ноги", "икры голень тренажер", R.drawable.ex_seated_calf_raise_0, R.drawable.ex_seated_calf_raise_1),
    ExercisePhoto("Ягодичный мост со штангой", "Ноги", "ягодицы хип траст мост", R.drawable.ex_barbell_hip_thrust_0, R.drawable.ex_barbell_hip_thrust_1),
    ExercisePhoto("Гоблет-присед", "Ноги", "присед гантель кубок", R.drawable.ex_goblet_squat_0, R.drawable.ex_goblet_squat_1, dumbbell = true),
    ExercisePhoto("Гакк-присед", "Ноги", "гак тренажер присед", R.drawable.ex_hack_squat_0, R.drawable.ex_hack_squat_1),
    ExercisePhoto("Сведение ног в тренажёре", "Ноги", "приводящие внутренняя поверхность бедра", R.drawable.ex_thigh_adductor_0, R.drawable.ex_thigh_adductor_1),
    ExercisePhoto("Разведение ног в тренажёре", "Ноги", "отводящие ягодицы", R.drawable.ex_thigh_abductor_0, R.drawable.ex_thigh_abductor_1),
    ExercisePhoto("Приседания в Смите", "Ноги", "смит тренажер присед", R.drawable.ex_smith_machine_squat_0, R.drawable.ex_smith_machine_squat_1),

    ExercisePhoto("Армейский жим стоя", "Плечи", "штанга над головой", R.drawable.ex_standing_military_press_0, R.drawable.ex_standing_military_press_1),
    ExercisePhoto("Разведение гантелей в наклоне", "Плечи", "задняя дельта махи", R.drawable.ex_reverse_flyes_0, R.drawable.ex_reverse_flyes_1, dumbbell = true),
    ExercisePhoto("Подъём гантелей перед собой", "Плечи", "передняя дельта махи", R.drawable.ex_front_dumbbell_raise_0, R.drawable.ex_front_dumbbell_raise_1, dumbbell = true),
    ExercisePhoto("Тяга штанги к подбородку", "Плечи", "протяжка", R.drawable.ex_upright_barbell_row_0, R.drawable.ex_upright_barbell_row_1),
    ExercisePhoto("Тяга каната к лицу", "Плечи", "фейс пулл задняя дельта блок", R.drawable.ex_face_pull_0, R.drawable.ex_face_pull_1),

    ExercisePhoto("Подъём штанги на бицепс", "Руки", "бицепс штанга", R.drawable.ex_barbell_curl_0, R.drawable.ex_barbell_curl_1),
    ExercisePhoto("Подъём гантелей на бицепс поочерёдно", "Руки", "бицепс гантели", R.drawable.ex_dumbbell_alternate_bicep_curl_0, R.drawable.ex_dumbbell_alternate_bicep_curl_1, dumbbell = true),
    ExercisePhoto("Сгибание рук на нижнем блоке", "Руки", "бицепс блок кроссовер", R.drawable.ex_standing_biceps_cable_curl_0, R.drawable.ex_standing_biceps_cable_curl_1),
    ExercisePhoto("Сгибание рук на скамье Скотта", "Руки", "скотт бицепс", R.drawable.ex_preacher_curl_0, R.drawable.ex_preacher_curl_1),
    ExercisePhoto("Разгибание рук на блоке с V-рукоятью", "Руки", "трицепс сверху вниз", R.drawable.ex_triceps_pushdown_vbar_attachment_0, R.drawable.ex_triceps_pushdown_vbar_attachment_1),
    ExercisePhoto("Разгибание из-за головы на блоке с канатом", "Руки", "трицепс кроссовер", R.drawable.ex_cable_rope_overhead_triceps_extension_0, R.drawable.ex_cable_rope_overhead_triceps_extension_1),
    ExercisePhoto("Французский жим EZ-штангой", "Руки", "трицепс лежа", R.drawable.ex_ezbar_skullcrusher_0, R.drawable.ex_ezbar_skullcrusher_1),
    ExercisePhoto("Жим штанги узким хватом", "Руки", "трицепс лежа", R.drawable.ex_closegrip_barbell_bench_press_0, R.drawable.ex_closegrip_barbell_bench_press_1),
    ExercisePhoto("Отжимания на брусьях на трицепс", "Руки", "собственный вес", R.drawable.ex_dips_triceps_version_0, R.drawable.ex_dips_triceps_version_1),

    ExercisePhoto("Скручивания", "Пресс", "лежа", R.drawable.ex_crunches_0, R.drawable.ex_crunches_1),
    ExercisePhoto("Подъём ног в висе", "Пресс", "турник", R.drawable.ex_hanging_leg_raise_0, R.drawable.ex_hanging_leg_raise_1),
    ExercisePhoto("Планка", "Пресс", "статика кор", R.drawable.ex_plank_0, R.drawable.ex_plank_1),
    ExercisePhoto("Скручивания на блоке", "Пресс", "блок молитва на коленях", R.drawable.ex_cable_crunch_0, R.drawable.ex_cable_crunch_1)
)

private fun String.searchKey() = trim().lowercase().replace('ё', 'е')

/** Ищет упражнения каталога по всем словам запроса в названии, группе мышц и синонимах. */
internal fun searchExercises(query: String): List<ExercisePhoto> {
    val words = query.searchKey().split(Regex("""\s+""")).filter(String::isNotEmpty)
    return exerciseCatalog.filter { item ->
        val text = "${item.name} ${item.muscle} ${item.keywords}".searchKey()
        words.all(text::contains)
    }
}
internal fun photoForExercise(name: String) = exerciseCatalog.firstOrNull {
    it.name.searchKey() == name.searchKey()
}

internal fun usesDumbbell(name: String): Boolean =
    photoForExercise(name)?.dumbbell ?: ("гантел" in name.searchKey())

@Composable
internal fun ExercisePicker(onSelect: (ExercisePhoto) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { searchExercises(query) }
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
