package com.example.mts.ui.expiry

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.ExpiringGroups
import com.example.mts.data.PantryItem
import com.example.mts.data.ScanHistoryEntry
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.EmptyState
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.ExpiryPill
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.formatDateLabel
import com.example.mts.ui.common.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow

class ExpiryVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<ExpiringGroups>>(Load.Loading)

    fun load() = state.load(silent = true) { api.expiring() }
}

@Composable
fun ExpiryScreen(onBack: () -> Unit, onFindRecipes: (List<String>) -> Unit, vm: ExpiryVm = viewModel()) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(stringResource(R.string.expiry_tracker), onBack) },
    ) { padding ->
        when (val current = state) {
            Load.Loading -> LoadingState(Modifier.padding(padding))
            is Load.Failed -> ErrorState(current.message, vm::load, Modifier.padding(padding))
            is Load.Ready -> {
                val groups = current.value
                val usable = groups.expiringToday + groups.expiringSoon
                if (usable.isEmpty() && groups.expired.isEmpty()) {
                    EmptyState(
                        Icons.Filled.NotificationsActive,
                        stringResource(R.string.expiry_empty_title),
                        stringResource(R.string.expiry_empty_body),
                        Modifier.padding(padding),
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (usable.isNotEmpty()) {
                            item {
                                Button(
                                    onClick = { onFindRecipes(usable.map { it.nameEn }) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                ) { Text(stringResource(R.string.recipes_for_expiring)) }
                            }
                        }
                        group(R.string.expires_today_title, groups.expiringToday)
                        group(R.string.expires_soon_title, groups.expiringSoon)
                        group(R.string.expired_title, groups.expired)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(title: Int, items: List<PantryItem>) {
    if (items.isEmpty()) return
    item { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) }
    items(items, key = { it.id }) { item ->
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            item.quantity?.let { "${formatQuantity(it)} ${item.unit}" },
                            item.expiresAt?.let(::formatDateLabel),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpiryPill(item.expiryStatus, item.expiresAt)
            }
        }
    }
}

class HistoryVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<List<ScanHistoryEntry>>>(Load.Loading)

    fun load() = state.load(silent = true) { api.history() }

    fun delete(entry: ScanHistoryEntry) = act {
        api.deleteHistory(entry.id)
        load()
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, vm: HistoryVm = viewModel()) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(stringResource(R.string.scan_history), onBack) },
    ) { padding ->
        when (val current = state) {
            Load.Loading -> LoadingState(Modifier.padding(padding))
            is Load.Failed -> ErrorState(current.message, vm::load, Modifier.padding(padding))
            is Load.Ready -> if (current.value.isEmpty()) {
                EmptyState(
                    Icons.Filled.History,
                    stringResource(R.string.history_empty_title),
                    stringResource(R.string.history_empty_body),
                    Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(current.value, key = { it.id }) { entry ->
                        SoftCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatDateLabel(entry.createdAt.take(10)), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        entry.ingredients.joinToString(", ") { it.name_id ?: it.name }
                                            .ifEmpty { stringResource(R.string.history_no_items) },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { vm.delete(entry) }) {
                                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
