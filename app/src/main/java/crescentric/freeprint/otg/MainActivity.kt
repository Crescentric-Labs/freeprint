package crescentric.freeprint.otg

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.IntentCompat
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
            // The system gives no icon view when it did not draw one (for example when the app is opened from a share), so it may be null
            val icon: android.view.View? = try { splash.iconView } catch (e: Exception) { null }
            icon?.animate()?.scaleX(1.25f)?.scaleY(1.25f)?.alpha(0f)?.setDuration(380)?.setInterpolator(AccelerateDecelerateInterpolator())?.start()
            splash.view.animate().alpha(0f).setStartDelay(120).setDuration(320).withEndAction { splash.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncoming(intent)
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
            // Files that arrive from another app are shown on the print tab, whatever was open
            LaunchedEffect(vm.externalOpenTick) { if (vm.externalOpenTick > 0) screen = Screen.HOME }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /** Opens what was shared to the app or opened with it: PDFs, pictures and text. Anything else is ignored. */
    private fun handleIncoming(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> return
        }
        // Some apps share through the clip data instead of the extra
        val found = uris.ifEmpty { intent?.clipData?.let { c -> (0 until c.itemCount).mapNotNull { c.getItemAt(it).uri } }.orEmpty() }
        // Text shared on its own (a note, a message, a link) is printed as it is
        val text = if (intent?.action == Intent.ACTION_SEND && found.isEmpty()) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
        vm.openFromOutside(found, text, intent?.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPrinter()
    }
}
