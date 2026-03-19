package com.letr.chatui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.data.repository.RealSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RealSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var settingsRoot: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        settingsRoot = File(context.filesDir, "settings-test-${UUID.randomUUID()}")
        settingsRoot.mkdirs()
    }

    @Test
    fun `repository exposes masked chat settings but keeps raw api key in active config`() = runBlocking {
        val repository = createRepository()

        repository.updateNonSensitiveSettings(
            NonSensitiveChatSettings(
                apiBaseUrl = " https://api.example.com/v1/ ",
                modelId = " gpt-4o-mini ",
            )
        )
        repository.setApiKey(" sk-secret-1234 ")

        val chatSettings = repository.getChatSettings()
        val activeConfig = repository.getActiveConfig()
        val providerConfig = repository.getOpenAiProviderConfig()

        assertEquals("https://api.example.com/v1/", chatSettings.apiBaseUrl)
        assertEquals("gpt-4o-mini", chatSettings.modelId)
        assertEquals(
            PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            chatSettings.apiKeyState,
        )
        assertEquals("https://api.example.com/v1/", activeConfig.apiBaseUrl)
        assertEquals("sk-secret-1234", activeConfig.apiKey)
        assertEquals("gpt-4o-mini", activeConfig.modelId)
        assertEquals("https://api.example.com/v1/", providerConfig.baseUrl)
        assertEquals("sk-secret-1234", providerConfig.apiKey)
        assertEquals("gpt-4o-mini", providerConfig.modelId)
    }

    @Test
    fun `repository persistence survives recreation and does not leak api key into plain settings`() = runBlocking {
        val firstRepository = createRepository()
        firstRepository.updateNonSensitiveSettings(
            NonSensitiveChatSettings(
                apiBaseUrl = "https://api.example.com/v1/",
                modelId = "gpt-4.1-mini",
            )
        )
        firstRepository.setApiKey("sk-live-9999")

        val recreatedRepository = createRepository()
        val restoredSettings = recreatedRepository.getChatSettings()
        val restoredActiveConfig = recreatedRepository.getActiveConfig()
        val dataStoreText = settingsFile("chat_settings.preferences_pb").readBytes().decodeToString()

        assertEquals("https://api.example.com/v1/", restoredSettings.apiBaseUrl)
        assertEquals("gpt-4.1-mini", restoredSettings.modelId)
        assertEquals(
            PersistedApiKeyState.Persisted(maskedValue = "••••9999"),
            restoredSettings.apiKeyState,
        )
        assertEquals("sk-live-9999", restoredActiveConfig.apiKey)
        assertFalse(dataStoreText.contains("sk-live-9999"))
    }

    @Test
    fun `invalid base urls are rejected while debug override allows localhost http`() = runBlocking {
        val repository = createRepository(insecureBaseUrlOverride = InsecureBaseUrlOverride.DISALLOWED)

        try {
            repository.updateNonSensitiveSettings(
                NonSensitiveChatSettings(
                    apiBaseUrl = "abc",
                    modelId = "gpt-4o-mini",
                )
            )
            throw AssertionError("Expected invalid base URL to be rejected.")
        } catch (_: IllegalArgumentException) {
            assertEquals("", repository.getChatSettings().apiBaseUrl)
        }

        val debugRepository = createRepository(
            rootDir = File(settingsRoot, "debug"),
            insecureBaseUrlOverride = InsecureBaseUrlOverride.DEBUG_ONLY,
        )
        debugRepository.updateNonSensitiveSettings(
            NonSensitiveChatSettings(
                apiBaseUrl = "http://localhost:8080/v1/",
                modelId = "gpt-4o-mini",
            )
        )

        assertEquals("http://localhost:8080/v1/", debugRepository.getChatSettings().apiBaseUrl)
    }

    @Test
    fun `clearing secret updates masked state and runtime config`() = runBlocking {
        val repository = createRepository()
        repository.updateNonSensitiveSettings(
            NonSensitiveChatSettings(
                apiBaseUrl = "https://api.example.com/v1/",
                modelId = "gpt-4o-mini",
            )
        )
        repository.setApiKey("sk-secret-1234")

        repository.clearApiKey()

        assertEquals(PersistedApiKeyState.Missing, repository.observePersistedApiKeyState().first())
        assertNull(repository.getApiKey())
        assertNull(repository.getActiveConfig().apiKey)
    }

    private fun createRepository(
        rootDir: File = settingsRoot,
        insecureBaseUrlOverride: InsecureBaseUrlOverride = InsecureBaseUrlOverride.DISALLOWED,
    ): RealSettingsRepository {
        rootDir.mkdirs()
        val nonSensitiveDataSource = DataStoreNonSensitiveSettingsLocalDataSource(
            dataStore = InMemoryDataStoreRegistry.getOrCreate(File(rootDir, "chat_settings.preferences_pb")),
        )
        val encryptedPreferences = InMemorySharedPreferencesStore.getOrCreate(rootDir.absolutePath)
        val secretDataSource = EncryptedSecretSettingsLocalDataSource(encryptedPreferences)

        return RealSettingsRepository(
            nonSensitiveLocalDataSource = nonSensitiveDataSource,
            secretLocalDataSource = secretDataSource,
            insecureBaseUrlOverride = insecureBaseUrlOverride,
        )
    }

    private fun settingsFile(relativePath: String): File {
        return File(settingsRoot, relativePath)
    }
}

private object InMemorySharedPreferencesStore {
    private val stores = linkedMapOf<String, InMemorySharedPreferences>()

    fun getOrCreate(key: String): InMemorySharedPreferences {
        return stores.getOrPut(key) { InMemorySharedPreferences() }
    }
}

private object InMemoryDataStoreRegistry {
    private val stores = linkedMapOf<String, DataStore<Preferences>>()

    fun getOrCreate(file: File): DataStore<Preferences> {
        file.parentFile?.mkdirs()
        return stores.getOrPut(file.absolutePath) {
            PreferenceDataStoreFactory.create(produceFile = { file })
        }
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()
    private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        return values[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (values[key] as? MutableSet<String>) ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) {
            listeners += listener
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) {
            listeners -= listener
        }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pendingValues = linkedMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = values
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pendingValues[key.orEmpty()] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) {
                values.clear()
            }

            pendingValues.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
                listeners.forEach { listener ->
                    listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, key)
                }
            }
        }
    }
}
