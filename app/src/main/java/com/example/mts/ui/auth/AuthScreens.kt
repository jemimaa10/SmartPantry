package com.example.mts.ui.auth

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.BuildConfig
import com.example.mts.R
import com.example.mts.data.ForgotRequest
import com.example.mts.data.GoogleRequest
import com.example.mts.data.LoginRequest
import com.example.mts.data.RegisterRequest
import com.example.mts.data.ResetRequest
import com.example.mts.data.Session
import com.example.mts.data.toAppFailure
import com.example.mts.ui.common.BackBar
import com.example.mts.ui.common.BaseVm
import com.example.mts.ui.common.CollectMessages
import com.example.mts.ui.theme.Leaf
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AuthVm(app: Application) : BaseVm(app) {
    val busy = MutableStateFlow(false)
    val resetSent = MutableStateFlow(false)

    private fun submit(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (e: Throwable) {
                messages.emit(e.toAppFailure().message.orEmpty())
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun signIn(session: Session) = pantryApp.tokens.save(session)

    fun login(email: String, password: String) = submit { signIn(api.login(LoginRequest(email.trim(), password))) }

    fun register(name: String, email: String, password: String) =
        submit { signIn(api.register(RegisterRequest(name.trim(), email.trim(), password))) }

    fun google(context: Context, notConfigured: String, failed: String) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            messages.tryEmit(notConfigured)
            return
        }
        submit {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    signIn(api.google(GoogleRequest(idToken)))
                } else {
                    messages.emit(failed)
                }
            } catch (_: GetCredentialCancellationException) {
                // The user closed the account picker.
            } catch (_: GetCredentialException) {
                messages.emit(failed)
            }
        }
    }

    fun forgot(email: String) = submit {
        val response = api.forgotPassword(ForgotRequest(email.trim()))
        resetSent.value = true
        messages.emit(response.message)
    }

    fun reset(token: String, password: String, onDone: () -> Unit) = submit {
        val response = api.resetPassword(ResetRequest(token.trim(), password))
        messages.emit(response.message)
        onDone()
    }
}

@Composable
private fun Brand() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).background(Leaf, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Eco, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.tagline),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String = stringResource(R.string.password)) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value,
        onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(R.string.toggle_password),
                )
            }
        },
    )
}

@Composable
private fun SubmitButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(52.dp), enabled = enabled && !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(label)
    }
}

@Composable
fun LoginScreen(onRegister: () -> Unit, onForgot: () -> Unit, vm: AuthVm = viewModel()) {
    CollectMessages(vm)
    val busy by vm.busy.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val notConfigured = stringResource(R.string.google_not_configured)
    val failed = stringResource(R.string.google_failed)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Brand()
        Spacer(Modifier.height(20.dp))
        EmailField(email) { email = it }
        PasswordField(password, { password = it })
        TextButton(onForgot, Modifier.align(Alignment.End)) { Text(stringResource(R.string.forgot_password)) }
        SubmitButton(stringResource(R.string.login), busy, email.isNotBlank() && password.isNotEmpty()) {
            vm.login(email, password)
        }
        OutlinedButton(
            onClick = { vm.google(context, notConfigured, failed) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !busy,
        ) { Text(stringResource(R.string.continue_google)) }
        TextButton(onRegister, Modifier.fillMaxWidth()) { Text(stringResource(R.string.no_account)) }
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, vm: AuthVm = viewModel()) {
    CollectMessages(vm)
    val busy by vm.busy.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val notConfigured = stringResource(R.string.google_not_configured)
    val failed = stringResource(R.string.google_failed)

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        BackBar(stringResource(R.string.register), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
            )
            EmailField(email) { email = it }
            PasswordField(password, { password = it })
            Text(
                stringResource(R.string.password_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SubmitButton(
                stringResource(R.string.register),
                busy,
                name.isNotBlank() && email.isNotBlank() && password.length >= 8,
            ) { vm.register(name, email, password) }
            OutlinedButton(
                onClick = { vm.google(context, notConfigured, failed) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
            ) { Text(stringResource(R.string.register_google)) }
        }
    }
}

@Composable
fun ForgotScreen(onBack: () -> Unit, vm: AuthVm = viewModel()) {
    CollectMessages(vm)
    val busy by vm.busy.collectAsState()
    val sent by vm.resetSent.collectAsState()
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        BackBar(stringResource(R.string.forgot_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!sent) {
                Text(stringResource(R.string.forgot_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                EmailField(email) { email = it }
                SubmitButton(stringResource(R.string.send_reset), busy, email.isNotBlank()) { vm.forgot(email) }
            } else {
                Text(stringResource(R.string.reset_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    token,
                    { token = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reset_code)) },
                    singleLine = true,
                )
                PasswordField(password, { password = it }, stringResource(R.string.new_password))
                SubmitButton(
                    stringResource(R.string.save_password),
                    busy,
                    token.isNotBlank() && password.length >= 8,
                ) { vm.reset(token, password, onBack) }
            }
        }
    }
}
