package crescentric.freeprint.otg.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crescentric.freeprint.otg.PrintViewModel
import crescentric.freeprint.otg.R
import crescentric.freeprint.otg.scan.ScanViewModel
import crescentric.freeprint.otg.scan.ScannerStatus

/** What sits at the bottom of the home screen: a tab's action bar, the dock, or nothing. */
private enum class BottomState { PRINT_BAR, SCAN_BAR, DOCK, NONE }

/** The two things the app does. They share the home screen; the floating dock switches between them. */
private enum class HomeTab(val icon: ImageVector, val labelRes: Int) {
    PRINT(Icons.Default.Print, R.string.dock_print),
    SCAN(Icons.Default.DocumentScanner, R.string.dock_scan),
}

/**
 * Home: one scaffold for printing and scanning. A floating dock at the bottom switches the tab; each tab shows its own cards.
 * Once a document (print) or scanned pages (scan) are in play the dock gives way to that tab's action bar, and a back arrow
 * appears in front of the app icon to put the app back to how it started. [onOpenAbout] opens the About page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: PrintViewModel, scanVm: ScanViewModel, onOpenAbout: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.PRINT) }
    var showLanguage by remember { mutableStateOf(false) }
    // Files shared or opened from another app go to the print tab
    androidx.compose.runtime.LaunchedEffect(vm.externalOpenTick) { if (vm.externalOpenTick > 0) tab = HomeTab.PRINT }
    var confirmDiscardScan by remember { mutableStateOf(false) }

    val hasContent = when (tab) {
        HomeTab.PRINT -> vm.doc != null
        HomeTab.SCAN -> scanVm.pages.isNotEmpty()
    }
    // Back puts the tab back to its start. Scanned pages are not kept anywhere else, so that one asks first.
    val goBack: () -> Unit = {
        when (tab) {
            HomeTab.PRINT -> vm.resetAll()
            HomeTab.SCAN -> confirmDiscardScan = true
        }
    }
    BackHandler(enabled = hasContent, onBack = goBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    AnimatedVisibility(visible = hasContent, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.app_mark),
                            contentDescription = stringResource(R.string.cd_app_icon),
                            modifier = Modifier.size(38.dp),
                        )
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                stringResource(if (tab == HomeTab.PRINT) R.string.app_subtitle else R.string.scan_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLanguage = true }) {
                        Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.cd_language))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.cd_about))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            // One thing at the bottom at a time, swapped by a single animation. Showing and hiding the bars separately made them
            // stack for a moment, so the new one appeared too high and then dropped into place.
            val bottom = when {
                tab == HomeTab.PRINT && vm.doc != null -> BottomState.PRINT_BAR
                tab == HomeTab.SCAN && scanVm.pages.isNotEmpty() ->
                    if (scanVm.status == ScannerStatus.READY) BottomState.SCAN_BAR else BottomState.NONE
                else -> BottomState.DOCK
            }
            AnimatedContent(
                targetState = bottom,
                transitionSpec = {
                    (slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260))).togetherWith(
                        slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160))
                    ).using(SizeTransform(clip = false) { _, _ -> tween(380, easing = FastOutSlowInEasing) })
                },
                label = "home_bottom",
            ) { shown ->
                when (shown) {
                    BottomState.PRINT_BAR -> StickyPrintBar(vm)
                    BottomState.SCAN_BAR -> StickyScanBar(scanVm)
                    BottomState.DOCK -> FloatingDock(tab) { tab = it }
                    BottomState.NONE -> Spacer(Modifier.height(0.dp))
                }
            }
        },
    ) { padding ->
        // Swiping an empty part of the home screen moves between the tabs (while a document is open it is left to the page)
        var swipe by remember { mutableFloatStateOf(0f) }
        val threshold = with(LocalDensity.current) { 72.dp.toPx() }
        AnimatedContent(
            modifier = Modifier.pointerInput(hasContent, tab) {
                if (!hasContent) detectHorizontalDragGestures(
                    onDragStart = { swipe = 0f },
                    onDragEnd = {
                        when {
                            swipe < -threshold && tab == HomeTab.PRINT -> tab = HomeTab.SCAN
                            swipe > threshold && tab == HomeTab.SCAN -> tab = HomeTab.PRINT
                        }
                        swipe = 0f
                    },
                    onDragCancel = { swipe = 0f },
                    onHorizontalDrag = { _, amount -> swipe += amount },
                )
            },
            targetState = tab,
            transitionSpec = {
                val toScan = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (toScan) it / 3 else -it / 3 } + fadeIn(tween(250))).togetherWith(
                    slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { if (toScan) -it / 3 else it / 3 } + fadeOut(tween(150))
                )
            },
            label = "home_tab",
        ) { shown ->
            when (shown) {
                HomeTab.PRINT -> PrintContent(vm, Modifier.padding(padding))
                HomeTab.SCAN -> ScanContent(
                    scanVm,
                    onPrint = { files: List<Uri> -> vm.replaceFiles(files); tab = HomeTab.PRINT },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showLanguage) LanguageDialog { showLanguage = false }
    if (confirmDiscardScan) {
        AlertDialog(
            onDismissRequest = { confirmDiscardScan = false },
            title = { Text(stringResource(R.string.scan_discard_title)) },
            text = { Text(stringResource(R.string.scan_discard_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscardScan = false; scanVm.clearPages() }) { Text(stringResource(R.string.scan_discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscardScan = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** A pill that floats above the content with one item per tab; a highlight slides and stretches to whichever tab is chosen. */
@Composable
private fun FloatingDock(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    // Where each item sits inside the dock (x, width, height in pixels), reported by the items as they are laid out
    val spots = remember { mutableStateMapOf<HomeTab, IntArray>() }
    val target = spots[selected]
    val spring = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)
    val x by animateFloatAsState(target?.get(0)?.toFloat() ?: 0f, spring, label = "dock_x")
    val w by animateFloatAsState(target?.get(1)?.toFloat() ?: 0f, spring, label = "dock_w")
    val h = target?.get(2) ?: 0

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
        ) {
            Box(Modifier.padding(6.dp)) {
                if (target != null) {
                    val density = LocalDensity.current
                    Box(
                        Modifier
                            .offset { IntOffset(x.roundToInt(), 0) }
                            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                            .clip(RoundedCornerShape(26.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HomeTab.entries.forEach { tab ->
                        DockItem(
                            tab, tab == selected,
                            modifier = Modifier.onGloballyPositioned { c ->
                                spots[tab] = intArrayOf(c.positionInParent().x.roundToInt(), c.size.width, c.size.height)
                            },
                        ) { onSelect(tab) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockItem(tab: HomeTab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "dock_item_fg"
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(stringResource(tab.labelRes), color = content, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
