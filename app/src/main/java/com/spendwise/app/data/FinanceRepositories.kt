package com.spendwise.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.IncomeInput
import com.spendwise.app.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Live reads of income, debts, holdings and settings, and income's writes. The others' writes arrive with their slices.

@Singleton
class IncomeRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    private val collection get() = db.collection("income")

    fun income(): Flow<List<Income>> = auth.forUser(emptyList()) { user ->
        collection.whereEqualTo("uid", user.uid).live().map { s ->
            s.documents.map { incomeFrom(it.id, it.data.orEmpty(), it.metadata.hasPendingWrites()) }.sortedByDescending { it.date }
        }
    }

    suspend fun income(id: String): Income? {
        val doc = collection.document(id).get().await()
        return if (doc.exists()) incomeFrom(doc.id, doc.data.orEmpty(), doc.metadata.hasPendingWrites()) else null
    }

    fun add(input: IncomeInput): PendingWrite {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(newIncomeFields(uid, input, FieldValue.serverTimestamp())))
    }

    fun edit(id: String, input: IncomeInput): PendingWrite = PendingWrite(id, collection.document(id).update(editedIncomeFields(input)))

    fun delete(id: String): PendingWrite = PendingWrite(id, collection.document(id).delete())
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
