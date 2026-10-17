package crescentric.freeprint.otg.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
internal const val SOURCE_URL = ""

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
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = stringResource(R.string.cd_app_icon),
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)),
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
