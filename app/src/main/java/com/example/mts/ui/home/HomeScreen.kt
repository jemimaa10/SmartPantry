package com.example.mts.ui.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.ExpiringGroups
import com.example.mts.data.FindResult
import com.example.mts.data.PantryItem
import com.example.mts.data.RecipeSummary
import com.example.mts.data.UserMe
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.ChipRow
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.OnResume
import com.example.mts.ui.common.RecipeTile
import com.example.mts.ui.common.SectionHeader
import com.example.mts.ui.common.SelectChip
import com.example.mts.ui.common.SoftCard
import com.example.mts.ui.theme.Carrot
import com.example.mts.ui.theme.Leaf
import com.example.mts.ui.theme.LeafDark
import kotlinx.coroutines.flow.MutableStateFlow

data class HomeData(
    val me: UserMe,
    val pantry: List<PantryItem>,
    val expiring: ExpiringGroups,
    val recent: List<RecipeSummary>,
)

class HomeVm(app: Application) : BaseVm(app) {
    val state = MutableStateFlow<Load<HomeData>>(Load.Loading)
    val recommended = MutableStateFlow<Load<FindResult>>(Load.Loading)

    fun load() {
        state.load(silent = true) {
            HomeData(api.me(), api.pantry(), api.expiring(), api.recentlyViewed())
        }
        // Recipes hit external APIs, so a slow source should not hold up the rest of the screen.
        recommended.load(silent = true) { api.recommendations() }
    }
}

@Composable
fun HomeScreen(
    onSearch: (String) -> Unit,
    onScan: () -> Unit,
    onAddManual: () -> Unit,
    onPantry: () -> Unit,
    onRecipe: (String) -> Unit,
    onExpiry: () -> Unit,
    onShopping: () -> Unit,
    vm: HomeVm = viewModel(),
) {
    CollectMessages(vm)
    OnResume { vm.load() }
    val state by vm.state.collectAsState()
    val recommended by vm.recommended.collectAsState()
    val data = (state as? Load.Ready)?.value
    val recipes = (recommended as? Load.Ready)?.value?.recipes.orEmpty()
    val expiringRecipes = recipes.filter { it.usesExpiring.isNotEmpty() }
    val categories = listOf(
        R.string.cat_chicken to "chicken",
        R.string.cat_seafood to "seafood",
        R.string.cat_vegetarian to "vegetarian",
        R.string.cat_breakfast to "breakfast",
        R.string.cat_dessert to "dessert",
        R.string.cat_soup to "soup",
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_hello),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        data?.me?.displayName ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                val alerts = data?.expiring?.let { it.expired.size + it.expiringToday.size + it.expiringSoon.size } ?: 0
                IconButton(onClick = onShopping) {
                    Icon(Icons.Filled.ShoppingCart, stringResource(R.string.shopping_list))
                }
                IconButton(onClick = onExpiry) {
                    BadgedBox(badge = { if (alerts > 0) Badge { Text("$alerts") } }) {
                        Icon(Icons.Filled.Notifications, stringResource(R.string.expiry_tracker))
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable { onSearch("") }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.home_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Leaf, LeafDark)), MaterialTheme.shapes.large)
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.home_scan_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_scan_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onScan,
                        colors = ButtonDefaults.buttonColors(containerColor = Carrot),
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_ingredients))
                    }
                    OutlinedButton(
                        onClick = onAddManual,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.manual))
                    }
                }
            }
        }

        if (data != null) {
            item {
                SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable(onClick = onPantry)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Inventory2, null, tint = Leaf) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.home_pantry_count, data.pantry.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                data.pantry.take(4).joinToString(", ") { it.name }
                                    .ifEmpty { stringResource(R.string.home_pantry_empty) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        val attention = data.expiring.expired.size + data.expiring.expiringToday.size
                        if (attention > 0) Icon(Icons.Filled.Warning, null, tint = Carrot)
                    }
                }
            }
        }

        item {
            ChipRow {
                categories.forEach { (label, query) ->
                    SelectChip(stringResource(label), selected = false) { onSearch(query) }
                }
            }
        }

        if (expiringRecipes.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(stringResource(R.string.home_use_soon))
                    Spacer(Modifier.height(8.dp))
                    RecipeCarousel(expiringRecipes, onRecipe)
                }
            }
        }

        item {
            Column {
                SectionHeader(stringResource(R.string.home_recommended), stringResource(R.string.see_all)) { onSearch("") }
                Spacer(Modifier.height(8.dp))
                when (val current = recommended) {
                    Load.Loading -> Text(
                        stringResource(R.string.loading_recipes),
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is Load.Failed -> Text(
                        current.message,
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is Load.Ready ->
                        if (current.value.recipes.isEmpty()) {
                            Text(
                                stringResource(R.string.home_no_recommendations),
                                Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            RecipeCarousel(current.value.recipes, onRecipe)
                        }
                }
            }
        }

        if (!data?.recent.isNullOrEmpty()) {
            item {
                Column {
                    SectionHeader(stringResource(R.string.home_recent))
                    Spacer(Modifier.height(8.dp))
                    RecipeCarousel(data.recent, onRecipe)
                }
            }
        }
    }
}

@Composable
private fun RecipeCarousel(recipes: List<RecipeSummary>, onRecipe: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recipes, key = { it.key }) { recipe ->
            RecipeTile(recipe) { onRecipe(recipe.key) }
        }
    }
}
