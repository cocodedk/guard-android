package dk.cocode.guard.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dk.cocode.guard.MainActivity
import dk.cocode.guard.R
import dk.cocode.guard.vpn.GuardVpnService
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.StopReason

private const val CHANNEL_PROTECTION = "protection"
private const val CHANNEL_ALERTS = "alerts"

const val ONGOING_ID = 1
private const val ALERT_ID = 2

private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

// Channels are created on first use; creating one that exists is a no-op.
private fun ensureChannels(context: Context) {
    manager(context).createNotificationChannels(
        listOf(
            NotificationChannel(
                CHANNEL_PROTECTION, context.getString(R.string.channel_protection), NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH,
            ),
        ),
    )
}

private fun openApp(context: Context) = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
)

/** The ongoing notification: it says in the screen's own words whether the filter works. */
fun ongoingNotification(context: Context, state: ProtectionState): Notification {
    ensureChannels(context)
    val content = ongoingContent(state)
    val builder = Notification.Builder(context, CHANNEL_PROTECTION)
        .setSmallIcon(content.icon)
        .setContentTitle(context.getString(content.title))
        .setContentIntent(openApp(context))
        .setOngoing(true)
    content.text?.let { builder.setContentText(context.getString(it)) }
    if (content.showStop) {
        val stop = PendingIntent.getService(
            context, 0, GuardVpnService.stopIntent(context), PendingIntent.FLAG_IMMUTABLE,
        )
        builder.addAction(
            Notification.Action.Builder(null, context.getString(R.string.notif_action_stop), stop).build(),
        )
    }
    return builder.build()
}

/** Said whenever protection ends without the owner asking for it. */
fun postStoppedAlert(context: Context, reason: StopReason) {
    ensureChannels(context)
    val content = alertContent(reason)
    val body = context.getString(content.body)
    val alert = Notification.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_shield_stopped)
        .setContentTitle(context.getString(content.title))
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setContentIntent(openApp(context))
        .setAutoCancel(true)
        .setOnlyAlertOnce(content.onlyAlertOnce)
        .build()
    manager(context).notify(ALERT_ID, alert)
}

fun clearStoppedAlert(context: Context) = manager(context).cancel(ALERT_ID)

/** False when notifications are off for the app or for its alerts channel: a stop could not be told. */
fun alertsAllowed(context: Context): Boolean {
    val m = manager(context)
    val channel = m.getNotificationChannel(CHANNEL_ALERTS) // null until first use, when it is created on
    return m.areNotificationsEnabled() && (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
}

fun updateOngoing(context: Context, state: ProtectionState) {
    manager(context).notify(ONGOING_ID, ongoingNotification(context, state))
}
