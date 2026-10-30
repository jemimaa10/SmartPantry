package com.example.mts.ui.pantry

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.Category
import com.example.mts.data.ExpiryStatus
import com.example.mts.data.PantryItem
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.EmptyState
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.ExpiryPill
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.ScreenTitle
import com.example.mts.ui.common.SelectChip
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.formatQuantity
import com.example.mts.ui.theme.Leaf
import com.example.mts.ui.theme.TomatoSoft
import kotlinx.coroutines.flow.MutableStateFlow

class PantryVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<List<PantryItem>>>(Load.Loading)
    val categories = MutableStateFlow<List<Category>>(emptyList())

    fun load() {
        state.load(silent = true) { api.pantry() }
        act { if (categories.value.isEmpty()) categories.value = api.categories() }
    }

    fun delete(item: PantryItem) = act {
        api.deletePantryItem(item.id)
        load()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantryScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onFindRecipes: (List<String>) -> Unit,
    vm: PantryVm = viewModel(),
) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()
    val categories by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<PantryItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selected.isEmpty()) {
                FloatingActionButton(onAdd, containerColor = Leaf, contentColor = Color.White) {
                    Icon(Icons.Filled.Add, stringResource(R.string.add_ingredient))
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = { onFindRecipes(selected.toList()) },
                    icon = { Icon(Icons.Filled.RestaurantMenu, null) },
                    text = { Text(stringResource(R.string.find_recipes_with, selected.size)) },
                    containerColor = Leaf,
                    contentColor = Color.White,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(12.dp))
            ScreenTitle(stringResource(R.string.nav_pantry))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.pantry_hint),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                query,
                { query = it },
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_ingredients)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = CircleShape,
            )
            Spacer(Modifier.height(10.dp))
            ChipRow {
                SelectChip(stringResource(R.string.all), category == null) { category = null }
                categories.forEach { c ->
                    SelectChip(c.nameId, category == c.slug) { category = if (category == c.slug) null else c.slug }
                }
            }
            Spacer(Modifier.height(8.dp))

            when (val current = state) {
                Load.Loading -> LoadingState()
                is Load.Failed -> ErrorState(current.message, vm::load)
                is Load.Ready -> {
                    val needle = query.trim().lowercase()
                    val visible = current.value.filter {
                        (category == null || it.category.slug == category) &&
                            (needle.isEmpty() || it.name.lowercase().contains(needle) || it.nameEn.contains(needle))
                    }
                    if (visible.isEmpty()) {
                        EmptyState(
                            Icons.Filled.Inventory2,
                            stringResource(R.string.pantry_empty_title),
                            stringResource(R.string.pantry_empty_body),
                        ) {
                            Button(onClick = onAdd) { Text(stringResource(R.string.add_ingredient)) }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.id }) { item ->
                                PantryRow(
                                    item = item,
                                    selected = item.nameEn in selected,
                                    selectionActive = selected.isNotEmpty(),
                                    onToggle = {
                                        selected = if (item.nameEn in selected) selected - item.nameEn else selected + item.nameEn
                                    },
                                    onEdit = { onEdit(item.id) },
                                    onDelete = { pendingDelete = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_ingredient_title)) },
            text = { Text(stringResource(R.string.delete_ingredient_body, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(item)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantryRow(
    item: PantryItem,
    selected: Boolean,
    selectionActive: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = if (item.expiryStatus == ExpiryStatus.EXPIRED) TomatoSoft else MaterialTheme.colorScheme.surface
    SoftCard(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selectionActive) onToggle() else onEdit() },
            onLongClick = onToggle,
        ),
        color = color,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (selected) Leaf else MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .combinedClickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, stringResource(R.string.selected), tint = Color.White)
                } else {
                    Text(item.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Leaf)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        item.quantity?.let { "${formatQuantity(it)} ${item.unit}" },
                        item.category.nameId,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExpiryPill(item.expiryStatus, item.expiresAt)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
