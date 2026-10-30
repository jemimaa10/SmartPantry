package com.example.mts.ui.discover

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.GenerateRequest
import com.example.mts.data.GenerateResponse
import com.example.mts.data.PantryItem
import com.example.mts.data.RecipeDetail
import com.example.mts.data.RecipeSummary
import com.example.mts.data.toAppFailure
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.RecipeRow
import com.example.mts.ui.common.SelectChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class GenerateVm(app: Application) : BaseVm(app) {
    val pantry = MutableStateFlow<List<PantryItem>>(emptyList())
    val result = MutableStateFlow<Load<GenerateResponse>?>(null)

    init {
        act { pantry.value = api.pantry() }
    }

    fun generate(request: GenerateRequest) {
        viewModelScope.launch {
            result.value = Load.Loading
            result.value = try {
                Load.Ready(api.generateRecipe(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failure = e.toAppFailure()
                Load.Failed(failure.message.orEmpty(), failure.code)
            }
        }
    }
}

private fun RecipeDetail.toSummary() = RecipeSummary(
    key = key,
    source = source,
    title = title,
    imageUrl = imageUrl,
    cookingMinutes = cookingMinutes,
    servings = servings,
    difficulty = difficulty,
    nutrition = nutrition,
    isAiGenerated = isAiGenerated,
    matchPercent = match.matchPercent,
    available = match.available,
    missing = match.missing,
    optionalMissing = match.optionalMissing,
    usesExpiring = match.usesExpiring,
)

@Composable
fun GenerateScreen(onBack: () -> Unit, onRecipe: (String) -> Unit, vm: GenerateVm = viewModel()) {
    CollectMessages(vm)
    val pantry by vm.pantry.collectAsState()
    val result by vm.result.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var extra by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val categories = listOf(
        stringResource(R.string.gen_cat_breakfast),
        stringResource(R.string.gen_cat_main),
        stringResource(R.string.gen_cat_snack),
        stringResource(R.string.gen_cat_soup),
        stringResource(R.string.gen_cat_dessert),
    )

    LaunchedEffect(pantry) { if (selected.isEmpty()) selected = pantry.map { it.nameEn }.toSet() }

    val ingredients = selected + extra.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val submit = {
        vm.generate(
            GenerateRequest(
                ingredients = ingredients.toList(),
                maxMinutes = minutes.toIntOrNull()?.takeIf { it > 0 },
                servings = servings.toIntOrNull()?.takeIf { it > 0 },
                category = category.ifBlank { null },
                notes = notes.trim().ifBlank { null },
                count = 3,
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(stringResource(R.string.ai_generator), onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).imePadding(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.ai_notice),
                    Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        extra,
                        { extra = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.extra_ingredients)) },
                        placeholder = { Text(stringResource(R.string.extra_ingredients_hint)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            minutes,
                            { minutes = it.filter(Char::isDigit) },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.max_minutes)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            servings,
                            { servings = it.filter(Char::isDigit) },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.servings)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            item {
                ChipRow {
                    categories.forEach { c ->
                        SelectChip(c, category == c) { category = if (category == c) "" else c }
                    }
                }
            }
            item {
                OutlinedTextField(
                    notes,
                    { notes = it },
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_notes)) },
                )
            }
            item {
                Button(
                    onClick = submit,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp),
                    enabled = ingredients.isNotEmpty() && result != Load.Loading,
                ) { Text(stringResource(R.string.generate_recipes)) }
            }
            when (val current = result) {
                null -> Unit
                Load.Loading -> item { LoadingState(Modifier.height(180.dp), stringResource(R.string.generating)) }
                is Load.Failed -> item { ErrorState(current.message, submit) }
                is Load.Ready -> items(current.value.recipes, key = { it.key }) { recipe ->
                    RecipeRow(recipe.toSummary(), { onRecipe(recipe.key) }, Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}
