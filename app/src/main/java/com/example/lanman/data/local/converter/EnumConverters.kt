package com.example.lanman.data.local.converter

import androidx.room.TypeConverter
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType

class EnumConverters {

    @TypeConverter
    fun fromMessageDirection(value: MessageDirection?): String? {
        return value?.name
    }

    @TypeConverter
    fun toMessageDirection(value: String?): MessageDirection? {
        return value?.let { MessageDirection.valueOf(it) }
    }

    @TypeConverter
    fun fromMessageType(value: MessageType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toMessageType(value: String?): MessageType? {
        return value?.let { MessageType.valueOf(it) }
    }

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus?): String? {
        return value?.name
    }

    @TypeConverter
    fun toMessageStatus(value: String?): MessageStatus? {
        return value?.let { MessageStatus.valueOf(it) }
    }
}
