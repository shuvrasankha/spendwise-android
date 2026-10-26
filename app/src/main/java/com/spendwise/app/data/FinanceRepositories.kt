package com.spendwise.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.domain.Debt
import com.spendwise.app.domain.DebtInput
import com.spendwise.app.domain.Holding
import com.spendwise.app.domain.HoldingFields
import com.spendwise.app.domain.Income
import com.spendwise.app.domain.IncomeInput
import com.spendwise.app.domain.Payment
import com.spendwise.app.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Live reads of income, debts, holdings and settings, and the writes of all but settings (its slice adds them).

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
    private val collection get() = db.collection("debts")

    /** Your own debts plus those shared with your verified email, from your side (split.js › fetchSharedDebts). */
    fun debts(): Flow<List<Debt>> = auth.forUser(emptyList()) { user ->
        val own = collection.whereEqualTo("uid", user.uid).live()
            .map { s -> s.documents.map { ownDebtFrom(it.id, it.data.orEmpty(), it.metadata.hasPendingWrites()) } }
        // A second where, on withEmail, as the rules require; the web treats a failure as no shared debts
        val shared = if (user.email.isEmpty() || !user.emailVerified) flowOf(emptyList()) else
            collection.whereEqualTo("withEmail", user.email.lowercase()).live()
                .map { s -> s.documents.map { sharedDebtFrom(it.id, it.data.orEmpty(), it.metadata.hasPendingWrites()) } }
                .catch { emit(emptyList()) }
        combine(own, shared) { a, b -> (a + b).sortedByDescending { it.date } }
    }

    /** Shared when [DebtInput.email] is set; the caller only offers that to a verified account. */
    fun add(input: DebtInput): PendingWrite {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        val share = input.email.takeIf { it.isNotEmpty() }?.let { Share.of(it, user.displayName, user.email) }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(newDebtFields(user.uid, input, share, FieldValue.serverTimestamp())))
    }

    fun delete(id: String): PendingWrite = PendingWrite(id, collection.document(id).delete())

    fun pay(id: String, paid: List<Payment>, settles: Boolean, date: String) = update(id, payFields(paid, settles, date, FieldValue.delete()))

    fun removePayment(id: String, paid: List<Payment>) = update(id, mapOf("paid" to paidField(paid)))

    fun claim(id: String, amount: Double, date: String) = update(id, claimFields(amount, date))

    fun clearClaim(id: String) = update(id, clearClaimFields(FieldValue.delete()))

    fun confirmClaim(id: String, paid: List<Payment>, settles: Boolean, date: String) =
        update(id, confirmClaimFields(paid, settles, date, FieldValue.delete()))

    fun reopen(id: String, paid: List<Payment>?) = update(id, reopenFields(paid))

    /** Field-path updates only, so fields this client doesn't know about are kept. */
    private fun update(id: String, fields: Map<String, Any>) = PendingWrite(id, collection.document(id).update(fields))
}

@Singleton
class HoldingRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    private val collection get() = db.collection("holdings")

    /** Throws on a failed read; the Dashboard shows nothing then (dashboard.js › loadHoldings), Investments says why. */
    fun holdings(): Flow<List<Holding>> = auth.forUser(emptyList()) { user ->
        collection.whereEqualTo("uid", user.uid).live()
            .map { s -> s.documents.map { holdingFrom(it.id, it.data.orEmpty()) } }
    }

    fun add(fields: HoldingFields): PendingWrite {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(newHoldingFields(uid, fields, FieldValue.serverTimestamp())))
    }

    /** Field-path update, so uid and createdAt stay as they were (the rules check uid). */
    fun edit(was: Holding, fields: HoldingFields): PendingWrite =
        PendingWrite(was.id, collection.document(was.id).update(editedHoldingFields(fields, was, FieldValue.delete())))

    /** Its entries keep holdingId and description, so a new holding with that name takes them back. */
    fun delete(id: String): PendingWrite = PendingWrite(id, collection.document(id).delete())
}

@Singleton
class SettingsRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {
    /** `settings/{uid}`: budget and goals. A missing doc means neither is set. */
    fun settings(): Flow<UserSettings> = auth.forUser(UserSettings()) { user ->
        db.collection("settings").document(user.uid).live().map { settingsFrom(it.data) }
    }
}
