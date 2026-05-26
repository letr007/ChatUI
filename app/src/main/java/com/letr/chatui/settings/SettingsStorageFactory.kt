package com.letr.chatui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException

object SettingsStorageFactory {
    @Volatile
    private var nonSensitiveSettingsDataStore: DataStore<Preferences>? = null

    fun createNonSensitiveSettingsDataStore(context: Context): DataStore<Preferences> {
        nonSensitiveSettingsDataStore?.let { return it }

        return synchronized(this) {
            nonSensitiveSettingsDataStore ?: PreferenceDataStoreFactory.create(
                produceFile = { context.applicationContext.preferencesDataStoreFile(NON_SENSITIVE_SETTINGS_FILE_NAME) },
            ).also { created ->
                nonSensitiveSettingsDataStore = created
            }
        }
    }

    fun createEncryptedSecretPreferences(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        return try {
            createEncryptedSecretPreferencesInternal(appContext)
        } catch (throwable: Throwable) {
            if (!isEncryptedSecretPreferencesRecoveryCandidate(throwable)) {
                throw throwable
            }

            deleteEncryptedSecretPreferencesFiles(appContext)
            createEncryptedSecretPreferencesInternal(appContext)
        }
    }

    private fun createEncryptedSecretPreferencesInternal(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SECRET_SETTINGS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun isEncryptedSecretPreferencesRecoveryCandidate(throwable: Throwable): Boolean {
        val visited = mutableSetOf<Throwable>()
        var current: Throwable? = throwable

        while (current != null && visited.add(current)) {
            if (current is GeneralSecurityException) {
                return true
            }

            val className = current.javaClass.name
            if (className in CRYPTO_EXCEPTION_CLASS_NAMES) {
                return true
            }

            val message = current.message?.lowercase().orEmpty()
            if (CRYPTO_ERROR_MESSAGE_HINTS.any(message::contains)) {
                return true
            }

            current = current.cause
        }

        return false
    }

    private fun deleteEncryptedSecretPreferencesFiles(context: Context) {
        val dataDir = context.filesDir.parentFile ?: return
        val sharedPrefsDir = java.io.File(dataDir, SHARED_PREFS_DIRECTORY_NAME)
        if (!sharedPrefsDir.exists()) {
            return
        }

        val filesToDelete: List<java.io.File> = listOf(
            java.io.File(sharedPrefsDir, SECRET_SETTINGS_XML_FILE_NAME),
            java.io.File(sharedPrefsDir, SECRET_SETTINGS_XML_BACKUP_FILE_NAME),
        )

        filesToDelete.forEach { file: java.io.File ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private const val NON_SENSITIVE_SETTINGS_FILE_NAME = "chat_settings.preferences_pb"
    private const val SECRET_SETTINGS_FILE_NAME = "chat_secret_settings"
    private const val SHARED_PREFS_DIRECTORY_NAME = "shared_prefs"
    private const val SECRET_SETTINGS_XML_FILE_NAME = "$SECRET_SETTINGS_FILE_NAME.xml"
    private const val SECRET_SETTINGS_XML_BACKUP_FILE_NAME = "$SECRET_SETTINGS_XML_FILE_NAME.bak"

    private val CRYPTO_EXCEPTION_CLASS_NAMES = setOf(
        "android.security.KeyStoreException",
        "android.security.keystore.KeyPermanentlyInvalidatedException",
        "java.security.InvalidKeyException",
        "java.security.UnrecoverableKeyException",
        "javax.crypto.AEADBadTagException",
        "javax.crypto.BadPaddingException",
        "javax.crypto.IllegalBlockSizeException",
    )

    private val CRYPTO_ERROR_MESSAGE_HINTS = listOf(
        "aead",
        "bad padding",
        "decrypt",
        "decryption",
        "failed to decrypt",
        "keystore",
        "mac verification failed",
        "signature verification failed",
    )
}
