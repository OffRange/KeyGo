package de.davis.keygo.core.domain.model

import java.time.Instant
    
data class MainPassword(val hash: String, val createdAt: Instant)
