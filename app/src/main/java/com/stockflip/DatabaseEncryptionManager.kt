package com.stockflip

import android.content.Context
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object DatabaseEncryptionManager {
    @Volatile
    private var sqlCipherLoaded = false

    fun createFactory(context: Context): SupportSQLiteOpenHelper.Factory {
        AppSecurityManager.init(context)
        ensureSqlCipherLoaded()
        val secret = AppSecurityManager.getOrCreateDatabasePassphrase()
        val passphrase = Base64.encodeToString(secret, Base64.NO_WRAP)
        return SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))
    }

    private fun ensureSqlCipherLoaded() {
        if (!sqlCipherLoaded) {
            synchronized(this) {
                if (!sqlCipherLoaded) {
                    System.loadLibrary("sqlcipher")
                    sqlCipherLoaded = true
                }
            }
        }
    }
}
