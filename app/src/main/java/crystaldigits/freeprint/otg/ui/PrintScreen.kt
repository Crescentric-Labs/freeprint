package crystaldigits.freeprint.otg.ui

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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.DocumentScanner
import crystaldigits.freeprint.otg.print.effectiveMarginMm
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
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
import crystaldigits.freeprint.otg.AppLocale
import crystaldigits.freeprint.otg.AppLocale.findActivity
import crystaldigits.freeprint.otg.JobState
import crystaldigits.freeprint.otg.PrintViewModel
import crystaldigits.freeprint.otg.R
import crystaldigits.freeprint.otg.print.Anchor
import crystaldigits.freeprint.otg.print.FitMode
import crystaldigits.freeprint.otg.print.MAX_SCALE_PERCENT
import crystaldigits.freeprint.otg.print.MIN_SCALE_PERCENT
import crystaldigits.freeprint.otg.print.Orientation
import crystaldigits.freeprint.otg.print.PageOrder
import crystaldigits.freeprint.otg.print.PageSelection
import crystaldigits.freeprint.otg.print.Paper
import crystaldigits.freeprint.otg.print.Parity
import crystaldigits.freeprint.otg.print.PerSheet
import crystaldigits.freeprint.otg.print.Quality
import crystaldigits.freeprint.otg.print.TwoSided


