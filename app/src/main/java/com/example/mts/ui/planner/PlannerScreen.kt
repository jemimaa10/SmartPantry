package com.example.mts.ui.planner

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.MealPlanItem
import com.example.mts.data.MealPlanRequest
import com.example.mts.data.MealWeek
import com.example.mts.data.RecipeSummary
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.RecipeImage
import com.example.mts.ui.common.RecipeRow
import com.example.mts.ui.common.ScreenTitle
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.addDays
import com.example.mts.ui.common.formatDateLabel
import com.example.mts.ui.common.formatDayLabel
import com.example.mts.ui.theme.Leaf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PlannerVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<MealWeek>>(Load.Loading)
    private var weekStart: String? = null

    fun load() = state.load(silent = true) {
        api.mealWeek(weekStart).also { weekStart = it.weekStart }
    }

    fun shiftWeek(days: Int) {
        weekStart = weekStart?.let { addDays(it, days) }
        load()
    }

    fun remove(item: MealPlanItem) = act {
        api.removeMeal(item.id)
        load()
    }
}

private val mealTypes = listOf("BREAKFAST", "LUNCH", "DINNER")

@Composable
private fun mealLabel(type: String) = stringResource(
    when (type) {
        "BREAKFAST" -> R.string.breakfast
        "LUNCH" -> R.string.lunch
        else -> R.string.dinner
    },
)

@Composable
fun PlannerScreen(onPick: (String, String) -> Unit, onRecipe: (String) -> Unit, vm: PlannerVm = viewModel()) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.size(12.dp))
        ScreenTitle(stringResource(R.string.nav_planner))
        when (val current = state) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(current.message, vm::load)
            is Load.Ready -> {
                val week = current.value
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftWeek(-7) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_week))
                    }
                    Text(
                        "${formatDateLabel(week.weekStart)} – ${formatDateLabel(week.weekEnd)}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.shiftWeek(7) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_week))
                    }
                }
                val days = (0..6).map { addDays(week.weekStart, it) }
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(days, key = { it }) { day ->
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(formatDayLabel(day), style = MaterialTheme.typography.titleMedium)
                                mealTypes.forEach { type ->
                                    MealSlot(
                                        label = mealLabel(type),
                                        item = week.items.firstOrNull { it.date == day && it.mealType == type },
                                        onAdd = { onPick(day, type) },
                                        onOpen = onRecipe,
                                        onRemove = vm::remove,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealSlot(
    label: String,
    item: MealPlanItem?,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (MealPlanItem) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(64.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item == null) {
            Row(Modifier.weight(1f).clickable(onClick = onAdd).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = Leaf, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_meal), color = Leaf, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Row(
                Modifier.weight(1f).clickable { onOpen(item.recipe.key) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecipeImage(item.recipe.imageUrl, Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.width(8.dp))
                Text(item.recipe.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.replace_meal)) }
            IconButton(onClick = { onRemove(item) }) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
        }
    }
}

class MealPickerVm(app: Application, handle: SavedStateHandle) : BaseVm(app) {
    val date: String = checkNotNull(handle["date"])
    val mealType: String = checkNotNull(handle["mealType"])
    val state = MutableStateFlow<Load<List<RecipeSummary>>>(Load.Loading)
    val done = MutableStateFlow(false)

    fun loadSuggestions() = state.load {
        val favorites = api.favorites().map { it.recipe }
        (favorites + api.recommendations().recipes).distinctBy { it.key }
    }

    fun search(query: String) {
        if (query.isBlank()) loadSuggestions() else state.load { api.searchRecipes(query.trim()).recipes }
    }

    fun pick(recipe: RecipeSummary) = act {
        api.planMeal(MealPlanRequest(recipe.key, date, mealType))
        done.value = true
    }
}

@Composable
fun MealPickerScreen(onBack: () -> Unit, onDone: () -> Unit, vm: MealPickerVm = viewModel()) {
    CollectMessages(vm)
    val state by vm.state.collectAsState()
    val done by vm.done.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadSuggestions() }
    LaunchedEffect(done) { if (done) onDone() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar("${mealLabel(vm.mealType)}, ${formatDayLabel(vm.date)}", onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.discover_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            )
            when (val current = state) {
                Load.Loading -> LoadingState()
                is Load.Failed -> ErrorState(current.message, { vm.search(query) })
                is Load.Ready -> LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (current.value.isEmpty()) {
                        item { Text(stringResource(R.string.picker_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(current.value, key = { it.key }) { recipe ->
                        RecipeRow(recipe, { vm.pick(recipe) })
                    }
                }
            }
        }
    }
}
