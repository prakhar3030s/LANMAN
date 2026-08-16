package com.example.lanman.di

import android.content.Context
import androidx.room.Room
import com.example.lanman.data.local.LocalLinkDatabase
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideLocalLinkDatabase(
        @ApplicationContext context: Context
    ): LocalLinkDatabase {
        return Room.databaseBuilder(
            context,
            LocalLinkDatabase::class.java,
            "local_link_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideProfileDao(database: LocalLinkDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideContactDao(database: LocalLinkDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideMessageDao(database: LocalLinkDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideFileTransferDao(database: LocalLinkDatabase): com.example.lanman.data.local.dao.FileTransferDao {
        return database.fileTransferDao()
    }

    @Provides
    fun provideSessionDao(database: LocalLinkDatabase): com.example.lanman.data.local.dao.SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideMessageRequestDao(database: LocalLinkDatabase): MessageRequestDao {
        return database.messageRequestDao()
    }
}