private enum class PrinterStatusType {
    DISCONNECTED,
    PERMISSION_NEEDED,
    READY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(vm: PrintViewModel, onOpenScan: () -> Unit = {}) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.replaceFiles(uris)
    }
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.addFiles(uris)
    }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var refreshSpinDegrees by remember { mutableFloatStateOf(0f) }
    val animatedRefreshSpin by animateFloatAsState(
        targetValue = refreshSpinDegrees,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "refresh_spin"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon),
                            contentDescription = stringResource(R.string.cd_app_icon),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Language, contentDescription = stringResource(R.string.cd_language))
                    }
                    IconButton(onClick = {
                        refreshSpinDegrees += 360f
                        vm.refreshPrinter()
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cd_rescan),
                            modifier = Modifier.graphicsLayer { rotationZ = animatedRefreshSpin }
                        )
                    }
                    IconButton(onClick = { showDiagnosticsSheet = true }) {
                        Icon(Icons.Default.BugReport, contentDescription = stringResource(R.string.cd_diagnostics))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = vm.doc != null,
                enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
            ) {
                StickyPrintBar(vm)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Printer connection status banner with animated transitions
            PrinterStatusCard(
                vm = vm,
                onRescan = {
                    refreshSpinDegrees += 360f
                    vm.refreshPrinter()
                }
            )

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
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EmptyDocumentHero(
                            onChooseFile = { picker.launch(arrayOf("application/pdf", "image/*")) }
                        )
                        ScanCard(onOpenScan)
                    }
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

        if (showLanguageDialog) LanguageDialog { showLanguageDialog = false }
        (vm.job as? JobState.NeedsFlip)?.let { FlipDialog(it, onContinue = vm::continueTwoSided, onStop = vm::cancelTwoSided) }

        if (showDiagnosticsSheet) {
            DiagnosticsBottomSheet(
                vm = vm,
                onDismiss = { showDiagnosticsSheet = false }
            )
        }
    }
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
                        Button(
                            onClick = onRescan,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.scan), style = MaterialTheme.typography.labelMedium)
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
                // Living breathing pulse for the ready indicator dot
                val infiniteTransition = rememberInfiniteTransition(label = "ready_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
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
                                Text(
                                    name ?: stringResource(R.string.printer_fallback),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .graphicsLayer {
                                                    scaleX = pulseScale
                                                    scaleY = pulseScale
                                                    alpha = pulseAlpha
                                                }
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                        Text(
                                            stringResource(R.string.ready),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                            Text(
                                stringResource(R.string.printer_mode),
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

/** Start-screen card that opens the scanner, matching the "Ready to print" card above it. */
@Composable
private fun ScanCard(onOpenScan: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_scanner_hero),
                contentDescription = stringResource(R.string.scan_card_title),
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.scan_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.scan_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onOpenScan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_card_button), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Inviting empty state hero card with soft hover shadow */
@Composable
private fun EmptyDocumentHero(
    onChooseFile: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
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
        // Chosen files in print order: PDFs and images can be mixed, added to, removed and reordered
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
                vm.files.forEachIndexed { index, file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                    file.mime ?: stringResource(R.string.doc_fallback)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (vm.files.size > 1) {
                            IconButton(onClick = { vm.moveFile(index, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.cd_move_up), modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { vm.moveFile(index, 1) }, enabled = index < vm.files.lastIndex, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.cd_move_down), modifier = Modifier.size(18.dp))
                            }
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
                    TextButton(onClick = onAddFiles) {
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
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (vm.options.color) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            stringResource(if (vm.options.color) R.string.mode_colour else R.string.mode_grayscale),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
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
                            transitionSpec = {
                                val from = initialState?.sheet
                                val to = targetState?.sheet
                                val slide = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                                val transform = when {
                                    from == null || to == null -> fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                                    to > from ->                                  // forward: the new sheet comes in from the right
                                        (slideInHorizontally(slide) { width -> width } + fadeIn(tween(250))).togetherWith(
                                            slideOutHorizontally(slide) { width -> -width } + fadeOut(tween(200))
                                        )
                                    to < from ->                                  // back: the new sheet comes in from the left
                                        (slideInHorizontally(slide) { width -> -width } + fadeIn(tween(250))).togetherWith(
                                            slideOutHorizontally(slide) { width -> width } + fadeOut(tween(200))
                                        )
                                    else -> fadeIn(tween(220)).togetherWith(fadeOut(tween(220)))
                                }
                                transform.using(SizeTransform(clip = false))
                            },
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
        Setting.PAPER -> o.paper.label
        Setting.COLOUR -> stringResource(if (o.color) R.string.mode_colour else R.string.mode_grayscale)
        Setting.QUALITY -> stringResource(o.quality.labelRes)
        Setting.ORIENTATION -> stringResource(o.orientation.labelRes)
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
        Setting.PER_SHEET ->
            if (o.perSheet == PerSheet.ONE) stringResource(R.string.sub_per_sheet_one)
            else stringResource(R.string.sub_per_sheet_n, o.perSheet.n, stringResource(o.order.labelRes)) +
                if (o.border) ", " + stringResource(R.string.sub_with_borders) else ""
        Setting.TWO_SIDED -> when (o.twoSided) {
            TwoSided.OFF -> stringResource(R.string.sub_two_sided_off)
            else -> stringResource(if (o.twoSided == TwoSided.LONG_EDGE) R.string.sub_two_sided_long else R.string.sub_two_sided_short) +
                if (o.reverseBacks) ", " + stringResource(R.string.sub_two_sided_reversed) else ""
        }
        Setting.SIZING -> stringResource(R.string.sub_sizing, stringResource(o.fit.labelRes), o.scalePercent)
        Setting.MARGINS -> when (o.marginMm) {
            null -> stringResource(R.string.margin_auto)
            0 -> stringResource(R.string.margin_none)
            else -> stringResource(R.string.margin_mm, o.marginMm)
        }
        Setting.POSITION ->
            if (o.anchor == Anchor.CENTER && o.offsetXmm == 0 && o.offsetYmm == 0) stringResource(R.string.sub_pos_centre)
            else listOfNotNull(
                stringResource(o.anchor.labelRes),
                if (o.offsetXmm != 0) stringResource(R.string.sub_across, o.offsetXmm) else null,
                if (o.offsetYmm != 0) stringResource(R.string.sub_down, o.offsetYmm) else null,
            ).joinToString(", ")
    }
}

/** All print settings as a plain list; tapping a row opens a popup for just that setting. */
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
            Setting.entries.forEachIndexed { i, setting ->
                SettingRow(setting, subtitle(vm, setting)) { open = setting }
                if (i < Setting.entries.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }
        }
    }

    open?.let { SettingDialog(it, vm) { open = null } }
}

@Composable
private fun SettingRow(setting: Setting, value: String, onClick: () -> Unit) =
    SettingRow(setting.icon, stringResource(setting.titleRes), value, onClick)

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
                    Setting.PAPER -> RadioList(Paper.entries, o.paper, { it.label }) {
                        vm.updateOptions(o.copy(paper = it)); onDismiss()
                    }

                    Setting.COLOUR -> RadioList(listOf(true, false), o.color, { stringResource(if (it) R.string.colour_opt_colour else R.string.colour_opt_gray) }) {
                        vm.updateOptions(o.copy(color = it)); onDismiss()
                    }

                    Setting.QUALITY -> RadioList(Quality.entries, o.quality, { stringResource(it.labelRes) }) {
                        vm.updateOptions(o.copy(quality = it)); onDismiss()
                    }

                    Setting.ORIENTATION -> {
                        RadioList(Orientation.entries, o.orientation, { stringResource(it.labelRes) }) {
                            vm.updateOptions(o.copy(orientation = it)); onDismiss()
                        }
                        Text(
                            stringResource(R.string.orientation_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Setting.PAGES -> PagesToPrintControls(vm)

                    Setting.PER_SHEET -> {
                        RadioList(PerSheet.entries, o.perSheet, { if (it == PerSheet.ONE) stringResource(R.string.per_sheet_one_opt) else stringResource(R.string.per_sheet_n_opt, it.n) }) {
                            vm.updateOptions(o.copy(perSheet = it))
                        }
                        if (o.perSheet != PerSheet.ONE) {
                            HorizontalDivider()
                            Text(stringResource(R.string.page_order), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            RadioList(PageOrder.entries, o.order, { stringResource(it.labelRes) }) { vm.updateOptions(o.copy(order = it)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.border_each), style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = o.border, onCheckedChange = { vm.updateOptions(o.copy(border = it)) })
                            }
                        }
                    }

                    Setting.TWO_SIDED -> TwoSidedControls(vm)
                    Setting.SIZING -> SizingControls(vm)
                    Setting.MARGINS -> MarginControls(vm)
                    Setting.POSITION -> PositionControls(vm)
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
            Text(
                when (sel) {
                    is PageSelection.Invalid -> stringResource(sel.errorRes, *sel.args.toTypedArray())
                    is PageSelection.Pages -> stringResource(R.string.range_selected, sel.indices.size, total)
                }
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SizingControls(vm: PrintViewModel) {
    val o = vm.options
    RadioList(FitMode.entries, o.fit, { stringResource(it.labelRes) }) { vm.updateOptions(o.copy(fit = it)) }
    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.scale_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("${o.scalePercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = o.scalePercent.toFloat(),
        onValueChange = { vm.updateOptions(o.copy(scalePercent = it.toInt())) },
        valueRange = MIN_SCALE_PERCENT.toFloat()..MAX_SCALE_PERCENT.toFloat()
    )
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(50, 75, 100, 125, 150, 200).forEach { pct ->
            FilterChip(
                selected = o.scalePercent == pct,
                onClick = { vm.updateOptions(o.copy(scalePercent = pct)) },
                label = { Text("$pct%") },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun TwoSidedControls(vm: PrintViewModel) {
    val o = vm.options
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioList(
            TwoSided.entries, o.twoSided, { stringResource(it.labelRes) },
            onPick = { vm.updateOptions(o.copy(twoSided = it)) },
            description = {
                stringResource(
                    when (it) {
                        TwoSided.OFF -> R.string.two_sided_desc_off
                        TwoSided.LONG_EDGE -> R.string.two_sided_desc_long
                        TwoSided.SHORT_EDGE -> R.string.two_sided_desc_short
                    }
                )
            },
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
                    if (isLongEdge) "Long-Edge Flip (Book Style)" else "Short-Edge Flip (Notepad Style)",
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
                                "SIDE 1",
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
                                    "SIDE 2",
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
                if (isLongEdge) "Turns sideways like turning the page of a book" else "Turns end-over-end like flipping a calendar pad",
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

@Composable
private fun MarginControls(vm: PrintViewModel) {
    val o = vm.options
    val shown = o.effectiveMarginMm
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = o.marginMm == null,
            onClick = { vm.updateOptions(o.copy(marginMm = null)) },
            label = { Text(stringResource(R.string.margin_auto)) },
            shape = RoundedCornerShape(8.dp)
        )
        listOf(0, 5, 13, 25).forEach { mm ->
            FilterChip(
                selected = o.marginMm == mm,
                onClick = { vm.updateOptions(o.copy(marginMm = mm)) },
                label = { Text(if (mm == 0) stringResource(R.string.margin_none) else stringResource(R.string.margin_mm, mm)) },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
    Text(stringResource(R.string.margin_custom, shown), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    Slider(
        value = shown.toFloat(),
        onValueChange = { vm.updateOptions(o.copy(marginMm = it.toInt())) },
        valueRange = 0f..50f
    )
    Text(
        stringResource(R.string.margin_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PositionControls(vm: PrintViewModel) {
    val o = vm.options
    val isCustom = o.anchor != Anchor.CENTER || o.offsetXmm != 0 || o.offsetYmm != 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.pos_anchor), style = MaterialTheme.typography.labelSmall)
            Anchor.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { a ->
                        val active = a == o.anchor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.updateOptions(o.copy(anchor = a)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (active) Icon(Icons.Default.Check, contentDescription = stringResource(a.labelRes), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.pos_across, o.offsetXmm), style = MaterialTheme.typography.bodySmall)
            Slider(o.offsetXmm.toFloat(), { vm.updateOptions(o.copy(offsetXmm = it.toInt())) }, valueRange = -50f..50f)
            Text(stringResource(R.string.pos_down, o.offsetYmm), style = MaterialTheme.typography.bodySmall)
            Slider(o.offsetYmm.toFloat(), { vm.updateOptions(o.copy(offsetYmm = it.toInt())) }, valueRange = -50f..50f)
        }
    }
    Text(
        stringResource(R.string.pos_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (isCustom) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.updateOptions(o.copy(anchor = Anchor.CENTER, offsetXmm = 0, offsetYmm = 0)) }) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.pos_reset))
            }
        }
    }
}

/** Sticky bottom bar with animated job feedback & smooth progress */
@Composable
private fun StickyPrintBar(vm: PrintViewModel) {
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
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    if (currentJob.page == 0) stringResource(R.string.printing_preparing) else stringResource(R.string.printing_sheet, currentJob.page, currentJob.pages),
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
                            Row(
                                modifier = Modifier.padding(10.dp),
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
                        }
                    }

                    else -> {}
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pluralStringResource(R.plurals.sheets_count, vm.sheetCount, vm.sheetCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.bar_paper_mode, vm.options.paper.label, stringResource(if (vm.options.color) R.string.mode_colour else R.string.mode_grayscale)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = vm::print,
                    enabled = canPrint,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (job is JobState.Printing) R.string.printing_button else if (vm.duplexPlan != null) R.string.print_fronts else R.string.print_button),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Bottom sheet for hardware diagnostics, USB interface details, and test prints */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsBottomSheet(
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
                OutlinedButton(onClick = vm::openSample, shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.diag_sample))
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
        image = vm.largePreview?.asImageBitmap(),
        grayscale = !vm.options.color,
        title = position,
        description = position,
        hint = stringResource(R.string.viewer_hint),
        count = vm.sheetCount,
        current = vm.pageIndex,
        onSelect = vm::setPage,
        onDismiss = onDismiss,
        strip = { SheetStrip(vm, onDark = true) },
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
    )
}


/** Language picker: system default, English or Sinhala. Choosing one restarts the screen in that language. */
@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
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
