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
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import crescentric.freeprint.otg.ui.HomeScreen

/** The screens: the home screen (print and scan tabs) and the About page, which opens on top and slides back. */
private enum class Screen { HOME, ABOUT }

class MainActivity : ComponentActivity() {
    private val vm: PrintViewModel by viewModels()
    private val scanVm: ScanViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate: swaps the starting theme for the app's, and takes over the splash's exit
        installSplashScreen().setOnExitAnimationListener { splash ->
            // The icon swells a little and fades while the blue backdrop fades away over the app
            val icon = splash.iconView
            icon.animate().scaleX(1.25f).scaleY(1.25f).alpha(0f).setDuration(380).setInterpolator(AccelerateDecelerateInterpolator()).start()
            splash.view.animate().alpha(0f).setStartDelay(120).setDuration(320).withEndAction { splash.remove() }.start()
        }
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
            var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
            MaterialTheme(colorScheme = scheme) {
              // A solid base under the sliding screens: while one fades or slides, what shows through is this, not the window
              Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (targetState != Screen.HOME) {
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
                        Screen.HOME -> HomeScreen(vm, scanVm, onOpenAbout = { screen = Screen.ABOUT })
                        Screen.ABOUT -> {
                            BackHandler { screen = Screen.HOME }
                            AboutScreen(vm, onBack = { screen = Screen.HOME })
                        }
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
