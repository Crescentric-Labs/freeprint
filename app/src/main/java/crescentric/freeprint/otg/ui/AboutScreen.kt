package crescentric.freeprint.otg.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import crescentric.freeprint.otg.Changelog
import crescentric.freeprint.otg.ChangelogRelease
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import crescentric.freeprint.otg.PrintViewModel
import crescentric.freeprint.otg.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the project's source code lives. Leave it empty until the repository is public: the "Source code" row is hidden while this
 * is blank. When builds are distributed, the GPL expects users to be able to find the source, so set it before publishing.
 */
internal const val SOURCE_URL = "https://github.com/ChethiyaNuwan/freeprint"

/** The version of libusb vendored under app/src/main/cpp/third_party/libusb. */
private const val LIBUSB_VERSION = "1.0.30"

/** A licence text shipped inside the app (copied from LICENSE and libusb's COPYING by the build). */
private enum class LicenseDoc(val asset: String, @StringRes val titleRes: Int) {
    GPL("licenses/GPL-3.0.txt", R.string.about_license_gpl_title),
    LIBUSB("licenses/libusb-LGPL-2.1.txt", R.string.about_license_libusb_title),
}

/**
 * Who made the app, what it is, how it is licensed and what it is built on. For a GPL program this is also where the
 * "appropriate legal notices" (copyright holder, no warranty, how to read the licence) are shown to the user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: PrintViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    var showing by remember { mutableStateOf<LicenseDoc?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
            // Identity
            Card {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.app_mark),
                        contentDescription = stringResource(R.string.cd_app_icon),
                        modifier = Modifier.size(112.dp),
                    )
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (version.isNotEmpty()) {
                        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.about_developed_by), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.about_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // What changed in each release, from CHANGELOG.md; tap to open it
            ChangelogCard()

            // Hardware diagnostics, USB details and test prints
            OutlinedButton(
                onClick = { showDiagnostics = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cd_diagnostics))
            }

            // The GPL notice
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_license_notice), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showing = LicenseDoc.GPL }, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.about_view_license))
                    }
                }
            }

            // Third-party software
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.about_third_party), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_libusb, LIBUSB_VERSION), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showing = LicenseDoc.LIBUSB }, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.about_view_license))
                    }
                }
            }

            // Source code, once there is somewhere to point at
            if (SOURCE_URL.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) } catch (_: ActivityNotFoundException) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(stringResource(R.string.about_source)) }
            }
        }
    }

    if (showDiagnostics) DiagnosticsBottomSheet(vm = vm, onDismiss = { showDiagnostics = false })
    showing?.let { doc -> LicenseDialog(doc) { showing = null } }
}

/**
 * The project's changelog. Folded it shows the newest release; tapping the header unfolds every release, each with its groups
 * (Added, Changed, Fixed) and entries. The text comes from CHANGELOG.md, which the build copies into the app's assets.
 */
@Composable
private fun ChangelogCard() {
    val context = LocalContext.current
    val releases by produceState<List<ChangelogRelease>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { Changelog.parse(context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
        }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val all = releases.orEmpty()
    if (all.isEmpty()) return
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "changelog_arrow")

    Card {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.about_changelog_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.about_changelog_latest, all.first().version, all.first().date).trimEnd(' ', '·', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand),
                    modifier = Modifier.rotate(arrow),
                )
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider()
                    all.forEach { release -> ReleaseBlock(release) }
                }
            }
        }
    }
}

/** A changelog line with its **bold** and `code` markers turned into styling. */
@Composable
private fun inlineMarkdown(text: String) = buildAnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Changelog.spans(text).forEach { span ->
        when {
            span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            span.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(span.text) }
            else -> append(span.text)
        }
    }
}

@Composable
private fun ReleaseBlock(release: ChangelogRelease) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The version on the left, its date on the right
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Text(
                    release.version,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            if (release.date.isNotEmpty()) Text(release.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (release.note.isNotEmpty()) Text(inlineMarkdown(release.note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        release.sections.forEach { section ->
            Text(section.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            section.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodySmall)
                    Text(inlineMarkdown(item), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { content() }
}

/** The full text of one licence, read from the app's assets and shown in a scrollable, selectable box. */
@Composable
private fun LicenseDialog(doc: LicenseDoc, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null, doc) {
        value = withContext(Dispatchers.IO) { runCatching { context.assets.open(doc.asset).bufferedReader().use { it.readText() } }.getOrNull().orEmpty() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(doc.titleRes), style = MaterialTheme.typography.titleMedium) },
        text = {
            val shown = text
            if (shown == null) {
                CircularProgressIndicator()
            } else {
                SelectionContainer {
                    Text(
                        shown,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_done)) } },
    )
}
