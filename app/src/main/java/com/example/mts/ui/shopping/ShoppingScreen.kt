package com.example.mts.ui.shopping

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.ShoppingItem
import com.example.mts.data.ShoppingItemInput
import com.example.mts.data.ShoppingItemsRequest
import com.example.mts.data.ShoppingList
import com.example.mts.data.ShoppingListRequest
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.EmptyState
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.SelectChip
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ShoppingVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<List<ShoppingList>>>(Load.Loading)

    fun load() = state.load(silent = true) { api.shoppingLists() }

    fun addItem(list: ShoppingList, name: String) = act {
        api.addShoppingItems(list.id, ShoppingItemsRequest(listOf(ShoppingItemInput(name = name.trim()))))
        load()
    }

    fun togglePurchased(list: ShoppingList, item: ShoppingItem) = act {
        api.updateShoppingItem(list.id, item.id, buildJsonObject { put("isPurchased", !item.isPurchased) })
        load()
    }

    fun editItem(list: ShoppingList, item: ShoppingItem, name: String, quantity: Double?, unit: String?) = act {
        api.updateShoppingItem(
            list.id,
            item.id,
            buildJsonObject {
                put("name", name.trim())
                put("quantity", quantity)
                put("unit", unit)
            },
        )
        load()
    }

    fun deleteItem(list: ShoppingList, item: ShoppingItem) = act {
        api.deleteShoppingItem(list.id, item.id)
        load()
    }

    fun addList(name: String) = act {
        api.addShoppingList(ShoppingListRequest(name.trim()))
        load()
    }

    fun deleteList(list: ShoppingList) = act {
        api.deleteShoppingList(list.id)
        load()
    }
}

@Composable
fun ShoppingScreen(onBack: () -> Unit, vm: ShoppingVm = viewModel()) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var creatingList by remember { mutableStateOf(false) }
    var listName by remember { mutableStateOf("") }
    var deletingList by remember { mutableStateOf<ShoppingList?>(null) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var newItem by remember { mutableStateOf("") }
    val lists = (state as? Load.Ready)?.value.orEmpty()
    val list = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar(stringResource(R.string.shopping_list), onBack) {
                IconButton(onClick = { creatingList = true }) {
                    Icon(Icons.Filled.PlaylistAdd, stringResource(R.string.new_list))
                }
                if (list != null && lists.size > 1) {
                    IconButton(onClick = { deletingList = list }) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_list))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (lists.size > 1) {
                ChipRow {
                    lists.forEach { l -> SelectChip(l.name, l.id == list?.id) { selectedId = l.id } }
                }
                Spacer(Modifier.height(8.dp))
            }
            when (val current = state) {
                Load.Loading -> LoadingState()
                is Load.Failed -> ErrorState(current.message, vm::load)
                is Load.Ready -> if (list != null) {
                    val groups = list.items
                        .filter { !it.isPurchased }
                        .groupBy { it.category ?: "" }
                        .toSortedMap()
                    val purchased = list.items.filter { it.isPurchased }

                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (list.items.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Filled.ShoppingCart,
                                    stringResource(R.string.shopping_empty_title),
                                    stringResource(R.string.shopping_empty_body),
                                )
                            }
                        }
                        groups.forEach { (category, items) ->
                            if (category.isNotEmpty()) {
                                item { Text(category, style = MaterialTheme.typography.titleSmall) }
                            }
                            items(items, key = { it.id }) { item ->
                                ItemRow(item, { vm.togglePurchased(list, item) }, { editing = item }, { vm.deleteItem(list, item) })
                            }
                        }
                        if (purchased.isNotEmpty()) {
                            item { Text(stringResource(R.string.purchased), style = MaterialTheme.typography.titleSmall) }
                            items(purchased, key = { it.id }) { item ->
                                ItemRow(item, { vm.togglePurchased(list, item) }, { editing = item }, { vm.deleteItem(list, item) })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.add_shopping_item)) },
                        trailingIcon = {
                            IconButton(
                                enabled = newItem.isNotBlank(),
                                onClick = {
                                    vm.addItem(list, newItem)
                                    newItem = ""
                                },
                            ) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                        },
                    )
                }
            }
        }
    }

    if (creatingList) {
        AlertDialog(
            onDismissRequest = { creatingList = false },
            title = { Text(stringResource(R.string.new_list)) },
            text = {
                OutlinedTextField(listName, { listName = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = listName.isNotBlank(), onClick = {
                    vm.addList(listName)
                    listName = ""
                    creatingList = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { creatingList = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deletingList?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingList = null },
            title = { Text(stringResource(R.string.delete_list)) },
            text = { Text(stringResource(R.string.delete_list_body, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteList(target)
                    selectedId = null
                    deletingList = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingList = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    val currentList = list
    editing?.let { item ->
        if (currentList != null) {
            EditItemDialog(
                item = item,
                onDismiss = { editing = null },
                onSave = { name, quantity, unit ->
                    vm.editItem(currentList, item, name, quantity, unit)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.isPurchased, { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (item.isPurchased) TextDecoration.LineThrough else null,
                )
                val amount = listOfNotNull(item.quantity?.let(::formatQuantity), item.unit).joinToString(" ")
                if (amount.isNotEmpty()) {
                    Text(amount, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditItemDialog(item: ShoppingItem, onDismiss: () -> Unit, onSave: (String, Double?, String?) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity?.let(::formatQuantity).orEmpty()) }
    var unit by remember { mutableStateOf(item.unit.orEmpty()) }
    val parsed = quantity.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && (quantity.isBlank() || (parsed != null && parsed > 0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(
                    quantity,
                    { quantity = it },
                    label = { Text(stringResource(R.string.quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(unit, { unit = it }, label = { Text(stringResource(R.string.unit)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name, parsed, unit.trim().ifBlank { null }) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
