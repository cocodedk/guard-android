package dk.cocode.guard.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dk.cocode.guard.blocklist.BlockList
import dk.cocode.guard.iplist.AddressList
import dk.cocode.guard.iplist.RouteSet
import dk.cocode.guard.notify.clearStoppedAlert
import dk.cocode.guard.notify.postAddressNotice
import dk.cocode.guard.notify.postStoppedAlert
import dk.cocode.guard.notify.updateOngoing
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A local tunnel that ends inside the app: its routes are the fake DNS address and the bad-address ranges. */
class GuardVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private var tunnel: ParcelFileDescriptor? = null
    private var loop: PacketLoop? = null
    private var upstream: Upstream? = null
    private var names: BlockList? = null
    private var routes = RouteSet(emptyMap())
    private var refusals = Refusals()
    private val updates by lazy { AddressListUpdates(AddressListStore(File(filesDir, "iplists"))) }

    // True from the first start until protection ends, however it ends.
    private var active = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopByOwner()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RECHECK) {
            // Only the screen sends this, and only while protecting; never let it start protection.
            if (active) recheck() else stopSelf()
            return START_STICKY
        }
        // Anything else is a start, including the system's own start for Always-on VPN.
        if (!active) start()
        return START_STICKY
    }

    private fun start() {
        active = true
        ProtectionRepository.update {
            it.copy(
                status = ProtectionStatus.Starting, blockedCount = 0, blockedAddressCount = 0,
                addressLists = emptyList(), alwaysOn = isAlwaysOn,
            )
        }
        refusals = Refusals { address, listId, number ->
            postAddressNotice(this, number, address, AddressList.entries.first { it.id == listId }.title)
        }
        showForeground()
        cannotRunReason()?.let { return stopForOther(it) }
        // Everything here after the list load runs on the main thread with no suspension point, and
        // revocation is also posted to the main thread, so it cannot interleave with startup.
        startJob = scope.launch {
            try {
                // The lists load before establish(), so DNS never enters a tunnel that cannot answer yet.
                val (list, stored) = withContext(Dispatchers.IO) {
                    ProtectionRepository.blockList(this@GuardVpnService) to updates.load()
                }
                names = list
                routes = stored.routes
                val fd = establishTunnel(routes) ?: return@launch stopForOther(StopReason.Error)
                tunnel = fd
                val up = Upstream(this@GuardVpnService) {
                    // Called on a network thread; the main thread orders it against stop cleanup.
                    scope.launch { stopForOther(StopReason.PrivateDns) }
                }
                upstream = up
                up.start()
                val packets = newLoop(fd, routes)
                loop = packets
                ProtectionRepository.update {
                    it.copy(status = ProtectionStatus.Protected, listSize = list.size, addressLists = stored.statuses)
                }
                updateOngoing(this@GuardVpnService, ProtectionRepository.state.value)
                // An alert from an earlier stop no longer holds, and left alone it would silence the next one.
                clearStoppedAlert(this@GuardVpnService)
                packets.start()
                launch { updates.watch({ routes }, this@GuardVpnService::swapTunnel) { stopForOther(StopReason.Error) } }
                // Android need not restart the service when lockdown or Always-on change, so look again.
                while (true) {
                    delay(RECHECK_MS)
                    recheck()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Loading the list, establishing or registering the callback failed: clean up and say so.
                stopForOther(StopReason.Error)
            }
        }
    }

    private fun cannotRunReason(): StopReason? = cannotRun(isLockdownEnabled, privateDnsStrict(this))

    /** While protecting: stop if protection can no longer work, else keep the Always-on flag current. */
    private fun recheck() {
        if (ProtectionRepository.state.value.status != ProtectionStatus.Protected) return
        cannotRunReason()?.let { return stopForOther(it) }
        if (ProtectionRepository.state.value.alwaysOn != isAlwaysOn) {
            ProtectionRepository.update { it.copy(alwaysOn = isAlwaysOn) }
            updateOngoing(this, ProtectionRepository.state.value)
        }
    }

    private fun newLoop(fd: ParcelFileDescriptor, routes: RouteSet) = PacketLoop(
        FileInputStream(fd.fileDescriptor), FileOutputStream(fd.fileDescriptor), checkNotNull(names),
        checkNotNull(upstream), routes, refusals,
    ) { scope.launch { stopForOther(StopReason.Error) } }

    /** Brings up a tunnel with the new routes, then retires the old one: its loop stops before its descriptor closes. */
    private fun swapTunnel(next: RouteSet) {
        if (!active) return
        val fd = establishTunnel(next) ?: return stopForOther(StopReason.Error)
        val oldLoop = loop
        val oldTunnel = tunnel
        val packets = newLoop(fd, next)
        tunnel = fd
        loop = packets
        routes = next
        packets.start()
        oldLoop?.stop()
        oldTunnel?.close()
    }

    private fun stopByOwner() {
        release()
        ProtectionRepository.update { it.copy(status = ProtectionStatus.Off) }
        stopSelf()
    }

    private fun stopForOther(reason: StopReason) {
        if (!active) return
        release()
        postStoppedAlert(this, reason)
        ProtectionRepository.update { it.copy(status = ProtectionStatus.Stopped(reason)) }
        stopSelf()
    }

    /** Closes the tunnel, stops the thread and the callback, and removes the ongoing notification. */
    private fun release() {
        active = false
        startJob?.cancel()
        loop?.stop()
        upstream?.stop()
        tunnel?.close()
        startJob = null
        loop = null
        upstream = null
        tunnel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // Android calls this when another VPN app takes over and when the VPN is turned off in settings.
    // It may arrive on any thread, so it is handed to the main thread where start and stop run.
    override fun onRevoke() {
        scope.launch { stopForOther(StopReason.Revoked) }
        super.onRevoke()
    }

    override fun onDestroy() {
        // Destroyed while still protecting: the system ended it, so say so.
        stopForOther(StopReason.Error)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "dk.cocode.guard.STOP"
        private const val ACTION_RECHECK = "dk.cocode.guard.RECHECK"
        private const val RECHECK_MS = 30_000L

        fun startIntent(context: Context) = Intent(context, GuardVpnService::class.java)

        fun stopIntent(context: Context) = startIntent(context).setAction(ACTION_STOP)

        fun recheckIntent(context: Context) = startIntent(context).setAction(ACTION_RECHECK)
    }
}
