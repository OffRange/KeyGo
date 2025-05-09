package de.davis.keygo.automation.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import java.io.OutputStream

@DslMarker
annotation class WriterDsl

@Deprecated("Migrate to kotlinpoet")
@WriterDsl
class WriterScope(
    private val outputStream: OutputStream
) {

    private var parentType: String? = null
    private var indentLevel = 0

    private val indent: String
        get() = "\t".repeat(indentLevel)

    private var needsIndent: Boolean = true

    fun write(s: String, newLine: Boolean = false) {
        if (needsIndent)
            outputStream.write(indent.toByteArray())

        outputStream.write(s.toByteArray())
        if (newLine)
            outputStream.write("\n".toByteArray())

        needsIndent = newLine
    }

    fun writeNewLine() = write("", newLine = true)

    fun withIndent(
        parent: String,
        block: WriterScope.() -> Unit
    ) {
        val prevParent = parentType
        parentType = parent

        indentLevel++
        block()
        indentLevel--

        parentType = prevParent
    }

    fun sealedInterface(
        name: String,
        block: WriterScope.() -> Unit
    ) {
        write("sealed interface $name")

        writeInheritance()

        write(" {", newLine = true)
        withIndent(name, block)
        write("}", newLine = true)
    }

    fun dataObject(name: String, annotations: List<String> = emptyList()) {
        annotations.forEach {
            write("@$it", newLine = true)
        }
        write("data object $name")
        writeInheritance()
        writeNewLine()
    }

    private fun writeInheritance(prefix: String = " ", suffix: String = "") {
        if (parentType != null)
            write("$prefix: $parentType$suffix")
    }
}

@WriterDsl
fun CodeGenerator.file(
    packageName: String = "de.davis.keygo.generated",
    fileName: String,
    block: WriterScope.() -> Unit
) {
    val outputStream = try {
        createNewFile(Dependencies.ALL_FILES, packageName, fileName)
    } catch (ex: FileAlreadyExistsException) {
        ex.file.outputStream()
    }

    outputStream.use {
        WriterScope(it).apply {
            write("package $packageName", newLine = true)
            writeNewLine()
            block()
        }
    }
}