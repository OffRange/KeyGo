package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.security.data.crypto.AccountRecordSealerImpl
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.AccountRecordSealer

/**
 * Exposes the internal production [AccountRecordSealer] over [session] for tests.
 */
@Suppress("TestFunctionName")
fun BindingAccountRecordSealer(session: Session): AccountRecordSealer =
    AccountRecordSealerImpl(session)
