package com.prakhar.lanman.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_transfers",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class FileTransferEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val localUri: String,
    val thumbnailUri: String?
)
