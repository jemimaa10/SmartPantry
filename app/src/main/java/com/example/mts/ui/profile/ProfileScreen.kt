package com.example.mts.ui.profile

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.mts.R
import com.example.mts.data.NotificationPrefs
import com.example.mts.data.RefreshRequest
import com.example.mts.data.UpdatePreferences
import com.example.mts.data.UpdateProfile
import com.example.mts.data.UserMe
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.MenuRow
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.ScreenTitle
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.common.TagEditor
import com.example.mts.ui.theme.Leaf
import kotlinx.coroutines.flow.MutableStateFlow

class ProfileVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<UserMe>>(Load.Loading)
    val saved = MutableStateFlow(false)

    fun load() = state.load(silent = true) { api.me() }

    fun updateName(name: String) = act {
        state.value = Load.Ready(api.updateMe(UpdateProfile(name.trim())))
    }

    fun savePreferences(preferences: UpdatePreferences, done: String) = act {
        state.value = Load.Ready(api.updatePreferences(preferences))
        messages.emit(done)
        saved.value = true
    }

    fun logout() = act {
        val tokens = pantryApp.tokens
        // The local session ends even if the server cannot be reached.
        tokens.refreshToken()?.let { runCatching { api.logout(RefreshRequest(it)) } }
        tokens.clear()
    }
}

@Composable
fun ProfileScreen(
    onPreferences: () -> Unit,
    onFavorites: () -> Unit,
    onShopping: () -> Unit,
    onHistory: () -> Unit,
    onExpiry: () -> Unit,
    vm: ProfileVm = viewModel(),
) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()
    var editingName by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    when (val current = state) {
        Load.Loading -> LoadingState()
        is Load.Failed -> ErrorState(current.message, vm::load)
        is Load.Ready -> {
            val me = current.value
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                ScreenTitle(stringResource(R.string.nav_profile))
                Spacer(Modifier.height(16.dp))
                SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (me.avatarUrl != null) {
                            AsyncImage(me.avatarUrl, null, Modifier.size(56.dp).clip(CircleShape))
                        } else {
                            Box(Modifier.size(56.dp).background(Leaf, CircleShape), contentAlignment = Alignment.Center) {
                                Text(
                                    me.displayName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(me.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(me.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { editingName = true }) { Text(stringResource(R.string.edit)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MenuRow(Icons.Filled.Tune, stringResource(R.string.preferences), onPreferences)
                MenuRow(Icons.Filled.Favorite, stringResource(R.string.favorites), onFavorites)
                MenuRow(Icons.Filled.ShoppingCart, stringResource(R.string.shopping_list), onShopping)
                MenuRow(Icons.Filled.NotificationsActive, stringResource(R.string.expiry_tracker), onExpiry)
                MenuRow(Icons.Filled.History, stringResource(R.string.scan_history), onHistory)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.logout), { confirmLogout = true })
            }

            if (editingName) {
                var name by remember { mutableStateOf(me.displayName) }
                AlertDialog(
                    onDismissRequest = { editingName = false },
                    title = { Text(stringResource(R.string.edit_name)) },
                    text = { OutlinedTextField(name, { name = it }, singleLine = true) },
                    confirmButton = {
                        TextButton(enabled = name.isNotBlank(), onClick = {
                            vm.updateName(name)
                            editingName = false
                        }) { Text(stringResource(R.string.save)) }
                    },
                    dismissButton = { TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }

            if (confirmLogout) {
                AlertDialog(
                    onDismissRequest = { confirmLogout = false },
                    title = { Text(stringResource(R.string.logout)) },
                    text = { Text(stringResource(R.string.logout_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmLogout = false
                            vm.logout()
                        }) { Text(stringResource(R.string.logout)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

@Composable
fun PreferencesScreen(onBack: () -> Unit, vm: ProfileVm = viewModel()) {
    CollectMessages(vm)
    val state by vm.state.collectAsState()
    val saved by vm.saved.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(saved) { if (saved) onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(stringResource(R.string.preferences), onBack) },
    ) { padding ->
        when (val current = state) {
            Load.Loading -> LoadingState(Modifier.padding(padding))
            is Load.Failed -> ErrorState(current.message, vm::load, Modifier.padding(padding))
            is Load.Ready -> PreferencesForm(current.value, vm, PaddingValues(top = padding.calculateTopPadding()))
        }
    }
}

@Composable
private fun PreferencesForm(me: UserMe, vm: ProfileVm, padding: PaddingValues) {
    var dietary by remember { mutableStateOf(me.dietaryPreferences) }
    var allergies by remember { mutableStateOf(me.allergies) }
    var disliked by remember { mutableStateOf(me.dislikedIngredients) }
    var expiryEnabled by remember { mutableStateOf(me.notifications.expiryEnabled) }
    var expiryDays by remember { mutableStateOf(me.notifications.expiryDaysBefore) }
    var mealEnabled by remember { mutableStateOf(me.notifications.mealPlanEnabled) }
    val doneMessage = stringResource(R.string.preferences_saved)
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun enableWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TagEditor(stringResource(R.string.dietary_preferences), dietary, { dietary = it })
        TagEditor(stringResource(R.string.allergies), allergies, { allergies = it })
        Text(
            stringResource(R.string.allergy_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TagEditor(stringResource(R.string.disliked_ingredients), disliked, { disliked = it })

        Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notify_expiry), Modifier.weight(1f))
            Switch(expiryEnabled, {
                expiryEnabled = it
                if (it) enableWithPermission()
            })
        }
        if (expiryEnabled) {
            Text(stringResource(R.string.notify_days_before, expiryDays))
            Slider(
                value = expiryDays.toFloat(),
                onValueChange = { expiryDays = it.toInt() },
                valueRange = 1f..14f,
                steps = 12,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notify_meal_plan), Modifier.weight(1f))
            Switch(mealEnabled, {
                mealEnabled = it
                if (it) enableWithPermission()
            })
        }

        Button(
            onClick = {
                vm.savePreferences(
                    UpdatePreferences(
                        dietaryPreferences = dietary,
                        allergies = allergies,
                        dislikedIngredients = disliked,
                        notifications = NotificationPrefs(expiryEnabled, expiryDays, mealEnabled),
                    ),
                    doneMessage,
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.save)) }
    }
}
