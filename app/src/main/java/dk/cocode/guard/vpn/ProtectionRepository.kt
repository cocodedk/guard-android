package dk.cocode.guard.vpn

import android.content.Context
import dk.cocode.guard.blocklist.BlockList
import dk.cocode.guard.blocklist.parseRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ProtectionStatus {
    data object Off : ProtectionStatus
    data object Starting : ProtectionStatus
    data object Protected : ProtectionStatus
    data object PermissionRefused : ProtectionStatus
    data class Stopped(val reason: StopReason) : ProtectionStatus
}

enum class StopReason { Revoked, Lockdown, PrivateDns, Error }

/**
 * Why protection cannot run, or null if it can. Lockdown lets no traffic past a tunnel that carries
 * only DNS; with Private DNS set to a server, Android sends its lookups into the tunnel, which only
 * reaches the fake DNS address. Either way the phone would have no internet.
 */
fun cannotRun(lockdown: Boolean, privateDnsStrict: Boolean): StopReason? = when {
    lockdown -> StopReason.Lockdown
    privateDnsStrict -> StopReason.PrivateDns
    else -> null
}

data class ProtectionState(
    val status: ProtectionStatus = ProtectionStatus.Off,
    val blockedCount: Int = 0, // since the current start; reset to 0 on each start
    val listSize: Int = 0, // usable block rules loaded
    val alwaysOn: Boolean = false, // VpnService.isAlwaysOn() at the last start
)

/** The one process-wide record of protection, shared by the service and the screen. */
object ProtectionRepository {
    private val mutableState = MutableStateFlow(ProtectionState())
    val state: StateFlow<ProtectionState> = mutableState.asStateFlow()

    private var blockList: BlockList? = null

    fun update(change: (ProtectionState) -> ProtectionState) = mutableState.update(change)

    /** Loads the shipped list on first use and keeps it for later starts. Call off the main thread. */
    @Synchronized
    fun blockList(context: Context): BlockList = blockList ?: context.assets.open(LIST_ASSET)
        .bufferedReader().use { parseRules(it.lineSequence()) }
        .also { blockList = it }

    private const val LIST_ASSET = "adguard-dns-filter.txt"
}
