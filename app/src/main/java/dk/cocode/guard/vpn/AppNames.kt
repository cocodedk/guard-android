package dk.cocode.guard.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import dk.cocode.guard.R
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * The name of the app that owns a connection through the tunnel, or null when Android cannot tell.
 * Asked before the refusal is sent, while the app's socket still exists. Only an app Android lets
 * this one see (any app with a launcher icon, through the manifest's `<queries>`) is named.
 */
internal fun Context.appOwning(protocol: Int, src: ByteArray, srcPort: Int, dst: ByteArray, dstPort: Int): String? {
    val uid = try {
        getSystemService(ConnectivityManager::class.java).getConnectionOwnerUid(
            protocol,
            InetSocketAddress(InetAddress.getByAddress(src), srcPort),
            InetSocketAddress(InetAddress.getByAddress(dst), dstPort),
        )
    } catch (e: Exception) {
        return null
    }
    if (uid == Process.INVALID_UID) return null
    val pm = packageManager
    val labels = pm.getPackagesForUid(uid).orEmpty().mapNotNull { name ->
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString() }.getOrNull()
    }.distinct()
    return when {
        labels.isNotEmpty() -> labels.take(2).joinToString(" / ") // a shared UID: name two, never guess one
        uid < Process.FIRST_APPLICATION_UID -> getString(R.string.app_android_system)
        else -> null
    }
}
