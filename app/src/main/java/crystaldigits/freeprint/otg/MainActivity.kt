package crystaldigits.freeprint.otg

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import crystaldigits.freeprint.otg.scan.ScanViewModel
import crystaldigits.freeprint.otg.ui.PrintScreen
import crystaldigits.freeprint.otg.ui.ScanScreen

class MainActivity : ComponentActivity() {
    private val vm: PrintViewModel by viewModels()
    private val scanVm: ScanViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            // Two screens: printing (the home screen) and scanning, which can hand its pages back to be printed as a copy.
            var scanning by rememberSaveable { mutableStateOf(false) }
            MaterialTheme(colorScheme = scheme) {
                AnimatedContent(
                    targetState = scanning,
                    transitionSpec = {
                        if (targetState) {
                            (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(tween(250))).togetherWith(
                                slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> -width / 4 } + fadeOut(tween(200))
                            )
                        } else {
                            (slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { width -> -width / 4 } + fadeIn(tween(250))).togetherWith(
                                slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(tween(200))
                            )
                        }
                    },
                    label = "screen_navigation"
                ) { isScanning ->
                    if (isScanning) {
                        BackHandler { scanning = false }
                        ScanScreen(
                            scanVm,
                            onBack = { scanning = false },
                            onPrint = { files -> vm.replaceFiles(files); scanning = false },
                        )
                    } else {
                        PrintScreen(vm, onOpenScan = { scanning = true })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPrinter()
    }
}
