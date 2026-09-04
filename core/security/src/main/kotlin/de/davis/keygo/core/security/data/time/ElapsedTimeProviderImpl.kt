package de.davis.keygo.core.security.data.time

import android.os.SystemClock
import de.davis.keygo.core.security.domain.time.ElapsedTimeProvider
import org.koin.core.annotation.Single

@Single
internal class ElapsedTimeProviderImpl : ElapsedTimeProvider {

    override fun elapsedTime(): Long = SystemClock.elapsedRealtime()
}