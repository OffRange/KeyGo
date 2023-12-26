package de.davis.passwordmanager.database.entities.details.password

import com.google.gson.annotations.SerializedName
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.entities.details.ElementDetail
import de.davis.passwordmanager.security.Cryptography
import java.io.Serial

class PasswordDetails(
    password: String,
    var origin: String,
    var username: String,
) : ElementDetail {

    var strength: Strength = EstimationHandler.estimate(password)
        private set

    @SerializedName("password")
    private var passwordEncrypted: ByteArray = Cryptography.encryptAES(password.toByteArray())

    fun setPassword(password: String) {
        strength = EstimationHandler.estimate(password)
        passwordEncrypted = Cryptography.encryptAES(password.toByteArray())
    }

    val password get() = String(Cryptography.decryptAES(passwordEncrypted))


    override fun getElementType(): ElementType {
        return ElementType.PASSWORD
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasswordDetails

        if (origin != other.origin) return false
        if (username != other.username) return false
        if (!passwordEncrypted.contentEquals(other.passwordEncrypted)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = origin.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordEncrypted.contentHashCode()
        return result
    }


    companion object {
        @Serial
        private val serialVersionUID = 4938873580704485021L
    }
}