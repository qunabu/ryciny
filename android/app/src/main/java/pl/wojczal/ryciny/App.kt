package pl.wojczal.ryciny

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pl.wojczal.ryciny.art.Art
import kotlinx.coroutines.flow.MutableStateFlow
import pl.wojczal.ryciny.audio.Analyzer
import pl.wojczal.ryciny.audio.Live
import pl.wojczal.ryciny.data.Catalog
import pl.wojczal.ryciny.data.Place
import pl.wojczal.ryciny.data.SettingsStore
import pl.wojczal.ryciny.data.Store
import pl.wojczal.ryciny.planes.Sky

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
    val live = MutableStateFlow(Live())

    /** Loads ~80 MB of models: touch it off the main thread only. */
    val analyzer by lazy { Analyzer(context, settings, place, store, sky, live) }
}

val Context.graph: Graph get() = (applicationContext as App).graph
