package de.davis.keygo.core.util.data.serializer

import androidx.datastore.core.Serializer
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import java.io.InputStream
import java.io.OutputStream

class DefaultProtoSerializer<T : MessageLite>(
    private val defaultInstance: T,
    private val parser: Parser<T>
) : Serializer<T> {

    override val defaultValue: T
        get() = defaultInstance

    override suspend fun readFrom(input: InputStream): T =
        parser.parseFrom(input)

    override suspend fun writeTo(t: T, output: OutputStream) {
        t.writeTo(output)
    }
}
