package de.davis.passwordmanager.backup.keygo

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import de.davis.passwordmanager.R
import de.davis.passwordmanager.backup.Result
import de.davis.passwordmanager.backup.SecureDataBackup
import de.davis.passwordmanager.backup.TYPE_EXPORT
import de.davis.passwordmanager.backup.TYPE_IMPORT
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.ElementDetail
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.gson.strategies.ExcludeAnnotationStrategy
import de.davis.passwordmanager.security.Cryptography
import org.apache.commons.io.IOUtils
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Type

class KeyGoBackup(context: Context) : SecureDataBackup(context) {

    class ElementDetailTypeAdapter : JsonSerializer<ElementDetail>,
        JsonDeserializer<ElementDetail> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): ElementDetail {
            json.asJsonObject.run {
                val type = this["type"].asInt
                if (type == ElementType.PASSWORD.typeId) {
                    val passwordArray = JsonArray().apply {
                        for (b in Cryptography.encryptAES(this@run["password"].asString.toByteArray())) {
                            add(b)
                        }
                    }

                    add("password", passwordArray)
                }

                return context.deserialize(
                    json,
                    ElementType.getTypeByTypeId(type).elementDetailClass
                )
            }
        }

        override fun serialize(
            src: ElementDetail,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val jsonObject = context.serialize(src)
            jsonObject.asJsonObject.run {
                if (src is PasswordDetails) addProperty(
                    "password",
                    src.password
                )
                addProperty("type", src.elementType.typeId)
            }

            return jsonObject
        }
    }

    private val gson = GsonBuilder().apply {
        registerTypeAdapter(ElementDetail::class.java, ElementDetailTypeAdapter())
        setExclusionStrategies(ExcludeAnnotationStrategy())
    }.create()

    @Throws(Exception::class)
    override suspend fun runImport(inputStream: InputStream): Result {
        var file = IOUtils.toByteArray(inputStream)
        if (file.isEmpty()) return Result.Error(context.getString(R.string.invalid_file_length))

        file = Cryptography.decryptWithPwd(file, password)

        val list: List<SecureElement> = try {
            gson.fromJson(
                String(file),
                object : TypeToken<ArrayList<SecureElement>>() {}.type
            )
        } catch (e: Exception) {
            return Result.Error(context.getString(R.string.invalid_file))
        }

        val elements: List<SecureElement> = SecureElementManager.getSecureElements()

        var existed = 0
        val length = list.size

        for (i in 0 until length) {
            val element = list[i]
            if (elements.any { it.title == element.title && it.detail == element.detail }) {
                existed++
                notifyUpdate(i + 1, length)
                continue
            }
            SecureElementManager.insertElement(element)
            notifyUpdate(i + 1, length)
        }
        return if (existed != 0) Result.Duplicate(existed) else Result.Success(TYPE_IMPORT)
    }

    @Throws(Exception::class)
    override suspend fun runExport(outputStream: OutputStream): Result {
        val elements: List<SecureElement> = SecureElementManager.getSecureElements()

        val json = gson.toJson(elements)

        outputStream.use {
            it.write(Cryptography.encryptWithPwd(json.toByteArray(), password))
        }

        return Result.Success(TYPE_EXPORT)
    }
}