package com.example.lanman.data.repository

import com.example.lanman.data.local.entity.ContactEntity
import com.example.lanman.data.local.entity.FileTransferEntity
import com.example.lanman.data.local.entity.MessageEntity
import com.example.lanman.data.local.entity.ProfileEntity
import com.example.lanman.domain.model.Contact
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.Profile

fun ProfileEntity.toDomain(): Profile {
    return Profile(
        id = id,
        displayName = displayName,
        publicKeyBundle = publicKeyBundle,
        createdAt = createdAt
    )
}

fun Profile.toEntity(): ProfileEntity {
    return ProfileEntity(
        id = id,
        displayName = displayName,
        publicKeyBundle = publicKeyBundle,
        createdAt = createdAt
    )
}

fun ContactEntity.toDomain(): Contact {
    return Contact(
        id = id,
        displayName = displayName,
        publicKeyBundle = publicKeyBundle,
        lastKnownIp = lastKnownIp,
        lastSeenAt = lastSeenAt,
        addedAt = addedAt
    )
}

fun Contact.toEntity(): ContactEntity {
    return ContactEntity(
        id = id,
        displayName = displayName,
        publicKeyBundle = publicKeyBundle,
        lastKnownIp = lastKnownIp,
        lastSeenAt = lastSeenAt,
        addedAt = addedAt
    )
}

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        contactId = contactId,
        direction = direction,
        content = content,
        type = type,
        status = status,
        timestamp = timestamp
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        contactId = contactId,
        direction = direction,
        content = content,
        type = type,
        status = status,
        timestamp = timestamp
    )
}

fun FileTransferEntity.toDomain(): FileTransfer {
    return FileTransfer(
        id = id,
        messageId = messageId,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        mimeType = mimeType,
        localUri = localUri,
        thumbnailUri = thumbnailUri
    )
}

fun FileTransfer.toEntity(): FileTransferEntity {
    return FileTransferEntity(
        id = id,
        messageId = messageId,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        mimeType = mimeType,
        localUri = localUri,
        thumbnailUri = thumbnailUri
    )
}
