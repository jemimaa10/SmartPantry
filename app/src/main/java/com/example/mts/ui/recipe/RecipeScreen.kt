package com.example.mts.ui.recipe

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.FavoriteRequest
import com.example.mts.data.MealPlanRequest
import com.example.mts.data.RecipeDetail
import com.example.mts.data.RecipeIngredient
import com.example.mts.data.ShoppingItemInput
import com.example.mts.data.ShoppingItemsRequest
import com.example.mts.data.ShoppingListRequest
import com.example.mts.data.SubstituteRequest
import com.example.mts.data.Substitution
import com.example.mts.ui.common.AiPill
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.MatchPill
import com.example.mts.ui.common.MetaRow
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.Pill
import com.example.mts.ui.common.RecipeImage
import com.example.mts.ui.common.SelectChip
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.formatQuantity
import com.example.mts.ui.common.millisToIso
import com.example.mts.ui.theme.Carrot
import com.example.mts.ui.theme.CarrotSoft
import com.example.mts.ui.theme.Honey
import com.example.mts.ui.theme.HoneySoft
import com.example.mts.ui.theme.Leaf
import com.example.mts.ui.theme.LeafSoft
import com.example.mts.ui.theme.Tomato
import com.example.mts.ui.theme.TomatoSoft
import kotlinx.coroutines.flow.MutableStateFlow

class RecipeVm(app: Application, handle: SavedStateHandle) : BaseVm(app) {
    val key: String = checkNotNull(handle["key"])
    val state = MutableStateFlow<Load<RecipeDetail>>(Load.Loading)
    val substitutions = MutableStateFlow<Load<List<Substitution>>?>(null)

    fun load() = state.load(silent = true) { api.recipe(key) }

    fun toggleFavorite(recipe: RecipeDetail, added: String, removed: String) = act {
        val favoriteId = recipe.favoriteId
        if (favoriteId != null) {
            api.removeFavorite(favoriteId)
            messages.emit(removed)
        } else {
            api.addFavorite(FavoriteRequest(recipe.key))
            messages.emit(added)
        }
        load()
    }

    fun addMissingToShopping(recipe: RecipeDetail, servingScale: Double, listName: String, done: String) = act {
        val list = api.shoppingLists().firstOrNull() ?: api.addShoppingList(ShoppingListRequest(listName))
        val items = recipe.ingredients.filter { it.required && !it.available }.map {
            ShoppingItemInput(
                name = it.displayName ?: it.name,
                quantity = it.quantity?.let { q -> q * servingScale },
                unit = it.unit,
                recipeKey = recipe.key,
            )
        }
        api.addShoppingItems(list.id, ShoppingItemsRequest(items))
        messages.emit(done)
    }

    fun loadSubstitutions(recipe: RecipeDetail) {
        val missing = recipe.ingredients.filter { !it.available }.map { it.displayName ?: it.name }
        substitutions.value = Load.Loading
        act {
            substitutions.value = try {
                Load.Ready(api.substitute(SubstituteRequest(recipe.title, missing)).substitutions)
            } catch (e: Throwable) {
                substitutions.value = null
                throw e
            }
        }
    }

    fun dismissSubstitutions() {
        substitutions.value = null
    }

    fun addToPlan(recipe: RecipeDetail, date: String, mealType: String, done: String) = act {
        api.planMeal(MealPlanRequest(recipe.key, date, mealType))
        messages.emit(done)
    }
}

