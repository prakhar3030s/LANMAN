package com.example.lanman.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.lanman.domain.model.MessageStatus

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
