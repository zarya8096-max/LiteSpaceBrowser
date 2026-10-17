package org.litespace.browser

import org.litespace.browser.domain.LiteApp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.litespace.browser.data.*

@RunWith(AndroidJUnit4::class)
class RepositoryDeviceTest {
    private lateinit var database: LiteDatabase
    private lateinit var repository: LocalRepository
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiteDatabase::class.java).build()
        repository = LocalRepository(database)
    }
    @After fun tearDown() { database.close() }
    @Test fun roundTripPersianAndCustomSettings() = runBlocking {
        val original = LiteApp(name = "آزمون", url = "example.com", css = "body{color:red}", script = "window.test=1", profile = "work", camera = true, fontSize = 125)
        repository.save(original)
        val backup = repository.export()
        repository.delete(original.id)
        assertEquals(1, repository.restore(backup))
        val restored = repository.get(original.id)!!
        assertEquals("آزمون", restored.name)
        assertEquals("https://example.com", restored.url)
        assertEquals(original.css, restored.css)
        assertEquals(original.script, restored.script)
        assertEquals(original.profile, restored.profile)
        assertTrue(restored.camera)
        assertEquals(125, restored.fontSize)
    }
    @Test fun invalidImportRollsBackEveryChange() = runBlocking {
        val original = LiteApp(name = "Original", url = "https://example.com")
        repository.save(original)
        val root = JSONObject(repository.export())
        val good = root.getJSONArray("apps").getJSONObject(0)
        good.put("name", "Overwritten")
        val bad = JSONObject(good.toString()).put("id", "second").put("url", "file:///secret")
        root.getJSONArray("apps").put(bad)
        assertTrue(runCatching { repository.restore(root.toString()) }.isFailure)
        assertEquals("Original", repository.get(original.id)?.name)
        assertNull(repository.get("second"))
    }
}
