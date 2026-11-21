package com.callgalli.data

import android.content.Context
import com.callgalli.rules.RuleSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val rules: RuleSettings = RuleSettings(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboardingDone: Boolean = false,
    /** Main On/Off switch. Off = no timing, no blocking, no background watcher. */
    val enabled: Boolean = true,
)

/**
 * Small settings live in SharedPreferences: they are read synchronously by the
 * call screening service, which must answer within a few seconds.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val defaults = RuleSettings()
        val rules = runCatching {
            RuleSettings(
                firstCallMinutes = prefs.getInt(KEY_FIRST, defaults.firstCallMinutes),
                repeatCallMinutes = prefs.getInt(KEY_REPEAT, defaults.repeatCallMinutes),
                resetMinuteOfDay = prefs.getInt(KEY_RESET, defaults.resetMinuteOfDay),
                firstWarningSeconds = prefs.getInt(KEY_WARN1, defaults.firstWarningSeconds),
                secondWarningSeconds = prefs.getInt(KEY_WARN2, defaults.secondWarningSeconds),
            )
        }.getOrDefault(defaults)
        val theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        return AppSettings(rules, theme, prefs.getBoolean(KEY_ONBOARDED, false), prefs.getBoolean(KEY_ENABLED, true))
    }

    fun updateRules(transform: (RuleSettings) -> RuleSettings) {
        val next = runCatching { transform(current.rules) }.getOrNull() ?: return
        prefs.edit()
            .putInt(KEY_FIRST, next.firstCallMinutes)
            .putInt(KEY_REPEAT, next.repeatCallMinutes)
            .putInt(KEY_RESET, next.resetMinuteOfDay)
            .putInt(KEY_WARN1, next.firstWarningSeconds)
            .putInt(KEY_WARN2, next.secondWarningSeconds)
            .apply()
        _settings.value = current.copy(rules = next)
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settings.value = current.copy(enabled = enabled)
    }

    fun setTheme(theme: ThemeMode) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _settings.value = current.copy(theme = theme)
    }

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, done).apply()
        _settings.value = current.copy(onboardingDone = done)
    }

    /** Back to defaults (keeps onboarding done so the user is not sent through it again). */
    fun resetToDefaults() {
        prefs.edit().clear().putBoolean(KEY_ONBOARDED, current.onboardingDone).apply()
        _settings.value = load()
    }

    private companion object {
        const val KEY_FIRST = "first_call_minutes"
        const val KEY_REPEAT = "repeat_call_minutes"
        const val KEY_RESET = "reset_minute_of_day"
        const val KEY_WARN1 = "first_warning_seconds"
        const val KEY_WARN2 = "second_warning_seconds"
        const val KEY_THEME = "theme"
        const val KEY_ONBOARDED = "onboarding_done"
        const val KEY_ENABLED = "timer_enabled"
    }
}
