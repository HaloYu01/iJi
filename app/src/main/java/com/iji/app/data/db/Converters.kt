package com.iji.app.data.db

import androidx.room.TypeConverter
import com.iji.app.data.model.TransactionType
import java.time.LocalDate

/** Room 类型转换：枚举 / 标签列表 / 日期(epochDay) */
class Converters {

    @TypeConverter
    fun typeToString(type: TransactionType): String = type.name

    @TypeConverter
    fun stringToType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)

    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(",")

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").filter { it.isNotBlank() }

    @TypeConverter
    fun dateToLong(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun longToDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)
}
