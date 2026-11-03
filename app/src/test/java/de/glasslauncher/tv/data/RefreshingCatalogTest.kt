package de.glasslauncher.tv.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RefreshingCatalogTest {
    @Test fun resumesReuseSnapshotAndInvalidationReloads() = runBlocking {
        var calls=0
        val cache=RefreshingCatalog { listOf(++calls) }
        assertEquals(listOf(1),cache.get())
        assertEquals(listOf(1),cache.get())
        cache.invalidate()
        assertEquals(listOf(2),cache.get())
    }
    @Test fun concurrentRequestsShareOneLoad() = runBlocking {
        var calls=0; val gate=CompletableDeferred<Unit>()
        val cache=RefreshingCatalog { calls++; gate.await(); listOf("app") }
        val one=async { cache.get() }; val two=async { cache.get() }
        yield(); gate.complete(Unit)
        assertEquals(one.await(),two.await()); assertEquals(1,calls)
    }
    @Test fun invalidationDuringLoadIsNotLost() = runBlocking {
        val gate=CompletableDeferred<Unit>(); var calls=0
        val cache=RefreshingCatalog { calls++; if(calls==1) gate.await(); listOf(calls) }
        val one=async { cache.get() }; yield(); cache.invalidate(); gate.complete(Unit); one.await()
        assertEquals(listOf(2),cache.get())
    }
    @Test fun cancellationDoesNotPublishPartialSnapshot() = runBlocking {
        var calls=0
        val cache=RefreshingCatalog { calls++; if(calls==1) awaitCancellation(); listOf("complete") }
        val one=launch { cache.get() }; yield(); one.cancelAndJoin()
        assertEquals(listOf("complete"),cache.get()); assertEquals(2,calls)
    }
}
