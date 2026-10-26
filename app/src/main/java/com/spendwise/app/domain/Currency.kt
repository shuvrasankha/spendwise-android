package com.spendwise.app.domain

// Copied from currency.js › CURRENCIES (spec §2.12). Display only: amounts are never converted.

data class Currency(val code: String, val symbol: String, val name: String, val locale: String)

val CURRENCIES = listOf(
    Currency("INR", "₹", "Indian Rupee", "en-IN"),
    Currency("USD", "$", "US Dollar", "en-US"),
    Currency("EUR", "€", "Euro", "de-DE"),
    Currency("GBP", "£", "British Pound", "en-GB"),
    Currency("BDT", "৳", "Bangladeshi Taka", "bn-BD"),
    Currency("PKR", "Rs", "Pakistani Rupee", "en-PK"),
    Currency("LKR", "Rs", "Sri Lankan Rupee", "si-LK"),
    Currency("NPR", "Rs", "Nepalese Rupee", "ne-NP"),
    Currency("AED", "د.إ", "UAE Dirham", "ar-AE"),
    Currency("SAR", "﷼", "Saudi Riyal", "ar-SA"),
    Currency("JPY", "¥", "Japanese Yen", "ja-JP"),
    Currency("AUD", "A$", "Australian Dollar", "en-AU"),
    Currency("CAD", "C$", "Canadian Dollar", "en-CA"),
    Currency("SGD", "S$", "Singapore Dollar", "en-SG"),
    Currency("MYR", "RM", "Malaysian Ringgit", "ms-MY"),
    Currency("THB", "฿", "Thai Baht", "th-TH"),
    Currency("IDR", "Rp", "Indonesian Rupiah", "id-ID"),
    Currency("PHP", "₱", "Philippine Peso", "fil-PH"),
    Currency("KRW", "₩", "South Korean Won", "ko-KR"),
    Currency("CNY", "¥", "Chinese Yuan", "zh-CN"),
    Currency("BRL", "R$", "Brazilian Real", "pt-BR"),
    Currency("ZAR", "R", "South African Rand", "en-ZA"),
    Currency("NGN", "₦", "Nigerian Naira", "en-NG"),
    Currency("KES", "KSh", "Kenyan Shilling", "en-KE"),
    Currency("EGP", "E£", "Egyptian Pound", "ar-EG"),
    Currency("TRY", "₺", "Turkish Lira", "tr-TR"),
    Currency("RUB", "₽", "Russian Ruble", "ru-RU"),
    Currency("CHF", "CHF", "Swiss Franc", "de-CH"),
    Currency("SEK", "kr", "Swedish Krona", "sv-SE"),
    Currency("NOK", "kr", "Norwegian Krone", "nb-NO"),
    Currency("DKK", "kr", "Danish Krone", "da-DK"),
    Currency("PLN", "zł", "Polish Zloty", "pl-PL"),
    Currency("MXN", "MX$", "Mexican Peso", "es-MX"),
    Currency("ARS", "AR$", "Argentine Peso", "es-AR"),
    Currency("COP", "CO$", "Colombian Peso", "es-CO"),
)

const val DEFAULT_CURRENCY = "INR"

/** Unknown or missing codes fall back to INR, like currency.js › getCurrencyInfo. */
fun currencyOf(code: String?): Currency =
    CURRENCIES.firstOrNull { it.code == code } ?: CURRENCIES.first { it.code == DEFAULT_CURRENCY }
