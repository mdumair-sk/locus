package com.locus.core.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocusDatabaseMigrationTest {
    private lateinit var context: Context
    private val dbName = "test_migration_3_4.db"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration3To4_createsChatSessionsAndMessagesTablesAndIndex() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            // Create v3 schema
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `chunks` (
                                    `chunkId` TEXT NOT NULL,
                                    `noteId` TEXT NOT NULL,
                                    `headingPathJson` TEXT NOT NULL,
                                    `text` TEXT NOT NULL,
                                    `embedding` BLOB NOT NULL,
                                    `embeddingModelId` TEXT NOT NULL,
                                    `sourceChecksum` TEXT NOT NULL,
                                    PRIMARY KEY(`chunkId`)
                                )
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) {
                            // No-op for mock v3 database setup
                        }
                    },
                ).build()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(config)
        val v3Db = helper.writableDatabase

        // Execute MIGRATION_3_4
        LocusDatabase.MIGRATION_3_4.migrate(v3Db)

        // Verify chat_sessions exists and works
        v3Db.execSQL(
            """
            INSERT INTO chat_sessions (id, name, createdAt, modifiedAt)
            VALUES ('s1', 'Session 1', 1000, 2000)
            """.trimIndent(),
        )

        // Verify chat_messages exists and works
        v3Db.execSQL(
            """
            INSERT INTO chat_messages (id, sessionId, role, content, citationsJson, timestamp)
            VALUES ('m1', 's1', 'USER', 'Hello', '[]', 1500)
            """.trimIndent(),
        )

        val sessionCursor =
            v3Db.query(
                "SELECT id, name, createdAt, modifiedAt FROM chat_sessions WHERE id = 's1'",
            )
        assertTrue(sessionCursor.moveToFirst())
        assertEquals("s1", sessionCursor.getString(0))
        assertEquals("Session 1", sessionCursor.getString(1))
        assertEquals(1000L, sessionCursor.getLong(2))
        assertEquals(2000L, sessionCursor.getLong(3))
        sessionCursor.close()

        val msgCursor =
            v3Db.query(
                "SELECT id, sessionId, role, content, citationsJson, timestamp FROM chat_messages WHERE id = 'm1'",
            )
        assertTrue(msgCursor.moveToFirst())
        assertEquals("m1", msgCursor.getString(0))
        assertEquals("s1", msgCursor.getString(1))
        assertEquals("USER", msgCursor.getString(2))
        assertEquals("Hello", msgCursor.getString(3))
        assertEquals("[]", msgCursor.getString(4))
        assertEquals(1500L, msgCursor.getLong(5))
        msgCursor.close()

        // Verify index exists
        val indexCursor =
            v3Db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_chat_messages_sessionId'",
            )
        assertTrue(indexCursor.moveToFirst())
        assertEquals("index_chat_messages_sessionId", indexCursor.getString(0))
        indexCursor.close()

        helper.close()
    }
}
