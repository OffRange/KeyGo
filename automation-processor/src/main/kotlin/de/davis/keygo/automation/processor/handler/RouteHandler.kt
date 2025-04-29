package de.davis.keygo.automation.processor.handler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import de.davis.keygo.automation.processor.WriterScope
import de.davis.keygo.automation.processor.ext.getAnnotation
import de.davis.keygo.automation.processor.file
import de.davis.keygo.automation.processor.model.RouteTree
import de.davis.keygo.processor.annotation.Route
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RouteHandler : Handler<KSFunctionDeclaration, Route>, KoinComponent {
    private val codeGenerator: CodeGenerator by inject()

    override fun handleSymbols(symbols: List<KSFunctionDeclaration>) {
        val grouped = symbols.groupBy { symbol ->
            val annotation = symbol.getAnnotation<Route>() ?: return@groupBy ""

            annotation.parent
        }

        val tree = grouped.flatMap { (groupName, declarations) ->
            val endpoints = declarations.map { declaration ->
                val annotation = declaration.getAnnotation<Route>()
                val name = annotation?.name?.ifBlank { null }
                    ?: declaration.simpleName.getShortName()
                RouteTree.Endpoint(name)
            }

            if (groupName.isEmpty())
                endpoints
            else
                listOf(RouteTree.Root(groupName, endpoints.toSet()))
        }

        val root = RouteTree.Root("Route", tree.toSet())

        codeGenerator.file(fileName = root.name) {
            write(root)
        }
    }
}

private fun WriterScope.write(treeNode: RouteTree) {
    when (treeNode) {
        is RouteTree.Endpoint -> {
            dataObject(
                name = treeNode.name,
                annotations = listOf("kotlinx.serialization.Serializable")
            )
        }

        is RouteTree.Root -> {
            sealedInterface(treeNode.name) {
                dataObject(
                    name = "Root",
                    annotations = listOf("kotlinx.serialization.Serializable")
                )
                treeNode.routes.forEach(::write)
            }
        }
    }

}