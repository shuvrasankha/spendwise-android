package com.spendwise.app.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.auth.AuthUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import com.google.firebase.perf.FirebasePerformance

/** Live results, including pending-write changes so "waiting to sync" clears once a write lands. Errors end the flow. */
fun Query.live(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
        if (error != null) close(error) else trySend(snapshot!!)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.live(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else trySend(snapshot!!)
    }
    awaitClose { registration.remove() }
}

/**
 * A Performance Monitoring trace from collection to the first value, with [count] as its `entries` metric:
 * how long a screen waits for its first list. Dropped if the flow is cancelled first. Release builds only.
 */
fun <T> Flow<T>.traceFirst(name: String, count: (T) -> Int): Flow<T> = flow {
    val trace = FirebasePerformance.getInstance().newTrace(name).apply { start() }
    var first = true
    collect {
        if (first) {
            first = false
            trace.putMetric("entries", count(it).toLong())
            trace.stop()
        }
        emit(it)
    }
}

/** [block]'s data for whoever is signed in, restarted on every sign-in; [signedOut] when no one is. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> AuthRepository.forUser(signedOut: T, block: (AuthUser) -> Flow<T>): Flow<T> =
    authState.flatMapLatest { if (it is AuthState.SignedIn) block(it.user) else flowOf(signedOut) }

/**
 * One collection of [upstream] shared by every [observe]r, so screens open at once share a listener and the
 * snapshot is mapped once. Runs in [scope] (the app scope) while anyone collects, plus 5 s; the cached value
 * is dropped when it stops, so a later sign-in never sees the last user's data.
 *
 * An upstream error reaches the collectors open at the time, as it did before sharing. The next [observe]
 * after a failure starts the listener again, so "Try again" still retries.
 */
class SharedLive<T>(scope: CoroutineScope, upstream: Flow<T>) {
    private sealed interface Item<out T>
    private class Value<T>(val value: T) : Item<T>
    /** A class, not data, so a stale failure is told apart from a new one by identity. */
    private class Failed(val error: Throwable) : Item<Nothing>

    private val retries = MutableStateFlow(0)

    private val shared = flow<Item<T>> {
        while (true) {
            val attempt = retries.value
            var error: Throwable? = null
            upstream.catch { error = it }.collect { emit(Value(it)) }
            emit(Failed(error ?: return@flow))
            retries.first { it != attempt }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0), replay = 1)

    fun observe(): Flow<T> = flow {
        val stale = shared.replayCache.firstOrNull() as? Failed
        if (stale != null) retries.update { it + 1 }
        emitAll(shared.filter { it !== stale }.map {
            when (it) {
                is Value -> it.value
                is Failed -> throw it.error
            }
        })
    }
}