@Composable
fun RecipeScreen(onBack: () -> Unit, onCook: (String) -> Unit, vm: RecipeVm = viewModel()) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()

    when (val current = state) {
        Load.Loading -> LoadingState()
        is Load.Failed -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            ErrorState(current.message, vm::load)
        }
        is Load.Ready -> Detail(current.value, vm, onBack, onCook)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Detail(recipe: RecipeDetail, vm: RecipeVm, onBack: () -> Unit, onCook: (String) -> Unit) {
    val baseServings = recipe.servings?.takeIf { it > 0 }
    var servings by remember(recipe.key) { mutableIntStateOf(baseServings ?: 1) }
    val scale = if (baseServings != null) servings.toDouble() / baseServings else 1.0
    val substitutions by vm.substitutions.collectAsState()
    var planning by remember { mutableStateOf(false) }
    val favoriteAdded = stringResource(R.string.favorite_added)
    val favoriteRemoved = stringResource(R.string.favorite_removed)
    val listName = stringResource(R.string.shopping_list)
    val addedToList = stringResource(R.string.added_to_shopping)
    val addedToPlan = stringResource(R.string.added_to_plan)
    val missingRequired = recipe.ingredients.any { it.required && !it.available }
    val hasUnavailable = recipe.ingredients.any { !it.available }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box {
                RecipeImage(recipe.imageUrl, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundIcon(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    RoundIcon({ vm.toggleFavorite(recipe, favoriteAdded, favoriteRemoved) }) {
                        if (recipe.favoriteId != null) {
                            Icon(Icons.Filled.Favorite, stringResource(R.string.remove_favorite), tint = Tomato)
                        } else {
                            Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.save_favorite))
                        }
                    }
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MatchPill(recipe.match.matchPercent)
                        if (recipe.isAiGenerated) AiPill()
                    }
                    Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
                    MetaRow(recipe.cookingMinutes, recipe.servings, recipe.difficulty)
                    recipe.description?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (recipe.isAiGenerated) {
                        Text(
                            stringResource(R.string.ai_disclaimer),
                            style = MaterialTheme.typography.labelMedium,
                            color = Carrot,
                        )
                    }
                    if (recipe.match.usesExpiring.isNotEmpty()) {
                        Pill(stringResource(R.string.uses_expiring), HoneySoft, Honey)
                    }
                }

                ChipRow(androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    SelectChip(stringResource(R.string.plan_meal), false) { planning = true }
                    if (hasUnavailable) {
                        SelectChip(stringResource(R.string.find_substitutes), false) { vm.loadSubstitutions(recipe) }
                    }
                }

                Section(stringResource(R.string.ingredients)) {
                    if (baseServings != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.servings), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { if (servings > 1) servings -= 1 }, enabled = servings > 1) {
                                Icon(Icons.Filled.Remove, stringResource(R.string.decrease_servings))
                            }
                            Text("$servings", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { if (servings < 50) servings += 1 }) {
                                Icon(Icons.Filled.Add, stringResource(R.string.increase_servings))
                            }
                        }
                    }
                    recipe.ingredients.forEach { IngredientRow(it, scale) }
                    if (missingRequired) {
                        OutlinedButton(
                            onClick = { vm.addMissingToShopping(recipe, scale, listName, addedToList) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.AddShoppingCart, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_missing_to_shopping))
                        }
                    }
                }

                Section(stringResource(R.string.instructions)) {
                    if (recipe.instructions.isEmpty()) {
                        Text(stringResource(R.string.no_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    recipe.instructions.forEach { step ->
                        Row {
                            Box(
                                Modifier.size(28.dp).background(LeafSoft, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${step.step}", style = MaterialTheme.typography.labelMedium, color = Leaf)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(step.text, Modifier.weight(1f))
                        }
                    }
                }

                Section(stringResource(R.string.nutrition)) { NutritionBlock(recipe) }
            }
        }

        if (recipe.instructions.isNotEmpty()) {
            Button(
                onClick = { onCook(recipe.key) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.start_cooking))
            }
        }
    }

    substitutions?.let { current ->
        ModalBottomSheet(onDismissRequest = vm::dismissSubstitutions) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.find_substitutes), style = MaterialTheme.typography.headlineSmall)
                when (current) {
                    Load.Loading -> LoadingState(Modifier.height(160.dp))
                    is Load.Failed -> Text(current.message)
                    is Load.Ready -> current.value.forEach { SubstitutionCard(it) }
                }
            }
        }
    }

    if (planning) {
        PlanDialog(
            onDismiss = { planning = false },
            onPlan = { date, mealType -> vm.addToPlan(recipe, date, mealType, addedToPlan) },
        )
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun IngredientRow(ingredient: RecipeIngredient, scale: Double) {
    val amount = ingredient.quantity?.let { formatQuantity(it * scale) }
    val label = listOfNotNull(amount, ingredient.unit, ingredient.displayName ?: ingredient.name).joinToString(" ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ingredient.available) {
            Icon(Icons.Filled.CheckCircle, stringResource(R.string.available), tint = Leaf, modifier = Modifier.size(20.dp))
        } else {
            Icon(
                Icons.Outlined.Circle,
                stringResource(R.string.not_available),
                tint = if (ingredient.required) Tomato else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f))
        if (!ingredient.required) {
            Pill(stringResource(R.string.optional), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!ingredient.available) {
            Pill(stringResource(R.string.missing), TomatoSoft, Tomato)
        }
    }
}

@Composable
private fun NutritionBlock(recipe: RecipeDetail) {
    val nutrition = recipe.nutrition
    if (nutrition == null) {
        Text(stringResource(R.string.nutrition_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rows = listOf(
        stringResource(R.string.calories) to nutrition.calories?.let { "${formatQuantity(it)} kkal" },
        stringResource(R.string.protein) to nutrition.protein?.let { "${formatQuantity(it)} g" },
        stringResource(R.string.carbohydrates) to nutrition.carbohydrates?.let { "${formatQuantity(it)} g" },
        stringResource(R.string.fat) to nutrition.fat?.let { "${formatQuantity(it)} g" },
    )
    rows.forEach { (label, value) ->
        Row {
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: stringResource(R.string.not_available_short), style = MaterialTheme.typography.titleSmall)
        }
    }
    Text(
        stringResource(if (recipe.nutritionIsEstimate) R.string.nutrition_estimate else R.string.nutrition_per_serving),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubstitutionCard(item: Substitution) {
    val (label, background, content) = when (item.suitability) {
        "good" -> Triple(stringResource(R.string.suit_good), LeafSoft, Leaf)
        "not_recommended" -> Triple(stringResource(R.string.suit_bad), TomatoSoft, Tomato)
        else -> Triple(stringResource(R.string.suit_ok), CarrotSoft, Carrot)
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SwapHoriz, null, tint = Leaf)
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(item.missing_ingredient, item.substitute).joinToString(" → "),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Pill(label, background, content)
            if (item.how_to_substitute.isNotBlank()) Text(item.how_to_substitute)
            if (item.taste_texture_impact.isNotBlank()) {
                Text(item.taste_texture_impact, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.safety_note?.let { Text(it, color = Tomato, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDialog(onDismiss: () -> Unit, onPlan: (String, String) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var mealType by remember { mutableStateOf("DINNER") }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPlan(millisToIso(it), mealType) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.add_to_plan)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ChipRow {
                SelectChip(stringResource(R.string.breakfast), mealType == "BREAKFAST") { mealType = "BREAKFAST" }
                SelectChip(stringResource(R.string.lunch), mealType == "LUNCH") { mealType = "LUNCH" }
                SelectChip(stringResource(R.string.dinner), mealType == "DINNER") { mealType = "DINNER" }
            }
            DatePicker(state, title = null, headline = null, showModeToggle = false)
        }
    }
}
