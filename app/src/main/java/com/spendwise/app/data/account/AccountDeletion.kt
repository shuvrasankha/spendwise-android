package com.spendwise.app.data.account

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.prefs.DevicePrefs
import com.spendwise.app.reminder.ReminderScheduler
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

// Account deletion (spec §8.2, Q15). Only documents the user owns: uid == theirs in each collection, and their
// settings doc. Debts other people shared with them carry the other person's uid, so they're never touched.

sealed interface DeletionStep {
    val label: String

    /** Every doc in [collection] whose uid is the user's. */
    data class Owned(val collection: String, val uid: String, override val label: String) : DeletionStep

    /** One doc by id. */
    data class Doc(val collection: String, val id: String, override val label: String) : DeletionStep
}

/** In order; settings last, so a retry after a failure still finds everything else by uid. */
fun deletionPlan(uid: String): List<DeletionStep> = listOf(
    DeletionStep.Owned("expenses", uid, "expenses"),
    DeletionStep.Owned("income", uid, "income"),
    DeletionStep.Owned("debts", uid, "debts"),
    DeletionStep.Owned("holdings", uid, "investments"),
    DeletionStep.Doc("settings", uid, "settings"),
)

/** A Firestore batch takes at most 500 writes. */
const val MAX_BATCH = 500

fun <T> deletionBatches(ids: List<T>): List<List<T>> = ids.chunked(MAX_BATCH)

/**
 * Runs the plan, then deletes the sign-in. The caller reauthenticates first, so a failed reauth can't leave an
 * account with no data. Every step reads from the server and is safe to repeat: after a failure partway, running
 * it again deletes what's left, and the sign-in is only deleted once all the data is gone.
 */
@Singleton
class AccountDeleter @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
    private val prefs: DevicePrefs,
    private val reminder: ReminderScheduler,
) {
    /** [progress] gets each step's label and number (1-based) out of the plan's size + 1 for the account. */
    suspend fun deleteEverything(progress: (label: String, step: Int, of: Int) -> Unit) {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val plan = deletionPlan(uid)
        val of = plan.size + 1
        plan.forEachIndexed { i, step ->
            progress(step.label, i + 1, of)
            when (step) {
                is DeletionStep.Owned -> {
                    // From the server, so nothing is missed because the local cache is incomplete
                    val ids = db.collection(step.collection).whereEqualTo("uid", step.uid).get(Source.SERVER).await().documents.map { it.id }
                    deletionBatches(ids).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(db.collection(step.collection).document(it)) }
                        withTimeout(COMMIT_TIMEOUT_MS) { batch.commit().await() }
                    }
                }
                is DeletionStep.Doc -> withTimeout(COMMIT_TIMEOUT_MS) { db.collection(step.collection).document(step.id).delete().await() }
            }
        }
        progress("account", of, of)
        auth.deleteUser()
        reminder.cancel()
        prefs.clear()
    }

    private companion object {
        /** A commit waits for the server; offline it would wait forever. */
        const val COMMIT_TIMEOUT_MS = 30_000L
    }
}
