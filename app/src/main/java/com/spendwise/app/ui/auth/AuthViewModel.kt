package com.spendwise.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.EmailNotVerifiedException
import com.spendwise.app.data.auth.webAuthCode
import com.spendwise.app.domain.ACCOUNT_CREATED
import com.spendwise.app.domain.VERIFY_EMAIL_FIRST
import com.spendwise.app.domain.forgotEmailError
import com.spendwise.app.domain.forgotPasswordErr
import com.spendwise.app.domain.friendlyErr
import com.spendwise.app.domain.loginError
import com.spendwise.app.domain.signupError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthTab { LOGIN, SIGNUP }

data class AuthMessage(val text: String, val success: Boolean = false)

/** The forgot-password dialog: step 1 asks for the email, step 2 ([sentTo]) confirms. */
data class ForgotState(val email: String = "", val error: String? = null, val sentTo: String? = null)

data class AuthUiState(
    val tab: AuthTab = AuthTab.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val message: AuthMessage? = null,
    /** Login was refused for an unverified email: offer "Resend verification email". */
    val canResend: Boolean = false,
    val forgot: ForgotState? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun selectTab(tab: AuthTab) = _state.update { it.copy(tab = tab, message = null, canResend = false) }
    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, canResend = false) }
    fun setPassword(v: String) = _state.update { it.copy(password = v) }

    fun login() {
        val s = _state.value
        loginError(s.email, s.password)?.let { return fail(it) }
        launchAuth {
            try {
                repo.signIn(s.email, s.password)
                _state.update { it.copy(password = "") } // the auth gate takes over
            } catch (e: EmailNotVerifiedException) {
                _state.update { it.copy(message = AuthMessage(VERIFY_EMAIL_FIRST), canResend = true) }
            }
        }
    }

    fun signUp() {
        val s = _state.value
        signupError(s.name, s.email, s.password)?.let { return fail(it) }
        launchAuth {
            repo.signUp(s.name, s.email, s.password)
            _state.update { it.copy(tab = AuthTab.LOGIN, password = "", message = AuthMessage(ACCOUNT_CREATED, success = true)) }
        }
    }

    fun resendVerification() {
        val s = _state.value
        launchAuth {
            val text = if (repo.resendVerification(s.email, s.password)) {
                "We sent a new verification link to ${s.email.trim()}. Open it, then log in."
            } else {
                "Your email is already verified. Log in."
            }
            _state.update { it.copy(message = AuthMessage(text, success = true), canResend = false) }
        }
    }

    /** [activityContext] is only passed through to Credential Manager, never kept. */
    fun signInWithGoogle(activityContext: Context) = launchAuth {
        repo.signInWithGoogle(activityContext)
        _state.update { it.copy(password = "") }
    }

    fun openForgot() = _state.update { it.copy(forgot = ForgotState(email = it.email.trim())) }
    fun closeForgot() = _state.update { it.copy(forgot = null) }
    fun setForgotEmail(v: String) = _state.update { it.copy(forgot = it.forgot?.copy(email = v, error = null)) }

    fun sendReset() {
        val forgot = _state.value.forgot ?: return
        forgotEmailError(forgot.email)?.let { err -> return _state.update { it.copy(forgot = forgot.copy(error = err)) } }
        val email = forgot.email.trim()
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val error = try {
                repo.sendPasswordReset(email)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                forgotPasswordErr(webAuthCode(e))
            }
            _state.update {
                it.copy(busy = false, forgot = it.forgot?.copy(error = error, sentTo = if (error == null) email else null))
            }
        }
    }

    private fun fail(text: String) = _state.update { it.copy(message = AuthMessage(text), canResend = false) }

    /** Runs [block] once at a time with the busy flag; any other failure becomes the web's friendly message. */
    private fun launchAuth(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(friendlyErr(webAuthCode(e)))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
