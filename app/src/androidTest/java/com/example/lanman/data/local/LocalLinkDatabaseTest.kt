package com.example.lanman.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lanman.data.local.dao.ContactDao
import com.example.lanman.data.local.dao.MessageDao
import com.example.lanman.domain.model.Contact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LocalLinkDatabaseTest {
    private lateinit var db: LocalLinkDatabase
    private lateinit var contactDao: ContactDao
    private lateinit var messageDao: MessageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, LocalLinkDatabase::class.java).build()
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
    fun writeContactAndReadInList() = runTest {
        val contact = Contact(
            id = "user1",
            displayName = "Alice",
            addedAt = System.currentTimeMillis()
        )
        contactDao.insertContact(contact)
        
        val byId = contactDao.getContactById("user1").first()
        assertEquals(contact.displayName, byId?.displayName)
    }
}
