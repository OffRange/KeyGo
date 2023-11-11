package de.davis.passwordmanager.database.converter

import androidx.room.TypeConverter
import de.davis.passwordmanager.database.ElementType

object ElementTypeConverter {

    @TypeConverter
    fun elementTypeToInt(elementType: ElementType): Int = elementType.typeId

    @TypeConverter
    fun intToElementType(elementId: Int): ElementType = ElementType.getTypeByTypeId(elementId)
}