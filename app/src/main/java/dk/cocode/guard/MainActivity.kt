package dk.cocode.guard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dk.cocode.guard.notify.alertsAllowed
import dk.cocode.guard.ui.AboutLink
import dk.cocode.guard.ui.AboutScreen
import dk.cocode.guard.ui.aboutUrl
import dk.cocode.guard.ui.versionLine
import dk.cocode.guard.ui.HomeAction
import dk.cocode.guard.ui.HomeCard
import dk.cocode.guard.ui.HomeScreen
import dk.cocode.guard.ui.theme.GuardTheme
import dk.cocode.guard.vpn.GuardVpnService
import dk.cocode.guard.vpn.ProtectionRepository
import dk.cocode.guard.vpn.ProtectionStatus
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notificationsAllowed = mutableStateOf(true)

    // The answer does not block starting: the VPN consent comes next either way.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpnConsent() }

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startProtection()
        } else {
            ProtectionRepository.update { state -> state.copy(status = ProtectionStatus.PermissionRefused) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val version = packageManager.getPackageInfo(packageName, 0).let { versionLine(it.versionName.orEmpty(), it.longVersionCode) }
        setContent {
            val state by ProtectionRepository.state.collectAsState()
            var showAbout by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showAbout) { showAbout = false }
            GuardTheme {
                if (showAbout) {
                    AboutScreen(version, ::openLink) { showAbout = false }
                } else {
                    HomeScreen(state, notificationsAllowed.value, ::onAction, ::onCardAction) { showAbout = true }
                }
            }
        }
    }

    /** False when no app can open the link. */
    private fun openLink(link: AboutLink): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aboutUrl(link, Locale.getDefault().language))))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed.value = alertsAllowed(this)
        // Back from settings, lockdown, Private DNS or Always-on may have changed under a running tunnel.
        if (ProtectionRepository.state.value.status == ProtectionStatus.Protected) {
            startService(GuardVpnService.recheckIntent(this))
        }
    }

    private fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Start, HomeAction.StartAgain, HomeAction.TryAgain -> start()
            HomeAction.Stop -> startService(GuardVpnService.stopIntent(this))
            HomeAction.Starting, HomeAction.None -> Unit
        }
    }

    private fun start() {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpnConsent()
    }

    private fun requestVpnConsent() {
        val consent = VpnService.prepare(this)
        if (consent == null) startProtection() else vpnConsent.launch(consent)
    }

    private fun startProtection() {
        startForegroundService(GuardVpnService.startIntent(this))
    }

    private fun onCardAction(card: HomeCard) {
        when (card) {
            HomeCard.StoppedPrivateDns -> openSettings(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            HomeCard.NotificationsOff -> openSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
            HomeCard.AlwaysOn, HomeCard.StoppedLockdown -> openSettings(Intent(Settings.ACTION_VPN_SETTINGS))
            else -> Unit
        }
    }

    // Opens the first intent some activity handles.
    private fun openSettings(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
    }
}
