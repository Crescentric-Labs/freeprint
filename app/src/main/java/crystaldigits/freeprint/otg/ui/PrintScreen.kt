package crystaldigits.freeprint.otg.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(vm: PrintViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
    }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

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
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                "Free Print",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "USB Mobile Direct Print",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::refreshPrinter) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan Printer")
                    }
                    IconButton(onClick = { showDiagnosticsSheet = true }) {
                        Icon(Icons.Default.BugReport, contentDescription = "Diagnostics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (vm.doc != null) {
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
            // 1. Printer connection status banner
            PrinterStatusCard(vm)

            // 2. Document picker or loaded document preview
            if (vm.doc == null) {
                EmptyDocumentHero(
                    onChooseFile = { picker.launch(arrayOf("application/pdf", "image/*")) },
                    onLoadSample = vm::openSample
                )
            } else {
                DocumentPreviewSection(
                    vm = vm,
                    onChangeFile = { picker.launch(arrayOf("application/pdf", "image/*")) },
                    onClearFile = vm::clearDoc
                )

                // 3. Print settings: one row per setting, each opens a popup
                SettingsCard(vm)
            }

            Spacer(Modifier.height(48.dp))
        }

        if (showDiagnosticsSheet) {
            DiagnosticsBottomSheet(
                vm = vm,
                onDismiss = { showDiagnosticsSheet = false }
            )
        }
    }
}

/** Modern status card showing USB printer connection state with clear visual cues */
@Composable
private fun PrinterStatusCard(vm: PrintViewModel) {
    val name = vm.printerName
    val hasPermission = vm.hasPermission

    when {
        name == null -> {
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
                            "No USB Printer Connected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Plug in your USB OTG adapter and turn on the printer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = vm::refreshPrinter,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Scan", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        !hasPermission -> {
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
                                name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "USB permission needed to communicate with printer.",
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
                        Text("Grant USB Permission")
                    }
                }
            }
        }

        else -> {
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
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    "Ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "USB OTG • Driverless IPP (600 DPI)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = vm::refreshPrinter) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Inviting empty state hero card when no file has been selected */
@Composable
private fun EmptyDocumentHero(
    onChooseFile: () -> Unit,
    onLoadSample: () -> Unit
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
                contentDescription = "Printer Hero",
                modifier = Modifier
                    .size(130.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Ready to Print",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select any PDF or image to configure options and print directly to your USB printer.",
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
                    Text("Select Document or Image", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onLoadSample,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Load Sample Multi-Page PDF")
                }
            }
        }
    }
}

/** Document details bar and live sheet preview canvas */
@Composable
private fun DocumentPreviewSection(
    vm: PrintViewModel,
    onChangeFile: () -> Unit,
    onClearFile: () -> Unit
) {
    val doc = vm.doc ?: return
    var showFullScreen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Document info chip banner
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        doc.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"} • ${doc.mime ?: "Document"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onChangeFile) {
                    Text("Change")
                }

                IconButton(onClick = onClearFile, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear file", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Live Paper Sheet Canvas
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
                        "Live Print Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (vm.options.color) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (vm.options.color) "Color" else "Grayscale",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                val bmp = vm.preview
                val bw = if (vm.options.color) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                val aspect = bmp?.let { it.width.toFloat() / it.height }
                    ?: (vm.options.paper.widthPx.toFloat() / vm.options.paper.heightPx)

                // Realistic sheet container with elevation shadow
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .aspectRatio(aspect)
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
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Live Sheet Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                colorFilter = bw
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                        if (bmp != null) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Full screen",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous sheet")
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "Sheet ${vm.pageIndex + 1} of $sheets",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { vm.setPage(vm.pageIndex + 1) },
                        enabled = vm.pageIndex < sheets - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next sheet")
                    }
                }

                Text(
                    "Tap the sheet to enlarge. Swipe to change sheet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    "${vm.selectedCount} of ${doc.pageCount} page(s) selected • ${sheets} sheet(s) will be printed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
    
        if (showFullScreen) {
            FullScreenPreview(vm) {
                showFullScreen = false
                vm.setLargePreviewOpen(false)
            }
        }
    }
    }
}

/** One tappable settings row per option group. The row shows the group name and its current value. */
private enum class Setting(val title: String, val icon: ImageVector) {
    PAPER("Paper size", Icons.Default.Description),
    COLOUR("Colour", Icons.Default.Palette),
    QUALITY("Print quality", Icons.Default.HighQuality),
    ORIENTATION("Orientation", Icons.Default.ScreenRotation),
    PAGES("Pages to print", Icons.Default.FormatListNumbered),
    PER_SHEET("Pages per sheet", Icons.Default.GridView),
    SIZING("Page size and scale", Icons.Default.FitScreen),
    POSITION("Position", Icons.Default.OpenWith),
}

