package com.spendwise.app.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import kotlinx.coroutines.launch

private data class TourPage(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    TourPage(SwIcons.Plus, "Add expenses in seconds", "Type an amount, or use Speak or Scan to log a spend from your voice, a bank SMS or a receipt photo."),
    TourPage(SwIcons.Home, "See where your money goes", "Home shows this month at a glance, what's left of your budget, and your spending over time."),
    TourPage(SwIcons.Target, "Stay on budget", "Set a monthly budget with limits per category, and savings goals to work toward."),
    TourPage(SwIcons.People, "Keep track of debts", "Record who owes you and who you owe. Share a debt with someone by email and both of you see it."),
    TourPage(SwIcons.Insights, "Ask the AI coach", "Get tips on your spending, or ask a question in plain words. Answers are written by AI and can be wrong."),
)

/** First-run tour: a few pages that can be swiped, with Skip always there until the last one. [onDone] ends it either way. */
@Composable
fun TourScreen(onDone: () -> Unit) {
    val c = SpendWiseTheme.colors
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
            if (!last) SwButton("Skip", onDone, style = ButtonStyle.Text, compact = true)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val page = pages[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(112.dp).background(c.surface, Radius.pill), contentAlignment = Alignment.Center) {
                    Icon(page.icon, contentDescription = null, tint = c.text, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(page.title, style = SwType.title, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = SwType.body, color = c.textMuted, textAlign = TextAlign.Center)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp).semantics { contentDescription = "Page ${pager.currentPage + 1} of ${pages.size}" },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            pages.indices.forEach { i -> Box(Modifier.size(8.dp).background(if (i == pager.currentPage) c.text else c.line, Radius.pill)) }
        }
        SwButton(
            if (last) "Get started" else "Next",
            { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            Modifier.fillMaxWidth(),
        )
    }
}
