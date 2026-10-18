package crescentric.freeprint.otg.ui

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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import crescentric.freeprint.otg.print.DocEnhance
import crescentric.freeprint.otg.scan.ScanPage
import androidx.compose.material.icons.filled.Refresh
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
import crescentric.freeprint.otg.R
import crescentric.freeprint.otg.scan.ScanArea
import crescentric.freeprint.otg.scan.ScanMessage
import crescentric.freeprint.otg.scan.ScanViewModel
import crescentric.freeprint.otg.scan.ScannerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The scan tab, laid out like the print tab: a status card, a preview with a strip of page thumbnails, settings as tiles that
 * open popups, and (in [StickyScanBar], owned by the home screen) the main action. Place a page on the glass, scan, repeat for
 * more pages, then print a copy (the pages open in the print tab), save a PDF or share it. [onPrint] receives the files to print.
 */
@Composable
fun ScanContent(vm: ScanViewModel, onPrint: (List<Uri>) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showViewer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target != null) vm.withPdf { pdf -> vm.copyTo(pdf, target) {} }
    }
    // One picture goes to a file the user names; several go into a folder the user picks
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        if (target != null) vm.saveImages(target, folder = false)
    }
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { target ->
        if (target != null) vm.saveImages(target, folder = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScannerStatusCard(vm)
        vm.message?.let { MessageCard(it, vm::dismissMessage) }

        // Like the print tab, the start screen is always there before the first page; its button waits for a ready scanner
        if (vm.pages.isEmpty()) {
            ScanEmptyHero(onScan = vm::scan, enabled = vm.status == ScannerStatus.READY && !vm.scanning)
        }
        if (vm.status == ScannerStatus.READY) {
            if (vm.pages.isNotEmpty()) {
                ScanPreviewCard(vm, onOpenViewer = { showViewer = true; vm.setLargePreviewOpen(true) })
            }
            ScanSettingsCard(vm)
            if (vm.pages.isNotEmpty()) {
                OutputCard(
                    vm,
                    onPrint = { vm.withPdf(withLayout = false) { pdf -> onPrint(listOf(Uri.fromFile(pdf))) } },
                    onSavePdf = { savePdf.launch("scan.pdf") },
                    onSaveImages = { if (vm.outputCount == 1) saveImage.launch("scan.jpg") else saveFolder.launch(null) },
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
                    // Like the printer card: the name with a pulsing Ready badge, a line of detail underneath
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            caps.model.ifBlank { stringResource(R.string.printer_fallback) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ReadyBadge()
                    }
                    Text(
                        stringResource(R.string.scanner_glass, "%.1f".format(caps.maxWidth / 300f), "%.1f".format(caps.maxHeight / 300f)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when (vm.status) {
            ScannerStatus.NO_PRINTER -> IconButton(onClick = vm::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_rescan), tint = MaterialTheme.colorScheme.primary)
            }
            ScannerStatus.NEEDS_PERMISSION -> Button(
                onClick = vm::requestPermission,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.grant_permission)) }
            ScannerStatus.ERROR -> Button(onClick = vm::loadCapabilities, shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.scan_retry)) }
            ScannerStatus.READY -> IconButton(onClick = vm::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh), tint = MaterialTheme.colorScheme.primary)
            }
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
private fun ScanEmptyHero(onScan: () -> Unit, enabled: Boolean) {
    // Laid out like the print tab's start screen: no card, the picture, the words and the button on the page
    run {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("PDF", "JPEG").forEach { format ->
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp) {
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
            Button(
                onClick = onScan,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_start), fontWeight = FontWeight.SemiBold)
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
        bitmapFor = { i -> vm.pages.getOrNull(i)?.let { rememberThumb(it) } },
        onDark = onDark,
        scrollTick = vm.pageScrollTick,
        onDelete = { i -> vm.removePage(i) },
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
                // Appears once pages have been deleted
                if (vm.deletedCount > 0) RestoreButton(vm.deletedCount, vm::restorePages)
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
                        transitionSpec = { pageFlipTransition(initialState?.index, targetState?.index) },
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
                        // Optimize for print, for this page; tap again to go back to the scanner's own picture
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = stringResource(if (page.enhance) R.string.cd_enhance_revert else R.string.cd_enhance),
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(if (page.enhance) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
                                .clickable { vm.toggleEnhance(vm.pageIndex) }
                                .padding(5.dp)
                                .size(18.dp),
                        )
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
        image = remember(vm.largeImage) { vm.largeImage?.let { ViewerImage(it.index, it.bitmap.asImageBitmap()) } },
        grayscale = false,
        title = position,
        description = position,
        hint = stringResource(R.string.scan_viewer_hint),
        count = vm.pages.size,
        current = vm.pageIndex,
        onSelect = vm::selectPage,
        onDismiss = onDismiss,
        strip = { dark -> PageStrip(vm, onDark = dark) },
        actions = { tint ->
            val page = vm.pages.getOrNull(vm.pageIndex)
            if (page != null) {
                IconButton(onClick = { vm.toggleEnhance(vm.pageIndex) }) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = stringResource(if (page.enhance) R.string.cd_enhance_revert else R.string.cd_enhance),
                        tint = if (page.enhance) MaterialTheme.colorScheme.primary else tint
                    )
                }
            }
        },
    )
}

/** A small decoded copy of a scanned page for the strip (a full 300 dpi A4 page would be 35 MB). */
@Composable
private fun rememberThumb(page: ScanPage): ImageBitmap? {
    val file = page.file
    // Keyed on whether the page is optimized, so switching it redraws just this page's thumbnail
    val state = produceState<ImageBitmap?>(null, file, page.enhance) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 400) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = page.enhance })
                ?.also { if (page.enhance) DocEnhance.apply(it) }
                ?.asImageBitmap()
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
internal fun StickyScanBar(vm: ScanViewModel) {
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
private fun OutputCard(vm: ScanViewModel, onPrint: () -> Unit, onSavePdf: () -> Unit, onSaveImages: () -> Unit, onShare: () -> Unit) {
    var showSave by remember { mutableStateOf(false) }
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
                OutlinedButton(onClick = { showSave = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_save))
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
    if (showSave) SaveDialog(vm, onSavePdf = { showSave = false; onSavePdf() }, onSaveImages = { showSave = false; onSaveImages() }) { showSave = false }
}

/**
 * Choose how a scan is saved: as a PDF or as pictures, either each page as scanned or laid out on paper sheets with the same
 * settings the print screen has (pages per sheet, size and scale, margins, position, orientation, colour).
 */
@Composable
private fun SaveDialog(vm: ScanViewModel, onSavePdf: () -> Unit, onSaveImages: () -> Unit, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf<LayoutSetting?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.save_layout), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.save_layout_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.saveLayout, onCheckedChange = vm::useSaveLayout)
                }
                if (vm.saveLayout) {
                    LayoutSetting.entries.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { s ->
                                SettingTile(
                                    s.icon, stringResource(s.titleRes), layoutSubtitle(vm.saveOptions, s),
                                    onClick = { open = s },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.save_pictures, vm.outputCount, vm.outputCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onSavePdf, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_as_pdf))
                }
                OutlinedButton(onClick = onSaveImages, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_as_images))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
    open?.let { s ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(stringResource(s.titleRes)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LayoutSettingBody(s, vm.saveOptions, vm::updateSaveOptions) { open = null }
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.dialog_done)) } },
        )
    }
}
