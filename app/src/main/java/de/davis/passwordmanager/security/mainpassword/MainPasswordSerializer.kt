package de.davis.passwordmanager.security.mainpassword

import androidx.datastore.core.Serializer
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

object MainPasswordSerializer : Serializer<MainPassword> {

    override val defaultValue: MainPassword
        get() = MainPassword.EMPTY

    override suspend fun readFrom(input: InputStream): MainPassword {
        return Gson().fromJson(InputStreamReader(input), MainPassword::class.java)
    }

    override suspend fun writeTo(t: MainPassword, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(Gson().toJson(t).toByteArray())
        }
    }
}