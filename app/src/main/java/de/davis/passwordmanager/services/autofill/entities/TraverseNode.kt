package de.davis.passwordmanager.services.autofill.entities

import android.app.assist.AssistStructure.ViewNode

class TraverseNode(val node: ViewNode, val parent: TraverseNode? = null)
