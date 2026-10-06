# Cocode Guard for Android

Blocks dangerous sites and ads for every app on the phone, on any network: at home, on mobile data,
on café Wi-Fi, abroad. The [Cocode Guard box](https://guard.cocode.dk) protects the house; this app
protects the phone when it leaves the house. It works on its own, without the box.

**Free software:** GPL-3.0 and free of charge. No account, no analytics, no ads, and no server run by us. The app contacts only the network's own DNS server and, to download its address lists, the publishers of those lists (see below).

Status: early. The first version with protection is being built.

## Website

<https://android.guard.cocode.dk> (Danish) · <https://android.guard.cocode.dk/en/> (English) ·
[Privacy policy](https://android.guard.cocode.dk/en/privacy/)

## Download

- GitHub: <https://github.com/cocodedk/guard-android/releases/latest/download/GuardAndroid.apk>
- F-Droid: planned.

## How it works

Android lets one app see the phone's DNS lookups through its VPN feature (`VpnService`). Cocode Guard
uses that feature **only as a local tunnel that ends inside the app**. There is no VPN server.

1. The tunnel's routes are a fake DNS address and the ranges on public lists of known-bad IP
   addresses, so only DNS lookups and connections to those addresses enter it. Web pages, video and
   app data go out exactly as before.
2. Each lookup is checked against the block list (the AdGuard DNS filter, shipped inside the app).
3. A blocked name gets an empty answer (`0.0.0.0`). Every other lookup goes to the network's own DNS
   server, and the answer comes back through the tunnel.
4. A connection from any app to a listed address is refused inside the app (a TCP reset, or an ICMP
   "administratively prohibited" reply for UDP). Nothing is relayed to the internet. The app counts
   it, and a notification names the app Android says made it, the address and the list.

### Address lists

Some apps and malware connect straight to an IP address and never ask DNS, so the name list can't
stop them. While protection runs, the app downloads these lists **on the phone, straight from their
publishers, over HTTPS**: Spamhaus DROP (`drop_v4.json`, `drop_v6.json`) and the abuse.ch Feodo
Tracker recommended IP blocklist. There is no Cocode server in between. Details:

- Only those three files are fetched. They are data, parsed and never run.
- Normally once a day. A failed download is retried after at least an hour.
- Each list is stored in the app's private files. A list older than 7 days is paused, and the screen
  says so: old lists can block addresses that others use now.
- Each publisher sees what any web server sees: the phone's IP address, the time and the ordinary
  details of a request (Android's default user agent). The app sends no account, identifier or data
  about the user or about what was blocked.
- The name of the app that made a blocked connection comes from Android, on the phone. It appears
  only in the notification; the app writes nothing about blocked connections to disk and sends nothing anywhere.

Android shows its VPN key icon while the app protects the phone. That icon is Android's, not a sign
of a remote VPN.

### Limits, said plainly

- Android's **Private DNS** set to a specific server can't be used with the app: protection stops
  and says so. "Automatic" works as normal.
- Apps with **their own secure DNS** (for example a browser's DoH setting) bypass it too.
- Android allows **one VPN at a time**, so the app can't run alongside a work VPN.
- If protection stops, a notification says so: DNS lookups are no longer filtered.
- Android's "Block connections without VPN" can't be used with the app: its tunnel carries only DNS
  lookups and connections to listed addresses, so with that setting the phone can't reach the internet.
- Address blocking needs downloaded lists. A list that is missing, or older than 7 days, is not used,
  and the screen says so. The name filter works regardless. No list catches everything.

## Accessibility

Built for blind and low-vision people from the start: every control has a spoken TalkBack label,
status changes are announced, text scales to 200%, and a unit test holds every color pair to WCAG
AA contrast.

## Permissions

You grant only the VPN connection and notifications. Network access (to pass lookups on and to
download the address lists) is an install-time permission Android grants automatically. No contacts,
location, storage or accessibility access. The manifest also declares `<queries>` for apps with a
launcher icon, so a notification can name the app that tried to connect; it is not a permission, and
`QUERY_ALL_PACKAGES` is not used.

## Build from source

Needs a JDK 17+ and the Android SDK (set `ANDROID_HOME` or `sdk.dir` in `local.properties`).

```sh
./gradlew buildSmoke        # debug build + unit tests + lint: the one canonical check
./gradlew assembleRelease   # unsigned unless the signing environment variables are set
bash scripts/install-hooks.sh
```

## Architecture

Kotlin and Jetpack Compose, one `app` module, no Google libraries. Package root `dk.cocode.guard`:

| Package | Holds |
|---|---|
| `ui/` | Compose screens |
| `ui/theme/` | The palette (`GuardColors`, shared with the box's site) and `GuardTheme` |

Feature work is specified in [`docs/lean/`](docs/lean/), one file per feature.

## Third-party data

`app/src/main/assets/adguard-dns-filter.txt` is the [AdGuard DNS filter](https://github.com/AdguardTeam/AdGuardSDNSFilter)
(GPL-3.0), snapshot of 2026-10-05.

The address lists are **not** in this repository or in the APK; the phone downloads them itself
(see [Address lists](#address-lists)). Tests use small hand-written fixtures.

- **Spamhaus DROP** (IPv4 and IPv6), <https://www.spamhaus.org/drop/>, by The Spamhaus Project. Free
  to use. Its content is copyrighted and comes with no licence to redistribute it, which is why the
  app downloads it instead of shipping it. Credit: The Spamhaus Project. The name appears on the
  app's screen and in its notifications.
- **abuse.ch Feodo Tracker**, recommended IP blocklist,
  <https://feodotracker.abuse.ch/blocklist/>. Its terms state that all datasets can be used for
  commercial and non-commercial purposes without limitations (CC0).

## Author

Babak Bandpey, [Cocode](https://cocode.dk).

## License

[GPL-3.0-or-later](LICENSE) (SPDX `GPL-3.0-or-later`).
