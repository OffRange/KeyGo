package de.davis.passwordmanager.security.mainpassword

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object MainPasswordSerializer : Serializer<UserMainPassword> {

    override val defaultValue: UserMainPassword = UserMainPassword.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): UserMainPassword {
        try {
            return UserMainPassword.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: UserMainPassword, output: OutputStream) = t.writeTo(output)
}