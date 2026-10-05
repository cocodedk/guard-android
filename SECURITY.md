# Security Policy

## Reporting a Vulnerability

Do **not** open a public GitHub issue for security vulnerabilities.

- Use the **"Report a vulnerability"** button on the Security tab of this repository (GitHub private advisory)
- Or email: bb@cocode.dk

We will acknowledge within 5 business days and aim to release a fix within 30 days of confirmation.

## Scope notes

- The app has **no backend, no account and no analytics**. There are no credentials to leak.
- It sees every DNS lookup the phone makes. It keeps none of them: lookups are answered or passed on
  to the chosen DNS resolver, and only a count of blocked lookups is held in memory.
- Its only network traffic is those passed-on lookups.

Reports about lookup handling, block-list bypasses, the tunnel setup, or anything that makes the
app fail silently (protection off without a notification) are in scope and welcome.

## Supported Versions

| Version | Supported |
|---------|-----------|
| latest  | ✅ |
| older   | ❌ |
