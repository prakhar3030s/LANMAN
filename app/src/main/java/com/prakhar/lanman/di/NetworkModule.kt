package com.prakhar.lanman.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.prakhar.lanman.data.network.ConnectionManagerImpl
import com.prakhar.lanman.data.network.NetworkDiscoveryManagerImpl
import com.prakhar.lanman.data.network.PresenceTrackerImpl
import com.prakhar.lanman.domain.network.ConnectionManager
import com.prakhar.lanman.domain.network.NetworkDiscoveryManager
import com.prakhar.lanman.domain.network.PresenceTracker
import com.prakhar.lanman.domain.repository.ContactRepository
import com.prakhar.lanman.domain.repository.ProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideWifiManager(@ApplicationContext context: Context): WifiManager {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
        return context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideNetworkDiscoveryManager(
        @ApplicationContext context: Context,
        profileRepository: ProfileRepository,
        wifiManager: WifiManager,
        connectivityManager: ConnectivityManager
    ): NetworkDiscoveryManager {
        return NetworkDiscoveryManagerImpl(
            context = context,
            profileRepository = profileRepository,
            wifiManager = wifiManager,
            connectivityManager = connectivityManager
        )
    }

    @Provides
    @Singleton
    fun providePresenceTracker(
        networkDiscoveryManager: NetworkDiscoveryManager,
        contactRepository: ContactRepository
    ): PresenceTracker {
        return PresenceTrackerImpl(
            networkDiscoveryManager = networkDiscoveryManager,
            contactRepository = contactRepository
        )
    }

    @Provides
    @Singleton
    fun provideConnectionManager(
        profileRepository: ProfileRepository,
        contactRepository: ContactRepository,
        secureKeyStore: com.prakhar.lanman.data.security.SecureKeyStore,
        blockedContactsStore: com.prakhar.lanman.data.security.BlockedContactsStore
    ): ConnectionManager {
        return ConnectionManagerImpl(
            profileRepository = profileRepository,
            contactRepository = contactRepository,
            secureKeyStore = secureKeyStore,
            blockedContactsStore = blockedContactsStore
        )
    }

    @Provides
    @Singleton
    fun provideMessagingEngine(
        @ApplicationContext context: Context,
        connectionManager: ConnectionManager,
        presenceTracker: PresenceTracker,
        networkDiscoveryManager: NetworkDiscoveryManager,
        messageDispatcher: com.prakhar.lanman.data.network.MessageDispatcher,
        incomingMessageReceiver: com.prakhar.lanman.data.network.IncomingMessageReceiver,
        fileTransferReceiver: com.prakhar.lanman.data.network.FileTransferReceiver
    ): com.prakhar.lanman.domain.engine.MessagingEngine {
        return com.prakhar.lanman.data.engine.MessagingEngineImpl(
            context = context,
            connectionManager = connectionManager,
            presenceTracker = presenceTracker,
            networkDiscoveryManager = networkDiscoveryManager,
            messageDispatcher = messageDispatcher,
            incomingMessageReceiver = incomingMessageReceiver,
            fileTransferReceiver = fileTransferReceiver
        )
    }
}
