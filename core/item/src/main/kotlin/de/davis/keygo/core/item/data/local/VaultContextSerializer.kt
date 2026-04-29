package de.davis.keygo.core.item.data.local

import androidx.datastore.core.Serializer
import de.davis.keygo.core.item.data.local.model.ProtoVaultContextRecord
import java.io.InputStream
import java.io.OutputStream

internal object VaultContextSerializer : Serializer<ProtoVaultContextRecord> {

    override val defaultValue: ProtoVaultContextRecord =
        ProtoVaultContextRecord.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ProtoVaultContextRecord =
        ProtoVaultContextRecord.parseFrom(input)

    override suspend fun writeTo(t: ProtoVaultContextRecord, output: OutputStream) {
        t.writeTo(output)
    }
}