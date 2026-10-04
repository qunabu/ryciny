package pl.wojczal.ryciny.planes

import android.content.Context
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.wojczal.ryciny.data.json

/** ICAO type designators to readable names, from shared/aircraft_types.json. */
object AircraftTypes {
    private var names: Map<String, String> = emptyMap()

    fun load(context: Context) {
        names = json.parseToJsonElement(context.assets.open("aircraft_types.json").bufferedReader().readText())
            .jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    fun name(code: String): String? = names[code.uppercase()]
}
