package com.example.lanman.di

import android.content.Context
import androidx.room.Room
import com.example.lanman.data.local.LanmanDatabase
import com.example.lanman.data.local.dao.ContactDao
import com.example.lanman.data.local.dao.FileTransferDao
import com.example.lanman.data.local.dao.MessageDao
import com.example.lanman.data.local.dao.ProfileDao
import com.example.lanman.data.local.dao.MessageRequestDao
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
        .fallbackToDestructiveMigration()
        .build()
    }

    /**
     * Carries the database over from the app's previous name so an update does not look
     * like a fresh install and wipe local messages.
     */
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
    fun provideSessionDao(database: LanmanDatabase): com.example.lanman.data.local.dao.SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideMessageRequestDao(database: LanmanDatabase): MessageRequestDao {
        return database.messageRequestDao()
    }
}
