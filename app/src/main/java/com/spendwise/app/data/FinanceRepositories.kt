package com.spendwise.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Live reads of income, debts, holdings and settings for the Dashboard. Writes arrive with each feature's slice.

@Singleton
class IncomeRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    fun income(): Flow<List<Income>> = auth.forUser(emptyList()) { user ->
        db.collection("income").whereEqualTo("uid", user.uid).live()
            .map { s -> s.documents.map { incomeFrom(it.id, it.data.orEmpty()) }.sortedByDescending { it.date } }
    }
}

@Singleton
class DebtRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    /** Your own debts plus those shared with your (verified) email, from your side (split.js › fetchSharedDebts). */
    fun debts(): Flow<List<Debt>> = auth.forUser(emptyList()) { user ->
        val own = db.collection("debts").whereEqualTo("uid", user.uid).live()
            .map { s -> s.documents.map { ownDebtFrom(it.id, it.data.orEmpty()) } }
        // A second where, on withEmail, as the rules require; the web treats a failure as no shared debts
        val shared = if (user.email.isEmpty()) flowOf(emptyList()) else
            db.collection("debts").whereEqualTo("withEmail", user.email.lowercase()).live()
                .map { s -> s.documents.map { sharedDebtFrom(it.id, it.data.orEmpty()) } }
                .catch { emit(emptyList()) }
        combine(own, shared) { a, b -> (a + b).sortedByDescending { it.date } }
    }
}

@Singleton
class HoldingRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    /** The web shows nothing for holdings that fail to load (dashboard.js › loadHoldings), so neither does this. */
    fun holdings(): Flow<List<Holding>> = auth.forUser(emptyList()) { user ->
        db.collection("holdings").whereEqualTo("uid", user.uid).live()
            .map { s -> s.documents.map { holdingFrom(it.id, it.data.orEmpty()) } }
            .catch { emit(emptyList()) }
    }
}

@Singleton
class SettingsRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    /** `settings/{uid}`: budget and goals. A missing doc means neither is set. */
    fun settings(): Flow<UserSettings> = auth.forUser(UserSettings()) { user ->
        db.collection("settings").document(user.uid).live().map { settingsFrom(it.data) }
    }
}
