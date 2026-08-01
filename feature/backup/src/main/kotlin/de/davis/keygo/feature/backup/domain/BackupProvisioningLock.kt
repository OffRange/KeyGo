package de.davis.keygo.feature.backup.domain

import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single

/**
 * Serializes ARK-escrow provisioning against escrow teardown.
 *
 * The escrow and the job record live in separate DataStores with no cross-store transaction, so
 * holding this one lock across each whole section is what stops a cleanup from observing a
 * half-provisioned job (escrow written, record not yet) and destroying credentials the new job needs.
 */
@Single
class BackupProvisioningLock {
    val mutex = Mutex()
}
