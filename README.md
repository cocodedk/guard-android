# Guard for Android

Blocks dangerous sites and ads for every app on your phone, on any network: at home, on mobile data,
on café Wi-Fi, abroad. The [Cocode Guard box](https://guard.cocode.dk) protects the house; this app
protects the phone when it leaves the house. It works on its own, without the box.

**Free software:** GPL-3.0 and free of charge. No account, no analytics, no ads, and no server run by us. The app contacts only the network's own DNS server and, to download its address lists, the publishers of those lists (see below).

Status: early. The first version is out.

## Website

<https://android.guard.cocode.dk> (Danish) · <https://android.guard.cocode.dk/en/> (English) ·
[Privacy policy](https://android.guard.cocode.dk/en/privacy/)

## Download

<!-- cocode-apps:install:start -->
- Coming to F-Droid
- [Download the APK from GitHub](https://github.com/cocodedk/guard-android/releases/latest/download/GuardAndroid.apk)
- [Auto-update the GitHub APK with Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cocodedk/guard-android)
<!-- cocode-apps:install:end -->

After installing, open the app and tap **Start protection** (*Start beskyttelse* in Danish). Android
asks for your permission to set up the VPN connection and to send notifications. The screen always
says whether the phone is protected. You stop protection with **Stop protection**.

## Features

Blocks dangerous sites and ads in every app on the phone, on any network: home Wi-Fi, mobile data,
café Wi-Fi, abroad. What sets it apart:

- **It blocks dangerous addresses too, without taking in all your traffic.** Some malware skips DNS
  and connects straight to an IP address. The app refuses connections to addresses on public lists of
  known criminal networks and botnet servers. Only DNS lookups and those connections enter its local
  tunnel; all other traffic stays off it. No VPN server, no account, no server run by us.
- **It names the app that tried.** The notification for a blocked address names the app as Android
  reports it ("Chrome tried to connect to …"), and the Recent blocks list on the home screen groups
  blocks by app.
- **It never fails silently.** When protection stops without the owner asking, a notification says
  DNS lookups are no longer filtered. Private DNS set to a specific server, and "Block connections
  without VPN", would cut the phone off while a DNS-only tunnel runs, so the app detects both, stops
  and says why.
- **It keeps encrypted DNS encrypted.** Lookups that aren't blocked go out through Android's own
  resolver (`DnsResolver`), so with Private DNS on Automatic they stay encrypted.
- **Nothing to configure.** One button, no lists to choose. Danish and English, and built for
  TalkBack from the start (see [Accessibility](#accessibility)).

## How it works

Whenever an app opens a site, the phone first asks a DNS server where that site is. That question is
a DNS lookup. Android lets one app see these lookups through its VPN feature (`VpnService`). Guard
for Android uses that feature **only as a local tunnel that ends inside the app**. There is no VPN
server, and your traffic is not sent on to anyone.

1. Only two kinds of traffic enter the tunnel: DNS lookups, and connections to addresses on public
   lists of known dangerous IP addresses. (An IP address is the number that identifies a computer on
   the internet. Technically, the tunnel's routes are a made-up DNS address and the address ranges
   from those lists.) Web pages, video and app data go out exactly as before.
2. Each lookup is checked against the block list: the AdGuard DNS filter, which ships inside the app.
3. A blocked name gets an empty answer (`0.0.0.0`), so the site or ad does not load. Every other
   lookup goes to the network's own DNS server, and the answer comes back through the tunnel.
4. A connection from any app to a listed address is refused on the spot, inside the app (technically
   a TCP reset, or an ICMP "administratively prohibited" reply for UDP). Nothing is relayed to the
   internet. The app counts it, and a notification names the app Android says made it, the address
   and the list. If Android can't say which app it was, the notification says only "an app".
5. The home screen shows how many names and addresses were blocked since start, and a "Recent
   blocks" list: which apps were blocked, how many times, and what they tried to reach. A "Good to
   know" block repeats the promises on this page, and an About page gives the version, the license,
   links and credits.

While the app protects the phone, Android shows a key icon in the status bar. That icon is Android's
own sign for apps that use its VPN feature. It does not mean your traffic goes to a remote VPN.

### Address lists

Some apps and malware connect straight to an IP address and never ask DNS, so the name list can't
stop them. While protection runs, the app downloads these lists **on the phone, straight from their
publishers, over HTTPS**: Spamhaus DROP (`drop_v4.json`, `drop_v6.json`) and the abuse.ch Feodo
Tracker recommended IP blocklist. There is no Cocode server in between. Details:

- Only those three files are fetched. They are data: the app reads them and never runs them.
- Normally once a day. A failed download is retried after at least an hour.
- Each list is stored in the app's private files. A list older than 7 days is paused, and the screen
  says so, because old lists can block addresses that others use now.
- Each publisher sees what any web server sees: the phone's IP address, the time and the ordinary
  details of a request (Android's default user agent). The app sends no account, identifier or data
  about the user or about what was blocked.
- The name of the app that made a blocked lookup or connection comes from Android, on the phone. It
  appears in the notification for a blocked address and in the Recent blocks list on the screen. That
  list lives only in the phone's memory and is cleared whenever protection starts or stops. The app
  saves no record of blocked names, addresses or apps, and sends none to anyone. Android may keep
  the notification in its own notification history.

### Limits, said plainly

- Android's **Private DNS** set to a specific server can't be used with the app. The phone would
  have no internet, so protection stops and says so. "Automatic" works as normal.
- Android's "Block connections without VPN" can't be used with the app either. The tunnel carries
  only DNS lookups and connections to listed addresses, so with that setting the phone can't reach
  the internet. Protection can't run, and the screen says so.
- Apps with **their own secure DNS** (for example a browser's DoH setting) bypass the name filter.
- Android allows **one VPN at a time**, so the app can't run alongside a work VPN or another VPN
  app.
- If protection stops without your asking, a notification says so: DNS lookups are no longer
  filtered. If you stop it yourself, the screen says so. If you turn off the app's notifications, the
  app can't tell you when protection stops, and the screen reminds you of that.
- Address blocking needs downloaded lists. A list that is missing, or older than 7 days, is not used,
  and the screen says so. The name filter works regardless. No list catches everything.

## Accessibility

Built for blind and low-vision people from the start: every control has a spoken label for TalkBack
(Android's screen reader), status changes are announced, text scales to 200%, and a unit test holds
every color pair to WCAG AA contrast.

## Privacy

The app collects nothing about you and sends nothing to us. Read the full [privacy policy](https://android.guard.cocode.dk/en/privacy/) ([Danish](https://android.guard.cocode.dk/privacy/)).

### Permissions

You grant only the VPN connection and notifications. The VPN permission lets the app see DNS
lookups and refuse connections to dangerous addresses. Notifications let it tell you when protection
stops or a connection is refused. Network access (to pass lookups on and to download the address
lists) is an install-time permission Android grants automatically. No contacts, location, storage or
accessibility access. The manifest also declares `<queries>` for apps with a launcher icon, so a
notification can name the app that tried to connect (an app without a launcher icon is not named); it
is not a permission, and `QUERY_ALL_PACKAGES` is not used.

### Third-party data

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

## Build

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

## Contributing

Issues and pull requests are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## Author

Babak Bandpey, [Cocode](https://cocode.dk).

## License

[GPL-3.0-or-later](LICENSE) (SPDX `GPL-3.0-or-later`).
