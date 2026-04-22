package de.davis.keygo.feature.list_screen.data.local.datasource

import androidx.datastore.core.Serializer
import de.davis.keygo.feature.list_screen.data.local.model.ProtoListSelection
import java.io.InputStream
import java.io.OutputStream

internal object ListSelectionSerializer : Serializer<ProtoListSelection> {

    override val defaultValue: ProtoListSelection = ProtoListSelection.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ProtoListSelection =
        ProtoListSelection.parseFrom(input)

    override suspend fun writeTo(t: ProtoListSelection, output: OutputStream) {
        t.writeTo(output)
    }
}
