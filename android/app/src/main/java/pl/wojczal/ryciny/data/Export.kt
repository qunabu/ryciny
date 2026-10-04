package pl.wojczal.ryciny.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wojczal.ryciny.Graph
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The hand-off to the Raspberry Pi: generated plates laid out as a fugleramme style
 * folder (planes/, dogs/, birds/), the named dogs with their YAMNet bark samples
 * (same model on the Pi, so they carry over), and the journal.
 */
object Export {
    suspend fun write(g: Graph): File = withContext(Dispatchers.IO) {
        val dir = File(g.context.cacheDir, "export").apply { mkdirs() }
        val zip = File(dir, "ryciny-export.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            fun put(name: String, bytes: ByteArray) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
            g.art.generatedDirs.filter { it.isDirectory }.forEach { d ->
                val folder = if (d.name == "birds-generated") "birds" else d.name
                d.listFiles()?.forEach { put("artwork/ryciny/$folder/${it.name}", it.readBytes()) }
            }
            val journal = g.store.value
            put("dogs.json", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Dog.serializer()), journal.dogs).toByteArray())
            put("journal.json", json.encodeToString(Journal.serializer(), journal.copy(barks = journal.barks.map { it.copy(embedding = emptyList()) })).toByteArray())
            listOf("style.json", "dog_breeds.json", "aircraft_types.json").forEach { put(it, g.context.assets.open(it).readBytes()) }
        }
        zip
    }
}
