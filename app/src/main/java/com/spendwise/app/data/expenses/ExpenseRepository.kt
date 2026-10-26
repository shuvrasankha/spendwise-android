package com.spendwise.app.data.expenses

import com.spendwise.app.data.worker.WorkerPush
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.forUser
import com.spendwise.app.data.live
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.ExpenseInput
import com.spendwise.app.data.editedEntryFields
import com.spendwise.app.data.newInvestEntryFields
import com.spendwise.app.domain.EntryEdit
import com.spendwise.app.domain.InvestEntryInput
import com.spendwise.app.domain.DebtInput
import com.spendwise.app.data.Share
import com.spendwise.app.data.newDebtFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A write applied to the local cache at once (so it works offline, spec §7.3). [confirmed] waits for the
 * server, and throws if it refused the write (for example PERMISSION_DENIED); offline, it waits until sync.
 */
class PendingWrite(val id: String, private val task: Task<Void>) {
    suspend fun confirmed() {
        task.await()
    }
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val push: WorkerPush,
) {
    private val collection get() = db.collection("expenses")

    /**
     * The signed-in user's expenses, live, in the web's order: newest date first, then Firestore's document
     * order. Ties matter to the repeating-bill helpers. One where on uid, as the rules require (spec §4.3).
     */
    fun expenses(): Flow<List<Expense>> = auth.forUser(emptyList()) { user ->
        collection.whereEqualTo("uid", user.uid).live().map { snapshot ->
            snapshot.documents
                .map { expenseFrom(it.id, it.data.orEmpty(), pending = it.metadata.hasPendingWrites()) }
                .sortedByDescending { it.date }
        }
    }

    suspend fun expense(id: String): Expense? {
        val doc = collection.document(id).get().await()
        return if (doc.exists()) expenseFrom(doc.id, doc.data.orEmpty(), doc.metadata.hasPendingWrites()) else null
    }

    fun add(input: ExpenseInput): PendingWrite {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(newExpenseFields(uid, input, FieldValue.serverTimestamp())).addOnSuccessListener { push.logged() })
    }

    /**
     * addExpense with a split: your share as the expense and a "they owe me" debt per person, in one batch so
     * neither is saved without the other. Returns the write (its id is the expense's) and the debts' ids.
     */
    fun addSplit(input: ExpenseInput, debts: List<DebtInput>): Pair<PendingWrite, List<String>> {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        val batch = db.batch()
        val ref = collection.document()
        batch.set(ref, newExpenseFields(user.uid, input, FieldValue.serverTimestamp()))
        val debtIds = debts.map { d ->
            val share = d.email.takeIf { it.isNotEmpty() }?.let { Share.of(it, user.displayName, user.email) }
            db.collection("debts").document().also { batch.set(it, newDebtFields(user.uid, d, share, FieldValue.serverTimestamp())) }.id
        }
        val commit = batch.commit().addOnSuccessListener {
            debtIds.forEachIndexed { i, id -> if (debts[i].email.isNotEmpty()) push.debt(id) }
            push.logged()
        }
        return PendingWrite(ref.id, commit) to debtIds
    }

    /** Undo for a split: the expense and its debts. */
    fun deleteSplit(id: String, debtIds: List<String>): PendingWrite {
        val batch = db.batch()
        batch.delete(collection.document(id))
        debtIds.forEach { batch.delete(db.collection("debts").document(it)) }
        return PendingWrite(id, batch.commit())
    }

    /** Dashboard › Due this month › Add (dashboard.js › addDue). */
    fun addDue(template: Expense, due: String): PendingWrite {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(dueExpenseFields(uid, template, due, FieldValue.serverTimestamp())).addOnSuccessListener { push.logged() })
    }

    fun edit(id: String, input: ExpenseInput): PendingWrite =
        PendingWrite(id, collection.document(id).update(editedExpenseFields(input)))

    fun delete(id: String): PendingWrite = PendingWrite(id, collection.document(id).delete())

    /** Investments › Put money in (invest.js › addEntry). */
    fun addInvestment(input: InvestEntryInput): PendingWrite {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(newInvestEntryFields(uid, input, FieldValue.serverTimestamp())).addOnSuccessListener { push.logged() })
    }

    /** Investments › the entry dialog (invest.js › saveEntry). */
    fun editInvestment(id: String, edit: EntryEdit, hadHoldingId: Boolean): PendingWrite =
        PendingWrite(id, collection.document(id).update(editedEntryFields(edit, hadHoldingId, FieldValue.delete())))

    /** A deleted holding's SIP stops, so the Dashboard doesn't keep offering it (invest.js › deleteHolding). */
    fun stopRepeat(id: String): PendingWrite = PendingWrite(id, collection.document(id).update("repeat", "stopped"))
}
