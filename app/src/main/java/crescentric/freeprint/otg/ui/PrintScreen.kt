package crescentric.freeprint.otg.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import crescentric.freeprint.otg.print.MAX_COPIES
import crescentric.freeprint.otg.print.MIN_COPIES
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.DocumentScanner
import crescentric.freeprint.otg.print.effectiveMarginMm
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import crescentric.freeprint.otg.AppLocale
import crescentric.freeprint.otg.AppLocale.findActivity
import crescentric.freeprint.otg.JobState
import crescentric.freeprint.otg.PrintViewModel
import crescentric.freeprint.otg.R
import crescentric.freeprint.otg.print.Anchor
import crescentric.freeprint.otg.print.FitMode
import crescentric.freeprint.otg.print.MAX_SCALE_PERCENT
import crescentric.freeprint.otg.print.MIN_SCALE_PERCENT
import crescentric.freeprint.otg.print.Orientation
import crescentric.freeprint.otg.print.PageOrder
import crescentric.freeprint.otg.print.PageSelection
import crescentric.freeprint.otg.print.Paper
import crescentric.freeprint.otg.print.Parity
import crescentric.freeprint.otg.print.PerSheet
import crescentric.freeprint.otg.print.Quality
import crescentric.freeprint.otg.print.TwoSided


private enum class PrinterStatusType {
    DISCONNECTED,
    PERMISSION_NEEDED,
    READY
}

/**
 * The print tab: the printer's status, then either the empty state or the document workspace (file list, preview, settings).
 * The scaffold, top bar, bottom bar and dock belong to [HomeScreen]; [modifier] carries its padding.
 */
