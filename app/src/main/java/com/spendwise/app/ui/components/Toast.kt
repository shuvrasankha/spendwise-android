package com.spendwise.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/** A message for the snackbar. [error]s stay until dismissed, like the web's error toasts (accessibility.js). */
data class Toast(val text: String, val error: Boolean = false)

suspend fun SnackbarHostState.show(toast: Toast, actionLabel: String? = null, duration: SnackbarDuration = SnackbarDuration.Short): SnackbarResult =
    if (toast.error) showSnackbar(toast.text, actionLabel, withDismissAction = true, duration = SnackbarDuration.Indefinite)
    else showSnackbar(toast.text, actionLabel, duration = duration)
