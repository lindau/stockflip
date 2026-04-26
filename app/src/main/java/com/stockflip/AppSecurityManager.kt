package com.stockflip

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AppSecurityManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "stockflip_master_key"
    private const val PREFS_NAME = "stockflip_secure_store"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val KEY_BACKUP_HMAC_SECRET = "backup_hmac_secret"
    private const val GCM_IV_SIZE_BYTES = 12
    private const val GCM_TAG_SIZE_BITS = 128

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun getOrCreateDatabasePassphrase(): ByteArray {
        return getOrCreateRandomSecret(KEY_DB_PASSPHRASE, 32)
    }

    fun signBackupPayload(payload: String): String {
        val secret = getOrCreateRandomSecret(KEY_BACKUP_HMAC_SECRET, 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val signature = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun verifyBackupPayload(payload: String, signature: String): Boolean {
        return try {
            val expected = Base64.decode(signature, Base64.NO_WRAP)
            val actual = Base64.decode(signBackupPayload(payload), Base64.NO_WRAP)
            java.security.MessageDigest.isEqual(expected, actual)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun putString(key: String, value: String?) {
        val prefs = prefs()
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        prefs.edit().putString(key, encrypt(value.toByteArray(StandardCharsets.UTF_8))).apply()
    }

    fun getString(key: String): String? {
        val encrypted = prefs().getString(key, null) ?: return null
        val decrypted = decrypt(encrypted) ?: return null
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun putBoolean(key: String, value: Boolean) {
        putString(key, value.toString())
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return getString(key)?.toBooleanStrictOrNull() ?: defaultValue
    }

    fun putStringSet(key: String, values: Set<String>) {
        val jsonArray = JSONArray()
        values.sorted().forEach(jsonArray::put)
        putString(key, jsonArray.toString())
    }

    fun getStringSet(key: String): Set<String> {
        val raw = getString(key) ?: return emptySet()
        val array = JSONArray(raw)
        return buildSet {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }

    private fun getOrCreateRandomSecret(key: String, sizeBytes: Int): ByteArray {
        getString(key)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }

        val bytes = ByteArray(sizeBytes)
        SecureRandom().nextBytes(bytes)
        putString(key, Base64.encodeToString(bytes, Base64.NO_WRAP))
        return bytes
    }

    private fun encrypt(plainBytes: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        require(iv.size == GCM_IV_SIZE_BYTES) { "Unexpected IV size: ${iv.size}" }
        val encryptedBytes = cipher.doFinal(plainBytes)
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): ByteArray? {
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return null

        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
            )
            cipher.doFinal(cipherBytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun prefs() = context().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun context(): Context {
        return applicationContext
            ?: throw IllegalStateException("AppSecurityManager.init() must be called first")
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        val builder = KeyGenParameterSpec.Builder(MASTER_KEY_ALIAS, purposes)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
}
