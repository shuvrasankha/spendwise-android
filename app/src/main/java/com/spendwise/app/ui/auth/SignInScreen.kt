package com.spendwise.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.R
import com.spendwise.app.domain.PASSWORD_RULES
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.IconAction
import com.spendwise.app.ui.components.SegmentedControl
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Spec §1.1: Login and Sign up, forgot password, Google. */
@Composable
fun SignInScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = SpendWiseTheme.colors
    val login = state.tab == AuthTab.LOGIN
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val submit = { if (login) viewModel.login() else viewModel.signUp() }

    Column(
        Modifier.fillMaxSize().background(c.bg).systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Decorative, as the web's alt="": the name beside it says it
        Image(painterResource(R.drawable.logo_icon), contentDescription = null, modifier = Modifier.size(56.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Text(if (login) "Welcome back" else "Create your account", style = SwType.title, color = c.text)
            Text(if (login) "Log in to SpendWise to see where your money went." else "Track spending, income and debts in one place.", style = SwType.body, color = c.textMuted)
        }

        SegmentedControl(listOf("Log in", "Sign up"), state.tab.ordinal, { viewModel.selectTab(AuthTab.entries[it]) })

        if (!login) {
            SwField(
                state.name, viewModel::setName, "Full name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
        }
        SwField(
            state.email, viewModel::setEmail, "Email", leading = SwIcons.Mail,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        SwField(
            state.password, viewModel::setPassword, "Password", leading = SwIcons.Lock,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = { IconAction(if (showPassword) SwIcons.EyeOff else SwIcons.Eye, if (showPassword) "Hide password" else "Show password", { showPassword = !showPassword }) },
            supportingText = if (login) null else PASSWORD_RULES,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (login) {
            Box(Modifier.align(Alignment.End)) { SwButton("Forgot password?", viewModel::openForgot, style = ButtonStyle.Text, compact = true) }
        }

        state.message?.let { message ->
            Text(
                message.text, style = SwType.label, color = if (message.success) c.positive else c.negative,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.canResend) {
            SwButton("Resend verification email", viewModel::resendVerification, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = !state.busy)
        }

        Box(contentAlignment = Alignment.Center) {
            SwButton(if (login) "Log in" else "Create account", submit, Modifier.fillMaxWidth(), enabled = !state.busy)
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = c.onAccent, strokeWidth = 2.dp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = c.line)
            Text("or", style = SwType.label, color = c.textMuted)
            HorizontalDivider(Modifier.weight(1f), color = c.line)
        }
        GoogleButton(enabled = !state.busy, onClick = { viewModel.signInWithGoogle(context) })
    }

    state.forgot?.let { forgot -> ForgotPasswordSheet(forgot, state.busy, viewModel) }
}

/** A secondary button with Google's own colours, which stay as they are in both themes. */
@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    val c = SpendWiseTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Radius.control).border(1.dp, c.line, Radius.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_google), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
        Text("Continue with Google", style = SwType.bodyStrong, color = if (enabled) c.text else c.textFaint, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ForgotPasswordSheet(forgot: ForgotState, busy: Boolean, viewModel: AuthViewModel) {
    val c = SpendWiseTheme.colors
    val sent = forgot.sentTo
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    SwSheet(
        if (sent == null) "Reset password" else "Check your email", viewModel::closeForgot,
        footer = {
            Box(gutter) {
                if (sent != null) SwButton("Done", viewModel::closeForgot, Modifier.fillMaxWidth())
                else SwButton(if (busy) "Sending…" else "Send link", viewModel::sendReset, Modifier.fillMaxWidth(), enabled = !busy)
            }
        },
    ) {
        if (sent != null) {
            Text("We sent a password reset link to $sent. Check your inbox and follow the instructions.", style = SwType.body, color = c.textMuted, modifier = gutter)
        } else {
            Text("Enter your email and we'll send you a link to reset your password.", style = SwType.body, color = c.textMuted, modifier = gutter)
            Box(gutter) {
                SwField(
                    forgot.email, viewModel::setForgotEmail, "Email", leading = SwIcons.Mail,
                    isError = forgot.error != null, supportingText = forgot.error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendReset() }),
                )
            }
        }
    }
}

/** Shown until Firebase reports whether someone is signed in. */
@Composable
fun AuthLoading() {
    Box(Modifier.fillMaxSize().background(SpendWiseTheme.colors.bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SpendWiseTheme.colors.text) }
}
