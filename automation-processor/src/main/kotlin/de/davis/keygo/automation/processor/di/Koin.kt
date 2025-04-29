package de.davis.keygo.automation.processor.di

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun initiateKoin(logger: KSPLogger, codeGenerator: CodeGenerator) {
    startKoin {
        module {
            single { logger }
            single { codeGenerator }
        }.also(::modules)
    }
}