package crescentric.freeprint.otg

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
import crescentric.freeprint.otg.scan.ScanViewModel
import crescentric.freeprint.otg.ui.AboutScreen
import crescentric.freeprint.otg.ui.PrintScreen
import crescentric.freeprint.otg.ui.ScanScreen

/** The screens: printing is home; scanning and the About page open on top and slide back. */
private enum class Screen { PRINT, SCAN, ABOUT }

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
            // Printing is the home screen. Scanning can hand its pages back to be printed as a copy; About just goes back.
            var screen by rememberSaveable { mutableStateOf(Screen.PRINT) }
            MaterialTheme(colorScheme = scheme) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (targetState != Screen.PRINT) {
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
                ) { shown ->
                    when (shown) {
                        Screen.PRINT -> PrintScreen(vm, onOpenScan = { screen = Screen.SCAN }, onOpenAbout = { screen = Screen.ABOUT })
                        Screen.SCAN -> {
                            BackHandler { screen = Screen.PRINT }
                            ScanScreen(
                                scanVm,
                                onBack = { screen = Screen.PRINT },
                                onPrint = { files -> vm.replaceFiles(files); screen = Screen.PRINT },
                            )
                        }
                        Screen.ABOUT -> {
                            BackHandler { screen = Screen.PRINT }
                            AboutScreen(vm, onBack = { screen = Screen.PRINT })
                        }
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
