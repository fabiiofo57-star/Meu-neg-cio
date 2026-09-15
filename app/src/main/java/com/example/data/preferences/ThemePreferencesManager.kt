package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.AppIconStyle
import com.example.ui.theme.AppThemeKey
import com.example.ui.theme.AppThemeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferencesManager(private val context: Context) {

    private var currentUserId: String? = null
    private var prefs: SharedPreferences = getPrefsForUser(null)

    private val _themeState = MutableStateFlow(loadInitialState())
    val themeState: StateFlow<AppThemeState> = _themeState.asStateFlow()

    private fun getPrefsForUser(userId: String?): SharedPreferences {
        val prefsName = if (!userId.isNullOrBlank()) {
            "meu_negocio_theme_prefs_${userId.replace("/", "_")}"
        } else {
            "meu_negocio_theme_prefs_default"
        }
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    fun switchUser(userId: String?) {
        currentUserId = userId
        prefs = getPrefsForUser(userId)
        _themeState.value = loadInitialState()
    }

    private fun loadInitialState(): AppThemeState {
        val themeKeyStr = prefs.getString(KEY_THEME, AppThemeKey.VERDE.name) ?: AppThemeKey.VERDE.name
        val modeStr = prefs.getString(KEY_MODE, AppearanceMode.SYSTEM.name) ?: AppearanceMode.SYSTEM.name
        val iconStyleStr = prefs.getString(KEY_ICON_STYLE, AppIconStyle.ARREDONDADO.name) ?: AppIconStyle.ARREDONDADO.name
        val coverPhoto = prefs.getString(KEY_COVER_PHOTO, null)
        val profilePhoto = prefs.getString(KEY_PROFILE_PHOTO, null)
        val coverPreset = prefs.getString(KEY_COVER_PRESET, "gradient_emerald") ?: "gradient_emerald"

        val themeKey = try {
            AppThemeKey.valueOf(themeKeyStr)
        } catch (e: Exception) {
            AppThemeKey.VERDE
        }

        val mode = try {
            AppearanceMode.valueOf(modeStr)
        } catch (e: Exception) {
            AppearanceMode.SYSTEM
        }

        val iconStyle = try {
            AppIconStyle.valueOf(iconStyleStr)
        } catch (e: Exception) {
            AppIconStyle.ARREDONDADO
        }

        return AppThemeState(
            themeKey = themeKey,
            appearanceMode = mode,
            iconStyle = iconStyle,
            coverPhotoUri = coverPhoto,
            coverPresetId = coverPreset,
            profilePhotoUri = profilePhoto
        )
    }

    fun setThemeKey(themeKey: AppThemeKey) {
        prefs.edit().putString(KEY_THEME, themeKey.name).apply()
        _themeState.value = _themeState.value.copy(themeKey = themeKey)
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _themeState.value = _themeState.value.copy(appearanceMode = mode)
    }

    fun setIconStyle(iconStyle: AppIconStyle) {
        prefs.edit().putString(KEY_ICON_STYLE, iconStyle.name).apply()
        _themeState.value = _themeState.value.copy(iconStyle = iconStyle)
    }

    fun setCoverPhoto(uri: String?) {
        prefs.edit().putString(KEY_COVER_PHOTO, uri).apply()
        _themeState.value = _themeState.value.copy(coverPhotoUri = uri)
    }

    fun setProfilePhoto(uri: String?) {
        prefs.edit().putString(KEY_PROFILE_PHOTO, uri).apply()
        _themeState.value = _themeState.value.copy(profilePhotoUri = uri)
    }

    fun setCoverPreset(presetId: String) {
        prefs.edit().putString(KEY_COVER_PRESET, presetId).apply()
        _themeState.value = _themeState.value.copy(coverPresetId = presetId)
    }

    fun restoreDefaults() {
        prefs.edit()
            .putString(KEY_THEME, AppThemeKey.VERDE.name)
            .putString(KEY_MODE, AppearanceMode.SYSTEM.name)
            .putString(KEY_ICON_STYLE, AppIconStyle.ARREDONDADO.name)
            .remove(KEY_COVER_PHOTO)
            .putString(KEY_COVER_PRESET, "gradient_emerald")
            .apply()

        _themeState.value = _themeState.value.copy(
            themeKey = AppThemeKey.VERDE,
            appearanceMode = AppearanceMode.SYSTEM,
            iconStyle = AppIconStyle.ARREDONDADO,
            coverPhotoUri = null,
            coverPresetId = "gradient_emerald"
        )
    }

    fun updateState(newState: AppThemeState) {
        prefs.edit()
            .putString(KEY_THEME, newState.themeKey.name)
            .putString(KEY_MODE, newState.appearanceMode.name)
            .putString(KEY_ICON_STYLE, newState.iconStyle.name)
            .putString(KEY_COVER_PHOTO, newState.coverPhotoUri)
            .putString(KEY_PROFILE_PHOTO, newState.profilePhotoUri)
            .putString(KEY_COVER_PRESET, newState.coverPresetId)
            .apply()
        _themeState.value = newState
    }

    companion object {
        private const val KEY_THEME = "app_theme_key"
        private const val KEY_MODE = "app_appearance_mode"
        private const val KEY_ICON_STYLE = "app_icon_style"
        private const val KEY_COVER_PHOTO = "app_cover_photo"
        private const val KEY_PROFILE_PHOTO = "app_profile_photo"
        private const val KEY_COVER_PRESET = "app_cover_preset"
    }
}
