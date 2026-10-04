package pl.wojczal.ryciny.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.wojczal.ryciny.Graph
import pl.wojczal.ryciny.art.ArtRequest
import pl.wojczal.ryciny.art.Kind
import pl.wojczal.ryciny.data.Dog

/** The neighbourhood's dogs as the app knows them. */
@Composable
fun DogsScreen(g: Graph) {
    val journal by g.store.flow.collectAsState()
    var editing by remember { mutableStateOf<Dog?>(null) }
    Column(Modifier.fillMaxSize().paper().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Caption("Psy z sąsiedztwa", size = 24, italic = false, modifier = Modifier.fillMaxWidth())
        Caption(
            "Szczekanie wykrywa YAMNet. Rasy nie da się odczytać z samego dźwięku, więc każdego psa oznaczasz raz " +
                "w Dzienniku („Kto to?”). Od tej pory jego szczekanie jest porównywane z zapamiętanymi próbkami " +
                "— im więcej oznaczeń, tym pewniej. Nieoznaczone psy dostają tylko szacunek wielkości z wysokości głosu.",
            size = 14, color = InkSoft,
        )
        if (journal.dogs.isEmpty()) {
            Caption("Jeszcze żadnego. Poczekaj, aż któryś zaszczeka, i oznacz go w Dzienniku.", color = InkSoft, modifier = Modifier.padding(top = 24.dp))
        }
        LazyColumn {
            items(journal.dogs) { dog ->
                val barks = journal.barks.filter { it.dogId == dog.id }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Engraving(ArtRequest(Kind.DOG, dog.breed, dog.breedEn), height = 80.dp, placeholder = "…")
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(dog.name)
                        Text("${dog.breed} · próbek: ${dog.samples.size} · szczekań: ${barks.sumOf { it.count }}", style = Italic, color = InkSoft)
                    }
                    TextButton(onClick = { editing = dog }) { Text("Edytuj") }
                }
                HorizontalDivider(color = InkSoft.copy(alpha = 0.2f))
            }
        }
    }
    editing?.let { dog ->
        var name by remember(dog.id) { mutableStateOf(dog.name) }
        var breed by remember(dog.id) { mutableStateOf(dog.breed) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Pies") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Imię") }, singleLine = true)
                    OutlinedTextField(breed, { breed = it }, label = { Text("Rasa") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { g.store.editDog(dog.id, name, breed, g.catalog.breedEn(breed)); editing = null }) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { g.store.deleteDog(dog.id); editing = null }) { Text("Usuń psa", color = Rubric) }
            },
        )
    }
}