@Composable
fun PrintContent(vm: PrintViewModel, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.replaceFiles(uris)
    }
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Printer connection status banner with animated transitions
        PrinterStatusCard(vm = vm, onRescan = vm::refreshPrinter)

        // 2. Animated transition between Empty Hero and Document Print Workspace
        AnimatedContent(
            targetState = vm.doc != null,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(350)) + slideInVertically(tween(350)) { 50 }).togetherWith(
                        fadeOut(tween(200)) + shrinkVertically(tween(200))
                    )
                } else {
                    (fadeIn(tween(350)) + expandVertically(tween(350))).togetherWith(
                        fadeOut(tween(200)) + slideOutVertically(tween(200)) { 50 }
                    )
                }
            },
            label = "doc_workspace"
        ) { hasDoc ->
            if (!hasDoc) {
                EmptyDocumentHero(
                    onChooseFile = { picker.launch(arrayOf("application/pdf", "image/*")) }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DocumentPreviewSection(
                        vm = vm,
                        onAddFiles = { addPicker.launch(arrayOf("application/pdf", "image/*")) }
                    )

                    // 3. Print settings: one row per setting, each opens a popup
                    SettingsCard(vm)
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    (vm.job as? JobState.NeedsFlip)?.let { FlipDialog(it, onContinue = vm::continueTwoSided, onStop = vm::cancelTwoSided) }

}

/** Modern status card showing USB printer connection state with living animations */
@Composable
private fun PrinterStatusCard(
    vm: PrintViewModel,
    onRescan: () -> Unit
) {
    val name = vm.printerName
    val hasPermission = vm.hasPermission

    val currentStatus = when {
        name == null -> PrinterStatusType.DISCONNECTED
        !hasPermission -> PrinterStatusType.PERMISSION_NEEDED
        else -> PrinterStatusType.READY
    }

    AnimatedContent(
        targetState = currentStatus,
        transitionSpec = {
            (fadeIn(tween(300)) + expandVertically(tween(300))).togetherWith(
                fadeOut(tween(200)) + shrinkVertically(tween(200))
            )
        },
        label = "printer_status_card"
    ) { status ->
        when (status) {
            PrinterStatusType.DISCONNECTED -> {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.UsbOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.printer_none_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.printer_none_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRescan) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_rescan),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            PrinterStatusType.PERMISSION_NEEDED -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name ?: stringResource(R.string.printer_fallback),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.printer_permission_needed),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Button(
                            onClick = vm::requestPermission,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            }

            PrinterStatusType.READY -> {
                // The tint is laid onto the surface colour first so the card is opaque: a see-through card shows its own shadow
                // and surface behind it as a second rounded box.
                val readyTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f).compositeOver(MaterialTheme.colorScheme.surface)
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = readyTint),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // A long name wraps onto more lines; the badge keeps its shape
                                Text(
                                    name ?: stringResource(R.string.printer_fallback),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                ReadyBadge()
                            }
                            Text(
                                stringResource(if (vm.hostBased) R.string.printer_mode_spl else R.string.printer_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRescan) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Inviting empty state hero card with soft hover shadow */
@Composable
private fun EmptyDocumentHero(
    onChooseFile: () -> Unit
) {
    // No card behind it: the picture, the words and the button sit directly on the page
    run {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_printer_hero),
                contentDescription = stringResource(R.string.cd_printer_hero),
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.hero_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("PDF", "JPG", "PNG", "WEBP").forEach { format ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            format,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onChooseFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.select_document), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Document details bar and live sheet preview canvas with page-turn slide animation */
@Composable
private fun DocumentPreviewSection(
    vm: PrintViewModel,
    onAddFiles: () -> Unit
) {
    val doc = vm.doc ?: return
    var showFullScreen by remember { mutableStateOf(false) }

    // Smooth color matrix desaturation animation
    val saturation by animateFloatAsState(
        targetValue = if (vm.options.color) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "saturation_fade"
    )
    val colorFilter = if (saturation >= 0.99f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Chosen files in print order: PDFs and images can be mixed, added to, removed and reordered by dragging
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Drag a row by its handle to reorder. The order is only committed when the finger lifts, so the files are
                // not reopened on every swap; meanwhile the rows in between slide out of the way.
                var dragFrom by remember { mutableStateOf<Int?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                var rowStep by remember { mutableFloatStateOf(0f) }          // one row plus the gap, in pixels
                val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
                fun dragTarget(): Int? {
                    val from = dragFrom ?: return null
                    if (rowStep <= 0f) return from
                    return (from + (dragOffset / rowStep).roundToInt()).coerceIn(0, vm.files.lastIndex)
                }
                fun endDrag(commit: Boolean) {
                    val from = dragFrom
                    val to = dragTarget()
                    dragFrom = null; dragOffset = 0f
                    if (commit && from != null && to != null && from != to) vm.moveFileTo(from, to)
                }

                vm.files.forEachIndexed { index, file ->
                    val from = dragFrom
                    val target = dragTarget()
                    val slide = when {
                        from == null || target == null || index == from -> 0f
                        from < target && index in (from + 1)..target -> -rowStep
                        from > target && index in target until from -> rowStep
                        else -> 0f
                    }
                    val animatedSlide by animateFloatAsState(slide, label = "file_row_slide")
                    val dragged = index == from
                    Row(
                        modifier = Modifier
                            .onSizeChanged { if (dragFrom == null) rowStep = it.height + gapPx }
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer { translationY = if (dragged) dragOffset else animatedSlide },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (vm.files.size > 1) {
                            Icon(
                                Icons.Default.DragIndicator,
                                contentDescription = stringResource(R.string.cd_drag_to_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .pointerInput(index) {
                                        detectDragGestures(
                                            onDragStart = { dragFrom = index; dragOffset = 0f },
                                            onDragEnd = { endDrag(commit = true) },
                                            onDragCancel = { endDrag(commit = false) },
                                            onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                                        )
                                    }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (file.mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(
                                    R.string.doc_meta,
                                    pluralStringResource(R.plurals.pages_count, file.pageCount, file.pageCount),
                                    friendlyKind(file.mime)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { vm.removeFile(index) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_file), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (vm.files.size > 1) {
                        Text(
                            stringResource(R.string.files_order_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    FilledTonalButton(onClick = onAddFiles) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_files))
                    }
                }
            }
        }

        // Live Paper Sheet Canvas with realistic elevation
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.preview_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // Appears once pages have been removed with the strip's delete button
                    if (vm.removedCount > 0) RestoreButton(vm.removedCount, vm::restoreRemoved)
                }

                SheetStrip(vm)

                val bmp = vm.preview
                val targetAspect = bmp?.let { it.width.toFloat() / it.height }
                    ?: (vm.options.paper.widthPx.toFloat() / vm.options.paper.heightPx)
                val animatedAspect by animateFloatAsState(
                    targetValue = targetAspect,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "aspect_ratio_spring"
                )

                // Realistic sheet container with animated page navigation
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .aspectRatio(animatedAspect)
                            .shadow(6.dp, RoundedCornerShape(4.dp))
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .border(BorderStroke(1.dp, Color(0xFFDDDDDD)), RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = bmp != null) {
                                showFullScreen = true
                                vm.setLargePreviewOpen(true)
                            }
                            .swipeToChangeSheet(vm.pageIndex, vm.sheetCount) { vm.setPage(it) },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            // Keyed on the image and the sheet it shows, so the direction comes from the sheet that
                            // actually changed and a new picture of the same sheet just cross-fades.
                            targetState = vm.previewImage,
                            transitionSpec = { pageFlipTransition(initialState?.sheet, targetState?.sheet) },
                            label = "page_flip"
                        ) { image ->
                            val currentBmp = image?.bitmap
                            if (currentBmp != null) {
                                Image(
                                    bitmap = currentBmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.cd_sheet_preview),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = colorFilter
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                        // Optimize for print, for just the page(s) on this sheet; tap again to go back to the original
                        val sheetEnhanced = vm.isSheetEnhanced(vm.pageIndex)
                        if (bmp != null && vm.canEnhanceSheet(vm.pageIndex)) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = stringResource(if (sheetEnhanced) R.string.cd_enhance_revert else R.string.cd_enhance),
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(if (sheetEnhanced) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
                                    .clickable { vm.toggleEnhanceSheet(vm.pageIndex) }
                                    .padding(5.dp)
                                    .size(18.dp)
                            )
                        }
                        if (bmp != null) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.cd_fullscreen),
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .padding(4.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }

                // Pager controls
                val sheets = vm.sheetCount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { vm.setPage(vm.pageIndex - 1) },
                        enabled = vm.pageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_prev_sheet))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            stringResource(R.string.sheet_x_of_y, vm.pageIndex + 1, sheets),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { vm.setPage(vm.pageIndex + 1) },
                        enabled = vm.pageIndex < sheets - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_sheet))
                    }
                }

                Text(
                    stringResource(R.string.preview_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.selection_summary, vm.selectedCount, doc.pageCount, pluralStringResource(R.plurals.sheets_count, sheets, sheets)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showFullScreen) {
            FullScreenPreview(vm) {
                showFullScreen = false
                vm.setLargePreviewOpen(false)
            }
        }
    }
}

/** One tappable settings row per option group. The row shows the group name and its current value. */
private enum class Setting(@StringRes val titleRes: Int, val icon: ImageVector) {
    PAPER(R.string.setting_paper, Icons.Default.Description),
    COLOUR(R.string.setting_colour, Icons.Default.Palette),
    QUALITY(R.string.setting_quality, Icons.Default.HighQuality),
    ORIENTATION(R.string.setting_orientation, Icons.Default.ScreenRotation),
    PAGES(R.string.setting_pages, Icons.Default.FormatListNumbered),
    COPIES(R.string.setting_copies, Icons.Default.ContentCopy),
    PER_SHEET(R.string.setting_per_sheet, Icons.Default.GridView),
    TWO_SIDED(R.string.setting_two_sided, Icons.Default.FlipToBack),
    SIZING(R.string.setting_sizing, Icons.Default.FitScreen),
    MARGINS(R.string.setting_margins, Icons.Default.Crop),
    POSITION(R.string.setting_position, Icons.Default.OpenWith),
}

/** The current value of [setting], shown as the row's subtitle. */
@Composable
private fun subtitle(vm: PrintViewModel, setting: Setting): String {
    val o = vm.options
    val total = vm.doc?.pageCount ?: 0
    return when (setting) {
        Setting.PAPER, Setting.COLOUR, Setting.ORIENTATION, Setting.PER_SHEET, Setting.SIZING, Setting.MARGINS, Setting.POSITION ->
            layoutSubtitle(o, LayoutSetting.valueOf(setting.name))
        Setting.QUALITY -> stringResource(o.quality.labelRes)
        Setting.PAGES -> when (val sel = vm.selection) {
            is PageSelection.Invalid -> stringResource(R.string.sub_check_range)
            is PageSelection.Pages -> {
                val n = sel.indices.size
                val range = vm.rangeText.trim()
                when {
                    range.isEmpty() && vm.parity == Parity.ALL -> stringResource(R.string.sub_all_pages_n, total)
                    vm.parity == Parity.ALL -> stringResource(R.string.sub_pages_range, range, n, total)
                    else -> stringResource(
                        R.string.sub_pages_range_parity,
                        range.ifEmpty { stringResource(R.string.sub_all_pages) },
                        stringResource(if (vm.parity == Parity.ODD) R.string.parity_only_odd else R.string.parity_only_even),
                        n, total,
                    )
                }
            }
        }
        Setting.COPIES -> pluralStringResource(R.plurals.copies_count, o.copies, o.copies)
        Setting.TWO_SIDED -> when (o.twoSided) {
            TwoSided.OFF -> stringResource(R.string.sub_two_sided_off)
            else -> stringResource(if (o.twoSided == TwoSided.LONG_EDGE) R.string.sub_two_sided_long else R.string.sub_two_sided_short) +
                if (o.reverseBacks) ", " + stringResource(R.string.sub_two_sided_reversed) else ""
        }
    }
}

/** All print settings as a two-column grid of tiles; tapping one opens a popup for just that setting. */
@Composable
private fun SettingsCard(vm: PrintViewModel) {
    var open by remember { mutableStateOf<Setting?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.print_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Setting.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { setting ->
                            SettingTile(
                                setting.icon, stringResource(setting.titleRes), subtitle(vm, setting),
                                onClick = { open = setting },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    open?.let { SettingDialog(it, vm) { open = null } }
}

/** The popup for one setting. Changes apply immediately (the preview updates behind it); Done closes it. */
@Composable
private fun SettingDialog(setting: Setting, vm: PrintViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(setting.titleRes)) },
        text = {
            val o = vm.options
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (setting) {
                    Setting.PAPER, Setting.COLOUR, Setting.ORIENTATION, Setting.PER_SHEET, Setting.SIZING, Setting.MARGINS, Setting.POSITION ->
                        LayoutSettingBody(LayoutSetting.valueOf(setting.name), o, vm::updateOptions, onDismiss)

                    Setting.QUALITY -> RadioList(
                        Quality.entries, o.quality, { stringResource(it.labelRes) },
                        description = {
                            stringResource(
                                when (it) {
                                    Quality.DRAFT -> R.string.quality_draft_desc
                                    Quality.NORMAL -> R.string.quality_normal_desc
                                    Quality.HIGH -> R.string.quality_high_desc
                                }
                            )
                        }
                    ) {
                        vm.updateOptions(o.copy(quality = it)); onDismiss()
                    }

                    Setting.PAGES -> PagesToPrintControls(vm)

                    Setting.COPIES -> CopiesControls(vm)
                    Setting.TWO_SIDED -> TwoSidedControls(vm)
                                    }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_done)) } }
    )
}


