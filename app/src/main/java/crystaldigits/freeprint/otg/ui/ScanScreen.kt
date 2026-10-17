package crystaldigits.freeprint.otg.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import crystaldigits.freeprint.otg.R
import crystaldigits.freeprint.otg.scan.ScanArea
import crystaldigits.freeprint.otg.scan.ScanMessage
import crystaldigits.freeprint.otg.scan.ScanViewModel
import crystaldigits.freeprint.otg.scan.ScannerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The scanner, laid out like the print screen: a status card, a preview with a strip of page thumbnails, settings as rows
 * that open popups, and a bar at the bottom with the main action. Place a page on the glass, scan, repeat for more pages,
 * then print a copy (the pages open on the print screen), save a PDF or share it. [onPrint] receives the files to print.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel, onBack: () -> Unit, onPrint: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    var showViewer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target != null) vm.withPdf { pdf -> vm.copyTo(pdf, target) {} }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon),
                            contentDescription = stringResource(R.string.cd_app_icon),
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = vm.status == ScannerStatus.READY,
                enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200)),
            ) { StickyScanBar(vm) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScannerStatusCard(vm)
            vm.message?.let { MessageCard(it, vm::dismissMessage) }

            if (vm.status == ScannerStatus.READY) {
                if (vm.pages.isEmpty()) {
                    ScanEmptyHero()
                } else {
                    ScanPreviewCard(vm, onOpenViewer = { showViewer = true; vm.setLargePreviewOpen(true) })
                }
                ScanSettingsCard(vm)
                if (vm.pages.isNotEmpty()) {
                    OutputCard(
                        vm,
                        onPrint = { vm.withPdf { pdf -> onPrint(listOf(Uri.fromFile(pdf))) } },
                        onSave = { savePdf.launch("scan.pdf") },
                        onShare = {
                            vm.withPdf { pdf ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", pdf)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showViewer) {
        ScanViewer(vm) { showViewer = false; vm.setLargePreviewOpen(false) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Status, messages, empty state
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The scanner's state, drawn with the same three card styles as the printer card on the print screen: outlined while
 * there is no printer or it is loading, a tinted card when attention is needed, an elevated card when ready. The tints
 * are composited onto the surface, so the card is opaque and its shadow cannot show through as a second box.
 */
@Composable
private fun ScannerStatusCard(vm: ScanViewModel) {
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(16.dp)
    val circle = when (vm.status) {
        ScannerStatus.READY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val body: @Composable () -> Unit = { ScannerStatusRow(vm, circle) }

    when (vm.status) {
        ScannerStatus.NO_PRINTER, ScannerStatus.LOADING -> OutlinedCard(
            Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f).compositeOver(surface)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { body() }

        ScannerStatus.NEEDS_PERMISSION, ScannerStatus.ERROR -> Card(
            Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f).compositeOver(surface),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { body() }

        ScannerStatus.READY -> ElevatedCard(
            Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f).compositeOver(surface)),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        ) { body() }
    }
}

@Composable
private fun ScannerStatusRow(vm: ScanViewModel, circle: Color) {
    val caps = vm.caps
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            when (vm.status) {
                ScannerStatus.LOADING -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                ScannerStatus.NO_PRINTER -> Icon(Icons.Default.UsbOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                ScannerStatus.NEEDS_PERMISSION, ScannerStatus.ERROR -> Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                ScannerStatus.READY -> Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (vm.status) {
                ScannerStatus.NO_PRINTER -> {
                    Text(stringResource(R.string.printer_none_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.printer_none_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScannerStatus.NEEDS_PERMISSION ->
                    Text(stringResource(R.string.printer_permission_needed), style = MaterialTheme.typography.bodyMedium)
                ScannerStatus.LOADING ->
                    Text(stringResource(R.string.scanner_loading), style = MaterialTheme.typography.bodyMedium)
                ScannerStatus.ERROR ->
                    Text(stringResource(R.string.scan_err_not_scanner), style = MaterialTheme.typography.bodyMedium)
                ScannerStatus.READY -> if (caps != null) {
                    Text(caps.model.ifBlank { stringResource(R.string.printer_fallback) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.scanner_glass, "%.1f".format(caps.maxWidth / 300f), "%.1f".format(caps.maxHeight / 300f)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when (vm.status) {
            ScannerStatus.NO_PRINTER -> Button(
                onClick = vm::refresh,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.cd_rescan), style = MaterialTheme.typography.labelMedium) }
            ScannerStatus.NEEDS_PERMISSION -> Button(
                onClick = vm::requestPermission,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.grant_permission)) }
            ScannerStatus.ERROR -> Button(onClick = vm::loadCapabilities, shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.scan_retry)) }
            else -> {}
        }
    }
}

@Composable
private fun MessageCard(m: ScanMessage, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(m.res, *m.args.toTypedArray()), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                if (m.detail.isNotBlank()) {
                    Text(m.detail, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close)) }
        }
    }
}

/** Shown before the first page, like the "Ready to print" card on the print screen. */
@Composable
private fun ScanEmptyHero() {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_scanner_hero),
                contentDescription = stringResource(R.string.scan_card_title),
                modifier = Modifier.size(130.dp).clip(RoundedCornerShape(18.dp)),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.scan_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.scan_place_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Preview, page strip and viewer
// ---------------------------------------------------------------------------------------------------------------------

/** Thumbnails of the scanned pages; tap one to preview it. Uses the same strip as the print screen's sheets. */
@Composable
private fun PageStrip(vm: ScanViewModel, onDark: Boolean = false) {
    ThumbStrip(
        count = vm.pages.size,
        current = vm.pageIndex,
        onSelect = vm::selectPage,
        aspectFor = { i -> vm.pages.getOrNull(i)?.let { it.widthPx.toFloat() / it.heightPx } ?: 0.707f },
        labelFor = { i -> "${i + 1}" },
        descriptionFor = { i -> stringResource(R.string.cd_scan_page, i + 1) },
        bitmapFor = { i -> vm.pages.getOrNull(i)?.let { rememberThumb(it.file) } },
        onDark = onDark,
        onDelete = if (onDark) null else { i -> vm.removePage(i) },
        deleteDescription = { stringResource(R.string.cd_delete_page) },
    )
}

@Composable
private fun ScanPreviewCard(vm: ScanViewModel, onOpenViewer: () -> Unit) {
    val count = vm.pages.size
    val page = vm.pages.getOrNull(vm.pageIndex) ?: return
    val image = vm.preview

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.scan_preview_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (!page.gray) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        stringResource(if (page.gray) R.string.mode_grayscale else R.string.mode_colour),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            PageStrip(vm)

            val targetAspect = page.widthPx.toFloat() / page.heightPx
            val aspect by animateFloatAsState(targetAspect, spring(stiffness = Spring.StiffnessMediumLow), label = "scan_aspect")

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .aspectRatio(aspect)
                        .shadow(6.dp, RoundedCornerShape(4.dp))
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, Color(0xFFDDDDDD)), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = image != null, onClick = onOpenViewer)
                        .swipeToChangeSheet(vm.pageIndex, count) { vm.selectPage(it) },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        // Keyed on the picture and the page it shows: a different page slides in from the right when it
                        // is later in the list and from the left when it is earlier.
                        targetState = image,
                        transitionSpec = {
                            val from = initialState?.index
                            val to = targetState?.index
                            val slide = tween<IntOffset>(280, easing = FastOutSlowInEasing)
                            val transform: ContentTransform = when {
                                from == null || to == null -> fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                                to > from ->
                                    (slideInHorizontally(slide) { w -> w } + fadeIn(tween(250))).togetherWith(
                                        slideOutHorizontally(slide) { w -> -w } + fadeOut(tween(200))
                                    )
                                to < from ->
                                    (slideInHorizontally(slide) { w -> -w } + fadeIn(tween(250))).togetherWith(
                                        slideOutHorizontally(slide) { w -> w } + fadeOut(tween(200))
                                    )
                                else -> fadeIn(tween(220)).togetherWith(fadeOut(tween(220)))
                            }
                            transform.using(SizeTransform(clip = false))
                        },
                        label = "scan_page_flip",
                    ) { shown ->
                        if (shown != null) {
                            Image(
                                bitmap = shown.bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_scan_page, shown.index + 1),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                    if (image != null) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.cd_fullscreen),
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(4.dp)
                                .size(18.dp),
                        )
                    }
                }
            }

            // Pager controls, as on the print screen
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.selectPage(vm.pageIndex - 1) }, enabled = vm.pageIndex > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_prev_sheet))
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Text(
                        stringResource(R.string.page_x_of_y, vm.pageIndex + 1, count),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = { vm.selectPage(vm.pageIndex + 1) }, enabled = vm.pageIndex < count - 1) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_next_sheet))
                }
            }

            // What can be done with the current page
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.movePage(vm.pageIndex, -1) }, enabled = vm.pageIndex > 0) {
                    Icon(Icons.Default.KeyboardDoubleArrowLeft, contentDescription = stringResource(R.string.cd_page_earlier))
                }
                IconButton(onClick = { vm.movePage(vm.pageIndex, 1) }, enabled = vm.pageIndex < count - 1) {
                    Icon(Icons.Default.KeyboardDoubleArrowRight, contentDescription = stringResource(R.string.cd_page_later))
                }
                IconButton(onClick = { vm.removePage(vm.pageIndex) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_page), tint = MaterialTheme.colorScheme.error)
                }
            }

            Text(
                stringResource(R.string.scan_preview_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScanViewer(vm: ScanViewModel, onDismiss: () -> Unit) {
    val position = stringResource(R.string.page_x_of_y, vm.pageIndex + 1, vm.pages.size)
    ZoomableViewer(
        image = vm.largePreview?.asImageBitmap(),
        grayscale = false,
        title = position,
        description = position,
        hint = stringResource(R.string.scan_viewer_hint),
        count = vm.pages.size,
        current = vm.pageIndex,
        onSelect = vm::selectPage,
        onDismiss = onDismiss,
        strip = { PageStrip(vm, onDark = true) },
    )
}

/** A small decoded copy of a scanned page for the strip (a full 300 dpi A4 page would be 35 MB). */
@Composable
private fun rememberThumb(file: File): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 400) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    return state.value
}

