package pl.wojczal.ryciny.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Breed(val pl: String, val en: String)

/** Static lists shipped in assets (shared/ in the repo, so the Pi reads the same ones). */
class Catalog(context: Context) {
    val breeds: List<Breed> = json.decodeFromString(
        ListSerializer(Breed.serializer()),
        context.assets.open("dog_breeds.json").bufferedReader().readText(),
    )

    /** Body mass in grams per scientific name, from fugleramme's bird_sizes.csv. */
    val birdMass: Map<String, Double> = context.assets.open("bird_sizes.csv").bufferedReader().readLines().drop(1)
        .mapNotNull { line ->
            val (name, mass) = line.split(',').takeIf { it.size >= 2 } ?: return@mapNotNull null
            mass.toDoubleOrNull()?.let { name.trim('"') to it }
        }.toMap()

    fun breedEn(pl: String) = breeds.firstOrNull { it.pl.equals(pl, ignoreCase = true) }?.en ?: "a $pl dog"

    /** Unknown dogs are drawn as a mongrel of the size their bark suggests. */
    fun mongrel(size: String) = when (size) {
        "duży" -> breeds[2]
        "mały" -> breeds[0]
        else -> breeds[1]
    }
}
