package de.davis.keygo.feature.autofill.presentation

import android.app.assist.AssistStructure

internal val AssistStructure.WindowNode.packageName get() = title.split("/").first()