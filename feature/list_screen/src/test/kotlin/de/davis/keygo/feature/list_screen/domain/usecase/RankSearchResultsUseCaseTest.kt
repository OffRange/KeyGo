package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RankSearchResultsUseCaseTest {

    private val useCase = RankSearchResultsUseCase(SortUseCase())

    private fun result(
        name: String,
        matchedName: Boolean = false,
        matchedUsername: Boolean = false,
        matchedNote: Boolean = false,
        matchedTag: Boolean = false,
    ) = LiteItemSearchResult(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        name = name,
        itemType = VaultItemType.Login,
        pinned = false,
        matchedName = matchedName,
        matchedUsername = matchedUsername,
        matchedNote = matchedNote,
        matchedTag = matchedTag,
    )

    private fun rank(query: String, vararg results: LiteItemSearchResult) =
        useCase(query, results.toList()).map { it.name }

    @Test
    fun `name matches outrank tag matches and tag matches outrank note-only matches`() {
        val ordered = rank(
            "git",
            result("Notes about git", matchedNote = true),
            result("Tagged git", matchedTag = true),
            result("My git remote", matchedName = true),
        )

        assertEquals(listOf("My git remote", "Tagged git", "Notes about git"), ordered)
    }

    @Test
    fun `a name match outranks a username match and a username match outranks a tag match`() {
        val ordered = rank(
            "git",
            result("Alpha tagged", matchedTag = true),
            result("Zulu account", matchedUsername = true),
            result("Middle git", matchedName = true),
        )

        assertEquals(listOf("Middle git", "Zulu account", "Alpha tagged"), ordered)
    }

    @Test
    fun `a username match outranks a note-only match`() {
        val ordered = rank(
            "git",
            result("Alpha note", matchedNote = true),
            result("Zulu account", matchedUsername = true),
        )

        assertEquals(listOf("Zulu account", "Alpha note"), ordered)
    }

    @Test
    fun `an exact name beats a prefix and a prefix beats a match in the middle`() {
        val ordered = rank(
            "git",
            result("Ancient git", matchedName = true),
            result("GitHub", matchedName = true),
            result("git", matchedName = true),
        )

        assertEquals(listOf("git", "GitHub", "Ancient git"), ordered)
    }

    @Test
    fun `results of the same rank stay alphabetical`() {
        val ordered = rank(
            "git",
            result("Zeta git", matchedName = true),
            result("Alpha git", matchedName = true),
            result("Middle git", matchedName = true),
        )

        assertEquals(listOf("Alpha git", "Middle git", "Zeta git"), ordered)
    }

    @Test
    fun `an item matching several fields is ranked by its strongest field`() {
        val ordered = rank(
            "git",
            result("Zeta", matchedTag = true),
            result("Alpha note only", matchedNote = true),
            result("Yankee git client", matchedName = true, matchedNote = true, matchedTag = true),
        )

        assertEquals(listOf("Yankee git client", "Zeta", "Alpha note only"), ordered)
    }

    @Test
    fun `a blank query falls back to plain alphabetical order`() {
        val ordered = rank(
            "",
            result("Charlie", matchedName = true),
            result("alpha", matchedName = true),
            result("Bravo", matchedName = true),
        )

        assertEquals(listOf("alpha", "Bravo", "Charlie"), ordered)
    }
}
