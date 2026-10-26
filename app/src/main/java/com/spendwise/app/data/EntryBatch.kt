package com.spendwise.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.expenses.PendingWrite
import com.spendwise.app.data.expenses.newExpenseFields
import com.spendwise.app.data.worker.WorkerPush
import com.spendwise.app.domain.ENTRY_DEBT
import com.spendwise.app.domain.ENTRY_EXPENSE
import com.spendwise.app.domain.VoiceEntry
import com.spendwise.app.domain.debtInput
import com.spendwise.app.domain.expenseInput
import com.spendwise.app.domain.incomeInput
import javax.inject.Inject
import javax.inject.Singleton

/** voice-command.js › saveEntry: the reviewed entries in one batch, so all save or none do. */
@Singleton
class EntryBatch @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository, private val push: WorkerPush) {
    /** A new document id; the caller keeps it per entry, so a retry overwrites instead of duplicating (refFor). */
    fun newId(): String = db.collection("expenses").document().id

    fun save(entries: List<VoiceEntry>, ids: List<String>): PendingWrite {
        val user = checkNotNull(auth.currentUser) { "Not signed in" }
        val batch = db.batch()
        val now = FieldValue.serverTimestamp()
        val shared = mutableListOf<String>()
        entries.forEachIndexed { i, e ->
            when (e.type) {
                ENTRY_EXPENSE -> batch.set(db.collection("expenses").document(ids[i]), newExpenseFields(user.uid, e.expenseInput(), now))
                ENTRY_DEBT -> {
                    // firestore.rules › validShare refuses a share from an unverified account, which would fail the whole batch
                    val share = e.email.takeIf { it.isNotEmpty() && user.emailVerified }?.let { Share.of(it, user.displayName, user.email) }
                    if (share != null) shared += ids[i]
                    batch.set(db.collection("debts").document(ids[i]), newDebtFields(user.uid, e.debtInput(), share, now))
                }
                else -> batch.set(db.collection("income").document(ids[i]), newIncomeFields(user.uid, e.incomeInput(), now))
            }
        }
        return PendingWrite(ids.first(), batch.commit().addOnSuccessListener {
            shared.forEach(push::debt)
            push.logged()
        })
    }
}
