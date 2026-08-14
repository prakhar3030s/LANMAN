package com.example.lanman.di

import com.example.lanman.data.repository.ContactRepositoryImpl
import com.example.lanman.data.repository.FileTransferRepositoryImpl
import com.example.lanman.data.repository.MessageRepositoryImpl
import com.example.lanman.data.repository.ProfileRepositoryImpl
import com.example.lanman.domain.repository.ContactRepository
import com.example.lanman.domain.repository.FileTransferRepository
import com.example.lanman.domain.repository.MessageRepository
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.repository.MessageRequestRepository
import com.example.lanman.data.repository.MessageRequestRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        contactRepositoryImpl: ContactRepositoryImpl
    ): ContactRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindFileTransferRepository(
        fileTransferRepositoryImpl: FileTransferRepositoryImpl
    ): FileTransferRepository

    @Binds
    @Singleton
    abstract fun bindMessageRequestRepository(
        messageRequestRepositoryImpl: MessageRequestRepositoryImpl
    ): MessageRequestRepository
}
