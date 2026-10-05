# Cocode Guard for Android

Blocks dangerous sites and ads for every app on the phone, on any network: at home, on mobile data,
on café Wi-Fi, abroad. The [Cocode Guard box](https://guard.cocode.dk) protects the house; this app
protects the phone when it leaves the house. It works on its own, without the box.

**Free software:** GPL-3.0 and free of charge. No account, no server, no analytics, no ads.

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

1. The tunnel's only route is a fake DNS address, so only DNS lookups enter it. Web pages, video and
   app data go out exactly as before.
2. Each lookup is checked against the block list (the AdGuard DNS filter, shipped inside the app).
3. A blocked name gets an empty answer (`0.0.0.0`). Every other lookup goes to the network's own DNS
   server, and the answer comes back through the tunnel.

Android shows its VPN key icon while the app protects the phone. That icon is Android's, not a sign
of a remote VPN.

### Limits, said plainly

- Android's **Private DNS** setting bypasses the filter. The app warns when it is on.
- Apps with **their own secure DNS** (for example a browser's DoH setting) bypass it too.
- Android allows **one VPN at a time**, so the app can't run alongside a work VPN.
- If protection stops, a notification says so and the phone uses normal DNS without blocking.

## Accessibility

Built for blind and low-vision people from the start: every control has a spoken TalkBack label,
status changes are announced, text scales to 200%, and a unit test holds every color pair to WCAG
AA contrast.

## Permissions

You grant only the VPN connection and notifications. Network access (to pass lookups on) is an
install-time permission Android grants automatically. No contacts, location, storage or
accessibility access.

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

## Author

Babak Bandpey, [Cocode](https://cocode.dk).

## License

[GPL-3.0](LICENSE).
