package crescentric.freeprint.otg.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import crescentric.freeprint.otg.R

/*
 * Pieces used by both the print screen and the scan screen, so the two look and behave the same: settings rows, the
 * single-choice list, the swipe-to-change-page gesture, the strip of page thumbnails and the zoomable full-screen viewer.
 * "Sheet" (print) and "page" (scan) are the same idea here: one item in a row of items, one of which is current.
 */

internal const val SWIPE_DISTANCE_PX = 90f

/**
 * How one page gives way to another in every preview: a later page slides in from the right, an earlier one from the left,
 * and a new picture of the same page (an option changed, or a sharper render arrived) just cross-fades. [from] and [to] are the
 * page numbers of the picture leaving and the one arriving; null means there was none.
 */
internal fun pageFlipTransition(from: Int?, to: Int?): ContentTransform {
    val slide = tween<IntOffset>(280, easing = FastOutSlowInEasing)
    val (enter, exit) = when {
        from == null || to == null -> fadeIn(tween(200)) to fadeOut(tween(200))
        to > from -> (slideInHorizontally(slide) { w -> w } + fadeIn(tween(250))) to (slideOutHorizontally(slide) { w -> -w } + fadeOut(tween(200)))
        to < from -> (slideInHorizontally(slide) { w -> -w } + fadeIn(tween(250))) to (slideOutHorizontally(slide) { w -> w } + fadeOut(tween(200)))
        else -> fadeIn(tween(220)) to fadeOut(tween(220))
    }
    return ContentTransform(targetContentEnter = enter, initialContentExit = exit, sizeTransform = SizeTransform(clip = false))
}

/** A picture for the full-screen viewer together with the page number it shows, so the viewer can slide the right way. */
internal class ViewerImage(val index: Int, val bitmap: ImageBitmap)

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

/** A settings tile for a two-column grid: icon, name and the current value underneath. */
@Composable
internal fun SettingTile(icon: ImageVector, title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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

/**
 * [accent] itself when it is bright enough to read on a black background, otherwise the same colour mixed towards white until it
 * is, so the highlight keeps its hue instead of turning plain white.
 */
internal fun readableOnDark(accent: Color): Color {
    var c = accent
    var step = 0
    while (c.luminance() < MIN_LUMINANCE_ON_DARK && step < 10) {
        c = lerp(c, Color.White, 0.12f)
        step++
    }
    return c
}

/** Luminance (0 black to 1 white) an accent needs to stand out on the viewer's near-black background. */
internal const val MIN_LUMINANCE_ON_DARK = 0.22f

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
    scrollTick: Int = 0,
) {
    if (count <= 1) return
    val listState = rememberLazyListState()
    val thumbWidthPx = with(LocalDensity.current) { 70.dp.toPx() }
    val latestCurrent = rememberUpdatedState(current)
    val latestCount = rememberUpdatedState(count)

    // Centre the current item when the strip appears and whenever [scrollTick] changes. The owner raises the tick when the user
    // moves to another item (swipe, arrows, a tap on a thumbnail) and not when an item is deleted, so deleting leaves the strip
    // where it is even though the current number or the item count changes.
    LaunchedEffect(scrollTick) {
        val viewport = listState.layoutInfo.viewportSize.width
        listState.animateScrollToItem(latestCurrent.value.coerceIn(0, latestCount.value - 1), -(viewport / 2 - (thumbWidthPx / 2).toInt()))
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
            // The same accent as the normal screen; on the black viewer it is only lightened when the theme's colour would be too dark to see.
            val accent = if (onDark) readableOnDark(MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.primary
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
 * swipe changes item. [strip] is shown below the image and is told whether the viewer is dark, so it can pass that to [ThumbStrip].
 * The viewer follows the app's light or dark theme.
 */
@Composable
internal fun ZoomableViewer(
    image: ViewerImage?,
    grayscale: Boolean,
    title: String,
    description: String,
    hint: String,
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    strip: @Composable (onDark: Boolean) -> Unit,
    actions: @Composable RowScope.(tint: Color) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Zoom and pan belong to the item on screen: they start again as soon as another item is chosen, in the same frame, so
        // the page sliding out and the one sliding in are never drawn with the old zoom.
        var scale by remember(current) { mutableFloatStateOf(1f) }
        var offset by remember(current) { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }

        fun clamp(o: Offset, s: Float): Offset {
            val mx = box.width * (s - 1f) / 2f
            val my = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }

        // Follow the theme: near-black with white controls in dark mode, a light grey with dark controls in light mode.
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val backdrop = if (dark) Color(0xFF111111) else MaterialTheme.colorScheme.surfaceVariant
        val barBackdrop = if (dark) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        val stripBackdrop = if (dark) Color(0xFF1B1B1B) else MaterialTheme.colorScheme.surface
        val content = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
        val disabled = if (dark) Color.Gray else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

        // The dialog is its own window, so its status and navigation bar icons must be set to contrast with the backdrop.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }

        Column(Modifier.fillMaxSize().background(backdrop)) {
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
                AnimatedContent(
                    targetState = image,
                    transitionSpec = { pageFlipTransition(initialState?.index, targetState?.index) },
                    modifier = Modifier.fillMaxSize(),
                    label = "viewer_page_flip",
                ) { shown ->
                    if (shown != null) {
                        Image(
                            bitmap = shown.bitmap,
                            contentDescription = description,
                            contentScale = ContentScale.Fit,
                            colorFilter = if (grayscale) GRAY_FILTER else null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = if (dark) Color.White else MaterialTheme.colorScheme.primary) }
                    }
                }

                // Top bar: close, position, previous and next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(barBackdrop)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = content) }
                    Text(
                        title + if (scale > 1.02f) "  •  ${"%.1f".format(scale)}x" else "",
                        color = content,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    actions(content)
                    IconButton(onClick = { onSelect(current - 1) }, enabled = current > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_prev_sheet), tint = if (current > 0) content else disabled)
                    }
                    IconButton(onClick = { onSelect(current + 1) }, enabled = current < count - 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_sheet), tint = if (current < count - 1) content else disabled)
                    }
                }
            }

            // Thumbnails, then the hint
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(stripBackdrop)
                    .navigationBarsPadding()
                    .padding(top = 6.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                strip(dark)
                Text(
                    hint,
                    color = content.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A plain icon-and-label action, with no background or padding, that puts back pages that were removed: "Restore 3 pages". Replaces nothing when there is nothing to restore. */
@Composable
internal fun RestoreButton(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            pluralStringResource(R.plurals.restore_pages, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** The small "Ready" badge with a softly pulsing dot, shared by the printer card and the scanner card. */
@Composable
internal fun ReadyBadge() {
    val infinite = rememberInfiniteTransition(label = "ready_pulse")
    val pulseScale by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse_scale"
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse_alpha"
    )
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier.size(6.dp)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = pulseAlpha }
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Text(stringResource(R.string.ready), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        }
    }
}
