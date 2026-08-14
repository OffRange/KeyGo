package de.davis.keygo.legacy_migration.data.local.datasource.datastore

import androidx.datastore.core.Serializer
import de.davis.keygo.legacy_migration.data.local.model.ProtoMainPassword
import java.io.InputStream
import java.io.OutputStream

internal object MainPasswordSerializer : Serializer<ProtoMainPassword> {

    override val defaultValue: ProtoMainPassword = ProtoMainPassword.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ProtoMainPassword =
        ProtoMainPassword.parseFrom(input)

    override suspend fun writeTo(t: ProtoMainPassword, output: OutputStream) {
        t.writeTo(output)
    }
}
