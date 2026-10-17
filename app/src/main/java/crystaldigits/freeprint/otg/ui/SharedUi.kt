package crystaldigits.freeprint.otg.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import crystaldigits.freeprint.otg.R

/*
 * Pieces used by both the print screen and the scan screen, so the two look and behave the same: settings rows, the
 * single-choice list, the swipe-to-change-page gesture, the strip of page thumbnails and the zoomable full-screen viewer.
 * "Sheet" (print) and "page" (scan) are the same idea here: one item in a row of items, one of which is current.
 */

internal const val SWIPE_DISTANCE_PX = 90f

/** Horizontal swipe on this element moves to the previous or next item. */
internal fun Modifier.swipeToChangeSheet(current: Int, count: Int, onSelect: (Int) -> Unit): Modifier =
    pointerInput(current, count) {
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragCancel = { dx = 0f },
            onDragEnd = {
                when {
                    dx < -SWIPE_DISTANCE_PX && current < count - 1 -> onSelect(current + 1)
                    dx > SWIPE_DISTANCE_PX && current > 0 -> onSelect(current - 1)
                }
            },
        ) { _, drag -> dx += drag }
    }

/** One tappable settings row: icon, name, the current value underneath, and an arrow. */
@Composable
internal fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Single-choice list: tapping a row picks it. */
@Composable
internal fun <T> RadioList(
    items: List<T>, selected: T, label: @Composable (T) -> String,
    description: (@Composable (T) -> String?)? = null,
    onPick: (T) -> Unit,
) {
    Column {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(item) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = item == selected, onClick = { onPick(item) })
                Column(Modifier.padding(end = 8.dp)) {
                    Text(label(item), style = MaterialTheme.typography.bodyLarge)
                    description?.invoke(item)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val GRAY_FILTER get() = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * A row of thumbnails, one per item. Tapping one selects it; the current one has a thicker outline and a bold label,
 * and the row scrolls to keep it centred. Thumbnails are produced lazily by [bitmapFor] as they scroll into view.
 * Hidden when there is only one item. [onDark] switches to colours that read on a black background.
 */
@Composable
internal fun ThumbStrip(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    aspectFor: (Int) -> Float,
    labelFor: (Int) -> String,
    descriptionFor: @Composable (Int) -> String,
    bitmapFor: @Composable (Int) -> ImageBitmap?,
    onDark: Boolean = false,
    grayscale: Boolean = false,
    onDelete: ((Int) -> Unit)? = null,
    deleteDescription: @Composable (Int) -> String = { "" },
) {
    if (count <= 1) return
    val listState = rememberLazyListState()
    val thumbWidthPx = with(LocalDensity.current) { 70.dp.toPx() }

    // Keep the current item centred when it changes (arrows, swipes or a tap on a thumbnail).
    LaunchedEffect(current, count) {
        val viewport = listState.layoutInfo.viewportSize.width
        listState.animateScrollToItem(current.coerceIn(0, count - 1), -(viewport / 2 - (thumbWidthPx / 2).toInt()))
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        items(count, key = { it }) { index ->
            val active = index == current
            val bmp = bitmapFor(index)
            // On the black viewer the accent colour may be too dim, so the current item is outlined in white there.
            val accent = if (onDark) Color.White else MaterialTheme.colorScheme.primary
            val borderColor = if (active) accent else if (onDark) Color(0xFF666666) else MaterialTheme.colorScheme.outlineVariant
            val labelColor = if (active) accent else if (onDark) Color(0xFFBBBBBB) else MaterialTheme.colorScheme.onSurfaceVariant
            val description = descriptionFor(index)

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(84.dp)
                        .aspectRatio(aspectFor(index))
                        .shadow(if (active) 4.dp else 1.dp, RoundedCornerShape(3.dp))
                        .background(Color.White, RoundedCornerShape(3.dp))
                        .border(BorderStroke(if (active) 2.5.dp else 1.dp, borderColor), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = description,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = if (grayscale) GRAY_FILTER else null
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))
                    }
                    // A small round button in the corner removes this item. It sits inside the thumbnail, so a tap on it
                    // does not also select the thumbnail. Not offered when there is only one item (the strip is hidden).
                    if (onDelete != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .clickable { onDelete(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = deleteDescription(index), tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                Text(
                    labelFor(index),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Full-screen viewer: pinch to zoom (up to 8x), drag to pan, double-tap to zoom in or out. While not zoomed, a horizontal
 * swipe changes item. [strip] is shown below the image (a [ThumbStrip] with onDark = true).
 */
@Composable
internal fun ZoomableViewer(
    image: ImageBitmap?,
    grayscale: Boolean,
    title: String,
    description: String,
    hint: String,
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    strip: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        // A different item starts un-zoomed.
        LaunchedEffect(current) { scale = 1f; offset = Offset.Zero }

        fun clamp(o: Offset, s: Float): Offset {
            val mx = box.width * (s - 1f) / 2f
            val my = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }

        Column(Modifier.fillMaxSize().background(Color(0xFF111111))) {
            // The zoomable image area. Gestures live here only, so the thumbnail strip below scrolls on its own.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { box = it }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else scale = 3f
                        })
                    }
                    .pointerInput(count, current) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var panX = 0f
                            var multiTouch = false
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) multiTouch = true
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                                    val c = centroid - Offset(box.width / 2f, box.height / 2f)
                                    val factor = newScale / scale
                                    offset = clamp(c - (c - offset) * factor + pan, newScale)
                                    scale = newScale
                                    panX += pan.x
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (scale <= 1.02f && !multiTouch) {
                                when {
                                    panX < -SWIPE_DISTANCE_PX && current < count - 1 -> onSelect(current + 1)
                                    panX > SWIPE_DISTANCE_PX && current > 0 -> onSelect(current - 1)
                                }
                            }
                        }
                    }
            ) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (grayscale) GRAY_FILTER else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                    )
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }

                // Top bar: close, position, previous and next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White) }
                    Text(
                        title + if (scale > 1.02f) "  •  ${"%.1f".format(scale)}x" else "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onSelect(current - 1) }, enabled = current > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_prev_sheet), tint = if (current > 0) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { onSelect(current + 1) }, enabled = current < count - 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_sheet), tint = if (current < count - 1) Color.White else Color.Gray)
                    }
                }
            }

            // Thumbnails, then the hint
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1B))
                    .navigationBarsPadding()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                strip()
                Text(
                    hint,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
