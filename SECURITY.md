# Security Policy

## Reporting a Vulnerability

Do **not** open a public GitHub issue for security vulnerabilities.

Report vulnerabilities privately by email to bb@cocode.dk.

We will acknowledge within 5 business days and aim to release a fix within 30 days of confirmation.

## Scope notes

- The app has **no backend, no account and no analytics**. There are no credentials to leak.
- It sees the DNS lookups that apps send through the phone's normal DNS setting. Apps with their own
  DNS (for example a browser's secure DNS) bypass it. It keeps none of the lookups: they are answered
  or passed on to the network's own DNS server. In memory only, it holds counters and up to 200
  recent blocked lookups and refused connections (the name or address, and the app's name when
  Android can tell). That list is cleared whenever protection starts or stops.
- Its network traffic is the passed-on lookups and the HTTPS downloads of the address lists from
  their publishers.

Reports about lookup handling, block-list bypasses, the tunnel setup, or anything that makes the
app fail silently (protection off without a notification) are in scope and welcome.

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest  | ✅ |
| older   | ❌ |
