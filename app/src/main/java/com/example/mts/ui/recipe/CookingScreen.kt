package com.example.mts.ui.recipe

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.R
import com.example.mts.data.RecipeDetail
import com.example.mts.data.RecipeInstruction
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.common.ErrorState
import com.example.mts.ui.common.Load
import com.example.mts.ui.common.LoadingState
import com.example.mts.ui.common.SoftCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class CookingVm(app: Application, handle: SavedStateHandle) : BaseVm(app) {
    private val key: String = checkNotNull(handle["key"])
    val state = MutableStateFlow<Load<RecipeDetail>>(Load.Loading)

    init {
        load()
    }

    fun load() = state.load { api.recipe(key) }
}

@Composable
fun CookingScreen(onExit: () -> Unit, vm: CookingVm = viewModel()) {
    CollectMessages(vm)
    val state by vm.state.collectAsState()
    val view = LocalView.current

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when (val current = state) {
        Load.Loading -> LoadingState()
        is Load.Failed -> ErrorState(current.message, vm::load)
        is Load.Ready -> Steps(current.value, onExit)
    }
}

@Composable
private fun Steps(recipe: RecipeDetail, onExit: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val total = recipe.instructions.size
    val step = recipe.instructions.getOrNull(index)
    val isLast = index == total - 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(recipe.title, onExit) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { index -= 1 },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.previous))
                }
                Button(
                    onClick = { if (isLast) onExit() else index += 1 },
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Text(stringResource(if (isLast) R.string.finish else R.string.next))
                    if (!isLast) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else (index + 1f) / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.step_of, index + 1, total),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            AnimatedContent(step, Modifier.weight(1f), label = "step") { current ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(current?.text.orEmpty(), fontSize = 28.sp, lineHeight = 38.sp)
                    current?.timerSeconds?.let {
                        Spacer(Modifier.height(24.dp))
                        StepTimer(current, it)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTimer(step: RecipeInstruction, seconds: Int) {
    var remaining by remember(step.step) { mutableIntStateOf(seconds) }
    var running by remember(step.step) { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0) running = false
    }

    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (remaining == 0) stringResource(R.string.timer_done) else "%d:%02d".format(remaining / 60, remaining % 60),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start,
            )
            IconButton(onClick = { running = !running }, enabled = remaining > 0) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (running) R.string.pause_timer else R.string.start_timer),
                )
            }
            IconButton(onClick = {
                running = false
                remaining = seconds
            }) { Icon(Icons.Filled.Refresh, stringResource(R.string.reset_timer)) }
        }
    }
}
