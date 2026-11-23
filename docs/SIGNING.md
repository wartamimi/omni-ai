# Signing release builds

OmniAI never needs your signing key and the repository must never contain it.

## Local builds
1. Create a keystore once (keep it and its passwords safe and backed up; you can't update the app without it):
   ```bash
   keytool -genkeypair -v -keystore omniai-release.jks -alias omniai -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Create `keystore.properties` in the project root (it's git-ignored):
   ```properties
   storeFile=../omniai-release.jks
   storePassword=...
   keyAlias=omniai
   keyPassword=...
   ```
   `storeFile` is resolved relative to the project root.
3. Build: `./gradlew assembleRelease` or `./gradlew bundleRelease` (for Google Play).

Without `keystore.properties`, release builds are produced unsigned.

## GitHub Actions
To sign in CI, add these repository secrets (Settings › Secrets and variables › Actions):
`OMNIAI_KEYSTORE_BASE64` (the .jks file, base64-encoded), `OMNIAI_KEYSTORE_PASSWORD`, `OMNIAI_KEY_ALIAS`,
`OMNIAI_KEY_PASSWORD`. The workflow decodes the keystore to a temporary file and passes the values as
environment variables. If the secrets are missing, CI builds only the debug APK.

For Google Play, enrol in Play App Signing and use this key as the upload key.
