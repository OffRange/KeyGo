package de.davis.passwordmanager.database.dtos

import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.TagWithCountEntity

data class TagWithCount(val tag: Tag, val count: Int) : Item {

    override val id: Long
        get() = tag.tagId
}

fun TagWithCountEntity.toDto() = TagWithCount(tag, count)
