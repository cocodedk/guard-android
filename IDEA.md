# Cocode Guard for Android — idea

Written 2026-10-04. Owner: Babak (bb@cocode.dk). Status: idea only, nothing built.

## The idea

An Android app that gives a phone the same protection the Cocode Guard box gives a home network:
dangerous sites and ads are blocked for every app on the phone, on any network: at home, on mobile
data, on café Wi-Fi, abroad. The box protects the house; the app protects the phone when it leaves the
house.

Sister project: `../network-defence` (the box: AdGuard Home + its own unbound resolver + the `guard`
service, Telegram alerts). Product page: https://guard.cocode.dk.

## How it would filter

Android lets one app act as a local VPN (`VpnService`). The app would use it only to catch DNS:

1. The app starts a local VPN that routes **only DNS** to itself, not the phone's other traffic.
2. Every lookup is checked against the block lists (the same ones the box uses: AdGuard DNS filter and
   the security lists).
3. Blocked names get an empty answer (`0.0.0.0`); the rest are resolved and returned.
4. Nothing leaves the phone except the lookups themselves. No Cocode server, no account.

This is how known open-source blockers work (for example DNS66, personalDNSfilter, RethinkDNS,
AdGuard's own app), so it is proven on Android.

## Rules carried over from the box

- **Independent:** no Cocode server, no shared account, no data sent to Babak. The phone holds its own
  lists and settings.
- **Stop means stop, said plainly:** if the app's VPN stops, the phone falls back to normal DNS without
  protection; the app must say so clearly (notification), never fail silently.
- **Few permissions:** no contacts, location, storage or accessibility access. Only the VPN permission
  and notifications.
- **No remote control** of the app from outside, and no remote code. Block-list files may be fetched
  from their published sources, as the box does.
- **Plain words** in every message, as on the box.

## Open questions

1. **Where lookups go after filtering.** The box asks the root servers itself (unbound). A phone on
   mobile data can't easily do that well; options: the phone's normal DNS, a resolver the owner picks, or
   the owner's own box over the internet (that would expose the box: probably not).
2. **Private DNS.** Android's "Private DNS" (DNS over TLS) bypasses a local VPN's DNS; the app must detect
   it and tell the owner to turn it off, or handle it.
3. **Apps with their own DNS** (Chrome's secure DNS, some apps using DoH) bypass a DNS filter, the same
   gap the box has. Detect and explain, or block known DoH endpoints.
4. **Another VPN.** Android allows one VPN at a time, so the app can't run alongside a work VPN.
5. **Battery** and **keeping it running** (some phone makers kill background apps).
6. **Alerts on the phone:** does the app report "a dangerous site was blocked" like the box does over
   Telegram, or just as a notification?
7. **Distribution:** Google Play (needs a VPN-use declaration) and/or F-Droid (the `fdroid-release` skill
   covers that route). Price: free with the box, or separate?
8. **Shared lists with the box:** should a phone and the owner's box share the same allow/block choices?

## First steps when this starts

1. Write a short spec (one feature: DNS-only VPN that blocks one list) and test it on one phone.
2. Look at how DNS66 / personalDNSfilter structure their `VpnService` before writing our own.
3. Set up the project with the `android-setup` skill.
