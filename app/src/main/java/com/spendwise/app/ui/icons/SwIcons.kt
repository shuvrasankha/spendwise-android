package com.spendwise.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Lucide outline icons (24 grid, round caps and joins) at a 1.75 stroke. Tinted by Icon, so the stroke colour is a placeholder.
private fun lucide(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    paths.forEach {
        addPath(
            pathData = PathParser().parsePathString(it).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

object SwIcons {
    val Home = lucide(
        "home", "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
    val History = lucide("history", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20", "M12 6v6l4 2")
    val Plus = lucide("plus", "M5 12h14", "M12 5v14")
    val Insights = lucide("insights", "M5 21v-6", "M12 21V3", "M19 21V9")
    val More = lucide("more", "M12 12h.01", "M19 12h.01", "M5 12h.01")
    val Back = lucide("back", "m15 18-6-6 6-6")
    val ChevronRight = lucide("chevronRight", "m9 18 6-6-6-6")
    val ChevronDown = lucide("chevronDown", "m6 9 6 6 6-6")
    val Close = lucide("close", "M18 6 6 18", "m6 6 12 12")
    val Search = lucide("search", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16", "m21 21-4.3-4.3")
    val Filters = lucide(
        "filters", "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4",
    )
    val Download = lucide("download", "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")
    val Upload = lucide("upload", "M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")
    val Calendar = lucide("calendar", "M8 2v4", "M16 2v4", "M3 10h18", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z")
    val Check = lucide("check", "M20 6 9 17l-5-5")
    val Trash = lucide(
        "trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6",
    )
    val MoneyIn = lucide("moneyIn", "M17 7 7 17", "M17 17H7V7")
    val MoneyOut = lucide("moneyOut", "M7 7h10v10", "M7 17 17 7")
    val People = lucide(
        "people", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8",
        "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75",
    )
    val TrendingUp = lucide("trendingUp", "M16 7h6v6", "m22 7-8.5 8.5-5-5L2 17")
    val Settings = lucide(
        "settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
    )
    val Bell = lucide(
        "bell", "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )
    val Mic = lucide("mic", "M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z")
    val Camera = lucide(
        "camera", "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M12 10a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
    )
    val Target = lucide("target", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20", "M12 6a6 6 0 1 0 0 12a6 6 0 1 0 0-12", "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4")
    val Wallet = lucide(
        "wallet", "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
        "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
    )
    val LogOut = lucide("logOut", "m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4")
}
