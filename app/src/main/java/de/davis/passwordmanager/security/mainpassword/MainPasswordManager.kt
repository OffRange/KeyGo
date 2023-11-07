package de.davis.passwordmanager.security.mainpassword

import android.content.Context
import androidx.datastore.dataStore
import de.davis.passwordmanager.PasswordManagerApplication
import kotlinx.coroutines.flow.first

object MainPasswordManager {

    private val Context.mainPasswordDataStore by dataStore(
        "main-password.json",
        MainPasswordSerializer
    )

    suspend fun getPassword(): MainPassword {
        return PasswordManagerApplication.getAppContext().mainPasswordDataStore.data.first()
    }

    suspend fun updatePassword(mainPassword: MainPassword) {
        PasswordManagerApplication.getAppContext().mainPasswordDataStore.updateData { mainPassword }
    }
}