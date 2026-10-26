package com.spendwise.app.ui.navigation

import android.content.Intent

/** App links: spendwise://{dashboard|add|history|insights}, the web's index.html#{page} (spec §1.0). */
const val LINK_SCHEME = "spendwise"

/**
 * The route a link opens, or null for anything else. History takes the web's history.html#income and
 * ?tab=income&q=&from=&to=&cat= (dashboard.js › initHistory); [param] returns every value of a key.
 */
fun linkRoute(scheme: String?, page: String?, fragment: String?, param: (String) -> List<String>): Any? {
    if (scheme != LINK_SCHEME) return null
    return when (page) {
        "dashboard" -> Dashboard
        "add" -> AddExpense
        "insights" -> Insights
        "history" -> History(
            income = fragment == "income" || param("tab").firstOrNull() == "income",
            q = param("q").firstOrNull(),
            from = param("from").firstOrNull(),
            to = param("to").firstOrNull(),
            cats = param("cat"),
        )
        else -> null
    }
}

fun Intent.linkRoute(): Any? = data?.let { uri ->
    // Opaque URIs (mailto:) can't be queried; only hierarchical ones are links
    if (!uri.isHierarchical) null else linkRoute(uri.scheme, uri.host, uri.fragment) { uri.getQueryParameters(it) }
}
