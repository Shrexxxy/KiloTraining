package app.calorietraining

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class ExercisePhoto(
    val name: String,
    val muscle: String,
    val keywords: String,
    val first: Int,
    val second: Int,
    val equipment: Equipment,
    val technique: ExerciseTechnique,
    val dumbbell: Boolean = false,
    val searchPhrase: String? = null,
)

internal val exerciseCatalog = (chestCatalog + backCatalog + legsCatalog + shouldersCatalog + armsCatalog + coreCatalog)
    .sortedBy { it.first }.map { it.second }

internal fun String.searchKey() = trim().lowercase().replace('ё', 'е')

/** Ищет упражнения каталога по всем словам запроса в названии, группе мышц и синонимах. */
internal fun searchExercises(
    query: String,
    group: String? = null,
    equipment: Equipment? = null,
): List<ExercisePhoto> {
    val words = query.searchKey().split(Regex("""\s+""")).filter(String::isNotEmpty)
    return exerciseCatalog.filter { item ->
        val phrase = item.searchPhrase?.takeIf { words.containsAll(it.searchKey().split(" ")) }.orEmpty()
        val text = "${item.name} ${item.muscle} ${item.keywords} $phrase".searchKey()
        (group == null || group == item.muscle) &&
            (equipment == null || equipment == item.equipment) && words.all(text::contains)
    }
}

internal fun similarExercises(exercise: ExercisePhoto): List<ExercisePhoto> =
    exerciseCatalog.asSequence()
        .filter { it != exercise && it.technique.primary.any(exercise.technique.primary::contains) }
        .sortedBy { it.muscle != exercise.muscle }
        .take(4)
        .toList()

internal fun photoForExercise(name: String) = exerciseCatalog.firstOrNull {
    it.name.searchKey() == name.searchKey()
}

internal fun usesDumbbell(name: String): Boolean =
    photoForExercise(name)?.dumbbell ?: ("гантел" in name.searchKey())

private val exerciseGroups = listOf("Грудь", "Спина", "Ноги", "Плечи", "Руки", "Пресс")

@Composable
internal fun ExercisePicker(onSelect: (ExercisePhoto) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val groupScroll = rememberScrollState()
    val equipmentScroll = rememberScrollState()
    details?.let(::photoForExercise)?.let { exercise ->
        ExerciseMusclesScreen(exercise, onBack = { details = null }, modifier = modifier)
        return
    }
    val matches = remember(query, group, equipment) { searchExercises(query, group, equipment) }
    fun resetList() = listState.requestScrollToItem(0)
    Column(modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ScreenHeader("Каталог упражнений", onBack = onClose)
        DiaryTextField(query, {
            query = it
            resetList()
        }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Название или группа мышц") })
        Row(Modifier.fillMaxWidth().horizontalScroll(groupScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiaryFilterChip(selected = group == null, onClick = {
                group = null
                resetList()
            }, label = { Text("Все") })
            exerciseGroups.forEach { value ->
                DiaryFilterChip(
                    selected = group == value,
                    onClick = {
                        group = if (group == value) null else value
                        resetList()
                    },
                    label = { Text(value, maxLines = 1, softWrap = false) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(equipmentScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.entries.forEach { value ->
                DiaryFilterChip(
                    selected = equipment == value,
                    onClick = {
                        equipment = if (equipment == value) null else value
                        resetList()
                    },
                    label = { Text(value.label, maxLines = 1, softWrap = false) },
                    leadingIcon = { EquipmentIcon(value) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Найдено: ${matches.size}", style = MaterialTheme.typography.bodyMedium)
            if (query.isNotEmpty() || group != null || equipment != null) {
                TextButton(onClick = {
                    query = ""
                    group = null
                    equipment = null
                    resetList()
                }) {
                    Text("Сбросить")
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            if (matches.isEmpty()) item { Text("Совпадений нет", Modifier.padding(vertical = 16.dp)) }
            items(matches, key = { it.name }) { item ->
                Row(
                    Modifier.fillMaxWidth().semantics { isTraversalGroup = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).clickable { onSelect(item) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(painterResource(item.second), null, Modifier.size(84.dp, 66.dp), contentScale = ContentScale.Fit)
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(item.muscle, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                EquipmentIcon(item.equipment)
                                Text(item.equipment.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ExerciseMusclesButton { details = item.name }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EquipmentIcon(equipment: Equipment) {
    Icon(painterResource(equipment.icon), contentDescription = null, tint = LocalDiaryAccents.current.workout)
}

@Composable
internal fun ExerciseFrames(photo: ExercisePhoto, modifier: Modifier = Modifier) {
    var opened by rememberSaveable(photo.name) { mutableStateOf<Int?>(null) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(photo.first, photo.second).forEachIndexed { index, resource ->
            Image(painterResource(resource), "Открыть фото: ${photo.name}, кадр ${index + 1}",
                Modifier.weight(1f).height(108.dp).clickable { opened = index }, contentScale = ContentScale.Fit)
        }
    }
    opened?.let { ExercisePhotoViewer(photo, it, { frame -> opened = frame }) { opened = null } }
}
