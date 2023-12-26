package de.davis.passwordmanager.database.entities

import androidx.room.Embedded

data class TagWithCountEntity(@Embedded val tag: Tag, val count: Int)