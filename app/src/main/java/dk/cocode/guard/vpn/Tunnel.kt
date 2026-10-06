package dk.cocode.guard.vpn

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import dk.cocode.guard.MainActivity
import dk.cocode.guard.R
import dk.cocode.guard.iplist.RouteSet
import dk.cocode.guard.iplist.ipText
import dk.cocode.guard.notify.ONGOING_ID
import dk.cocode.guard.notify.ongoingNotification

/** Establishes the local tunnel: the fake DNS address and one route per bad-address range. */
internal fun VpnService.establishTunnel(routes: RouteSet): ParcelFileDescriptor? = this.Builder()
    .addAddress("10.111.222.1", 32)
    .addAddress("fd47:7561:7264::1", 128)
    .addDnsServer(DNS_ADDRESS)
    .addRoute(DNS_ADDRESS, 32)
    .also { b -> routes.routes.forEach { b.addRoute(ipText(it.address), it.prefix) } }
    .allowFamily(OsConstants.AF_INET)
    .allowFamily(OsConstants.AF_INET6)
    .addDisallowedApplication(packageName)
    .setMtu(1500)
    // Android counts a VPN as metered unless told otherwise, which would make Wi-Fi look metered
    // to every app; unmetered, the tunnel takes its meteredness from the real network.
    .setMetered(false)
    .setSession(getString(R.string.app_name))
    .setConfigureIntent(
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
    )
    .setBlocking(true)
    .establish()

internal fun VpnService.showForeground() {
    val notification = ongoingNotification(this, ProtectionRepository.state.value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
    } else {
        startForeground(ONGOING_ID, notification)
    }
}
