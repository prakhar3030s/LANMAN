package com.prakhar.lanman.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prakhar.lanman.data.local.dao.ContactDao
import com.prakhar.lanman.data.local.dao.MessageDao
import com.prakhar.lanman.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LanmanDatabaseTest {
    private lateinit var db: LanmanDatabase
    private lateinit var contactDao: ContactDao
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, LanmanDatabase::class.java
        ).build()
        contactDao = db.contactDao()
        messageDao = db.messageDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeContactAndReadInList() = runBlocking {
        val contact = ContactEntity(
            id = "user1",
            displayName = "Alice",
            publicKeyBundle = null,
            lastKnownIp = null,
            lastSeenAt = null,
            addedAt = System.currentTimeMillis()
        )
        contactDao.upsertContact(contact)

        val byId = contactDao.getContactById("user1").first()
        assertEquals(contact.displayName, byId?.displayName)
    }
}
