package de.davis.keygo.core.util.di

import de.davis.keygo.core.util.di.annotation.AppScopeQualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
@ComponentScan("de.davis.keygo.core.util")
object CoreUtilModule {

    /**
     * Lives as long as the process, for work that has to outlive whatever started it. A
     * [SupervisorJob] so one failed child cannot take the rest down with it.
     *
     * Callers that need a different dispatcher pass one to their own launch. This is not a home for
     * work scoped to a screen or a framework callback: those belong to a scope that dies with them.
     */
    @Single
    @AppScopeQualifier
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
