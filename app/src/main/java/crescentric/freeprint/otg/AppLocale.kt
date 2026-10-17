package crescentric.freeprint.otg

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's own language choice. "" means follow the system (or Android 13's per-app language setting).
 * [wrap] is applied in attachBaseContext of the activity and the print service, so both show the same language.
 */
object AppLocale {
    private const val PREFS = "app_settings"
    private const val KEY = "language"

    /** Language tags offered in the picker; "" is the system default. */
    val choices = listOf("", "en", "si")

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun save(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** A context whose resources use the saved language, or [base] unchanged when following the system. */
    fun wrap(base: Context): Context {
        val tag = saved(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Saves [tag] and restarts the activity so every string is re-read. */
    fun change(activity: Activity, tag: String) {
        save(activity, tag)
        activity.recreate()
    }

    fun Context.findActivity(): Activity? {
        var c: Context = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
