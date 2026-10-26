package com.spendwise.app.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.spendwise.app.data.auth.AuthRepository
import com.spendwise.app.data.auth.AuthState
import com.spendwise.app.data.auth.AuthUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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

/** [block]'s data for whoever is signed in, restarted on every sign-in; [signedOut] when no one is. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> AuthRepository.forUser(signedOut: T, block: (AuthUser) -> Flow<T>): Flow<T> =
    authState.flatMapLatest { if (it is AuthState.SignedIn) block(it.user) else flowOf(signedOut) }
