package com.pureframe.player.data.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room 类型转换器 - Date 类型
 */
class DateTypeConverter {
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}