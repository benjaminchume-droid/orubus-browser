#!/usr/bin/env bash
set -euo pipefail

ALIAS="orubus-release"
KEYSTORE="orubus-release.jks"
B64_FILE="orubus-release-keystore.base64"

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: keytool is missing. Install a JDK (17 or newer) first." >&2
  exit 1
fi
if [[ -e "$KEYSTORE" || -e "$B64_FILE" ]]; then
  echo "ERROR: $KEYSTORE or $B64_FILE already exists. Refusing to overwrite a possible production key." >&2
  exit 1
fi

printf 'This creates a long-lived production signing identity. Back it up securely.\n'
read -r -s -p "Choose a strong keystore/key password: " STORE_PASS
printf '\n'
read -r -s -p "Confirm password: " STORE_PASS_CONFIRM
printf '\n'
if [[ "$STORE_PASS" != "$STORE_PASS_CONFIRM" || ${#STORE_PASS} -lt 16 ]]; then
  echo "ERROR: passwords must match and be at least 16 characters." >&2
  exit 1
fi
export STORE_PASS
read -r -p "Certificate owner name (e.g. Orubus): " OWNER
read -r -p "Organization (e.g. Orubus): " ORG
read -r -p "Two-letter country code: " COUNTRY
if [[ ${#COUNTRY} -ne 2 ]]; then
  echo "ERROR: country code must contain exactly two letters." >&2
  exit 1
fi

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storetype JKS \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=$OWNER, O=$ORG, C=$COUNTRY" \
  -storepass:env STORE_PASS \
  -keypass:env STORE_PASS

# Keep a transport copy only long enough to configure the private GitHub secret.
# The keystore and Base64 file are excluded from Git by .gitignore.
base64 < "$KEYSTORE" | tr -d '\n' > "$B64_FILE"
chmod 600 "$KEYSTORE" "$B64_FILE"

echo
echo "Created $KEYSTORE and $B64_FILE. Both are sensitive; neither belongs in Git."
echo "GitHub Actions secret ORUBUS_RELEASE_KEYSTORE_BASE64 must receive the complete content of $B64_FILE."
echo "Add ORUBUS_KEYSTORE_PASSWORD and ORUBUS_KEY_PASSWORD using the password you entered."
echo "Add ORUBUS_KEY_ALIAS with value: $ALIAS"
echo
echo "Certificate fingerprints:"
keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env STORE_PASS 2>/dev/null | grep -E 'SHA1:|SHA256:|Valid from:' || true
echo
echo "Move the keystore and encrypted backups to secure storage after setting the GitHub secrets."
unset STORE_PASS STORE_PASS_CONFIRM
