package dk.cocode.guard.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.LinkProperties
import android.net.Network
import android.os.CancellationSignal
import java.util.concurrent.Executor

/** True when Private DNS names a host (strict mode), the only mode that bypasses the filter. */
fun privateDnsStrict(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val link = cm.activeNetwork?.let { cm.getLinkProperties(it) }
    return link?.privateDnsServerName != null
}

/** What the packet loop needs from the network side; faked in JVM tests. */
interface DnsUpstream {
    val hasNetwork: Boolean

    /** The resolver's answer, or null on timeout or error (the asking app retries). */
    suspend fun query(payload: ByteArray): ByteArray?
}

/**
 * Forwards lookups through Android's own resolver, which talks to the network's DNS server and
 * encrypts the lookup whenever Private DNS is on, and tracks the default network while protecting.
 */
class Upstream(context: Context, private val onLinkChanged: () -> Unit) : DnsUpstream {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    override var hasNetwork = true
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            hasNetwork = true
        }

        override fun onLost(network: Network) {
            hasNetwork = false
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val strict = linkProperties.privateDnsServerName != null
            ProtectionRepository.update { it.copy(privateDnsStrict = strict) }
            onLinkChanged()
        }
    }

    fun start() {
        hasNetwork = cm.activeNetwork != null
        cm.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    override suspend fun query(payload: ByteArray): ByteArray? = timedQuery(QUERY_TIMEOUT_MS) { onAnswer, onError ->
        val signal = CancellationSignal()
        // A null network is the app's default one: the real network, as the app is excluded.
        DnsResolver.getInstance().rawQuery(
            null, payload, DnsResolver.FLAG_EMPTY, Executor { it.run() }, signal,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) = onAnswer(answer)
                override fun onError(error: DnsResolver.DnsException) = onError()
            },
        )
        signal::cancel
    }
}
