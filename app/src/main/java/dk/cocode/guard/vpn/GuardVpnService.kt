package dk.cocode.guard.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import dk.cocode.guard.MainActivity
import dk.cocode.guard.R
import dk.cocode.guard.notify.ONGOING_ID
import dk.cocode.guard.notify.ongoingNotification
import dk.cocode.guard.notify.postStoppedAlert
import dk.cocode.guard.notify.updateOngoing
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A local tunnel that ends inside the app: its only route is the fake DNS address. */
class GuardVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private var tunnel: ParcelFileDescriptor? = null
    private var loop: PacketLoop? = null
    private var upstream: Upstream? = null

    // True from the first start until protection ends, however it ends.
    private var active = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopByOwner()
            return START_NOT_STICKY
        }
        // Anything else is a start, including the system's own start for Always-on VPN.
        if (!active) start()
        return START_STICKY
    }

    private fun start() {
        active = true
        ProtectionRepository.update {
            it.copy(
                status = ProtectionStatus.Starting, blockedCount = 0, alwaysOn = isAlwaysOn,
                privateDnsStrict = privateDnsStrict(this),
            )
        }
        showForeground()
        if (isLockdownEnabled) {
            // Lockdown lets no traffic past the tunnel, and this one carries only DNS.
            stopForOther(StopReason.Lockdown)
            return
        }
        // Everything here after the list load runs on the main thread with no suspension point, and
        // revocation is also posted to the main thread, so it cannot interleave with startup.
        startJob = scope.launch {
            try {
                // The list loads before establish(), so DNS never enters a tunnel that cannot answer yet.
                val list = withContext(Dispatchers.IO) { ProtectionRepository.blockList(this@GuardVpnService) }
                val fd = establishTunnel()
                if (fd == null) {
                    stopForOther(StopReason.Error)
                    return@launch
                }
                tunnel = fd
                val up = Upstream(this@GuardVpnService) {
                    // Called on a network thread; the main thread orders it against stop cleanup.
                    scope.launch { refreshNotification() }
                }
                upstream = up
                up.start()
                val packets = PacketLoop(
                    FileInputStream(fd.fileDescriptor), FileOutputStream(fd.fileDescriptor), list, up,
                ) { scope.launch { stopForOther(StopReason.Error) } }
                loop = packets
                ProtectionRepository.update { it.copy(status = ProtectionStatus.Protected, listSize = list.size) }
                refreshNotification()
                packets.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Loading the list, establishing or registering the callback failed: clean up and say so.
                stopForOther(StopReason.Error)
            }
        }
    }

    private fun establishTunnel(): ParcelFileDescriptor? = Builder()
        .addAddress("10.111.222.1", 32)
        .addDnsServer(DNS_ADDRESS)
        .addRoute(DNS_ADDRESS, 32)
        .allowFamily(OsConstants.AF_INET)
        .allowFamily(OsConstants.AF_INET6)
        .addDisallowedApplication(packageName)
        .setMtu(1500)
        .setSession(getString(R.string.app_name))
        .setConfigureIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .setBlocking(true)
        .establish()

    private fun showForeground() {
        val notification = ongoingNotification(this, ProtectionRepository.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(ONGOING_ID, notification)
        }
    }

    private fun refreshNotification() {
        if (active) updateOngoing(this, ProtectionRepository.state.value)
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

        fun startIntent(context: Context) = Intent(context, GuardVpnService::class.java)

        fun stopIntent(context: Context) = startIntent(context).setAction(ACTION_STOP)
    }
}
