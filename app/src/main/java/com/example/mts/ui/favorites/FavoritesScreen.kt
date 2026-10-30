package com.example.mts.ui.favorites

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.CollectionRequest
import com.example.mts.data.Favorite
import com.example.mts.data.FavoriteMove
import com.example.mts.data.RecipeCollection
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.EmptyState
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.RecipeRow
import com.example.mts.ui.common.SelectChip
import kotlinx.coroutines.flow.MutableStateFlow

class FavoritesVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<List<Favorite>>>(Load.Loading)
    val collections = MutableStateFlow<List<RecipeCollection>>(emptyList())

    fun load() {
        state.load(silent = true) { api.favorites() }
        act { collections.value = api.collections() }
    }

    fun remove(favorite: Favorite) = act {
        api.removeFavorite(favorite.id)
        load()
    }

    fun move(favorite: Favorite, collectionId: String?) = act {
        api.moveFavorite(favorite.id, FavoriteMove(collectionId))
        load()
    }

    fun addCollection(name: String) = act {
        api.addCollection(CollectionRequest(name.trim()))
        load()
    }

    fun deleteCollection(collection: RecipeCollection) = act {
        api.deleteCollection(collection.id)
        load()
    }
}

@Composable
fun FavoritesScreen(onBack: () -> Unit, onRecipe: (String) -> Unit, vm: FavoritesVm = viewModel()) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()
    val collections by vm.collections.collectAsState()
    var query by remember { mutableStateOf("") }
    var collectionId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar(stringResource(R.string.favorites), onBack) {
                IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.new_collection)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_favorites)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = CircleShape,
            )
            Spacer(Modifier.height(10.dp))
            ChipRow {
                SelectChip(stringResource(R.string.all), collectionId == null) { collectionId = null }
                collections.forEach { c ->
                    SelectChip("${c.name} (${c.recipeCount})", collectionId == c.id) {
                        collectionId = if (collectionId == c.id) null else c.id
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when (val current = state) {
                Load.Loading -> LoadingState()
                is Load.Failed -> ErrorState(current.message, vm::load)
                is Load.Ready -> {
                    val needle = query.trim().lowercase()
                    val visible = current.value.filter {
                        (collectionId == null || it.collectionId == collectionId) &&
                            (needle.isEmpty() || it.recipe.title.lowercase().contains(needle))
                    }
                    if (visible.isEmpty()) {
                        EmptyState(
                            Icons.Filled.Favorite,
                            stringResource(R.string.favorites_empty_title),
                            stringResource(R.string.favorites_empty_body),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.id }) { favorite ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RecipeRow(favorite.recipe, { onRecipe(favorite.recipe.key) }, Modifier.weight(1f))
                                    FavoriteMenu(favorite, collections, vm)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.new_collection)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.addCollection(name)
                    name = ""
                    adding = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FavoriteMenu(favorite: Favorite, collections: List<RecipeCollection>, vm: FavoritesVm) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            collections.forEach { c ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to, c.name)) },
                    enabled = favorite.collectionId != c.id,
                    onClick = {
                        vm.move(favorite, c.id)
                        open = false
                    },
                )
            }
            if (favorite.collectionId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_collection)) },
                    onClick = {
                        vm.move(favorite, null)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_favorite)) },
                onClick = {
                    vm.remove(favorite)
                    open = false
                },
            )
        }
    }
}
