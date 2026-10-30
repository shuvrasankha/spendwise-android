package com.spendwise.app.ui.components

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.spendwise.app.ui.theme.SpendWiseTheme

@Preview(name = "Dark", widthDp = 390, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Light", widthDp = 390, uiMode = UI_MODE_NIGHT_NO)
annotation class DarkLight

/** Themed backdrop for previews; follows the preview's night mode. */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) = SpendWiseTheme {
    Box(Modifier.background(SpendWiseTheme.colors.bg)) { content() }
}
