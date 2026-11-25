package app.calorietraining

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

private const val MaxScale = 4f
private const val DoubleTapScale = 2.5f

/** Смещение увеличенного фото; нужно проверкам и не озвучивается. */
internal val ZoomPanKey = SemanticsPropertyKey<Offset>("ZoomPan")
internal var SemanticsPropertyReceiver.zoomPan by ZoomPanKey

/** Кадры упражнения на весь экран с листанием, щипком и двойным нажатием. */
@Composable
internal fun ExercisePhotoViewer(photo: ExercisePhoto, frame: Int, onFrame: (Int) -> Unit, onClose: () -> Unit) {
    // Окно рисуется под системными панелями (decorFitsSystemWindows = false), «Закрыть» отступает по safeDrawing.
    val properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    Dialog(onDismissRequest = onClose, properties = properties) {
        val pager = rememberPagerState(initialPage = frame) { 2 }
        var scale by remember { mutableFloatStateOf(1f) }
        LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onFrame(it); scale = 1f } }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = scale == 1f) { page ->
                val active = page == pager.currentPage
                ZoomableFrame(
                    resource = if (page == 0) photo.first else photo.second,
                    description = "Фото: ${photo.name}, кадр ${page + 1}",
                    scale = if (active) scale else 1f,
                    onScale = { scale = it },
                )
            }
            TextButton(onClose, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
                Text("Закрыть", color = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableFrame(resource: Int, description: String, scale: Float, onScale: (Float) -> Unit) {
    val painter = painterResource(resource)
    var rawPan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(Size.Zero) }
    val currentScale by rememberUpdatedState(scale)
    // Границы считаются при каждом чтении, поэтому следуют за размером окна и масштабом.
    fun limit(value: Offset, forScale: Float): Offset {
        val image = painter.intrinsicSize
        val fit = if (image.isSpecified && image.width > 0f && image.height > 0f)
            minOf(box.width / image.width, box.height / image.height) else 1f
        val shown = if (image.isSpecified) Size(image.width * fit, image.height * fit) else box
        val x = maxOf(0f, (shown.width * forScale - box.width) / 2f)
        val y = maxOf(0f, (shown.height * forScale - box.height) / 2f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val pan = limit(rawPan, scale)
    LaunchedEffect(scale) { if (scale == 1f) rawPan = Offset.Zero }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = description
                stateDescription = "Масштаб ${(scale * 100).roundToInt()}%"
                zoomPan = pan
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (currentScale > 1f) { onScale(1f); rawPan = Offset.Zero } else onScale(DoubleTapScale)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Между событиями жеста состояние Compose может не успеть обновиться.
                    var live = currentScale
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        // Одним пальцем двигаем только увеличенное, иначе жест достаётся листанию.
                        if (event.changes.size > 1 || live > 1f) {
                            val next = (live * event.calculateZoom()).coerceIn(1f, MaxScale)
                            rawPan = limit(rawPan + event.calculatePan(), next)
                            if (next != live) onScale(next)
                            live = next
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y),
    ) {
        Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
