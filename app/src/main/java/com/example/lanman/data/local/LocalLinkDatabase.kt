package com.example.lanman.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.lanman.data.local.converter.EnumConverters
import com.example.lanman.data.local.dao.*
import com.example.lanman.data.local.entity.*

@Database(
    entities = [
        ProfileEntity::class,
        ContactEntity::class,
        MessageEntity::class,
        FileTransferEntity::class,
        SessionEntity::class,
        MessageRequestEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(EnumConverters::class)
abstract class LocalLinkDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun fileTransferDao(): FileTransferDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageRequestDao(): MessageRequestDao
}
