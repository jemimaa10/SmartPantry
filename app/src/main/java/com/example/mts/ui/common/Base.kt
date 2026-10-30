package com.example.mts.ui.common

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.example.mts.SmartPantryApp
import com.example.mts.data.Api
import com.example.mts.data.AppFailure
import com.example.mts.data.toAppFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String, val code: String = "") : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

abstract class BaseVm(app: Application) : AndroidViewModel(app) {
    protected val pantryApp = app as SmartPantryApp
    protected val api: Api get() = pantryApp.api

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    protected fun <T> MutableStateFlow<Load<T>>.load(silent: Boolean = false, block: suspend () -> T) {
        val state = this
        viewModelScope.launch {
            if (!silent || state.value !is Load.Ready) state.value = Load.Loading
            state.value = try {
                Load.Ready(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failure = e.toAppFailure()
                Load.Failed(failure.message.orEmpty(), failure.code)
            }
        }
    }

    /** Runs a user action, surfacing any failure as a snackbar. */
    protected fun act(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failure: AppFailure = e.toAppFailure()
                messages.emit(failure.message.orEmpty())
            }
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun CollectMessages(vm: BaseVm) {
    val snackbar = LocalSnackbar.current
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
}

/** Reloads each time the screen returns to the foreground. */
@Composable
fun OnResume(block: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { block() }
}
