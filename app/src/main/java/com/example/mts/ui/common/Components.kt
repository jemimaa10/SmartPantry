package com.example.mts.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.mts.R
import com.example.mts.data.ExpiryStatus
import com.example.mts.data.RecipeSummary
import com.example.mts.ui.theme.Carrot
import com.example.mts.ui.theme.CarrotSoft
import com.example.mts.ui.theme.CreamDeep
import com.example.mts.ui.theme.Honey
import com.example.mts.ui.theme.HoneySoft
import com.example.mts.ui.theme.Leaf
import com.example.mts.ui.theme.LeafSoft
import com.example.mts.ui.theme.Tomato
import com.example.mts.ui.theme.TomatoSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        if (label != null) {
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(LeafSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Leaf, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
fun MatchPill(percent: Int, modifier: Modifier = Modifier) {
    val (background, content) = when {
        percent >= 80 -> LeafSoft to Leaf
        percent >= 50 -> HoneySoft to Honey
        else -> CarrotSoft to Carrot
    }
    Pill(stringResource(R.string.match_percent, percent), background, content, modifier)
}

@Composable
fun AiPill(modifier: Modifier = Modifier) {
    Pill(stringResource(R.string.ai_generated), CarrotSoft, Carrot, modifier, Icons.Filled.AutoAwesome)
}

@Composable
fun ExpiryPill(status: String, date: String?) {
    when (status) {
        ExpiryStatus.EXPIRED -> Pill(stringResource(R.string.expired), TomatoSoft, Tomato)
        ExpiryStatus.TODAY -> Pill(stringResource(R.string.expires_today), CarrotSoft, Carrot)
        ExpiryStatus.SOON -> Pill(stringResource(R.string.expires_soon), HoneySoft, Honey)
        ExpiryStatus.FRESH -> if (date != null) Pill(formatDateLabel(date), LeafSoft, Leaf)
    }
}

@Composable
fun RecipeImage(url: String?, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(CreamDeep),
        )
    } else {
        Box(
            modifier.background(Brush.linearGradient(listOf(LeafSoft, CarrotSoft))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Restaurant, null, tint = Leaf.copy(alpha = 0.55f), modifier = Modifier.size(44.dp))
        }
    }
}

/** Compact card for horizontal carousels. */
@Composable
fun RecipeTile(recipe: RecipeSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(216.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            RecipeImage(recipe.imageUrl, Modifier.fillMaxWidth().height(128.dp))
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MatchPill(recipe.matchPercent)
                if (recipe.isAiGenerated) AiPill()
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                missingLabel(recipe.missing.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun missingLabel(count: Int): String =
    if (count == 0) stringResource(R.string.all_ingredients_available)
    else stringResource(R.string.missing_count, count)

/** Full-width result row showing what the user has and lacks. */
@Composable
fun RecipeRow(recipe: RecipeSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp)) {
            RecipeImage(recipe.imageUrl, Modifier.size(96.dp).clip(MaterialTheme.shapes.medium))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MatchPill(recipe.matchPercent)
                    if (recipe.isAiGenerated) AiPill()
                }
                Text(
                    if (recipe.missing.isEmpty()) stringResource(R.string.all_ingredients_available)
                    else stringResource(R.string.missing_list, recipe.missing.take(3).joinToString(", ")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(recipe.cookingMinutes, recipe.servings, recipe.difficulty)
            }
        }
    }
}

@Composable
fun MetaRow(minutes: Int?, servings: Int?, difficulty: String?) {
    val parts = listOfNotNull(
        minutes?.let { stringResource(R.string.minutes_short, it) },
        servings?.let { stringResource(R.string.servings_short, it) },
        difficulty?.let { difficultyLabel(it) },
    )
    if (parts.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun difficultyLabel(value: String): String = when (value) {
    "Easy" -> stringResource(R.string.difficulty_easy)
    "Medium" -> stringResource(R.string.difficulty_medium)
    "Hard" -> stringResource(R.string.difficulty_hard)
    else -> value
}

@Composable
fun ChipRow(contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp), content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = Leaf,
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = Leaf,
        ),
    )
}

/** Editable list of free-text tags, used for allergies and preferences. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(label: String, tags: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { onChange(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.remove), Modifier.size(16.dp)) },
                )
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.add_item_hint)) },
            trailingIcon = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        val value = input.trim()
                        if (value !in tags) onChange(tags + value)
                        input = ""
                    },
                ) { Text(stringResource(R.string.add)) }
            },
        )
    }
}

@Composable
fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Leaf)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
}
