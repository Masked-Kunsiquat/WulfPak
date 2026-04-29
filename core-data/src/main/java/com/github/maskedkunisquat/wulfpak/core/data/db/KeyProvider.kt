package com.github.maskedkunisquat.wulfpak.core.data.db

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object KeyProvider {

    private const val PREF_FILE = "wulfpak_db_prefs"
    private const val KEY_ALIAS = "wulfpak_db_key"

    fun getOrCreateKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return prefs.getString(KEY_ALIAS, null)
            ?.let { Base64.decode(it, Base64.DEFAULT) }
            ?: ByteArray(32).also { key ->
                SecureRandom().nextBytes(key)
                val committed = prefs.edit()
                    .putString(KEY_ALIAS, Base64.encodeToString(key, Base64.DEFAULT))
                    .commit()
                if (!committed) {
                    throw IllegalStateException("KeyProvider: failed to persist DB key")
                }
            }
    }
}
