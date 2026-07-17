package de.davis.keygo.feature.backup.domain

import kotlinx.coroutines.sync.Mutex
import org.koin.core.annotation.Single

/**
 * Serializes ARK-escrow provisioning against escrow teardown.
 *
 * Provisioning (FinishExportWizardUseCase) writes the escrow + auth-less key aliases to one
 * DataStore and the job record that marks a job "live" to another, with no cross-store transaction.
 * CleanupBackupResourcesUseCase decides what to tear down solely from the job records. Holding this
 * single lock across the whole of each section means a cleanup can never observe a half-provisioned
 * job (escrow written, record not yet) and destroy credentials the new job needs.
 */
@Single
class BackupProvisioningLock {
    val mutex = Mutex()
}
