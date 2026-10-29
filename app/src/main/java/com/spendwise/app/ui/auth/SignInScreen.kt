package com.spendwise.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.spendwise.app.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.PASSWORD_RULES
import com.spendwise.app.ui.theme.SpendWiseTheme

/** Spec §1.1: Login and Sign up tabs, forgot password, Google. */
@Composable
fun SignInScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val login = state.tab == AuthTab.LOGIN

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Decorative, as the web's alt="": the name beside it says it
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.logo_icon), contentDescription = null, modifier = Modifier.size(88.dp))
            Text("SpendWise", style = MaterialTheme.typography.headlineMedium)
        }

        SecondaryTabRow(selectedTabIndex = state.tab.ordinal) {
            Tab(selected = login, onClick = { viewModel.selectTab(AuthTab.LOGIN) }, text = { Text("Login") })
            Tab(selected = !login, onClick = { viewModel.selectTab(AuthTab.SIGNUP) }, text = { Text("Sign up") })
        }

        if (!login) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Full name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::setEmail,
            label = { Text("Email") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text("Password") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = if (login) null else ({ Text(PASSWORD_RULES) }),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (login) viewModel.login() else viewModel.signUp() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (login) {
            TextButton(onClick = viewModel::openForgot, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?")
            }
        }

        state.message?.let { message ->
            Text(
                message.text,
                color = if (message.success) SpendWiseTheme.moneyColors.moneyIn else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.canResend) {
            OutlinedButton(onClick = viewModel::resendVerification, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Resend verification email")
            }
        }

        Button(
            onClick = { if (login) viewModel.login() else viewModel.signUp() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (login) "Log in" else "Create account")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f))
            Text("or", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = { viewModel.signInWithGoogle(context) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Continue with Google") }
        Spacer(Modifier.height(8.dp))
    }

    state.forgot?.let { forgot -> ForgotPasswordDialog(forgot, state.busy, viewModel) }
}

@Composable
private fun ForgotPasswordDialog(forgot: ForgotState, busy: Boolean, viewModel: AuthViewModel) {
    val sent = forgot.sentTo
    AlertDialog(
        onDismissRequest = viewModel::closeForgot,
        title = { Text(if (sent == null) "Reset password" else "Check your email") },
        text = {
            if (sent != null) {
                Text("We sent a password reset link to $sent. Check your inbox and follow the instructions.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your email and we'll send you a link to reset your password.")
                    OutlinedTextField(
                        value = forgot.email,
                        onValueChange = viewModel::setForgotEmail,
                        label = { Text("Email") },
                        singleLine = true,
                        isError = forgot.error != null,
                        supportingText = forgot.error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.sendReset() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (sent != null) {
                TextButton(onClick = viewModel::closeForgot) { Text("Done") }
            } else {
                TextButton(onClick = viewModel::sendReset, enabled = !busy) { Text(if (busy) "Sending…" else "Send link") }
            }
        },
        dismissButton = if (sent == null) ({ TextButton(onClick = viewModel::closeForgot) { Text("Cancel") } }) else null,
    )
}

/** Shown until Firebase reports whether someone is signed in. */
@Composable
fun AuthLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
