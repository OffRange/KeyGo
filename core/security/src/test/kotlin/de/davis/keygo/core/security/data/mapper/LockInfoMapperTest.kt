package de.davis.keygo.core.security.data.mapper

import de.davis.keygo.core.security.data.local.model.ProtoLockInfo
import de.davis.keygo.core.security.data.local.model.copy
import de.davis.keygo.core.security.domain.model.LockInfo
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LockInfoMapperTest {

    @Test
    fun `every domain timeout round trips through proto and back`() {
        LockInfo.Timeout.entries.forEach { timeout ->
            val proto =
                ProtoLockInfo.getDefaultInstance().copy { autoLockTimeout = timeout.toProto() }

            assertEquals(timeout, proto.toDomain().autoLockTimeout)
        }
    }

    @Test
    fun `an unrecognized proto value falls back to IMMEDIATELY instead of crashing`() {
        // A raw ordinal no build-time LockTimeout entry claims - stands in for a value a newer
        // app version wrote, read back after a downgrade. setAutoLockTimeout(UNRECOGNIZED) itself
        // throws, so the raw *Value setter is the only way to construct this on purpose.
        val proto = ProtoLockInfo.getDefaultInstance().copy { autoLockTimeoutValue = 99 }

        assertEquals(LockInfo.Timeout.IMMEDIATELY, proto.toDomain().autoLockTimeout)
    }

    @Test
    fun `an unset proto field - the proto3 wire default - maps to IMMEDIATELY`() {
        val proto = ProtoLockInfo.getDefaultInstance()

        assertEquals(LockInfo.Timeout.IMMEDIATELY, proto.toDomain().autoLockTimeout)
    }
}
