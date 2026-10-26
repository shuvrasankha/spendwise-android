package com.spendwise.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.worker.WorkerPush
import com.spendwise.app.domain.CSV_BATCH
import com.spendwise.app.domain.CsvTx
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** csv-upload.js › saveToFirestore's income doc: no `encoding` (web quirk); the description is the source. */
fun csvIncomeFields(uid: String, tx: CsvTx, createdAt: Any): Map<String, Any> = linkedMapOf(
    "uid" to uid,
    "amount" to toFirestoreNumber(tx.amount),
    "source" to tx.description,
    "date" to tx.date,
    "paymentType" to if (tx.payment == "Cash") "Cash" else "Online",
    "bank" to "",
    "notes" to tx.notes,
    "createdAt" to createdAt,
)

/** The expense doc: no `tags` (web quirk); readers take a missing tags as none. */
fun csvExpenseFields(uid: String, tx: CsvTx, createdAt: Any): Map<String, Any> = linkedMapOf(
    "uid" to uid,
    "amount" to toFirestoreNumber(tx.amount),
    "category" to tx.category,
    "date" to tx.date,
    "payment" to tx.payment,
    "cardName" to "",
    "description" to tx.description,
    "notes" to tx.notes,
    "encoding" to "plain",
    "createdAt" to createdAt,
)

@Singleton
class CsvImporter @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository, private val push: WorkerPush) {
    /** A new document id, kept per row so "try again" overwrites instead of duplicating. */
    fun newId(): String = db.collection("expenses").document().id

    /**
     * saveToFirestore: at most 500 writes per batch, each saved fully or not at all. Waits for the server, as the
     * web does; returns how many expenses and income entries were saved.
     */
    suspend fun save(rows: List<CsvTx>, ids: List<String>): Pair<Int, Int> {
        val uid = checkNotNull(auth.currentUid) { "Not signed in" }
        rows.indices.chunked(CSV_BATCH).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { i ->
                val tx = rows[i]
                if (tx.type == "income") batch.set(db.collection("income").document(ids[i]), csvIncomeFields(uid, tx, FieldValue.serverTimestamp()))
                else batch.set(db.collection("expenses").document(ids[i]), csvExpenseFields(uid, tx, FieldValue.serverTimestamp()))
            }
            batch.commit().await()
        }
        push.logged()
        return rows.count { it.type != "income" } to rows.count { it.type == "income" }
    }
}
