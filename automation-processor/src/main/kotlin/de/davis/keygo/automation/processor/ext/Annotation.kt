package de.davis.keygo.automation.processor.ext

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSAnnotated

@OptIn(KspExperimental::class)
inline fun <reified A : Annotation> KSAnnotated.getAnnotation() =
    getAnnotationsByType(A::class).firstOrNull()