package com.spendwise.app.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.spendwise.app.domain.NO_GOOGLE_ACCOUNT

/**
 * The web's Firebase error code for [e] ("auth/…"), so friendlyErr's copy applies to both clients.
 * Android's codes are the same names in another shape: ERROR_INVALID_CREDENTIAL → auth/invalid-credential.
 */
fun webAuthCode(e: Throwable): String? = when (e) {
    is FirebaseAuthException -> androidToWebCode(e.errorCode)
    is FirebaseTooManyRequestsException -> "auth/too-many-requests"
    is FirebaseNetworkException -> "auth/network-request-failed"
    is GetCredentialCancellationException -> "auth/popup-closed-by-user"
    is NoCredentialException -> NO_GOOGLE_ACCOUNT
    else -> null
}

fun androidToWebCode(errorCode: String): String =
    "auth/" + errorCode.removePrefix("ERROR_").lowercase().replace('_', '-')
