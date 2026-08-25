package de.davis.keygo.core.item.data.local.converter

import androidx.room3.ColumnTypeConverter
import java.time.YearMonth

internal object YearMonthConverter {

    @ColumnTypeConverter
    fun fromYearMonth(yearMonth: YearMonth?): Int? = yearMonth?.let {
        yearMonth.year * 100 + yearMonth.monthValue
    }

    @ColumnTypeConverter
    fun fromInt(value: Int?): YearMonth? = value?.let {
        YearMonth.of(it / 100, it % 100)
    }
}