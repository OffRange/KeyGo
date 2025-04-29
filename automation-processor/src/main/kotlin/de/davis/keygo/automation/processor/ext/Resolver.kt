package de.davis.keygo.automation.processor.ext

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.validate

inline fun <reified I : KSNode, reified A : Annotation> Resolver.findSymbolsWith() =
    getSymbolsWithAnnotation(A::class.qualifiedName!!)
        .filterIsInstance<I>()
        .filter(KSNode::validate)
        .toList()