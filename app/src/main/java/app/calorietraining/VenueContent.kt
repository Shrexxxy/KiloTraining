package app.calorietraining

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun VenueEditorFields(draft: FoodDraft, venues: List<Venue>, busy: Boolean, event: (FoodEvent) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val query = venueKey(draft.venueName)
    val matches = remember(venues, query, draft.venueName) {
        if (query.isEmpty()) emptyList() else venues.filter {
            it.name != draft.venueName && venueKey(it.name).contains(query)
        }.take(4)
    }
    val camera = rememberVenueCamera(event)
    Column {
        DiaryTextField(draft.venueName, { event(FoodEvent.Edit(draft.copy(venueName = it))) },
            Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
            label = { Text("Заведение") }, singleLine = true,
            supportingText = { Text("Необязательно") })
        if (focused && matches.isNotEmpty()) {
            GlassCard(MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) {
                Column {
                    matches.forEach { venue ->
                        Text(venue.name, Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            event(FoodEvent.Edit(draft.copy(venueName = venue.name)))
                        }.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
    val photo = draft.photoPath
    if (photo == null) {
        OutlinedButton(onClick = camera, enabled = !busy) { Text("Фото блюда") }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VenueThumbnail(photo, Modifier.size(width = 96.dp, height = 72.dp), "Фото блюда")
            IconButton(onClick = { event(FoodEvent.Edit(draft.copy(photoPath = null, photoHash = null))) }, enabled = !busy) {
                Icon(painterResource(R.drawable.ic_close), "Убрать фото")
            }
        }
    }
}

@Composable
internal fun VenuesList(venues: List<Venue>, accent: Color,
    onChoose: (Venue) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (venues.isEmpty()) item {
            Text("Укажите заведение в записи, и оно появится здесь.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(venues, key = Venue::id) { venue ->
            GlassCard(accent, Modifier.fillMaxWidth(), onClick = { onChoose(venue) }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VenueLogo(venue, Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(venue.name, style = MaterialTheme.typography.titleMedium)
                        Text("Блюд: ${venue.dishCount}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun VenueScreen(venue: Venue, meal: Meal, state: FoodUiState,
    event: (FoodEvent) -> Unit, modifier: Modifier = Modifier) {
    fun back() = event(FoodEvent.BackFromVenue)
    BackHandler(onBack = ::back)
    var pickingLogo by rememberSaveable(venue.id) { mutableStateOf(false) }
    val camera = rememberVenueCamera(event, venue.id)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) event(FoodEvent.VenuePhoto(uri, venueId = venue.id))
    }
    val accent = glassAccent(meal)
    Scaffold(modifier, containerColor = Color.Transparent) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
            ScreenHeader(venue.name, onBack = ::back, backEnabled = !state.busy)
            TextButton(onClick = { pickingLogo = true }, enabled = !state.busy, contentPadding = PaddingValues(4.dp)) {
                VenueLogo(venue, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (venue.logoPath == null) "Добавить логотип" else "Изменить логотип")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Блюда", Modifier.padding(top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.venueDishes.isEmpty()) item {
                    Text("Здесь пока нет записанных блюд.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.venueDishes, key = FoodDraft::name) { dish ->
                    GlassCard(accent, Modifier.fillMaxWidth(), onClick = { event(FoodEvent.Edit(dish.copy(meal = meal))) },
                        enabled = !state.busy) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            dish.photoPath?.let { VenueThumbnail(it, Modifier.size(48.dp), "Фото блюда") }
                            Column(Modifier.weight(1f)) {
                                Text(dish.name, style = MaterialTheme.typography.titleMedium)
                                Text("${dish.calories?.display().orEmpty()} ккал · ${dish.portionMacros.label()}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (pickingLogo) AlertDialog(
        onDismissRequest = { pickingLogo = false },
        title = { Text("Логотип заведения") },
        text = {
            Column {
                TextButton(onClick = {
                    pickingLogo = false
                    camera()
                }) { Text("Сфотографировать") }
                TextButton(onClick = {
                    pickingLogo = false
                    try {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (e: android.content.ActivityNotFoundException) {
                        event(FoodEvent.VenuePhotoFailed)
                    }
                }) { Text("Выбрать из галереи") }
            }
        },
        confirmButton = { TextButton(onClick = { pickingLogo = false }) { Text("Отмена") } },
    )
}

@Composable
private fun VenueLogo(venue: Venue, modifier: Modifier) {
    VenueThumbnail(venue.logoPath, modifier.clip(CircleShape), fallback = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Text(venue.name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleLarge)
        }
    })
}

@Composable
internal fun VenueThumbnail(path: String?, modifier: Modifier, description: String? = null,
    fallback: @Composable () -> Unit = {}) {
    val image by produceState<ImageBitmap?>(null, path) {
        value = null
        if (path != null) value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            try {
                File(path).inputStream().use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
            } catch (e: java.io.IOException) {
                null
            }
        }
    }
    Box(modifier.clip(MaterialTheme.shapes.small)) {
        val bitmap = image
        if (bitmap != null) Image(bitmap, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else fallback()
    }
}

@Composable
private fun rememberVenueCamera(event: (FoodEvent) -> Unit, venueId: Long? = null): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(remember { VenueTakePicture() }) { success ->
        val file = pending?.let(::File)
        pending = null
        if (file != null) {
            if (success) event(FoodEvent.VenuePhoto(Uri.fromFile(file), file, venueId)) else file.delete()
        }
    }
    return {
        if (pending == null) {
            try {
                val file = context.newVenueCapture()
                pending = file.absolutePath
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.venue-camera", file))
            } catch (e: Exception) {
                android.util.Log.w("VenueCamera", "Не удалось запустить камеру", e)
                pending?.let { File(it).delete() }
                pending = null
                event(FoodEvent.VenuePhotoFailed)
            }
        }
    }
}

private class VenueTakePicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            clipData = ClipData.newRawUri(null, input)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}
