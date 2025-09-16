package de.davis.keygo.automation.processor.model

import de.davis.keygo.automation.processor.util.Constants
import de.davis.keygo.automation.processor.util.GetClassName
import de.davis.keygo.processor.annotation.VaultEntity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

sealed class Entry : KoinComponent {

    protected val getClassName by inject<GetClassName>()

    abstract val simpleName: String
    abstract val packageName: String

    data class RootEntry(
        override val simpleName: String,
        override val packageName: String,
        val children: List<ChildEntry>
    ) : Entry() {

        fun enumTypeClassName() = getClassName(
            packageName = packageName,
            simpleName = "$simpleName${Constants.Suffixes.TYPE_SUFFIX}"
        )
    }

    data class ChildEntry(
        override val simpleName: String,
        override val packageName: String,
        val vaultEntity: VaultEntity,
    ) : Entry()
}
