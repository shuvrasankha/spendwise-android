package com.spendwise.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SharedLiveTest {

    @Test
    fun collectorsShareOneUpstream() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val starts = AtomicInteger()
        val source = MutableStateFlow(1)
        val live = SharedLive(scope, source.onStart { starts.incrementAndGet() })
        withTimeout(5_000) {
            val seen = AtomicInteger()
            val a = async { live.observe().onEach { if (it == 1) seen.incrementAndGet() }.take(2).toList() }
            val b = async { live.observe().onEach { if (it == 1) seen.incrementAndGet() }.take(2).toList() }
            while (seen.get() < 2) yield() // both collecting
            source.value = 2
            assertEquals(listOf(1, 2), a.await())
            assertEquals(listOf(1, 2), b.await())
        }
        assertEquals(1, starts.get())
        scope.cancel()
    }

    @Test
    fun failureReachesCollectorsAndNextObserveRetries() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val attempts = AtomicInteger()
        val live = SharedLive(scope, flow {
            if (attempts.incrementAndGet() == 1) throw IllegalStateException("offline")
            emit("ok")
            awaitCancellation()
        })
        withTimeout(5_000) {
            var error: Throwable? = null
            live.observe().catch { error = it }.toList()
            assertTrue(error is IllegalStateException)
            // The scope survives the failure, and a new collector starts the listener again
            assertEquals("ok", live.observe().first())
        }
        assertEquals(2, attempts.get())
        scope.cancel()
    }

    @Test
    fun cacheIsDroppedWhenSharingStops() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val stopped = MutableStateFlow(false)
        val source = MutableStateFlow("first user")
        val live = SharedLive(scope, source.onCompletion { stopped.value = true })
        withTimeout(10_000) {
            assertEquals("first user", live.observe().first())
            stopped.first { it } // 5 s after the last collector leaves
            source.value = "second user"
            assertEquals("second user", live.observe().first())
        }
        scope.cancel()
    }
}
