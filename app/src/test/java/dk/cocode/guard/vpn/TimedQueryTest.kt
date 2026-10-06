package dk.cocode.guard.vpn

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedQueryTest {
    private val cancels = AtomicInteger()

    private fun cancel() {
        cancels.incrementAndGet()
    }

    @Test
    fun answerIsReturned() = runBlocking {
        val r = timedQuery(5_000) { onAnswer, _ ->
            onAnswer(byteArrayOf(1, 2))
            ::cancel
        }
        assertArrayEquals(byteArrayOf(1, 2), r)
    }

    @Test
    fun silentResolverTimesOutAndIsCancelled() = runBlocking {
        val r = timedQuery(50) { _, _ -> { cancels.incrementAndGet() } }
        assertNull(r)
        assertEquals(1, cancels.get())
    }

    @Test
    fun resolverErrorGivesNull() = runBlocking {
        assertNull(timedQuery(5_000) { _, onError ->
            onError()
            ::cancel
        })
    }

    @Test
    fun startFailureGivesNull() = runBlocking {
        assertNull(timedQuery(5_000) { _, _ -> throw IllegalStateException("no resolver") })
    }

    @Test
    fun cancelledCallerCancelsTheLookup() = runBlocking {
        withTimeoutOrNull(50) { timedQuery(5_000) { _, _ -> { cancels.incrementAndGet() } } }
        assertEquals(1, cancels.get())
    }
}
