package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.SystemHandoff
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicInteger

@Single
internal class SystemHandoffImpl : SystemHandoff {

    private val pending = AtomicInteger()

    override val isPending: Boolean
        get() = pending.get() > 0

    override fun expectReturn() {
        pending.incrementAndGet()
    }

    override fun returned() {
        pending.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    override fun clear() = pending.set(0)
}
