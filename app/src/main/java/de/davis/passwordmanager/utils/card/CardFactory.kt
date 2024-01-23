package de.davis.passwordmanager.utils.card

object CardFactory {
    fun createFromCardNumber(cardNumber: String): Card {
        return Card(cardNumber.replace(" ", ""), CardType.getTypeByNumber(cardNumber))
    }
}