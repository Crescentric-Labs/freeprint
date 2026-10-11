package crystaldigits.freeprint.otg.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import crystaldigits.freeprint.otg.R
import crystaldigits.freeprint.otg.scan.ScanArea
import crystaldigits.freeprint.otg.scan.ScanMessage
import crystaldigits.freeprint.otg.scan.ScanPage
import crystaldigits.freeprint.otg.scan.ScanViewModel
import crystaldigits.freeprint.otg.scan.ScannerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The scanner: place a page on the glass, scan, repeat for more pages, then save a PDF, share it, or send it straight to
 * the print screen as a copy. [onPrint] receives the files to print.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel, onBack: () -> Unit, onPrint: (List<Uri>) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refresh() }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        if (target != null) vm.withPdf { pdf -> vm.copyTo(pdf, target) {} }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_rescan))
                    }
                }
            )
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
            // 1. Scanner connection card with animated state transitions
            ScannerStatusCard(vm)

            // 2. Error or notification message with smooth entrance/exit
            AnimatedVisibility(
                visible = vm.message != null,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(180))
            ) {
                vm.message?.let { MessageCard(it, vm::dismissMessage) }
            }

            // 3. Scanner active sweeping beam when scanning is in progress
            AnimatedVisibility(
                visible = vm.scanning,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                ActiveScanningBeamCard()
            }

            // 4. Scan settings card (visible when scanner is ready)
            AnimatedVisibility(
                visible = vm.status == ScannerStatus.READY,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                ScanSettingsCard(vm)
            }

            // 5. Scan action button
            AnimatedVisibility(
                visible = vm.status == ScannerStatus.READY,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                ScanButton(vm)
            }

            // 6. Scanned pages preview and export actions
            AnimatedVisibility(
                visible = vm.pages.isNotEmpty(),
                enter = fadeIn(tween(350)) + expandVertically(tween(350)),
                exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PagesSection(vm)
                    OutputActions(
                        vm,
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
                        onPrint = { vm.withPdf { pdf -> onPrint(listOf(Uri.fromFile(pdf))) } },
                    )
                }
            }
        }
    }
}