@Composable
private fun PagesToPrintControls(vm: PrintViewModel) {
    val sel = vm.selection
    val total = vm.doc?.pageCount ?: 0
    FriendlyChipGroup(stringResource(R.string.pages_which), Parity.entries, vm.parity, { stringResource(it.labelRes) }) { vm.updateParity(it) }
    OutlinedTextField(
        value = vm.rangeText,
        onValueChange = vm::setRange,
        label = { Text(stringResource(R.string.page_range_label)) },
        placeholder = { Text(stringResource(R.string.page_range_placeholder)) },
        singleLine = true,
        isError = sel is PageSelection.Invalid,
        supportingText = {
            if (sel is PageSelection.Invalid) Text(stringResource(sel.errorRes, *sel.args.toTypedArray()))
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    if (total in 1..MAX_PAGE_PILLS) {
        val chosen = (sel as? PageSelection.Pages)?.indices?.toSet().orEmpty()
        Text(
            stringResource(R.string.pages_tap_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until total) {
                val on = i in chosen
                Surface(
                    onClick = {
                        val next = if (on) chosen - i else chosen + i
                        if (next.isNotEmpty()) {
                            if (vm.parity != Parity.ALL) vm.updateParity(Parity.ALL)   // the list is explicit from here on
                            vm.setRange(PageSelection.format(next.sorted()))
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.widthIn(min = 28.dp).padding(horizontal = 7.dp, vertical = 5.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    // Below the pills: the count on the left, the reset on the right
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (sel is PageSelection.Pages) {
            Text(
                stringResource(R.string.range_selected, sel.indices.size, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        } else Spacer(Modifier.weight(1f))
        // Always laid out, only faded and disabled when there is nothing to reset, so the dialog never changes height
        val canReset = vm.rangeText.isNotBlank() || vm.parity != Parity.ALL
        val resetAlpha by animateFloatAsState(if (canReset) 1f else 0f, label = "reset_alpha")
        Row(
            modifier = Modifier
                .alpha(resetAlpha)
                .clickable(enabled = canReset) { vm.updateParity(Parity.ALL); vm.setRange("") },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.pages_reset), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private const val MAX_PAGE_PILLS = 300

/** A stepper for how many copies to print; each copy is collated. */
@Composable
private fun CopiesControls(vm: PrintViewModel) {
    val o = vm.options
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            FilledTonalIconButton(
                onClick = { vm.updateOptions(o.copy(copies = (o.copies - 1).coerceAtLeast(MIN_COPIES))) },
                enabled = o.copies > MIN_COPIES,
            ) { Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_copies_less)) }
            Text(
                "${o.copies}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 64.dp),
                textAlign = TextAlign.Center
            )
            FilledTonalIconButton(
                onClick = { vm.updateOptions(o.copy(copies = (o.copies + 1).coerceAtMost(MAX_COPIES))) },
                enabled = o.copies < MAX_COPIES,
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_copies_more)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 2, 3, 5, 10).forEach { n ->
                FilterChip(
                    selected = o.copies == n,
                    onClick = { vm.updateOptions(o.copy(copies = n)) },
                    label = { Text("$n") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        Text(
            stringResource(R.string.copies_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TwoSidedControls(vm: PrintViewModel) {
    val o = vm.options
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioList(
            TwoSided.entries, o.twoSided, { stringResource(it.labelRes) },
            onPick = { vm.updateOptions(o.copy(twoSided = it)) },
        )

        if (o.twoSided != TwoSided.OFF) {
            AnimatedPaperFlipDemo(o.twoSided)

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.two_sided_reverse), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = o.reverseBacks, onCheckedChange = { vm.updateOptions(o.copy(reverseBacks = it)) })
            }
        }
        Text(
            stringResource(R.string.two_sided_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Animated 3D paper demonstration showing how the paper will flip based on the chosen two-sided mode */
@Composable
private fun AnimatedPaperFlipDemo(twoSided: TwoSided) {
    val infiniteTransition = rememberInfiniteTransition(label = "paper_flip_demo")
    val flipAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing, delayMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flip_angle"
    )

    val isLongEdge = twoSided == TwoSided.LONG_EDGE || twoSided == TwoSided.OFF
    val isShowingBack = flipAngle > 90f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (isLongEdge) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.FlipToBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    stringResource(if (isLongEdge) R.string.flip_demo_long else R.string.flip_demo_short),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier.size(width = 120.dp, height = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 65.dp, height = 76.dp)
                        .graphicsLayer {
                            if (isLongEdge) {
                                rotationY = flipAngle
                                cameraDistance = 12f * density
                            } else {
                                rotationX = flipAngle
                                cameraDistance = 12f * density
                            }
                        }
                        .shadow(4.dp, RoundedCornerShape(3.dp))
                        .background(if (isShowingBack) Color(0xFFF1F5F9) else Color.White, RoundedCornerShape(3.dp))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isShowingBack) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color(0xFFCBD5E1)
                            ),
                            RoundedCornerShape(3.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isShowingBack) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.flip_demo_side_1),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (isLongEdge) rotationY = 180f else rotationX = 180f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.flip_demo_side_2),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                                repeat(3) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(Color(0xFF38BDF8), RoundedCornerShape(1.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                stringResource(if (isLongEdge) R.string.flip_demo_long_hint else R.string.flip_demo_short_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Shown between the two passes of manual two-sided printing. Features the 3D instructional flip guide graphic,
 * progress summary, and icon-based step-by-step guidance.
 */
@Composable
private fun FlipDialog(state: JobState.NeedsFlip, onContinue: () -> Unit, onStop: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FlipToBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    stringResource(R.string.flip_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Instructional 3D illustration banner
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_flip_guide_otg),
                            contentDescription = stringResource(R.string.flip_title),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // The same looping paper as in the settings popup, for the edge that was chosen
                AnimatedPaperFlipDemo(state.twoSided)

                // Fronts printed / backs next summary badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            pluralStringResource(R.plurals.fronts_printed, state.fronts, state.fronts) + " • " +
                                pluralStringResource(R.plurals.backs_next, state.backs, state.backs),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Icon-assisted numbered step cards
                val stepIcons = listOf(
                    Icons.Default.HourglassTop,
                    Icons.Default.Layers,
                    Icons.Default.FlipToBack,
                    Icons.Default.ArrowDownward
                )
                val stepStrings = listOf(
                    R.string.flip_step_1,
                    R.string.flip_step_2,
                    R.string.flip_step_3,
                    R.string.flip_step_4
                )

                stepStrings.forEachIndexed { i, stepRes ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Icon(
                                stepIcons[i],
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(stepRes),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Odd sheet warning callout
                if (state.unpairedLast) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(if (state.reversed) R.string.flip_unpaired_remove else R.string.flip_unpaired_keep),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.flip_continue), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.flip_stop))
            }
        }
    )
}

/** Sticky bottom bar with animated job feedback & smooth progress */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StickyPrintBar(vm: PrintViewModel) {
    val job = vm.job
    val canPrint = vm.canPrint

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = job,
                transitionSpec = {
                    (fadeIn(tween(250)) + expandVertically(tween(250, easing = FastOutSlowInEasing))).togetherWith(
                        fadeOut(tween(200)) + shrinkVertically(tween(200, easing = FastOutSlowInEasing))
                    )
                },
                label = "job_state_banner"
            ) { currentJob ->
                when (currentJob) {
                    is JobState.Printing -> {
                        val targetProgress = if (currentJob.pages > 0) {
                            (currentJob.page.toFloat() / currentJob.pages.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val animatedProgress by animateFloatAsState(
                            targetValue = targetProgress,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "print_stream_progress"
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (vm.paused) Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                else CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    when {
                                        vm.paused -> stringResource(R.string.printing_paused, currentJob.page, currentJob.pages)
                                        currentJob.page == 0 -> stringResource(R.string.printing_preparing)
                                        else -> stringResource(R.string.printing_sheet, currentJob.page, currentJob.pages)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (currentJob.pages > 0) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }

                    is JobState.Done -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    stringResource(R.string.job_done),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    is JobState.Failed -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        stringResource(currentJob.headline ?: R.string.job_failed) +
                                            if (currentJob.message.isNotBlank()) "\n" + currentJob.message else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // The printer may have printed the fronts even though the job reported an error
                                if (vm.backsAfterFailure) {
                                    Text(
                                        stringResource(R.string.failed_fronts_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    OutlinedButton(onClick = vm::resumeAfterFailedFronts, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.failed_fronts_button))
                                    }
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }

            val o = vm.options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        pluralStringResource(R.plurals.sheets_count, vm.printTotal, vm.printTotal),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    // What will be printed, at a glance: the settings in effect, as small pills
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PrefChip(Icons.Default.Description, o.paper.label)
                        PrefChip(Icons.Default.Palette, stringResource(if (o.color) R.string.mode_colour else R.string.mode_grayscale))
                        PrefChip(Icons.Default.HighQuality, stringResource(o.quality.labelRes))
                        if (o.copies > 1) PrefChip(Icons.Default.ContentCopy, pluralStringResource(R.plurals.copies_count, o.copies, o.copies))
                        if (o.perSheet.n > 1) PrefChip(Icons.Default.GridView, stringResource(R.string.chip_per_sheet, o.perSheet.n))
                        if (o.orientation != Orientation.AUTO) PrefChip(Icons.Default.ScreenRotation, stringResource(o.orientation.labelRes))
                        if (o.twoSided != TwoSided.OFF) {
                            PrefChip(
                                Icons.Default.FlipToBack,
                                stringResource(if (o.twoSided == TwoSided.LONG_EDGE) R.string.chip_two_long else R.string.chip_two_short),
                                highlighted = true
                            )
                        }
                        // Odd/even and the typed range share one pill: "Odd pages · 1-9"
                        val range = vm.rangeText.trim()
                        if (vm.parity != Parity.ALL || range.isNotEmpty()) {
                            val parityText = when (vm.parity) {
                                Parity.ODD -> stringResource(R.string.chip_odd_pages)
                                Parity.EVEN -> stringResource(R.string.chip_even_pages)
                                Parity.ALL -> null
                            }
                            PrefChip(
                                Icons.Default.FilterNone,
                                when {
                                    parityText == null -> stringResource(R.string.chip_pages, range)
                                    range.isEmpty() -> parityText
                                    else -> stringResource(R.string.chip_parity_range, parityText, range)
                                }
                            )
                        }
                    }
                }
            }

            if (job is JobState.Printing) {
                // While a job runs the big button pauses it (after the sheet being sent) and resumes it
                FilledTonalButton(
                    onClick = vm::togglePause,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Icon(if (vm.paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (vm.paused) R.string.resume_button else R.string.pause_button),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = vm::print,
                    enabled = canPrint,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (vm.duplexPlan != null) R.string.print_fronts else R.string.print_button),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

        }
    }
}

/** One setting in effect, shown on the print bar. [highlighted] marks one that changes how the job runs (two-sided needs a flip). */
@Composable
private fun PrefChip(icon: ImageVector, text: String, highlighted: Boolean = false) {
    val container = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** A person-readable name for a MIME type ("PDF document" rather than "application/pdf"). */
@Composable
private fun friendlyKind(mime: String?): String = stringResource(
    when (mime?.lowercase()) {
        null -> R.string.doc_fallback
        "application/pdf" -> R.string.kind_pdf
        "image/jpeg", "image/jpg" -> R.string.kind_jpeg
        "image/png" -> R.string.kind_png
        "image/webp" -> R.string.kind_webp
        "image/heic", "image/heif" -> R.string.kind_heic
        "image/gif" -> R.string.kind_gif
        else -> if (mime.startsWith("image/")) R.string.kind_image else R.string.doc_fallback
    }
)

/** Bottom sheet for hardware diagnostics, USB interface details, and test prints */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticsBottomSheet(
    vm: PrintViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.diag_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_done))
                }
            }

            Text(
                stringResource(R.string.diag_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = vm::describePrinter, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.diag_usb))
                }
                OutlinedButton(onClick = vm::printerAttributes, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.diag_ipp))
                }
                OutlinedButton(onClick = vm::printTestPage, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.diag_test))
                }
                OutlinedButton(onClick = vm::scannerProbe, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.diag_scan_probe))
                }
                OutlinedButton(onClick = vm::clearDiagnostics, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.diag_clear))
                }
            }

            if (vm.diagnostics.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    SelectionContainer {
                        Text(
                            text = vm.diagnostics,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.diag_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Reusable horizontal chip selector with clear active selection animation */
@Composable
private fun <T> FriendlyChipGroup(
    title: String,
    items: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                val chipContainerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(200),
                    label = "chip_container_color"
                )
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    label = { Text(label(item)) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipContainerColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}



/** The sheet viewer: zoom, pan, swipe and a thumbnail strip; see [ZoomableViewer]. */
@Composable
private fun FullScreenPreview(vm: PrintViewModel, onDismiss: () -> Unit) {
    val position = stringResource(R.string.sheet_x_of_y, vm.pageIndex + 1, vm.sheetCount)
    ZoomableViewer(
        image = remember(vm.largeImage) { vm.largeImage?.let { ViewerImage(it.sheet, it.bitmap.asImageBitmap()) } },
        grayscale = !vm.options.color,
        title = position,
        description = position,
        hint = stringResource(R.string.viewer_hint),
        count = vm.sheetCount,
        current = vm.pageIndex,
        onSelect = vm::setPage,
        onDismiss = onDismiss,
        strip = { dark -> SheetStrip(vm, onDark = dark) },
        actions = { tint ->
            // Optimize for print / revert, for just the page(s) on this sheet
            if (vm.canEnhanceSheet(vm.pageIndex)) {
                val enhanced = vm.isSheetEnhanced(vm.pageIndex)
                IconButton(onClick = { vm.toggleEnhanceSheet(vm.pageIndex) }) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(if (enhanced) R.string.cd_enhance_revert else R.string.cd_enhance),
                        tint = if (enhanced) MaterialTheme.colorScheme.primary else tint
                    )
                }
            }
        },
    )
}

/** Thumbnails of every sheet, rendered lazily by the view-model; see [ThumbStrip]. */
@Composable
private fun SheetStrip(vm: PrintViewModel, onDark: Boolean = false) {
    val paperAspect = vm.options.paper.widthPx.toFloat() / vm.options.paper.heightPx
    ThumbStrip(
        count = vm.sheetCount,
        current = vm.pageIndex,
        onSelect = vm::setPage,
        aspectFor = { i -> vm.thumbnail(i)?.let { it.width.toFloat() / it.height } ?: paperAspect },
        labelFor = vm::sheetLabel,
        descriptionFor = { i -> stringResource(R.string.sheet_x_of_y, i + 1, vm.sheetCount) },
        bitmapFor = { i ->
            val bmp = vm.thumbnail(i)
            // Reading thumbVersion makes this redraw when a thumbnail arrives or the cache is cleared.
            LaunchedEffect(i, vm.thumbVersion) { if (bmp == null) vm.requestThumbnail(i) }
            bmp?.asImageBitmap()
        },
        onDark = onDark,
        grayscale = !vm.options.color,
        scrollTick = vm.pageScrollTick,
        onDelete = { i -> vm.removeSheet(i) },
        deleteDescription = { stringResource(R.string.cd_remove_from_print) },
    )
}


/** Language picker: system default, English or Sinhala. Choosing one restarts the screen in that language. */
@Composable
internal fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = AppLocale.saved(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            RadioList(
                items = AppLocale.choices,
                selected = current,
                label = {
                    stringResource(
                        when (it) {
                            "en" -> R.string.language_en
                            "si" -> R.string.language_si
                            else -> R.string.language_system
                        }
                    )
                },
            ) { tag ->
                onDismiss()
                if (tag != current) context.findActivity()?.let { AppLocale.change(it, tag) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_done)) } }
    )
}
