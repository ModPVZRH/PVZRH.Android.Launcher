package com.bepinex.android.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application settings persisted via SharedPreferences.
 *
 * Covers: theme mode, language preference, and settings actions.
 */
object AppSettings {

    private const val PREFS_NAME = "bepinex_launcher_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_FLOATING_LOG_IN_GAME = "floating_log_in_game"
    private const val KEY_ACTIVE_MODPACK_PREFIX = "active_modpack_"
    private const val KEY_USE_UNSTRIPPED_LIBUNITY = "use_unstripped_libunity"
    private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
    private const val KEY_LAST_SEEN_ANNOUNCEMENT = "last_seen_announcement_date"
    private const val KEY_ANIMATION_DISABLED = "animation_disabled"
    private const val KEY_LANGUAGE_INCOMPLETE_SHOWN = "language_incomplete_shown"
    private const val KEY_PENDING_INCOMPLETE_DIALOG = "pending_incomplete_dialog"

    /** Static flag for pending incomplete dialog (survives activity recreation). */
    @Volatile
    var pendingIncompleteDialog = false
        private set

    enum class ThemeMode {
        SYSTEM, DARK, LIGHT;

        companion object {
            fun fromString(value: String?): ThemeMode = when (value) {
                "dark" -> DARK
                "light" -> LIGHT
                else -> SYSTEM  // default: follow system (Material You)
            }
        }
    }

    enum class Language(val key: String) {
        SYSTEM("system"),
        ENGLISH("en"),
        CHINESE("zh"),
        CHINESE_TW("zh-TW"),
        JAPANESE("ja"),
        KOREAN("ko"),
        RUSSIAN("ru"),
        PORTUGUESE("pt"),
        PORTUGUESE_BR("pt-BR"),
        SPANISH("es"),
        GERMAN("de"),
        FRENCH("fr"),
        ITALIAN("it"),
        DUTCH("nl"),
        TURKISH("tr"),
        ARABIC("ar"),
        UKRAINIAN("uk"),
        MALAYAML("ml"),
        VENETIAN("vec");

        companion object {
            fun fromKey(value: String?): Language = entries.find { it.key == value } ?: SYSTEM

            fun isCompleteTranslation(lang: Language) = lang == SYSTEM || lang == ENGLISH || lang == CHINESE
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Theme

    fun getThemeMode(context: Context): ThemeMode =
        ThemeMode.fromString(prefs(context).getString(KEY_THEME_MODE, null))

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name.lowercase()).apply()
        applyDayNight(mode)
    }

    fun applyDayNight(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    // Language

    fun getLanguage(context: Context): Language =
        Language.fromKey(prefs(context).getString(KEY_LANGUAGE, null))

    fun setLanguage(context: Context, language: Language) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.key).apply()
    }

    fun isLanguageIncompleteShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LANGUAGE_INCOMPLETE_SHOWN, false)

    fun setLanguageIncompleteShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean(KEY_LANGUAGE_INCOMPLETE_SHOWN, shown).apply()
    }

    fun isPendingIncompleteDialog(context: Context): Boolean =
        pendingIncompleteDialog || prefs(context).getBoolean(KEY_PENDING_INCOMPLETE_DIALOG, false)

    fun setPendingIncompleteDialog(context: Context, pending: Boolean) {
        pendingIncompleteDialog = pending
        prefs(context).edit().putBoolean(KEY_PENDING_INCOMPLETE_DIALOG, pending).commit()
    }

    // Floating Log in Game

    fun isFloatingLogInGameEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOATING_LOG_IN_GAME, false)

    fun setFloatingLogInGameEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLOATING_LOG_IN_GAME, enabled).apply()
    }

    fun getActiveModpack(context: Context, packageName: String): String? =
        prefs(context).getString(KEY_ACTIVE_MODPACK_PREFIX + packageName, null)?.ifEmpty { null }

    fun setActiveModpack(context: Context, packageName: String, modpackName: String?) {
        prefs(context).edit().putString(KEY_ACTIVE_MODPACK_PREFIX + packageName, modpackName ?: "").apply()
    }

    fun isUseUnstrippedLibUnity(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_UNSTRIPPED_LIBUNITY, true)

    fun setUseUnstrippedLibUnity(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_UNSTRIPPED_LIBUNITY, enabled).apply()
    }

    // Dynamic Color (Material You / Monet)

    fun isDynamicColorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_DYNAMIC_COLOR, false)

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_DYNAMIC_COLOR, enabled).apply()
    }

    // Animation

    fun isAnimationDisabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANIMATION_DISABLED, false)

    fun setAnimationDisabled(context: Context, disabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANIMATION_DISABLED, disabled).apply()
    }

    fun getLastSeenAnnouncementDate(context: Context): String =
        prefs(context).getString(KEY_LAST_SEEN_ANNOUNCEMENT, "") ?: ""

    fun setLastSeenAnnouncementDate(context: Context, date: String) {
        prefs(context).edit().putString(KEY_LAST_SEEN_ANNOUNCEMENT, date).apply()
    }

    // Initialize on startup

    fun initialize(context: Context) {
        applyDayNight(getThemeMode(context))
        // Language is handled via attachBaseContext in MainActivity  -- no need to apply here
    }
}
