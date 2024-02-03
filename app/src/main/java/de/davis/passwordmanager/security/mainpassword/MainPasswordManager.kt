package de.davis.passwordmanager.security.mainpassword

import android.content.Context
import androidx.datastore.dataStore
import de.davis.passwordmanager.PasswordManagerApplication
import kotlinx.coroutines.flow.first

object MainPasswordManager {

    private val Context.mainPasswordDataStore by dataStore(
        "main-password.db",
        MainPasswordSerializer
    )

    suspend fun getPassword(): MainPassword =
        PasswordManagerApplication.getAppContext().mainPasswordDataStore.data.first().toNormal()

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun updatePassword(hashedPassword: ByteArray) {
        PasswordManagerApplication.getAppContext().mainPasswordDataStore.updateData {
            it.copy {
                hexHash = hashedPassword.toHexString()
            }
        }
    }
}