#!/bin/sh
# setup-signing.sh
# Generates a release keystore and uploads all 4 GitHub Secrets to cocodedk/guard-android.
# Run once from the project root: ./scripts/setup-signing.sh
set -eu

restore_tty() { stty echo 2>/dev/null || true; }
trap restore_tty EXIT INT TERM

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
# This app's own release key. F-Droid pins its fingerprint (4c33dbed...), so never point this at
# another app's key. Override: KEYSTORE_FILE=/path/to/key KEYSTORE_ALIAS=name ./scripts/setup-signing.sh
KEYSTORE="${KEYSTORE_FILE:-$HOME/keystores/guard-android.keystore}"
ALIAS="${KEYSTORE_ALIAS:-guard}"

echo ""
echo "=== Cocode Guard Release Signing Setup ==="
echo ""

# ── Prerequisites ────────────────────────────────────────────────────────────
command -v keytool >/dev/null 2>&1 || { echo "ERROR: keytool not found — install JDK 17+"; exit 1; }
command -v gh     >/dev/null 2>&1 || { echo "ERROR: gh not found — install GitHub CLI";    exit 1; }
command -v base64 >/dev/null 2>&1 || { echo "ERROR: base64 not found";                     exit 1; }

gh auth status >/dev/null 2>&1 || { echo "ERROR: gh not authenticated — run: gh auth login"; exit 1; }

# ── Keystore ─────────────────────────────────────────────────────────────────
if [ -f "$KEYSTORE" ]; then
    echo "Found existing keystore: $KEYSTORE"
    echo "Skipping generation — using existing file."
else
    echo "Generating release keystore..."
    echo "You will be prompted for a keystore password, a key password,"
    echo "and some name/org fields (those can be anything)."
    echo ""
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000
fi

# ── Read passwords securely ───────────────────────────────────────────────────
echo ""
printf "Keystore password: "
stty -echo 2>/dev/null || true
read -r KSPASS
restore_tty
case "$KSPASS" in
  *'"'*)
    echo 'ERROR: Password must not contain " (double quote).'
    exit 1
    ;;
esac
echo ""

# keytool writes PKCS12 on JDK 17+, and PKCS12 has no separate key password. A different
# one here is ignored by keytool, sails through the verification below, and then fails
# inside the release build — so press Enter.
printf "Key password (press Enter — PKCS12 requires it to match the keystore password): "
stty -echo 2>/dev/null || true
read -r KEYPASS
restore_tty
case "$KEYPASS" in
  *'"'*)
    echo 'ERROR: Key password must not contain " (double quote).'
    exit 1
    ;;
esac
echo ""

[ -z "$KEYPASS" ] && KEYPASS="$KSPASS"

# ── Verify ───────────────────────────────────────────────────────────────────
echo "Verifying keystore..."
# Through the environment, so the password never shows in a process listing.
KSPASS="$KSPASS" keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" \
    -storepass:env KSPASS >/dev/null 2>&1 || {
    echo ""
    echo "ERROR: Wrong keystore password or alias. Nothing was uploaded."
    exit 1
}
echo "✓ Keystore password and alias verified"

if [ "$KEYPASS" != "$KSPASS" ]; then
    echo ""
    echo "ERROR: The key password differs from the keystore password. A PKCS12 keystore"
    echo "       cannot store the two separately, so the release build would fail to"
    echo "       sign. Re-run and press Enter at the key password prompt."
    exit 1
fi

# ── Upload secrets ────────────────────────────────────────────────────────────
KEYSTORE_B64=$(base64 "$KEYSTORE" | tr -d '\n')

echo "Uploading secrets to $REPO..."
printf '%s' "$KEYSTORE_B64" | gh secret set KEYSTORE_BASE64  --repo "$REPO"
printf '%s' "$KSPASS"       | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS"        | gh secret set KEY_ALIAS         --repo "$REPO"
printf '%s' "$KEYPASS"      | gh secret set KEY_PASSWORD      --repo "$REPO"

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "✓ All 4 secrets uploaded:"
echo "    KEYSTORE_BASE64    ✓"
echo "    KEYSTORE_PASSWORD  ✓"
echo "    KEY_ALIAS          ✓  ($ALIAS)"
echo "    KEY_PASSWORD       ✓"
echo ""
echo "IMPORTANT: $KEYSTORE is gitignored — back it up somewhere secure."
echo "           If you lose it, you cannot update the app on any store."
echo ""
echo "The release workflow is manual — nothing fires on a merge to main."
echo "To cut a release, bump VERSION_NAME and VERSION_CODE in gradle.properties"
echo "through a PR, then:"
echo ""
echo "    gh workflow run release-apk.yml"
echo ""
echo "GuardAndroid.apk is then attached to the release, and served from:"
echo "  https://github.com/$REPO/releases/latest/download/GuardAndroid.apk"
