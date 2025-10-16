package de.davis.keygo.auth.domain.repository

import de.davis.keygo.auth.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.identity.common.domain.repository.WrappedKeyRepository

typealias PasswordWrappedKeyRepository = WrappedKeyRepository<PasswordWrappedKeyData>