package com.example.mts.ui.discover

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.FindResult
import com.example.mts.data.PantryItem
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.EmptyState
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.RecipeRow
import com.example.mts.ui.common.ScreenTitle
import com.example.mts.ui.common.SelectChip
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.theme.Carrot
import com.example.mts.ui.theme.CarrotSoft
import kotlinx.coroutines.flow.MutableStateFlow

class DiscoverVm(app: Application) : BaseVm(app) {
    val results = MutableStateFlow<Load<FindResult>>(Load.Loading)
    val pantry = MutableStateFlow<List<PantryItem>>(emptyList())

    fun loadPantry() = act { pantry.value = api.pantry() }

    fun find(query: String, selected: Set<String>, minMatch: Int, mode: String) {
        results.load {
            if (query.isNotBlank()) {
                api.searchRecipes(query.trim(), minMatch, mode)
            } else {
                api.recipesByIngredients(selected.takeIf { it.isNotEmpty() }?.joinToString(","), minMatch, mode)
            }
        }
    }
}

@Composable
fun DiscoverScreen(
    initialQuery: String,
    initialIngredients: List<String>,
    onRecipe: (String) -> Unit,
    onGenerate: () -> Unit,
    vm: DiscoverVm = viewModel(),
) {
    CollectMessages(vm)
    val results by vm.results.collectAsState()
    val pantry by vm.pantry.collectAsState()
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var selected by remember(initialIngredients) { mutableStateOf(initialIngredients.toSet()) }
    var minMatch by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf("any") }
    val keyboard = LocalSoftwareKeyboardController.current
    val search = {
        keyboard?.hide()
        vm.find(query, selected, minMatch, mode)
    }

    LaunchedEffect(Unit) { vm.loadPantry() }
    LaunchedEffect(initialQuery, initialIngredients, mode) { vm.find(query, selected, minMatch, mode) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle(stringResource(R.string.nav_discover)) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.discover_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            vm.find("", selected, minMatch, mode)
                        }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
            )
        }

        item {
            SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), color = CarrotSoft) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ai_generator), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ai_generator_body), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(onGenerate, colors = ButtonDefaults.buttonColors(containerColor = Carrot)) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.try_it))
                    }
                }
            }
        }

        if (pantry.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.use_ingredients),
                    Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow {
                    pantry.forEach { item ->
                        SelectChip(item.name, item.nameEn in selected) {
                            selected = if (item.nameEn in selected) selected - item.nameEn else selected + item.nameEn
                            query = ""
                            vm.find("", selected, minMatch, mode)
                        }
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.availability),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            ChipRow {
                SelectChip(stringResource(R.string.mode_any), mode == "any") { mode = "any" }
                SelectChip(stringResource(R.string.mode_few), mode == "few") { mode = "few" }
                SelectChip(stringResource(R.string.mode_available), mode == "available") { mode = "available" }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.min_match, minMatch), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = minMatch.toFloat(),
                    onValueChange = { minMatch = it.toInt() },
                    onValueChangeFinished = { vm.find(query, selected, minMatch, mode) },
                    valueRange = 0f..100f,
                    steps = 19,
                )
            }
        }

        when (val current = results) {
            Load.Loading -> item { LoadingState(Modifier.height(200.dp), stringResource(R.string.loading_recipes)) }
            is Load.Failed -> item { ErrorState(current.message, { vm.find(query, selected, minMatch, mode) }) }
            is Load.Ready -> {
                val found = current.value
                if (found.sources.spoonacular == "failed" || found.sources.themealdb == "failed") {
                    item {
                        Text(
                            stringResource(R.string.partial_results),
                            Modifier.padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (found.recipes.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Filled.RestaurantMenu,
                            stringResource(R.string.no_recipes_title),
                            stringResource(if (found.suggestAi) R.string.sources_down_body else R.string.no_recipes_body),
                        ) {
                            Button(onGenerate) { Text(stringResource(R.string.generate_with_ai)) }
                        }
                    }
                } else {
                    items(found.recipes, key = { it.key }) { recipe ->
                        RecipeRow(recipe, { onRecipe(recipe.key) }, Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}
