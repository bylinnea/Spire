package no.bylinnea.spire.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

/**
 * Manages all app preferences and API key storage.
 * API keys are stored in EncryptedSharedPreferences.
 * All other settings use plain SharedPreferences.
 */
object ApiKeyManager {

    private const val ENCRYPTED_PREFS_FILE = "plant_mom_secure_prefs"
    private const val PLAIN_PREFS_FILE     = "plant_mom_prefs"

    private const val KEY_ANTHROPIC    = "anthropic_api_key"
    private const val KEY_PLANTNET     = "plantnet_api_key"
    private const val KEY_AI_ENABLED   = "ai_features_enabled"
    private const val KEY_ONBOARDING   = "onboarding_complete"
    private const val KEY_SINGLE_NOTIF = "single_notification"
    private const val KEY_WINTER_MODE  = "winter_mode_enabled"
    private const val KEY_NAME_STYLE   = "plant_name_style"
    private const val KEY_LOG_WATER     = "log_watering"
    private const val KEY_LOG_FERTILIZE = "log_fertilize"
    private const val KEY_LOG_REPOT     = "log_repot"
    private const val KEY_LOG_MIST      = "log_mist"
    private const val KEY_LOG_ROTATE    = "log_rotate"
    private const val KEY_LOG_CLEAN = "log_clean"
    private const val KEY_AI_DISCLAIMER = "ai_disclaimer_shown"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PLAIN_PREFS_FILE, Context.MODE_PRIVATE)

    private fun encrypted(context: Context): SharedPreferences {
        fun build(context: Context): SharedPreferences {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                context, ENCRYPTED_PREFS_FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            build(context)
        } catch (e: Exception) {
            // If the encrypted store is corrupted (e.g. after a reinstall),
            // clear it and rebuild rather than crashing
            context.getSharedPreferences(ENCRYPTED_PREFS_FILE, Context.MODE_PRIVATE)
                .edit { clear() }
            build(context)
        }
    }

    fun saveAnthropicKey(context: Context, key: String) =
        encrypted(context).edit { putString(KEY_ANTHROPIC, key.trim()) }
    fun getAnthropicKey(context: Context): String? =
        encrypted(context).getString(KEY_ANTHROPIC, null)?.takeIf { it.isNotBlank() }
    fun hasAnthropicKey(context: Context) = getAnthropicKey(context) != null
    fun clearAnthropicKey(context: Context) =
        encrypted(context).edit { remove(KEY_ANTHROPIC) }

    fun savePlantNetKey(context: Context, key: String) =
        encrypted(context).edit { putString(KEY_PLANTNET, key.trim()) }
    fun getPlantNetKey(context: Context): String? =
        encrypted(context).getString(KEY_PLANTNET, null)?.takeIf { it.isNotBlank() }
    fun hasPlantNetKey(context: Context) = getPlantNetKey(context) != null
    fun clearPlantNetKey(context: Context) =
        encrypted(context).edit { remove(KEY_PLANTNET) }

    fun isAiEnabled(context: Context) =
        prefs(context).getBoolean(KEY_AI_ENABLED, false)
    fun setAiEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_AI_ENABLED, enabled) }

    fun isOnboardingComplete(context: Context) =
        prefs(context).getBoolean(KEY_ONBOARDING, false)
    fun setOnboardingComplete(context: Context, complete: Boolean) =
        prefs(context).edit { putBoolean(KEY_ONBOARDING, complete) }

    fun isSingleNotification(context: Context) =
        prefs(context).getBoolean(KEY_SINGLE_NOTIF, false)
    fun setSingleNotification(context: Context, single: Boolean) =
        prefs(context).edit { putBoolean(KEY_SINGLE_NOTIF, single) }

    fun isWinterModeEnabled(context: Context) =
        prefs(context).getBoolean(KEY_WINTER_MODE, false)
    fun setWinterModeEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_WINTER_MODE, enabled) }

    fun getNameStyle(context: Context): String? =
        prefs(context).getString(KEY_NAME_STYLE, null)?.takeIf { it.isNotBlank() }
    fun setNameStyle(context: Context, style: String?) =
        prefs(context).edit { putString(KEY_NAME_STYLE, style) }

    fun isLogEnabled(context: Context, type: CareTask.CareType): Boolean = when (type) {
        CareTask.CareType.WATER     -> prefs(context).getBoolean(KEY_LOG_WATER,     false)
        CareTask.CareType.FERTILIZE -> prefs(context).getBoolean(KEY_LOG_FERTILIZE, true)
        CareTask.CareType.REPOT     -> prefs(context).getBoolean(KEY_LOG_REPOT,     true)
        CareTask.CareType.MIST      -> prefs(context).getBoolean(KEY_LOG_MIST,      false)
        CareTask.CareType.ROTATE    -> prefs(context).getBoolean(KEY_LOG_ROTATE,    false)
        CareTask.CareType.CLEAN     -> prefs(context).getBoolean(KEY_LOG_CLEAN, false)
    }

    fun setLogEnabled(context: Context, type: CareTask.CareType, enabled: Boolean) {
        val key = when (type) {
            CareTask.CareType.WATER     -> KEY_LOG_WATER
            CareTask.CareType.FERTILIZE -> KEY_LOG_FERTILIZE
            CareTask.CareType.REPOT     -> KEY_LOG_REPOT
            CareTask.CareType.MIST      -> KEY_LOG_MIST
            CareTask.CareType.ROTATE    -> KEY_LOG_ROTATE
            CareTask.CareType.CLEAN     -> KEY_LOG_CLEAN
        }
        prefs(context).edit { putBoolean(key, enabled) }
    }

    fun enabledLogTypes(context: Context): List<CareTask.CareType> =
        CareTask.CareType.entries.filter { isLogEnabled(context, it) }

    fun isAiDisclaimerShown(context: Context) =
        prefs(context).getBoolean(KEY_AI_DISCLAIMER, false)

    fun setAiDisclaimerShown(context: Context) =
        prefs(context).edit { putBoolean(KEY_AI_DISCLAIMER, true) }
}