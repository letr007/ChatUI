package com.letr.chatui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SettingsStorageFactory {
    fun createNonSensitiveSettingsDataStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(NON_SENSITIVE_SETTINGS_FILE_NAME) },
        )
    }

    fun createEncryptedSecretPreferences(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            SECRET_SETTINGS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private const val NON_SENSITIVE_SETTINGS_FILE_NAME = "chat_settings.preferences_pb"
    private const val SECRET_SETTINGS_FILE_NAME = "chat_secret_settings"
}
