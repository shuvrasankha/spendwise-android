package com.spendwise.app.domain

// Ported from dashboard.js › handleLogin, handleSignup, handleForgotPassword and helpers.js › friendlyErr (spec §2.1).
// Messages are the web's, except the weak-password one, which now states every rule (spec §8 Q12, §9.8).

const val FILL_ALL_FIELDS = "Please fill in all fields."
const val VERIFY_EMAIL_FIRST = "Please verify your email before logging in."
const val ACCOUNT_CREATED = "Account created! Please check your email to verify."

/** Not a web code: Android's Credential Manager found no Google account on the device. */
const val NO_GOOGLE_ACCOUNT = "android/no-google-account"

/** Shown under the sign-up password field, and for Firebase's weak-password error. */
const val PASSWORD_RULES =
    "Use at least 8 characters, with a lowercase letter, an uppercase letter, a number and a special character."

/** handleSignup's set: !@#$%^&*()_+-=[]{};':"\|,.<>/? */
private const val SPECIAL_CHARACTERS = "!@#\$%^&*()_+-=[]{};':\"\\|,.<>/?"

fun loginError(email: String, password: String): String? =
    if (email.trim().isEmpty() || password.isEmpty()) FILL_ALL_FIELDS else null

/** The first rule a sign-up breaks, checked in the web's order, or null. */
fun signupError(name: String, email: String, password: String): String? = when {
    name.trim().isEmpty() || email.trim().isEmpty() || password.isEmpty() -> FILL_ALL_FIELDS
    password.length < 8 -> "Password must be at least 8 characters."
    password.none { it in 'a'..'z' } -> "Password must contain at least one lowercase letter."
    password.none { it in 'A'..'Z' } -> "Password must contain at least one uppercase letter."
    password.none { it in '0'..'9' } -> "Password must contain at least one number."
    password.none { it in SPECIAL_CHARACTERS } -> "Password must contain at least one special character."
    else -> null
}

private val resetEmailShape = Regex("^[^@]+@[^@]+[.][^@]+")

fun forgotEmailError(email: String): String? = when {
    email.trim().isEmpty() -> "Please enter your email address."
    !resetEmailShape.containsMatchIn(email.trim()) -> "Please enter a valid email address."
    else -> null
}

/** [code] is a web Firebase code such as "auth/invalid-credential"; null or unknown gives the fallback. */
fun friendlyErr(code: String?): String = when (code) {
    "auth/user-not-found" -> "No account uses this email. Check it, or sign up."
    "auth/wrong-password" -> "That password isn't right. Try again, or reset it."
    "auth/invalid-credential" -> "Email or password isn't right. Try again, or reset your password."
    "auth/email-already-in-use" -> "This email already has an account. Log in instead."
    "auth/invalid-email" -> "Enter an email like name@example.com."
    "auth/weak-password" -> PASSWORD_RULES
    "auth/popup-closed-by-user" -> "Google sign-in was closed before it finished."
    "auth/network-request-failed" -> "No connection. Check your internet and try again."
    "auth/too-many-requests" -> "Too many attempts. Wait a minute, then try again."
    "auth/user-disabled" -> "This account has been disabled."
    NO_GOOGLE_ACCOUNT -> "No Google account on this phone. Add one in your phone's settings, or use email."
    else -> "That didn't work. Check your connection and try again."
}

/** The forgot-password dialog has its own, shorter messages (handleForgotPassword). */
fun forgotPasswordErr(code: String?): String = when (code) {
    "auth/user-not-found" -> "No account found with this email."
    "auth/invalid-email" -> "Invalid email address."
    "auth/too-many-requests" -> "Too many attempts. Try again later."
    else -> "Couldn't send the reset email. Check your connection and try again."
}

/** Only email-and-password accounts need a verified email; Google accounts arrive verified (spec §9.7). */
fun needsVerification(emailVerified: Boolean, providers: List<String>): Boolean =
    !emailVerified && "password" in providers
