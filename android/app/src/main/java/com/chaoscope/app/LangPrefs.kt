package com.chaoscope

import android.content.Context
import java.util.Locale

/** A selectable UI language: locale code + flag emoji for the picker rows. */
data class LangOption(val code: String, val flag: String)

/** Languages offered by the in-app pickers (splash + settings sheet). */
val LANGUAGES = listOf(
    LangOption("en",    "🇺🇸"),
    LangOption("pt-BR", "🇧🇷"),
    LangOption("fr",    "🇫🇷"),
    LangOption("es",    "🇪🇸"),
    LangOption("zh-CN", "🇨🇳"),
)

object LangPrefs {
    private const val PREFS_NAME = "lang_prefs"
    private const val KEY_LANG   = "lang_code"
    private const val DEFAULT    = "en"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANG, DEFAULT) ?: DEFAULT

    fun set(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, code).apply()
    }

    fun toLocale(code: String): Locale = when (code) {
        "pt-BR" -> Locale("pt", "BR")
        "fr"    -> Locale("fr")
        "es"    -> Locale("es")
        "zh-CN" -> Locale("zh", "CN")
        else    -> Locale("en")
    }

    fun wrap(base: Context): Context {
        val locale = toLocale(get(base))
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration).apply {
            setLocale(locale)
        }
        return base.createConfigurationContext(config)
    }
}
