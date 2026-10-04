package pl.wojczal.ryciny

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pl.wojczal.ryciny.art.Art
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pl.wojczal.ryciny.art.ArtRequest
import pl.wojczal.ryciny.art.Kind
import pl.wojczal.ryciny.audio.Analyzer
import pl.wojczal.ryciny.audio.Live
import pl.wojczal.ryciny.data.Catalog
import pl.wojczal.ryciny.data.Place
import pl.wojczal.ryciny.data.SettingsStore
import pl.wojczal.ryciny.data.Store
import pl.wojczal.ryciny.planes.Sky
import pl.wojczal.ryciny.rails.Rails

class App : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}

/** Everything the app holds for its whole life, built once. */
class Graph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context.filesDir, scope)
    val store = Store(context.filesDir, scope)
    val place = Place(context, settings, scope)
    val catalog by lazy { Catalog(context) }
    val art = Art(context, settings, scope)
    val sky = Sky(settings, place, store, scope)
    val rails = Rails(context, settings, place, scope)
    val live = MutableStateFlow(Live())

    init {
        // Sounds and dog breeds are a short, fixed list, so each gets its engraving as soon as it is first
        // heard, in the background: the journal's thumbnails then never wait for a visit to the plate.
        // Aircraft are left to a tap: there are far more type and livery pairs.
        scope.launch {
            store.flow
                .map { j ->
                    val sounds = j.sounds.mapNotNull { e -> catalog.sound(e.key)?.let { catalog.soundArt(it, e.variant) } }
                    val dogs = j.barks.map { b ->
                        j.dogs.firstOrNull { it.id == b.dogId }?.let { ArtRequest(Kind.DOG, it.breed, it.breedEn) }
                            ?: catalog.mongrel(b.size).let { ArtRequest(Kind.DOG, it.pl, it.en) }
                    }
                    (sounds + dogs).distinctBy { it.kind to it.key }
                }
                .distinctUntilChanged()
                .collect { wanted -> wanted.filter { art.cached(it) == null }.forEach { runCatching { art.load(it) } } }
        }
    }

    init {
        // A train heard while the timetable was still loading gets its route once the timetable is in.
        scope.launch {
            rails.flow.map { it.date to it.passes.size }.distinctUntilChanged().collect {
                val today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"))
                store.fillSounds("train") { e ->
                    val day = java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.of("Europe/Warsaw")).toLocalDate()
                    if (day != today) null else rails.match(e.at)?.let { t -> Triple(t.title, "${t.route} · planowo ${t.hhmm}", t.agency) }
                }
            }
        }
    }

    /** Loads ~80 MB of models: touch it off the main thread only. */
    val analyzer by lazy { Analyzer(context, settings, place, store, sky, live, catalog.sounds, rails) }
}

val Context.graph: Graph get() = (applicationContext as App).graph
