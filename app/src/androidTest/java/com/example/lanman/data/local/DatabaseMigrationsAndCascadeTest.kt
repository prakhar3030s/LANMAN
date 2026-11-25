package com.example.lanman.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lanman.data.local.entity.ContactEntity
import com.example.lanman.data.local.entity.FileTransferEntity
import com.example.lanman.data.local.entity.MessageEntity
import com.example.lanman.di.DatabaseModule
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationsAndCascadeTest {

    private lateinit var context: Context
    private lateinit var db: LanmanDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, LanmanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun deletingContactCascadesMessagesAndFileTransfers() = runBlocking {
        val contact = ContactEntity("c-1", "Alice", null, null, null, 100L)
        val message = MessageEntity(
            id = "m-1",
            contactId = "c-1",
            direction = MessageDirection.OUTGOING,
            content = "file".toByteArray(),
            type = MessageType.FILE,
            status = MessageStatus.PENDING,
            timestamp = 200L
        )
        val transfer = FileTransferEntity("ft-1", "m-1", "a.pdf", 42L, "application/pdf", "/tmp/a.pdf", null)

        db.contactDao().upsertContact(contact)
        assertTrue(db.messageDao().insertIfNotExists(message))
        assertFalse(db.messageDao().insertIfNotExists(message))
        db.fileTransferDao().insertFileTransfer(transfer)

        assertEquals(1, db.fileTransferDao().getForContactSync("c-1").size)

        db.contactDao().deleteContact("c-1")

        assertNull(db.messageDao().getMessageById("m-1"))
        assertTrue(db.fileTransferDao().getForContactSync("c-1").isEmpty())
    }

    @Test
    fun upsertingExistingContactUpdatesBundleWithoutCascadingMessages() = runBlocking {
        val initial = ContactEntity("c-upsert", "Alice", byteArrayOf(1, 2), "10.0.0.1", 10L, 100L)
        val message = MessageEntity(
            id = "m-keep",
            contactId = "c-upsert",
            direction = MessageDirection.INCOMING,
            content = "kept".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.READ,
            timestamp = 200L
        )
        db.contactDao().upsertContact(initial)
        db.messageDao().insertMessage(message)

        val updated = ContactEntity("c-upsert", "Alice QR", byteArrayOf(3, 4, 5), "10.0.0.1", 20L, 100L)
        db.contactDao().upsertContact(updated)

        val storedContact = db.contactDao().getContactByIdSync("c-upsert")
        assertEquals("Alice QR", storedContact?.displayName)
        assertTrue(byteArrayOf(3, 4, 5).contentEquals(storedContact?.publicKeyBundle))
        assertEquals("kept", db.messageDao().getMessageById("m-keep")?.content?.decodeToString())
    }

    @Test
    fun updateMessageStatusIfCurrentPreservesAlreadyDeliveredStatus() = runBlocking {
        val contact = ContactEntity("c-2", "Bob", null, null, null, 100L)
        val msg = MessageEntity(
            id = "m-2",
            contactId = "c-2",
            direction = MessageDirection.OUTGOING,
            content = "hi".toByteArray(),
            type = MessageType.TEXT,
            status = MessageStatus.DELIVERED,
            timestamp = 150L
        )
        db.contactDao().upsertContact(contact)
        db.messageDao().insertMessage(msg)

        db.messageDao().updateMessageStatusIfCurrent("m-2", MessageStatus.PENDING, MessageStatus.SENT)

        assertEquals(MessageStatus.DELIVERED, db.messageDao().getMessageById("m-2")?.status)
        assertTrue(db.messageDao().getMessagesForContact("c-2").first().isNotEmpty())
    }

    @Test
    fun explicitMigrationsUpgradeV1ToV9AndPreserveData() {
        val dbName = "migration_test_v1_v9.db"
        context.deleteDatabase(dbName)

        val callback = object : SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )

        try {
            val sqlDb = helper.writableDatabase
            sqlDb.execSQL("PRAGMA foreign_keys = ON")
            sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `profiles` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `publicKeyBundle` BLOB, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `publicKeyBundle` BLOB, `lastKnownIp` TEXT, `lastSeenAt` INTEGER, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `contactId` TEXT NOT NULL, `direction` TEXT NOT NULL, `content` BLOB NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`contactId`) REFERENCES `contacts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            sqlDb.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_contactId` ON `messages` (`contactId`)")
            sqlDb.execSQL("CREATE TABLE IF NOT EXISTS `file_transfers` (`id` TEXT NOT NULL, `messageId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `mimeType` TEXT NOT NULL, `localUri` TEXT NOT NULL, PRIMARY KEY(`id`))")

            sqlDb.execSQL("INSERT INTO `contacts` VALUES ('c1', 'Alice', NULL, NULL, NULL, 100)")
            sqlDb.execSQL("INSERT INTO `messages` VALUES ('m1', 'c1', 'OUTGOING', X'6869', 'FILE', 'DELIVERED', 200)")
            sqlDb.execSQL("INSERT INTO `file_transfers` VALUES ('ft1', 'm1', 'note.txt', 2, 'text/plain', '/data/note.txt')")

            DatabaseModule.MIGRATION_1_4.migrate(sqlDb)
            DatabaseModule.MIGRATION_4_5.migrate(sqlDb)
            DatabaseModule.MIGRATION_5_6.migrate(sqlDb)
            DatabaseModule.MIGRATION_6_7.migrate(sqlDb)
            DatabaseModule.MIGRATION_7_8.migrate(sqlDb)
            DatabaseModule.MIGRATION_8_9.migrate(sqlDb)

            sqlDb.query("SELECT fileName, thumbnailUri FROM `file_transfers` WHERE id = 'ft1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("note.txt", c.getString(0))
                assertNull(c.getString(1))
            }
            sqlDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sessions','message_requests')").use { c ->
                assertEquals(2, c.count)
            }
            sqlDb.query("SELECT name FROM sqlite_master WHERE type='index' AND name IN ('index_messages_contactId_timestamp','index_messages_status_direction_contactId','index_file_transfers_messageId')").use { c ->
                assertEquals(3, c.count)
            }
            // Verify cascading delete on migrated file_transfers foreign key
            sqlDb.execSQL("DELETE FROM `messages` WHERE id = 'm1'")
            sqlDb.query("SELECT COUNT(*) FROM `file_transfers` WHERE id = 'ft1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(dbName)
        }
    }
}
