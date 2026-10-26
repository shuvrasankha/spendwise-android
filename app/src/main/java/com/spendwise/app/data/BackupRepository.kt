package com.spendwise.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.domain.BACKUP_BATCH
import com.spendwise.app.domain.BackupDocs
import com.spendwise.app.domain.RestoreWrite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** This account's documents per collection, as backup.js › exportData reads them. */
data class BackupData(val expenses: BackupDocs, val income: BackupDocs, val debts: BackupDocs, val holdings: BackupDocs)

/** Backup & restore (spec §7.8): reads every own document, and writes a restore plan in batches. */
@Singleton
class BackupRepository @Inject constructor(private val db: FirebaseFirestore, private val auth: AuthRepository) {

    val uid: String get() = checkNotNull(auth.currentUid) { "Not signed in" }

    suspend fun read(): BackupData {
        val uid = uid
        suspend fun docs(collection: String): BackupDocs =
            db.collection(collection).whereEqualTo("uid", uid).get().await().documents.map { it.id to plainFields(it.data.orEmpty()) }
        val holdings = try {
            docs("holdings")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList() // as the web: none before the updated rules are published
        }
        return BackupData(docs("expenses"), docs("income"), docs("debts"), holdings)
    }

    /**
     * Writes [writes] in batches of 500, each doc with a fresh createdAt. A whole-document set, unlike
     * every other write here: restore must recreate deleted docs, and the web's restore does the same.
     * Reusing ids means a retry after a failed chunk overwrites instead of duplicating.
     */
    suspend fun restore(writes: List<RestoreWrite>, progress: (done: Int, of: Int) -> Unit) {
        writes.chunked(BACKUP_BATCH).forEachIndexed { i, chunk ->
            val batch = db.batch()
            for (w in chunk) {
                val ref = w.id?.let { db.collection(w.collection).document(it) } ?: db.collection(w.collection).document()
                batch.set(ref, w.fields + ("createdAt" to FieldValue.serverTimestamp()))
            }
            progress(minOf((i + 1) * BACKUP_BATCH, writes.size), writes.size)
            withTimeout(COMMIT_TIMEOUT_MS) { batch.commit().await() }
        }
    }

    private companion object {
        /** A commit waits for the server; offline it would wait forever. The ids make a retry safe. */
        const val COMMIT_TIMEOUT_MS = 30_000L
    }
}

/** Firestore values as plain Kotlin; a Timestamp becomes what JSON.stringify writes for it. */
private fun plain(v: Any?): Any? = when (v) {
    is Timestamp -> linkedMapOf("seconds" to v.seconds, "nanoseconds" to v.nanoseconds.toLong())
    is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to plain(x) }
    is List<*> -> v.map(::plain)
    else -> v
}

@Suppress("UNCHECKED_CAST")
private fun plainFields(fields: Map<String, Any>): Map<String, Any?> = plain(fields) as Map<String, Any?>
