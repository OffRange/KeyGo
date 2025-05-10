package de.davis.keygo.automation.processor.di

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import de.davis.keygo.automation.processor.model.Options
import de.davis.keygo.automation.processor.util.GetClassName
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun initiateKoin(logger: KSPLogger, codeGenerator: CodeGenerator, options: Options) {
    startKoin {
        module {
            single { logger }
            single { codeGenerator }
            single { options }

            singleOf(::GetClassName)
        }.also(::modules)
    }
}