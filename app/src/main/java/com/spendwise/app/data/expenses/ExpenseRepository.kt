package com.spendwise.app.data.expenses

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.domain.Expense
import com.spendwise.app.domain.ExpenseInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
) {
    private val collection get() = db.collection("expenses")

    /** The signed-in user's expenses, newest date first, live. Follows sign-in and sign-out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun expenses(): Flow<List<Expense>> = auth.authState.flatMapLatest { state ->
        if (state is AuthState.SignedIn) expensesOf(state.user.uid) else flowOf(emptyList())
    }

    // One where on uid, as the rules require (spec §4.3); sorted here, so no index is needed
    private fun expensesOf(uid: String): Flow<List<Expense>> = callbackFlow {
        val registration = collection.whereEqualTo("uid", uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val expenses = snapshot!!.documents.map { doc ->
                    expenseFrom(doc.id, doc.data.orEmpty(), pending = doc.metadata.hasPendingWrites())
                }
                trySend(expenses.sortedByDescending { it.date })
            }
        awaitClose { registration.remove() }
    }

    suspend fun expense(id: String): Expense? {
        val doc = collection.document(id).get().await()
        return if (doc.exists()) expenseFrom(doc.id, doc.data.orEmpty(), doc.metadata.hasPendingWrites()) else null
    }

    fun add(input: ExpenseInput): PendingWrite {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        val ref = collection.document()
        return PendingWrite(ref.id, ref.set(newExpenseFields(uid, input, FieldValue.serverTimestamp())))
    }

    fun edit(id: String, input: ExpenseInput): PendingWrite =
        PendingWrite(id, collection.document(id).update(editedExpenseFields(input)))

    fun delete(id: String): PendingWrite = PendingWrite(id, collection.document(id).delete())
}
