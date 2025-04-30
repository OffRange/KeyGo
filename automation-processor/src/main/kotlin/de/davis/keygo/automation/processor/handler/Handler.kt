package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSNode
import de.davis.keygo.automation.processor.ext.findSymbolsWith

interface Handler<Node : KSNode, A : Annotation> {

    fun handleSymbols(symbols: List<Node>): List<KSAnnotated>
}

/**
 * Handles the symbols with the given handler. The handler is responsible for processing the
 * symbols.
 *
 * @return A list of deferred symbols that the handler can't process. Only symbols that can't
 * be processed at this round should be returned. Symbols in compiled code (libraries) are always
 * valid and are ignored if returned in the deferral list.
 */
inline fun <reified Node : KSNode, reified A : Annotation> Resolver.handle(handler: Handler<Node, A>): List<KSAnnotated> =
    findSymbolsWith<Node, A>()
        .takeIf { it.isNotEmpty() }
        ?.let {
            handler.handleSymbols(it)
        } ?: emptyList()
