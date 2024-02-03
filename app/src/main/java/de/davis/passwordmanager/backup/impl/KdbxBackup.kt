package de.davis.passwordmanager.backup.impl

import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.database.traverse
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import de.davis.passwordmanager.backup.BackupResult
import de.davis.passwordmanager.backup.PasswordProvider
import de.davis.passwordmanager.backup.ProgressContext
import de.davis.passwordmanager.backup.SecureDataBackup
import de.davis.passwordmanager.backup.listener.BackupListener
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.database.entities.onlyCustoms
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class KdbxBackup(
    passwordProvider: PasswordProvider,
    backupListener: BackupListener = BackupListener.Empty,
) : SecureDataBackup(passwordProvider, backupListener) {
    private val credentialFactory: (String) -> Credentials = {
        Credentials.from(EncryptedValue.fromString(it))
    }

    override suspend fun ProgressContext.runImport(
        inputStream: InputStream,
        password: String
    ): BackupResult {
        val database =
            inputStream.use { KeePassDatabase.decode(it, credentialFactory(password)) }

        val existingElements = SecureElementManager.getSecureElements(ElementType.PASSWORD.typeId)
        val elementsToAdd = mutableListOf<SecureElement>()

        var count = 0
        database.traverse {
            when (it) {
                is Entry -> {
                    it.fields.run {
                        if (title?.content == null || this.password?.content == null)
                            return@run

                        val element = SecureElement(
                            title?.content!!,
                            PasswordDetails(
                                this.password?.content!!,
                                url?.content.orEmpty(),
                                userName?.content.orEmpty()
                            ),
                            it.tags.map { tagName -> Tag(tagName) },
                        )

                        if (existingElements.any { e -> e.title == element.title && e.detail == element.detail }) {
                            count++
                        } else {
                            elementsToAdd += element
                        }
                    }
                }

                else -> {}
            }
        }

        initiateProgress(elementsToAdd.size)
        elementsToAdd.forEach {
            SecureElementManager.insertElement(it)
            madeProgress(elementsToAdd.indexOf(it) + 1)
        }

        return if (count == 0) BackupResult.Success() else BackupResult.SuccessWithDuplicates(count)
    }

    override suspend fun ProgressContext.runExport(
        outputStream: OutputStream,
        password: String
    ): BackupResult {
        val elements = SecureElementManager.getSecureElements(ElementType.PASSWORD.typeId)
        val database = KeePassDatabase.Ver4x.create("", META, credentialFactory(password)).run {
            modifyParentGroup {
                copy(entries = elements.map {
                    val details = it.detail as PasswordDetails
                    Entry(
                        UUID.randomUUID(),
                        fields = EntryFields.of(
                            BasicField.Title() to EntryValue.Plain(it.title),
                            BasicField.Url() to EntryValue.Plain(details.origin),
                            BasicField.UserName() to EntryValue.Plain(details.username),
                            BasicField.Password() to EntryValue.Encrypted(
                                EncryptedValue.fromString(details.password)
                            ),
                        ),
                        tags = it.tags.onlyCustoms().map { tag -> tag.name }
                    )
                })
            }
        }

        outputStream.use {
            database.encode(it)
        }

        return BackupResult.Success()
    }

    companion object {
        private val META = Meta(
            generator = "KeyGo - Digital Vault",
            name = "Elements",
            description = "Securely stored elements in KeyGo Digital Vault. This database " +
                    "contains sensitive and encrypted data items such as passwords, personal " +
                    "information, and secure notes, meticulously organized for optimal security " +
                    "and accessibility.",
        )
    }
}