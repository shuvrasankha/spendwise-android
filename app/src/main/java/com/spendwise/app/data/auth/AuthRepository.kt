package com.spendwise.app.data.auth

import com.spendwise.app.data.worker.PushRegistry
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.spendwise.app.R
import com.spendwise.app.domain.needsVerification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** [emailVerified] gates sharing debts, as the rules do (firestore.rules › emailVerified). */
data class AuthUser(val uid: String, val email: String, val displayName: String, val emailVerified: Boolean = false)

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}

/** Login needs a verified email; the caller shows VERIFY_EMAIL_FIRST and can offer to resend. */
class EmailNotVerifiedException : Exception()

/** Firebase Auth and Credential Manager (spec §2.1). Failures throw; map them with webAuthCode. */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    @param:ApplicationContext private val appContext: Context,
    private val push: PushRegistry,
) {

    /** An unverified email-and-password user counts as signed out, so a half-finished sign-up never reaches the app. */
    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser.toState()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUid: String? get() = auth.currentUser?.uid

    val currentUser: AuthUser? get() = (auth.currentUser.toState() as? AuthState.SignedIn)?.user

    suspend fun signIn(email: String, password: String) {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
        if (user == null || user.needsVerification()) {
            auth.signOut()
            throw EmailNotVerifiedException()
        }
    }

    /** Creates the account, names it, sends the verification email, then signs out, like handleSignup. */
    suspend fun signUp(name: String, email: String, password: String) {
        try {
            val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
            user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
            user.sendEmailVerification().await()
        } finally {
            auth.signOut()
        }
    }

    /** Signs in just long enough to send a new verification email (spec §8 Q11). False when it's already verified. */
    suspend fun resendVerification(email: String, password: String): Boolean {
        try {
            val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user!!
            if (!user.needsVerification()) return false
            user.sendEmailVerification().await()
            return true
        } finally {
            auth.signOut()
        }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** [activityContext] must be an Activity: Credential Manager shows its sheet over it. */
    suspend fun signInWithGoogle(activityContext: Context) {
        auth.signInWithCredential(googleCredential(activityContext)).await()
    }

    private suspend fun googleCredential(activityContext: Context): AuthCredential {
        val option = GetSignInWithGoogleOption.Builder(appContext.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    /** Account deletion confirms with a password when the account has one, otherwise with Google. */
    val usesPassword: Boolean get() = auth.currentUser?.providerData?.any { it.providerId == "password" } == true

    /** A fresh sign-in, so Firebase lets the account be deleted (spec §8.2). Throws on a wrong password. */
    suspend fun reauthenticateWithPassword(password: String) {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
    }

    /** Picking a different Google account fails (FirebaseAuthInvalidUserException), so only this one is confirmed. */
    suspend fun reauthenticateWithGoogle(activityContext: Context) {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        user.reauthenticate(googleCredential(activityContext)).await()
    }

    /** Deletes the sign-in itself; Firebase then signs out. */
    suspend fun deleteUser() {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        push.unregister() // needs the sign-in to reach the Worker
        user.delete().await()
    }

    suspend fun signOut() {
        push.unregister() // needs the sign-in to reach the Worker
        auth.signOut()
        // So the next Google sign-in asks which account, instead of reusing this one
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun FirebaseUser.needsVerification() =
        needsVerification(isEmailVerified, providerData.map { it.providerId })

    private fun FirebaseUser?.toState(): AuthState = when {
        this == null || needsVerification() -> AuthState.SignedOut
        else -> AuthState.SignedIn(AuthUser(uid, email.orEmpty(), displayName.orEmpty(), isEmailVerified))
    }
}