/** Animated hardware scanning beam showing active physical scanner activity */
@Composable
private fun ActiveScanningBeamCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_beam")
    val beamProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_progress"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.scanning),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Illuminated scanner glass simulation with glowing light bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .border(BorderStroke(1.dp, Color(0xFF334155)), RoundedCornerShape(8.dp))
            ) {
                // Moving glowing cyan laser line
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (beamProgress * (size.width - 20.dp.toPx()))
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(36.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0x0006B6D4),
                                        Color(0xFF06B6D4),
                                        Color(0xFF67E8F9),
                                        Color(0xFF06B6D4),
                                        Color(0x0006B6D4)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerStatusCard(vm: ScanViewModel) {
    val caps = vm.caps

    // Living breathing pulse for ready state
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_ready_pulse")
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

    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        AnimatedContent(
            targetState = vm.status,
            transitionSpec = {
                (fadeIn(tween(250)) + expandVertically(tween(250))).togetherWith(
                    fadeOut(tween(180)) + shrinkVertically(tween(180))
                )
            },
            label = "scanner_status_anim"
        ) { status ->
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (status) {
                    ScannerStatus.LOADING -> CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    ScannerStatus.NO_PRINTER -> Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.UsbOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ScannerStatus.NEEDS_PERMISSION, ScannerStatus.ERROR -> Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    }
                    ScannerStatus.READY -> Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when (status) {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(caps.model.ifBlank { stringResource(R.string.printer_fallback) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                                stringResource(R.string.scanner_glass, "%.1f".format(caps.maxWidth / 300f), "%.1f".format(caps.maxHeight / 300f)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                when (status) {
                    ScannerStatus.NO_PRINTER -> OutlinedButton(onClick = vm::refresh) { Text(stringResource(R.string.cd_rescan)) }
                    ScannerStatus.NEEDS_PERMISSION -> Button(onClick = vm::requestPermission) { Text(stringResource(R.string.grant_permission)) }
                    ScannerStatus.ERROR -> Button(onClick = vm::loadCapabilities) { Text(stringResource(R.string.scan_retry)) }
                    else -> {}
                }
            }
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

@Composable
private fun ScanSettingsCard(vm: ScanViewModel) {
    val caps = vm.caps ?: return
    val s = vm.settings
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.scan_settings), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            ChipRow(stringResource(R.string.scan_area)) {
                ScanArea.entries.forEach { a ->
                    val isSelected = s.area == a
                    val chipColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "area_chip_color"
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = { vm.updateSettings(s.copy(area = a)) },
                        label = { Text(stringResource(a.labelRes)) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = chipColor)
                    )
                }
            }
            ChipRow(stringResource(R.string.scan_resolution)) {
                caps.resolutions.forEach { dpi ->
                    val isSelected = s.dpi == dpi
                    val chipColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "res_chip_color"
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = { vm.updateSettings(s.copy(dpi = dpi)) },
                        label = { Text(stringResource(R.string.scan_dpi, dpi)) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = chipColor)
                    )
                }
            }
            if (caps.supportsColor && caps.supportsGray) {
                ChipRow(stringResource(R.string.setting_colour)) {
                    val colorSelected = s.color
                    val colorChipColor by animateColorAsState(
                        targetValue = if (colorSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "color_chip_color"
                    )
                    val grayChipColor by animateColorAsState(
                        targetValue = if (!colorSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "gray_chip_color"
                    )
                    FilterChip(
                        selected = colorSelected,
                        onClick = { vm.updateSettings(s.copy(color = true)) },
                        label = { Text(stringResource(R.string.mode_colour)) },
                        leadingIcon = if (colorSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorChipColor)
                    )
                    FilterChip(
                        selected = !colorSelected,
                        onClick = { vm.updateSettings(s.copy(color = false)) },
                        label = { Text(stringResource(R.string.mode_grayscale)) },
                        leadingIcon = if (!colorSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = grayChipColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun ScanButton(vm: ScanViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = vm::scan,
            enabled = !vm.scanning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            AnimatedContent(
                targetState = vm.scanning,
                transitionSpec = {
                    fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
                },
                label = "scan_button_content"
            ) { isScanning ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    }
                    Text(
                        stringResource(if (vm.pages.isEmpty()) R.string.scan_button else R.string.scan_button_next),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        AnimatedContent(
            targetState = vm.scanning,
            transitionSpec = {
                fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
            },
            label = "scan_hint_text"
        ) { isScanning ->
            Text(
                stringResource(if (isScanning) R.string.scanning else R.string.scan_place_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PagesSection(vm: ScanViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.scanned_pages, vm.pages.size, vm.pages.size),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            itemsIndexed(vm.pages, key = { _, p -> p.file.path }) { index, page -> PageThumb(vm, index, page) }
        }
    }
}

@Composable
private fun PageThumb(vm: ScanViewModel, index: Int, page: ScanPage) {
    val bitmap = rememberThumb(page.file)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .height(150.dp)
                .aspectRatio(page.widthPx.toFloat() / page.heightPx)
                .shadow(3.dp, RoundedCornerShape(3.dp))
                .background(Color.White, RoundedCornerShape(3.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = bitmap,
                transitionSpec = {
                    fadeIn(tween(250)).togetherWith(fadeOut(tween(150)))
                },
                label = "thumb_crossfade"
            ) { bmp ->
                if (bmp != null) {
                    Image(bmp, stringResource(R.string.cd_scan_page, index + 1), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.movePage(index, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_prev_sheet), Modifier.size(18.dp))
            }
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
            IconButton(onClick = { vm.movePage(index, 1) }, enabled = index < vm.pages.lastIndex, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.cd_next_sheet), Modifier.size(18.dp))
            }
            IconButton(onClick = { vm.removePage(index) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.cd_clear_file), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun OutputActions(vm: ScanViewModel, onSave: () -> Unit, onShare: () -> Unit, onPrint: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        AnimatedVisibility(
            visible = vm.saved,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Text(stringResource(R.string.scan_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = vm::clearPages) { Text(stringResource(R.string.scan_clear)) }
    }
}

/** A small decoded copy of a scanned page for the strip (a full 300 dpi A4 page would be 35 MB). */
@Composable
private fun rememberThumb(file: File): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 700) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    return state.value
}
