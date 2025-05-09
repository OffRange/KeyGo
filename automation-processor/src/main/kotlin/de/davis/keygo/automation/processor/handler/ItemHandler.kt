package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import de.davis.keygo.automation.processor.writer.EntityWriter
import de.davis.keygo.automation.processor.writer.EnumWriter
import de.davis.keygo.processor.annotation.Ignore
import de.davis.keygo.processor.annotation.RootVaultEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemHandler : Handler<KSClassDeclaration, RootVaultEntity>, KoinComponent {

    private val entityWriter by inject<EntityWriter>()
    private val enumWriter by inject<EnumWriter>()

    @OptIn(KspExperimental::class)
    override fun handleSymbols(symbols: List<KSClassDeclaration>): List<KSAnnotated> {
        symbols.forEach { symbol ->
            enumWriter.write(symbol)
            entityWriter.write(symbol)

            symbol.getSealedSubclasses()
                .filterNot { it.isAnnotationPresent(Ignore::class) }
                .forEach { subclass ->
                    entityWriter.write(subclass, root = symbol)
                }
        }

        return emptyList()
    }
}
