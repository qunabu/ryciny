package pl.wojczal.ryciny.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.wojczal.ryciny.audio.Live
import pl.wojczal.ryciny.planes.SkyState
import java.nio.file.Files

/**
 * Against a running add-on: RYCINY_URL=http://localhost:8099 ./gradlew testDebugUnitTest.
 * The app's own Remote and Store pull the journal, then name a dog and tag a bark on the server.
 */
class RemoteTest {
    @Test
    fun showsTheAddOnsJournalAndTagsDogsThere() = runBlocking {
        val url = System.getenv("RYCINY_URL")
        assumeTrue(!url.isNullOrBlank())
        val dir = Files.createTempDirectory("ryciny").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsStore(dir, scope).apply { update { it.copy(source = "server", serverUrl = url!!) } }
        val store = Store(dir, scope)
        val live = MutableStateFlow(Live())
        var sky = SkyState()
        val remote = Remote(settings, store, live, { sky = it }, scope)
        store.server = remote

        repeat(40) { if (!store.remote || store.value.barks.isEmpty()) delay(250) }
        assertTrue("journal pulled", store.remote)
        assertTrue("birds from the add-on", store.value.birds.isNotEmpty())
        assertTrue("live from the add-on", live.value.listening)
        println("pulled: ${store.value.birds.size} birds, ${store.value.barks.size} barks, ${store.value.sounds.size} sounds; sky updated ${sky.updatedAt}")

        val bark = store.value.barks.last()
        val id = store.newDog("Testowy", "Jamnik", "a Dachshund")
        store.tagBark(bark.id, id)
        delay(2_000) // the outbox sends and pulls at once
        println("after tag: status=${remote.flow.value.status} dogs=${store.value.dogs.map { it.name }}")
        val dog = store.value.dogs.firstOrNull { it.id == id }
        assertEquals("Testowy", dog?.name)
        assertEquals(id, store.value.barks.first { it.id == bark.id }.dogId)
        assertTrue("dog has a sample on the server", dog!!.samples.isNotEmpty())
        assertTrue("phone's own journal untouched", !store.localFile.exists() || !store.localFile.readText().contains("Testowy"))

        store.deleteDog(id)
        delay(2_000)
        assertTrue(store.value.dogs.none { it.id == id })
        scope.cancel()
    }
}
