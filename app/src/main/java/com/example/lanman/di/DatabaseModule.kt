package com.example.lanman.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lanman.data.local.LanmanDatabase
import com.example.lanman.data.local.dao.ContactDao
import com.example.lanman.data.local.dao.FileTransferDao
import com.example.lanman.data.local.dao.MessageDao
import com.example.lanman.data.local.dao.MessageRequestDao
import com.example.lanman.data.local.dao.ProfileDao
import com.example.lanman.data.local.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val LEGACY_DATABASE_NAME = "local_link_db"
    private const val DATABASE_NAME = "lanman_db"

    val MIGRATION_1_4 = object : Migration(1, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sessions` (`addressName` TEXT NOT NULL, `deviceId` INTEGER NOT NULL, `sessionRecord` BLOB NOT NULL, PRIMARY KEY(`addressName`))"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `file_transfers_new` (`id` TEXT NOT NULL, `messageId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `mimeType` TEXT NOT NULL, `localUri` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "INSERT INTO `file_transfers_new` (`id`, `messageId`, `fileName`, `fileSizeBytes`, `mimeType`, `localUri`) SELECT `id`, `messageId`, `fileName`, `fileSizeBytes`, `mimeType`, `localUri` FROM `file_transfers`"
            )
            db.execSQL("DROP TABLE `file_transfers`")
            db.execSQL("ALTER TABLE `file_transfers_new` RENAME TO `file_transfers`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_transfers_messageId` ON `file_transfers` (`messageId`)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `file_transfers` ADD COLUMN `thumbnailUri` TEXT")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `message_requests` (`contactId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `identityKey` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `firstMessageText` TEXT NOT NULL, PRIMARY KEY(`contactId`))"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Version 7 and 8 share identical schemas.
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_messages_contactId`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_contactId_timestamp` ON `messages` (`contactId`, `timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_status_direction_contactId` ON `messages` (`status`, `direction`, `contactId`)")
        }
    }

    @Provides
    @Singleton
    fun provideLanmanDatabase(
        @ApplicationContext context: Context
    ): LanmanDatabase {
        renameLegacyDatabase(context)

        return Room.databaseBuilder(
            context,
            LanmanDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(
                MIGRATION_1_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
    }

    private fun renameLegacyDatabase(context: Context) {
        val legacy = context.getDatabasePath(LEGACY_DATABASE_NAME)
        val current = context.getDatabasePath(DATABASE_NAME)
        if (!legacy.exists() || current.exists()) return

        current.parentFile?.mkdirs()
        listOf("", "-wal", "-shm").forEach { suffix ->
            val from = File(legacy.path + suffix)
            if (from.exists()) from.renameTo(File(current.path + suffix))
        }
    }

    @Provides
    fun provideProfileDao(database: LanmanDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideContactDao(database: LanmanDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideMessageDao(database: LanmanDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideFileTransferDao(database: LanmanDatabase): FileTransferDao = database.fileTransferDao()

    @Provides
    fun provideSessionDao(database: LanmanDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideMessageRequestDao(database: LanmanDatabase): MessageRequestDao = database.messageRequestDao()
}
