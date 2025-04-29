package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSNode
import de.davis.keygo.automation.processor.ext.findSymbolsWith

interface Handler<Node : KSNode, A : Annotation> {

    fun handleSymbols(symbols: List<Node>)
}

inline fun <reified Node : KSNode, reified A : Annotation> Resolver.handle(handler: Handler<Node, A>) {
    findSymbolsWith<Node, A>()
        .takeIf { it.isNotEmpty() }
        ?.let {
            handler.handleSymbols(it)
        }
}