// ---------------------------------------------------------------------------------------------------------------------
// Settings as rows with popups
// ---------------------------------------------------------------------------------------------------------------------

private enum class ScanSetting(@StringRes val titleRes: Int, val icon: ImageVector) {
    AREA(R.string.scan_area, Icons.Default.Crop),
    RESOLUTION(R.string.scan_resolution, Icons.Default.HighQuality),
    COLOUR(R.string.setting_colour, Icons.Default.Palette),
}

@Composable
private fun scanSubtitle(vm: ScanViewModel, setting: ScanSetting): String {
    val s = vm.settings
    return when (setting) {
        ScanSetting.AREA -> stringResource(s.area.labelRes)
        ScanSetting.RESOLUTION -> stringResource(R.string.scan_dpi, s.dpi)
        ScanSetting.COLOUR -> stringResource(if (s.color) R.string.mode_colour else R.string.mode_grayscale)
    }
}

/** Scan settings as a plain list; tapping a row opens a popup for just that setting, like the print settings. */
@Composable
private fun ScanSettingsCard(vm: ScanViewModel) {
    val caps = vm.caps ?: return
    var open by remember { mutableStateOf<ScanSetting?>(null) }
    val rows = ScanSetting.entries.filter { it != ScanSetting.COLOUR || (caps.supportsColor && caps.supportsGray) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.scan_settings),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            rows.forEachIndexed { i, setting ->
                SettingRow(setting.icon, stringResource(setting.titleRes), scanSubtitle(vm, setting)) { open = setting }
                if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }

    open?.let { setting ->
        val close = { open = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(setting.titleRes)) },
            text = {
                val s = vm.settings
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (setting) {
                        ScanSetting.AREA -> RadioList(ScanArea.entries, s.area, { stringResource(it.labelRes) }) {
                            vm.updateSettings(s.copy(area = it)); close()
                        }
                        ScanSetting.RESOLUTION -> RadioList(caps.resolutions, s.dpi, { stringResource(R.string.scan_dpi, it) }) {
                            vm.updateSettings(s.copy(dpi = it)); close()
                        }
                        ScanSetting.COLOUR -> RadioList(
                            listOf(true, false), s.color,
                            { stringResource(if (it) R.string.colour_opt_colour else R.string.colour_opt_gray) },
                        ) { vm.updateSettings(s.copy(color = it)); close() }
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.dialog_done)) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Bottom bar and output
// ---------------------------------------------------------------------------------------------------------------------

/** The bar at the bottom: what has been scanned so far, progress while scanning, and the Scan button. */
@Composable
private fun StickyScanBar(vm: ScanViewModel) {
    val s = vm.settings
    Surface(tonalElevation = 6.dp, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = vm.scanning, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.scanning), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vm.pages.isEmpty()) stringResource(R.string.scan_no_pages)
                        else pluralStringResource(R.plurals.scanned_pages, vm.pages.size, vm.pages.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.scan_bar_detail,
                            stringResource(s.area.labelRes),
                            stringResource(R.string.scan_dpi, s.dpi),
                            stringResource(if (s.color) R.string.mode_colour else R.string.mode_grayscale),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = vm::scan,
                    enabled = !vm.scanning,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (vm.pages.isEmpty()) R.string.scan_button else R.string.scan_button_next),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutputCard(vm: ScanViewModel, onPrint: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.scan_output_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Button(onClick = onPrint, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_print))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.scan_save_pdf))
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_share))
                }
            }
            if (vm.saved) Text(stringResource(R.string.scan_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::clearPages, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.scan_clear)) }
        }
    }
}
