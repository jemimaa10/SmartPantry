package com.example.mts.ui.pantry

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.CatalogIngredient
import com.example.mts.data.Category
import com.example.mts.data.PantryInput
import com.example.mts.data.PantryItem
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.SelectChip
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.formatDateLabel
import com.example.mts.ui.common.formatQuantity
import com.example.mts.ui.common.isoToMillis
import com.example.mts.ui.common.millisToIso
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class IngredientFormVm(app: Application, handle: SavedStateHandle) : BaseVm(app) {
    val itemId: String? = handle["itemId"]
    val existing = MutableStateFlow<Load<PantryItem?>>(Load.Loading)
    val categories = MutableStateFlow<List<Category>>(emptyList())
    val suggestions = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    val saved = MutableStateFlow(false)
    private var searchJob: Job? = null

    init {
        existing.load { itemId?.let { id -> api.pantry().firstOrNull { it.id == id } } }
        act { categories.value = api.categories() }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            suggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            runCatching { api.ingredients(query.trim()) }.onSuccess { suggestions.value = it }
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        suggestions.value = emptyList()
    }

    fun save(input: PantryInput) = act {
        val id = itemId
        if (id != null) {
            // Built by hand so cleared fields are sent as null rather than omitted.
            api.updatePantryItem(
                id,
                buildJsonObject {
                    put("quantity", input.quantity)
                    input.unit?.let { put("unit", it) }
                    put("expiresAt", input.expiresAt)
                    put("notes", input.notes)
                },
            )
        } else {
            api.addPantryItem(input)
        }
        saved.value = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientFormScreen(onDone: () -> Unit, vm: IngredientFormVm = viewModel()) {
    CollectMessages(vm)
    val existing by vm.existing.collectAsState()
    val saved by vm.saved.collectAsState()
    LaunchedEffect(saved) { if (saved) onDone() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackBar(
                stringResource(if (vm.itemId == null) R.string.add_ingredient else R.string.edit_ingredient),
                onDone,
            )
        },
    ) { padding ->
        when (val current = existing) {
            is Load.Ready -> Form(current.value, vm, PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
            is Load.Failed -> Text(current.message, Modifier.padding(padding).padding(24.dp))
            Load.Loading -> LoadingState(Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Form(item: PantryItem?, vm: IngredientFormVm, padding: PaddingValues) {
    val categories by vm.categories.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var ingredientId by remember { mutableStateOf(item?.ingredientId) }
    var category by remember { mutableStateOf(item?.category?.slug) }
    var quantity by remember { mutableStateOf(item?.quantity?.let(::formatQuantity).orEmpty()) }
    var unit by remember { mutableStateOf(item?.unit.orEmpty()) }
    var expiresAt by remember { mutableStateOf(item?.expiresAt) }
    var notes by remember { mutableStateOf(item?.notes.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    val units = listOf("butir", "buah", "gram", "kg", "ml", "liter", "sdm", "sdt", "siung", "ikat")
    val quantityValue = quantity.replace(',', '.').toDoubleOrNull()
    val quantityInvalid = quantity.isNotBlank() && (quantityValue == null || quantityValue <= 0)

    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                ingredientId = null
                vm.search(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ingredient_name)) },
            placeholder = { Text(stringResource(R.string.ingredient_name_hint)) },
            singleLine = true,
            enabled = item == null,
        )
        if (suggestions.isNotEmpty()) {
            SoftCard(Modifier.fillMaxWidth()) {
                Column {
                    suggestions.forEachIndexed { index, s ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                name = s.nameId
                                ingredientId = s.id
                                category = s.category.slug
                                if (unit.isBlank()) unit = s.defaultUnit
                                vm.clearSuggestions()
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(s.nameId, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${s.nameEn} · ${s.category.nameId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (item == null) {
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
            ChipRow(PaddingValues(0.dp)) {
                categories.forEach { c ->
                    SelectChip(c.nameId, category == c.slug) { category = c.slug }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                quantity,
                { quantity = it },
                Modifier.weight(1f),
                label = { Text(stringResource(R.string.quantity)) },
                singleLine = true,
                isError = quantityInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                unit,
                { unit = it },
                Modifier.weight(1f),
                label = { Text(stringResource(R.string.unit)) },
                singleLine = true,
            )
        }
        ChipRow(PaddingValues(0.dp)) {
            units.forEach { u -> SelectChip(u, unit == u) { unit = u } }
        }

        OutlinedTextField(
            value = expiresAt?.let(::formatDateLabel).orEmpty(),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text(stringResource(R.string.expiry_date)) },
            placeholder = { Text(stringResource(R.string.expiry_none)) },
            trailingIcon = {
                if (expiresAt != null) {
                    IconButton(onClick = { expiresAt = null }) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                } else {
                    IconButton(onClick = { pickingDate = true }) {
                        Icon(Icons.Filled.CalendarMonth, stringResource(R.string.expiry_date))
                    }
                }
            },
        )
        OutlinedTextField(
            notes,
            { notes = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.notes)) },
            minLines = 2,
        )
        Button(
            onClick = {
                vm.save(
                    PantryInput(
                        ingredientId = ingredientId,
                        name = name.trim().takeIf { ingredientId == null },
                        categorySlug = category,
                        quantity = quantityValue,
                        unit = unit.trim().ifBlank { null },
                        expiresAt = expiresAt,
                        notes = notes.trim().ifBlank { null },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = name.isNotBlank() && !quantityInvalid,
        ) { Text(stringResource(R.string.save)) }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expiresAt?.let(::isoToMillis))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { expiresAt = millisToIso(it) }
                    pickingDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { DatePicker(state) }
    }
}
