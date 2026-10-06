package dk.cocode.guard.vpn

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

const val QUERY_TIMEOUT_MS = 5_000L

/**
 * Runs one asynchronous lookup and waits at most [timeoutMs]. [send] starts it, reports the outcome
 * through its two callbacks and returns a function that cancels it. On timeout, cancellation or error
 * the lookup is cancelled and the result is null (the asking app retries). Android-free, so it is
 * unit-tested; [Upstream] plugs `DnsResolver` into it.
 */
suspend fun timedQuery(
    timeoutMs: Long,
    send: (onAnswer: (ByteArray) -> Unit, onError: () -> Unit) -> () -> Unit,
): ByteArray? = withTimeoutOrNull(timeoutMs) {
    suspendCancellableCoroutine<ByteArray?> { cont ->
        val cancel = try {
            send({ if (cont.isActive) cont.resume(it) }, { if (cont.isActive) cont.resume(null) })
        } catch (e: Exception) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { cancel() }
    }
}
