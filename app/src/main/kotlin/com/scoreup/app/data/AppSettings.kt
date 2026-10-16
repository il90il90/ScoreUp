package com.scoreup.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class SettingsState(
    val sounds: Boolean = true,
    val haptics: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    /** "" = follow system, otherwise a BCP-47 tag ("en", "iw"). */
    val language: String = "",
    val keepScreenOn: Boolean = true,
    val onboarded: Boolean = false,
    val castAppId: String = "",
)

/** Small user preferences, observable from Compose. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("scoreup_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun read() = SettingsState(
        sounds = prefs.getBoolean("sounds", true),
        haptics = prefs.getBoolean("haptics", true),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.DARK.name)!!) }.getOrDefault(ThemeMode.DARK),
        language = prefs.getString("language", "") ?: "",
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        onboarded = prefs.getBoolean("onboarded", false),
        castAppId = prefs.getString("castAppId", "") ?: "",
    )

    fun update(block: (SettingsState) -> SettingsState) {
        val s = block(_state.value)
        prefs.edit()
            .putBoolean("sounds", s.sounds).putBoolean("haptics", s.haptics)
            .putString("theme", s.theme.name).putString("language", s.language)
            .putBoolean("keepScreenOn", s.keepScreenOn).putBoolean("onboarded", s.onboarded)
            .putString("castAppId", s.castAppId.trim())
            .apply()
        _state.value = s
    }

    companion object {
        /** Read synchronously (before Compose) e.g. for locale in attachBaseContext. */
        fun language(context: Context): String =
            context.getSharedPreferences("scoreup_settings", Context.MODE_PRIVATE).getString("language", "") ?: ""
        fun castAppId(context: Context): String =
            context.getSharedPreferences("scoreup_settings", Context.MODE_PRIVATE).getString("castAppId", "") ?: ""
    }
}
