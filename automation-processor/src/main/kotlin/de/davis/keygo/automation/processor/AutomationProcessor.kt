package de.davis.keygo.automation.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import de.davis.keygo.automation.processor.di.initiateKoin
import de.davis.keygo.automation.processor.handler.ItemHandler
import de.davis.keygo.automation.processor.handler.RouteHandler
import de.davis.keygo.automation.processor.handler.handle
import de.davis.keygo.automation.processor.model.Options
import org.koin.core.component.KoinComponent
import org.koin.core.context.stopKoin

class AutomationProcessor : SymbolProcessor, KoinComponent {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val handler = RouteHandler()
        resolver.handle(handler)

        val itemHandler = ItemHandler()
        resolver.handle(itemHandler)

        return emptyList()
    }

    override fun finish() {
        stopKoin()
    }
}

class AutomationProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        initiateKoin(environment.logger, environment.codeGenerator, Options(environment.options))
        return AutomationProcessor()
    }
}