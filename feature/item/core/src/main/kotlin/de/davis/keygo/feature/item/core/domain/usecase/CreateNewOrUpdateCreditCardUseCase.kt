package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.toYearMonthOrNull
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.feature.item.core.domain.model.CreditCardUpsertError
import de.davis.keygo.feature.item.core.domain.model.FieldUpdate
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertCreditCard
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davis.keygo.feature.item.core.domain.model.getValue
import de.davis.keygo.feature.item.core.domain.model.on
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.withoutClearingOn
import de.davis.keygo.rust.card.CardFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

@Single
class CreateNewOrUpdateCreditCardUseCase(
    private val creditCardRepository: CreditCardRepository,
    private val cardFormatter: CardFormatter,
    cryptographicScopeProvider: CryptographicScopeProvider,
    vaultRepository: VaultRepository,
    upsertVaultItem: UpsertVaultItemUseCase,
) : CreateOrUpdateItemUseCase<UpsertCreditCard, CreditCard>(
    cryptographicScopeProvider = cryptographicScopeProvider,
    vaultRepository = vaultRepository,
    upsertVaultItem = upsertVaultItem,
) {

    override fun validate(upsert: UpsertCreditCard): Set<ItemUpsertError> {
        val errors = mutableSetOf<ItemUpsertError>()
        val allowKeep = upsert.upsertType is UpsertType.Update

        if (!isPresent(upsert.name, allowKeep))
            errors.add(ItemUpsertError.BlankName)

        if (!isValidExpiration(upsert.expirationDate, allowKeep))
            errors.add(CreditCardUpsertError.InvalidExpiration)

        if (!isValidCardNumber(upsert.cardNumber, allowKeep))
            errors.add(CreditCardUpsertError.InvalidCardNumber)

        if (!isValidCvv(upsert.cvv, upsert.cardNumber))
            errors.add(CreditCardUpsertError.InvalidCvv)

        return errors
    }

    private fun isPresent(field: FieldUpdate<String>, allowKeep: Boolean): Boolean = when (field) {
        is FieldUpdate.Keep -> allowKeep
        is FieldUpdate.Clear -> false
        is FieldUpdate.Set<String> -> field.value.isNotBlank()
    }

    private fun isValidExpiration(field: FieldUpdate<String>, allowKeep: Boolean): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> true
            is FieldUpdate.Set<String> -> field.value.toYearMonthOrNull() != null
        }

    private fun isValidCardNumber(field: FieldUpdate<String>, allowKeep: Boolean): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> true
            is FieldUpdate.Set<String> -> cardFormatter.isValid(field.value)
        }

    private fun isValidCvv(cvv: FieldUpdate<String>, cardNumber: FieldUpdate<String>): Boolean {
        if (cvv !is FieldUpdate.Set || cardNumber !is FieldUpdate.Set) return true
        return cvv.value.length == cardFormatter.cvvLen(cardNumber.value)
    }

    override suspend fun fetchExisting(id: ItemId): CreditCard? =
        creditCardRepository.getCreditCardById(id)

    override fun isEmpty(item: CreditCard, upsert: UpsertCreditCard): Boolean = !item.hasAnyContent

    override fun relocate(
        item: CreditCard,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): CreditCard = item.copy(vaultId = vaultId, keyInformation = keyInformation)

    override fun touch(item: CreditCard, timestamp: Timestamp): CreditCard =
        item.copy(timestamp = timestamp)

    override suspend fun CryptographicScope.buildCreate(
        upsert: UpsertCreditCard,
        itemId: ItemId,
        vaultId: VaultId,
        keyInformation: KeyInformation,
    ): CreditCard = coroutineScope {
        val number = upsert.cardNumber.getValue()
        val encryptedNumber =
            number?.let { number -> async { CreditCard.CardNumber.encrypt(number) } }
        val encryptedCvv = upsert.cvv.onSet { cvv -> async { CreditCard.CVV.encrypt(cvv) } }

        CreditCard(
            id = itemId,
            vaultId = vaultId,
            name = upsert.name.getValue()!!,
            keyInformation = keyInformation,
            tags = upsert.tags.getValue().orEmpty(),
            note = upsert.note.getValue(),
            pinned = false,
            holder = upsert.holder.getValue(),
            cardNumber = encryptedNumber?.await(),
            cvv = encryptedCvv?.await(),
            expirationDate = upsert.expirationDate.getValue()
                ?.toYearMonthOrNull(), // toYearMonthOrNull will not return null, since isValidExpiration ensures the date to be correct
            timestamp = Timestamp(),
        )
    }

    override suspend fun CryptographicScope.buildUpdate(
        upsert: UpsertCreditCard,
        existing: CreditCard,
    ): CreditCard = coroutineScope {
        val encryptedNumber = upsert.cardNumber.onSet { num ->
            async { CreditCard.CardNumber.encrypt(num) }
        }
        val encryptedCvv = upsert.cvv.onSet { cvv -> async { CreditCard.CVV.encrypt(cvv) } }

        existing.copy(
            name = upsert.name.withoutClearingOn(existing.name),
            note = upsert.note.on(existing.note),
            tags = upsert.tags.on(existing.tags).orEmpty(),
            holder = upsert.holder.on(existing.holder),
            cardNumber = upsert.cardNumber.on(existing.cardNumber, encryptedNumber),
            cvv = upsert.cvv.on(existing.cvv, encryptedCvv),
            expirationDate = when (val exp = upsert.expirationDate) {
                FieldUpdate.Keep -> existing.expirationDate
                FieldUpdate.Clear -> null
                is FieldUpdate.Set -> exp.value.toYearMonthOrNull()
            },
        )
    }

}
