package de.davis.passwordmanager.database

import de.davis.passwordmanager.database.daos.SecureElementWithTagDao
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.database.dtos.toDto
import de.davis.passwordmanager.database.entities.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object SecureElementManager {

    private val dao: SecureElementWithTagDao = KeyGoDatabase.instance.combinedDao()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    @JvmStatic
    suspend fun getSecureElements(typeId: Int? = null): List<SecureElement> {
        return dao.getCombinedElement(typeId).map { SecureElement.fromEntity(it) }
    }

    @JvmStatic
    @Deprecated("Calling this blocks the Main Thread", ReplaceWith("Kotlin Coroutine"))
    fun getSecureElementsSync(typeId: Int? = null): List<SecureElement> {
        return runBlocking { getSecureElements(typeId) }
    }

    @JvmStatic
    fun getSecureElementsFlow(typeId: Int? = null): Flow<List<SecureElement>> {
        return dao.getCombinedElementFlow(typeId)
            .map { it.map { e -> SecureElement.fromEntity(e) } }
    }

    @JvmStatic
    suspend fun getByTitle(query: String): List<SecureElement> {
        return dao.getByTitle("%${query}%").map { SecureElement.fromEntity(it) }
    }

    suspend fun getTags(): List<Tag> = dao.getTags()

    fun getTagsWithCount(): Flow<List<TagWithCount>> =
        dao.getTagsWithCount().map { it.map { list -> list.toDto() } }

    @JvmStatic
    @Deprecated("Calling this blocks the Main Thread", ReplaceWith("Kotlin Coroutine"))
    fun getByTitleSync(query: String): List<SecureElement> {
        return runBlocking { getByTitle(query) }
    }

    @JvmStatic
    suspend fun switchFavState(secureElement: SecureElement) {
        secureElement.favorite = !secureElement.favorite
        updateElement(secureElement)
    }

    @JvmStatic
    @JvmName("switchFavState")
    fun switchFavStateCoroutine(secureElement: SecureElement) {
        secureElement.favorite = !secureElement.favorite
        scope.launch {
            updateElement(secureElement)
        }
    }

    @JvmStatic
    suspend fun updateElement(secureElement: SecureElement) {
        dao.update(secureElement.toEntity())
    }

    @JvmStatic
    @JvmName("updateElement")
    fun updateElementCoroutine(secureElement: SecureElement) {
        scope.launch {
            updateElement(secureElement)
        }
    }

    suspend fun updateTag(tag: Tag): Int {
        return dao.updateTag(tag)
    }

    @JvmStatic
    suspend fun updateModifiedAt(secureElement: SecureElement) {
        dao.updateModifiedAt(secureElement.toEntity())
    }

    @JvmStatic
    @JvmName("updateModifiedAt")
    fun updateModifiedAtCoroutine(secureElement: SecureElement) {
        scope.launch {
            updateModifiedAt(secureElement)
        }
    }

    @JvmStatic
    suspend fun insertElement(secureElement: SecureElement) {
        dao.insert(secureElement.toEntity())
    }

    @JvmStatic
    @JvmName("insertElement")
    fun insertElementCoroutine(secureElement: SecureElement) {
        scope.launch {
            insertElement(secureElement)
        }
    }

    @JvmStatic
    suspend fun deleteElements(secureElements: List<SecureElement>) {
        dao.deleteElements(secureElements.map { it.toEntity().secureElementEntity })
    }

    @JvmStatic
    suspend fun deleteTags(tags: List<Tag>) {
        dao.deleteTags(tags)
    }

    /**
     * Deletes a list of items from the database.
     *
     * This function can handle different types of items that extend from the base 'Item' class.
     * It filters and processes each item based on its actual type and performs the appropriate
     * deletion operation.
     *
     * @param items A list of items to be deleted. These can be of any type that extends from 'Item'.
     * @param <I> The type parameter indicating the subtype of 'Item' being deleted.
     */
    @JvmStatic
    suspend fun <I : Item> delete(items: List<I>) {
        deleteElements(items.filterIsInstance<SecureElement>())
        deleteTags(items.filterIsInstance<TagWithCount>().map { it.tag })
    }

    @JvmStatic
    @JvmName("delete")
    fun <I : Item> deleteCoroutine(items: List<I>) {
        scope.launch {
            delete(items)
        }
    }

    suspend fun mergeTags(tags: List<Tag>, resultingTagName: String) {
        dao.mergeTags(tags, resultingTagName)
    }

    @JvmStatic
    @Deprecated("Calling this blocks the Main Thread", ReplaceWith("Kotlin Coroutine"))
    fun getLastCreatedSync(limit: Int = 5): List<SecureElement> {
        return runBlocking {
            dao.getLastCreated(limit).map { SecureElement.fromEntity(it) }
        }
    }

    @JvmStatic
    @Deprecated("Calling this blocks the Main Thread", ReplaceWith("Kotlin Coroutine"))
    fun getLastModifiedSync(limit: Int = 5): List<SecureElement> {
        return runBlocking {
            dao.getLastModified(limit).map { SecureElement.fromEntity(it) }
        }
    }

    @JvmStatic
    @Deprecated("Calling this blocks the Main Thread", ReplaceWith("Kotlin Coroutine"))
    fun getFavoritesSync(limit: Int = 5): List<SecureElement> {
        return runBlocking {
            dao.getFavorites(limit).map { SecureElement.fromEntity(it) }
        }
    }
}