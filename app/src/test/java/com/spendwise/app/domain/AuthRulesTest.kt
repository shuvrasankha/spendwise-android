package com.spendwise.app.domain

import com.spendwise.app.data.auth.androidToWebCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRulesTest {

    private val good = "Abcdef1!"

    @Test fun signup_requiresEveryField() {
        assertEquals(FILL_ALL_FIELDS, signupError("", "a@b.co", good))
        assertEquals(FILL_ALL_FIELDS, signupError("   ", "a@b.co", good))
        assertEquals(FILL_ALL_FIELDS, signupError("Ravi", " ", good))
        assertEquals(FILL_ALL_FIELDS, signupError("Ravi", "a@b.co", ""))
    }

    @Test fun signup_checksRulesInTheWebsOrder() {
        assertEquals("Password must be at least 8 characters.", signupError("R", "a@b.co", "Ab1!"))
        // a short password that breaks every rule still reports length first
        assertEquals("Password must be at least 8 characters.", signupError("R", "a@b.co", "abc"))
        assertEquals("Password must contain at least one lowercase letter.", signupError("R", "a@b.co", "ABCDEF1!"))
        assertEquals("Password must contain at least one uppercase letter.", signupError("R", "a@b.co", "abcdef1!"))
        assertEquals("Password must contain at least one number.", signupError("R", "a@b.co", "Abcdefg!"))
        assertEquals("Password must contain at least one special character.", signupError("R", "a@b.co", "Abcdefg1"))
        assertNull(signupError("Ravi", "a@b.co", good))
    }

    @Test fun signup_eightCharactersIsEnough() {
        assertNull(signupError("R", "a@b.co", "Abcde1!x"))
        assertEquals("Password must be at least 8 characters.", signupError("R", "a@b.co", "Abcd1!x"))
    }

    @Test fun signup_specialCharactersAreExactlyTheWebsSet() {
        "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?".forEach { c ->
            assertNull("'$c' should count", signupError("R", "a@b.co", "Abcdef1$c"))
        }
        listOf('~', '`', ' ', '€', '£').forEach { c ->
            assertEquals("'$c' should not count", "Password must contain at least one special character.", signupError("R", "a@b.co", "Abcdef1$c"))
        }
    }

    @Test fun signup_lettersAreAsciiOnly() {
        // JS /[a-z]/ and /[A-Z]/ don't match accented letters
        assertEquals("Password must contain at least one lowercase letter.", signupError("R", "a@b.co", "ÉÀBCDE1!"))
        assertEquals("Password must contain at least one uppercase letter.", signupError("R", "a@b.co", "éàbcde1!"))
    }

    @Test fun login_requiresEmailAndPasswordButDoesNotTrimThePassword() {
        assertEquals(FILL_ALL_FIELDS, loginError("", "x"))
        assertEquals(FILL_ALL_FIELDS, loginError("  ", "x"))
        assertEquals(FILL_ALL_FIELDS, loginError("a@b.co", ""))
        assertNull(loginError("a@b.co", " "))
    }

    @Test fun forgot_emailChecks() {
        assertEquals("Please enter your email address.", forgotEmailError(" "))
        assertEquals("Please enter a valid email address.", forgotEmailError("ravi"))
        assertEquals("Please enter a valid email address.", forgotEmailError("ravi@example"))
        assertNull(forgotEmailError("ravi@example.com"))
        assertNull(forgotEmailError(" ravi@example.com "))
    }

    @Test fun friendlyErr_usesTheWebsCopy() {
        assertEquals("Email or password isn't right. Try again, or reset your password.", friendlyErr("auth/invalid-credential"))
        assertEquals("This email already has an account. Log in instead.", friendlyErr("auth/email-already-in-use"))
        assertEquals("No connection. Check your internet and try again.", friendlyErr("auth/network-request-failed"))
        assertEquals("Google sign-in was closed before it finished.", friendlyErr("auth/popup-closed-by-user"))
        assertEquals("That didn't work. Check your connection and try again.", friendlyErr("auth/something-new"))
        assertEquals("That didn't work. Check your connection and try again.", friendlyErr(null))
    }

    @Test fun friendlyErr_weakPasswordStatesEveryRule() {
        val msg = friendlyErr("auth/weak-password")
        listOf("8 characters", "lowercase", "uppercase", "number", "special character").forEach {
            assertTrue("missing '$it'", it in msg)
        }
    }

    @Test fun forgotPasswordErr_hasItsOwnCopy() {
        assertEquals("No account found with this email.", forgotPasswordErr("auth/user-not-found"))
        assertEquals("Couldn't send the reset email. Check your connection and try again.", forgotPasswordErr(null))
    }

    @Test fun androidErrorCodesMapToWebCodes() {
        assertEquals("auth/invalid-credential", androidToWebCode("ERROR_INVALID_CREDENTIAL"))
        assertEquals("auth/email-already-in-use", androidToWebCode("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals("auth/wrong-password", androidToWebCode("ERROR_WRONG_PASSWORD"))
        assertEquals("auth/user-not-found", androidToWebCode("ERROR_USER_NOT_FOUND"))
        assertEquals("auth/weak-password", androidToWebCode("ERROR_WEAK_PASSWORD"))
        assertEquals("auth/user-disabled", androidToWebCode("ERROR_USER_DISABLED"))
        assertEquals("auth/too-many-requests", androidToWebCode("ERROR_TOO_MANY_REQUESTS"))
        assertEquals("auth/invalid-email", androidToWebCode("ERROR_INVALID_EMAIL"))
    }

    @Test fun onlyUnverifiedPasswordAccountsAreHeldBack() {
        assertTrue(needsVerification(false, listOf("firebase", "password")))
        assertFalse(needsVerification(true, listOf("firebase", "password")))
        assertFalse(needsVerification(false, listOf("firebase", "google.com")))
    }

    @Test fun changePassword_requiresAllThreeFields() {
        assertEquals(FILL_ALL_FIELDS, changePasswordError("", good, good))
        assertEquals(FILL_ALL_FIELDS, changePasswordError("old", "", good))
        assertEquals(FILL_ALL_FIELDS, changePasswordError("old", good, ""))
    }

    @Test fun changePassword_newPasswordFollowsTheSignupRules() {
        assertEquals("Password must be at least 8 characters.", changePasswordError("old", "Ab1!", "Ab1!"))
        assertEquals("Password must contain at least one special character.", changePasswordError("old", "Abcdefg1", "Abcdefg1"))
    }

    @Test fun changePassword_mustMatchAndDiffer() {
        assertEquals("The new passwords don't match.", changePasswordError("old", good, good + "x"))
        assertEquals("Choose a password different from the current one.", changePasswordError(good, good, good))
        assertNull(changePasswordError("old", good, good))
    }

    @Test fun changePassword_aBadCredentialMeansTheCurrentPasswordIsWrong() {
        assertEquals("Your current password isn't right.", changePasswordErr("auth/invalid-credential"))
        assertEquals("Your current password isn't right.", changePasswordErr("auth/wrong-password"))
        assertEquals(friendlyErr("auth/weak-password"), changePasswordErr("auth/weak-password"))
        assertEquals(friendlyErr("auth/network-request-failed"), changePasswordErr("auth/network-request-failed"))
    }
}
