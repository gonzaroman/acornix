package com.gonzaroman.acornix.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gonzaroman.acornix.ai.AiProvider
import com.gonzaroman.acornix.ai.AnthropicProvider
import com.gonzaroman.acornix.ai.GeminiProvider
import com.gonzaroman.acornix.ai.OpenAiProvider
import com.gonzaroman.acornix.ai.ProviderNotConfiguredException
import com.gonzaroman.acornix.ai.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Language used for the UI text of generated apps. AUTO follows the phone. */
enum class AppLanguage(val id: String) { AUTO("auto"), SPANISH("es"), ENGLISH("en") }

data class ProviderConfig(
    val type: ProviderType,
    val model: String,
    val apiKey: String,
    val baseUrl: String,
) {
    val isUsable: Boolean
        get() = model.isNotBlank() && when (type) {
            ProviderType.CUSTOM -> baseUrl.isNotBlank()
            else -> apiKey.isNotBlank()
        }
}

data class Settings(
    val provider: ProviderType = ProviderType.ANTHROPIC,
    val models: Map<ProviderType, String> = emptyMap(),
    val keys: Map<ProviderType, String> = emptyMap(),
    val customBaseUrl: String = "",
    val onboardingDone: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.AUTO,
    val speakReplies: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
) {
    fun config(type: ProviderType = provider) = ProviderConfig(
        type = type,
        model = models[type]?.takeIf { it.isNotBlank() } ?: type.defaultModel,
        apiKey = keys[type].orEmpty(),
        baseUrl = if (type == ProviderType.CUSTOM) customBaseUrl else "",
    )
}

class SettingsRepository(private val context: Context) {

    private object K {
        val provider = stringPreferencesKey("provider")
        val customBaseUrl = stringPreferencesKey("custom_base_url")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val appLanguage = stringPreferencesKey("app_language")
        val speakReplies = booleanPreferencesKey("speak_replies")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        fun model(t: ProviderType) = stringPreferencesKey("model_${t.id}")
        fun key(t: ProviderType) = stringPreferencesKey("key_${t.id}")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        provider = ProviderType.fromId(this[K.provider]),
        models = ProviderType.entries.associateWith { this[K.model(it)].orEmpty() },
        keys = ProviderType.entries.associateWith { t -> this[K.key(t)]?.let { KeyCrypto.decrypt(it) }.orEmpty() },
        customBaseUrl = this[K.customBaseUrl].orEmpty(),
        onboardingDone = this[K.onboardingDone] ?: false,
        appLanguage = AppLanguage.entries.firstOrNull { it.id == this[K.appLanguage] } ?: AppLanguage.AUTO,
        speakReplies = this[K.speakReplies] ?: false,
        theme = runCatching { ThemeMode.valueOf(this[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = this[K.dynamicColor] ?: true,
    )

    suspend fun saveProvider(type: ProviderType, model: String, apiKey: String, baseUrl: String? = null) {
        context.dataStore.edit {
            it[K.provider] = type.id
            it[K.model(type)] = model.trim()
            it[K.key(type)] = KeyCrypto.encrypt(apiKey.trim())
            if (baseUrl != null) it[K.customBaseUrl] = baseUrl.trim()
        }
    }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboardingDone] = true }
    suspend fun setAppLanguage(lang: AppLanguage) = context.dataStore.edit { it[K.appLanguage] = lang.id }
    suspend fun setSpeakReplies(on: Boolean) = context.dataStore.edit { it[K.speakReplies] = on }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[K.dynamicColor] = on }

    /** Builds a provider client from the active configuration. */
    suspend fun activeProvider(): AiProvider = createProvider(current().config())

    companion object {
        fun createProvider(c: ProviderConfig): AiProvider {
            if (!c.isUsable) throw ProviderNotConfiguredException()
            return when (c.type) {
                ProviderType.ANTHROPIC -> AnthropicProvider(c.apiKey, c.model)
                ProviderType.OPENAI -> OpenAiProvider(c.apiKey, c.model)
                ProviderType.GEMINI -> GeminiProvider(c.apiKey, c.model)
                ProviderType.CUSTOM -> OpenAiProvider(c.apiKey, c.model, c.baseUrl)
            }
        }
    }
}

/** Encrypts API keys with an AES-GCM key that never leaves the Android Keystore. */
object KeyCrypto {
    private const val ALIAS = "acornix_api_keys"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        if (encoded.isEmpty()) return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}
