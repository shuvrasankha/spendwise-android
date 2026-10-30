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

    val Shield = lucide("shield", "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")
    val Image = lucide("image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 8a1 1 0 1 0 0 2a1 1 0 1 0 0-2", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21")
    val Utensils = lucide("utensils", "M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2", "M7 2v20", "M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7")
    val Cart = lucide(
        "cart", "M8 20a1 1 0 1 0 0 2a1 1 0 1 0 0-2", "M19 20a1 1 0 1 0 0 2a1 1 0 1 0 0-2",
        "M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12",
    )
    val Car = lucide(
        "car", "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
        "M9 17h6", "M5 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M15 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    )
    val Plane = lucide(
        "plane", "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z",
    )
    val Bag = lucide("bag", "M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z", "M3 6h18", "M16 10a4 4 0 0 1-8 0")
    val Receipt = lucide(
        "receipt", "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z",
        "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8", "M12 17.5v-11",
    )
    val HeartPulse = lucide(
        "heartPulse",
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z",
        "M3.22 12H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27",
    )
    val Film = lucide(
        "film", "M4 3h16a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M7 3v18", "M3 7.5h4", "M3 12h18",
        "M3 16.5h4", "M17 3v18", "M17 7.5h4", "M17 16.5h4",
    )
    val Graduation = lucide(
        "graduation", "M21.42 10.922a1 1 0 0 0-.019-1.838L12.83 5.18a2 2 0 0 0-1.66 0L2.6 9.08a1 1 0 0 0 0 1.832l8.57 3.908a2 2 0 0 0 1.66 0z",
        "M22 10v6", "M6 12.5V16a6 3 0 0 0 12 0v-3.5",
    )

    /** The icon for an expense category (EXPENSE_CATEGORIES); unknown or old categories get the three dots. */
    fun forCategory(category: String): ImageVector = when (category) {
        "Food & Dining" -> Utensils
        "Groceries" -> Cart
        "Transport" -> Car
        "Travel" -> Plane
        "Shopping" -> Bag
        "Bills & Utilities" -> Receipt
        "Home & Rent" -> Home
        "Health" -> HeartPulse
        "Entertainment" -> Film
        "Education" -> Graduation
        "Savings & Investments" -> TrendingUp
        else -> More
    }
}