/** The current value of [setting], shown as the row's subtitle. */
private fun subtitle(vm: PrintViewModel, setting: Setting): String {
    val o = vm.options
    val total = vm.doc?.pageCount ?: 0
    return when (setting) {
        Setting.PAPER -> o.paper.label
        Setting.COLOUR -> if (o.color) "Colour" else "Grayscale"
        Setting.QUALITY -> o.quality.label
        Setting.ORIENTATION -> o.orientation.label
        Setting.PAGES -> when (val sel = vm.selection) {
            is PageSelection.Invalid -> "Check the page range"
            is PageSelection.Pages ->
                if (vm.rangeText.isBlank() && vm.parity == Parity.ALL) "All pages ($total)"
                else buildString {
                    append(vm.rangeText.trim().ifBlank { "All pages" })
                    if (vm.parity != Parity.ALL) append(", ${vm.parity.label.lowercase()} only")
                    append(" (${sel.indices.size} of $total)")
                }
        }
        Setting.PER_SHEET ->
            if (o.perSheet == PerSheet.ONE) "1 per sheet"
            else "${o.perSheet.n} per sheet, ${o.order.label.lowercase()}${if (o.border) ", with borders" else ""}"
        Setting.SIZING -> "${o.fit.label}, ${o.scalePercent}%"
        Setting.POSITION ->
            if (o.anchor == Anchor.CENTER && o.offsetXmm == 0 && o.offsetYmm == 0) "Centre"
            else buildString {
                append(o.anchor.label)
                if (o.offsetXmm != 0) append(", ${o.offsetXmm} mm across")
                if (o.offsetYmm != 0) append(", ${o.offsetYmm} mm down")
            }
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
                "Print settings",
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
private fun SettingRow(setting: Setting, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(setting.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(setting.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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

/** The popup for one setting. Changes apply immediately (the preview updates behind it); Done closes it. */
@Composable
private fun SettingDialog(setting: Setting, vm: PrintViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(setting.title) },
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

                    Setting.COLOUR -> RadioList(listOf(true, false), o.color, { if (it) "Colour" else "Grayscale (black and white)" }) {
                        vm.updateOptions(o.copy(color = it)); onDismiss()
                    }

                    Setting.QUALITY -> RadioList(Quality.entries, o.quality, { it.label }) {
                        vm.updateOptions(o.copy(quality = it)); onDismiss()
                    }

                    Setting.ORIENTATION -> {
                        RadioList(Orientation.entries, o.orientation, { it.label }) {
                            vm.updateOptions(o.copy(orientation = it)); onDismiss()
                        }
                        Text(
                            "Auto follows the first page. Landscape turns the content a quarter turn counter-clockwise.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Setting.PAGES -> PagesToPrintControls(vm)

                    Setting.PER_SHEET -> {
                        RadioList(PerSheet.entries, o.perSheet, { if (it == PerSheet.ONE) "1 page per sheet" else "${it.n} pages per sheet" }) {
                            vm.updateOptions(o.copy(perSheet = it))
                        }
                        if (o.perSheet != PerSheet.ONE) {
                            HorizontalDivider()
                            Text("Page order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            RadioList(PageOrder.entries, o.order, { it.label }) { vm.updateOptions(o.copy(order = it)) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Border around each page", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = o.border, onCheckedChange = { vm.updateOptions(o.copy(border = it)) })
                            }
                        }
                    }

                    Setting.SIZING -> SizingControls(vm)
                    Setting.POSITION -> PositionControls(vm)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** Single-choice list: tapping a row picks it. */
@Composable
private fun <T> RadioList(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
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
                Text(label(item), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PagesToPrintControls(vm: PrintViewModel) {
    val sel = vm.selection
    val total = vm.doc?.pageCount ?: 0
    FriendlyChipGroup("Which pages", Parity.entries, vm.parity, { it.label }) { vm.updateParity(it) }
    OutlinedTextField(
        value = vm.rangeText,
        onValueChange = vm::setRange,
        label = { Text("Page range (blank = all)") },
        placeholder = { Text("e.g. 1-3, 5, 8-") },
        singleLine = true,
        isError = sel is PageSelection.Invalid,
        supportingText = {
            Text(
                when (sel) {
                    is PageSelection.Invalid -> sel.message
                    is PageSelection.Pages -> "${sel.indices.size} of $total pages selected"
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
    RadioList(FitMode.entries, o.fit, { it.label }) { vm.updateOptions(o.copy(fit = it)) }
    HorizontalDivider()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Scale", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
private fun PositionControls(vm: PrintViewModel) {
    val o = vm.options
    val isCustom = o.anchor != Anchor.CENTER || o.offsetXmm != 0 || o.offsetYmm != 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Anchor", style = MaterialTheme.typography.labelSmall)
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
                            if (active) Icon(Icons.Default.Check, contentDescription = a.label, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Across: ${o.offsetXmm} mm", style = MaterialTheme.typography.bodySmall)
            Slider(o.offsetXmm.toFloat(), { vm.updateOptions(o.copy(offsetXmm = it.toInt())) }, valueRange = -50f..50f)
            Text("Down: ${o.offsetYmm} mm", style = MaterialTheme.typography.bodySmall)
            Slider(o.offsetYmm.toFloat(), { vm.updateOptions(o.copy(offsetYmm = it.toInt())) }, valueRange = -50f..50f)
        }
    }
    Text(
        "The anchor sets where the page sits in the spare room; the sliders then nudge it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (isCustom) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.updateOptions(o.copy(anchor = Anchor.CENTER, offsetXmm = 0, offsetYmm = 0)) }) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset to centre")
            }
        }
    }
}

/** Sticky bottom bar with quick print summary, print button, and active progress */
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
            when (job) {
                is JobState.Printing -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    if (job.page == 0) "Preparing print job..." else "Streaming sheet ${job.page} of ${job.pages}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (job.pages > 0) {
                            LinearProgressIndicator(
                                progress = { (job.page.toFloat() / job.pages.toFloat()).coerceIn(0f, 1f) },
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
                                job.message,
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
                                job.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                else -> {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${vm.sheetCount} ${if (vm.sheetCount == 1) "Sheet" else "Sheets"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${vm.options.paper.label} • ${if (vm.options.color) "Color" else "Grayscale"}",
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
                        if (job is JobState.Printing) "Printing..." else "Print",
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
                    "Printer Diagnostics & Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }

            Text(
                "Hardware utilities for inspecting USB endpoints, IPP query responses, and raw test prints.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = vm::describePrinter, shape = RoundedCornerShape(8.dp)) {
                    Text("USB Interfaces")
                }
                OutlinedButton(onClick = vm::printerAttributes, shape = RoundedCornerShape(8.dp)) {
                    Text("IPP Attributes")
                }
                OutlinedButton(onClick = vm::printTestPage, shape = RoundedCornerShape(8.dp)) {
                    Text("Print Test Page")
                }
                OutlinedButton(onClick = vm::openSample, shape = RoundedCornerShape(8.dp)) {
                    Text("Load Sample PDF")
                }
                OutlinedButton(onClick = vm::clearDiagnostics, shape = RoundedCornerShape(8.dp)) {
                    Text("Clear")
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
                    "Select an action above to query your connected USB printer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Reusable horizontal chip selector with clear active selection styling */
@Composable
private fun <T> FriendlyChipGroup(
    title: String,
    items: List<T>,
    selected: T,
    label: (T) -> String,
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
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    label = { Text(label(item)) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}


/** Horizontal swipe on this element moves to the previous or next sheet. */
private fun Modifier.swipeToChangeSheet(current: Int, sheets: Int, onSheet: (Int) -> Unit): Modifier =
    pointerInput(current, sheets) {
        var dx = 0f
        detectHorizontalDragGestures(
            onDragStart = { dx = 0f },
            onDragCancel = { dx = 0f },
            onDragEnd = {
                when {
                    dx < -SWIPE_DISTANCE_PX && current < sheets - 1 -> onSheet(current + 1)
                    dx > SWIPE_DISTANCE_PX && current > 0 -> onSheet(current - 1)
                }
            },
        ) { _, drag -> dx += drag }
    }

private const val SWIPE_DISTANCE_PX = 90f

/**
 * Full-screen sheet viewer: pinch to zoom (up to 8x), drag to pan, double-tap to zoom in or out.
 * While not zoomed, a horizontal swipe changes sheet.
 */
@Composable
private fun FullScreenPreview(vm: PrintViewModel, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var box by remember { mutableStateOf(IntSize.Zero) }
        val sheets = vm.sheetCount
        val bmp = vm.largePreview
        val gray = if (vm.options.color) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

        // A different sheet starts un-zoomed.
        LaunchedEffect(vm.pageIndex) { scale = 1f; offset = Offset.Zero }

        fun clamp(o: Offset, s: Float): Offset {
            val mx = box.width * (s - 1f) / 2f
            val my = box.height * (s - 1f) / 2f
            return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF111111))
                .onSizeChanged { box = it }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else scale = 3f
                    })
                }
                .pointerInput(sheets, vm.pageIndex) {
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
                                panX < -SWIPE_DISTANCE_PX && vm.pageIndex < sheets - 1 -> vm.setPage(vm.pageIndex + 1)
                                panX > SWIPE_DISTANCE_PX && vm.pageIndex > 0 -> vm.setPage(vm.pageIndex - 1)
                            }
                        }
                    }
                }
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Sheet ${vm.pageIndex + 1} of $sheets",
                    contentScale = ContentScale.Fit,
                    colorFilter = gray,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }

            // Top bar: close, sheet position, reset zoom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                Text(
                    "Sheet ${vm.pageIndex + 1} of $sheets" + if (scale > 1.02f) "  •  ${"%.1f".format(scale)}x" else "",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.setPage(vm.pageIndex - 1) }, enabled = vm.pageIndex > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous sheet", tint = if (vm.pageIndex > 0) Color.White else Color.Gray)
                }
                IconButton(onClick = { vm.setPage(vm.pageIndex + 1) }, enabled = vm.pageIndex < sheets - 1) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next sheet", tint = if (vm.pageIndex < sheets - 1) Color.White else Color.Gray)
                }
            }

            Text(
                "Pinch or double-tap to zoom. Swipe to change sheet.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}
