package dev.sumanth.spd.utils

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/** Applies the language chosen inside the app (English by default), independent of the system language. */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val locale = if (SharedPref.readLanguage(base) == SharedPref.LANG_PT_BR) {
            Locale.forLanguageTag("pt-BR")
        } else {
            Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun string(context: Context, @StringRes id: Int, vararg args: Any): String {
        return wrap(context).getString(id, *args)
    }
}

/** User-facing error whose text depends on the selected language (still an IllegalArgumentException as before). */
class LocalizedException(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
    fallback: String
) : IllegalArgumentException(fallback)
