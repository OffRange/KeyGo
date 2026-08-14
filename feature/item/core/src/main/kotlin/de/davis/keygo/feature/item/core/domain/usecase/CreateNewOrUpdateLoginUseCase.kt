package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.core.util.fold
import de.davis.keygo.feature.item.core.domain.model.FieldUpdate
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davis.keygo.feature.item.core.domain.model.getValue
import de.davis.keygo.feature.item.core.domain.model.on
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.withoutClearingOn
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

@Single
class CreateNewOrUpdateLoginUseCase(
    cryptographicScopeProvider: CryptographicScopeProvider,
    private val loginRepository: LoginRepository,
    vaultRepository: VaultRepository,
    upsertVaultItem: UpsertVaultItemUseCase,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val totpService: TotpService,
) : CreateOrUpdateItemUseCase<UpsertLogin, Login>(
    cryptographicScopeProvider = cryptographicScopeProvider,
    vaultRepository = vaultRepository,
    upsertVaultItem = upsertVaultItem,
) {

    private fun isValid(field: FieldUpdate<String>, allowKeep: Boolean = false): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> false
            is FieldUpdate.Set<String> -> field.value.isNotBlank()
        }

    override fun validate(upsert: UpsertLogin): Set<ItemUpsertError> {
        val errors = mutableSetOf<ItemUpsertError>()
        val allowKeep = upsert.upsertType is UpsertType.Update

        if (!isValid(field = upsert.name, allowKeep = allowKeep))
            errors.add(ItemUpsertError.BlankName)

        return errors
    }

    override suspend fun fetchExisting(id: ItemId): Login? = loginRepository.getLoginById(id)

    override fun isEmpty(item: Login, upsert: UpsertLogin): Boolean = !item.hasAnyContent

    override fun relocate(item: Login, vaultId: VaultId, keyInformation: KeyInformation): Login =
        item.copy(vaultId = vaultId, keyInformation = keyInformation)

    override fun touch(item: Login, timestamp: Timestamp): Login = item.copy(timestamp = timestamp)

    override suspend fun CryptographicScope.buildCreate(
        upsert: UpsertLogin,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): Login = coroutineScope {
        val newPasswordCredential = when (val pw = upsert.password) {
            FieldUpdate.Keep,
            FieldUpdate.Clear -> null

            is FieldUpdate.Set<String> -> {
                val encrypted = async { PasswordSecret.encrypt(pw.value) }
                val strength = async { passwordStrengthEstimator(pw.value) }
                PasswordCredential(secret = encrypted.await(), score = strength.await())
            }
        }
        val totp = upsert.totpUriOrSecret.onSet { uriOrSecret ->
            async { uriOrSecret.convertTotpUriOrSecretToUri(itemId) }
        }

        Login(
            id = itemId,
            name = upsert.name.getValue()!!,
            username = upsert.username.getValue(),
            domainInfos = upsert.domains.getValue().orEmpty(),
            tags = upsert.tags.getValue().orEmpty(),
            passwordCredential = newPasswordCredential,
            totp = totp?.await(),
            note = upsert.note.getValue(),
            pinned = false,
            keyInformation = keyInformation,
            vaultId = vaultId,
            timestamp = Timestamp(),
        )
    }

    override suspend fun CryptographicScope.buildUpdate(
        upsert: UpsertLogin,
        existing: Login
    ): Login = coroutineScope {
        val newPasswordCredential = when (val pw = upsert.password) {
            is FieldUpdate.Keep -> existing.passwordCredential
            is FieldUpdate.Clear -> null
            is FieldUpdate.Set<String> -> {
                val encrypted = async { PasswordSecret.encrypt(pw.value) }
                val strength = async { passwordStrengthEstimator(pw.value) }
                PasswordCredential(secret = encrypted.await(), score = strength.await())
            }
        }
        val totp = upsert.totpUriOrSecret.onSet { uriOrSecret ->
            async { uriOrSecret.convertTotpUriOrSecretToUri(existing.id) }
        }

        existing.copy(
            name = upsert.name.withoutClearingOn(existing.name),
            username = upsert.username.on(existing.username),
            domainInfos = upsert.domains.on(existing.domainInfos).orEmpty(),
            tags = upsert.tags.on(existing.tags).orEmpty(),
            passwordCredential = newPasswordCredential,
            totp = upsert.totpUriOrSecret.on(existing.totp, totp),
            note = upsert.note.on(existing.note),
        )
    }

    context(scope: CryptographicScope)
    private suspend fun String.convertTotpUriOrSecretToUri(itemId: ItemId) = with(scope) {
        totpService.getInfoFromUriWithResult(this@convertTotpUriOrSecretToUri).fold(
            onSuccess = { secrets ->
                Totp(
                    loginId = itemId,
                    secret = Totp.Secret.encrypt(secrets.secret),
                    accountName = secrets.accountName,
                    issuer = secrets.issuer,
                    algorithm = secrets.algorithm.name.lowercase(),
                    digits = secrets.digits,
                    period = secrets.period,
                )
            },
            onFailure = {
                // not valid uri - treat it as secret
                Totp(
                    loginId = itemId,
                    secret = Totp.Secret.encrypt(this@convertTotpUriOrSecretToUri),
                )
            },
        )
    }
}
