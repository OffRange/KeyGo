package de.davis.keygo.core.item.data.local.converter

import androidx.room.TypeConverter
import java.time.YearMonth

internal object YearMonthConverter {

    @TypeConverter
    fun fromYearMonth(yearMonth: YearMonth?): Int? = yearMonth?.let {
        yearMonth.year * 100 + yearMonth.monthValue
    }

    @TypeConverter
    fun fromInt(value: Int?): YearMonth? = value?.let {
        YearMonth.of(it / 100, it % 100)
    }
}