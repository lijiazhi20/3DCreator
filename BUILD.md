# 3DCreator — Build Guide (Local + Cloud)

This document explains how to build the **3DCreator Android client** (the `android/`
module) both on your machine and in GitHub's cloud CI.

> **Scope:** CI / this guide only build the **Android client APK**. The actual
> **3D reconstruction runs on a SEPARATE GPU cloud** (Modal / RunPod, see
> `backend/DEPLOY.md`). Cloud compilation does **not** run reconstruction — it
> only produces the installable app that talks to that backend.

---

## 1. Toolchain & versions (verified from the repo)

| Component        | Version | Source |
|------------------|---------|--------|
| Android Gradle Plugin (AGP) | `8.5.2` | `gradle/libs.versions.toml` |
| Gradle (wrapper) | `8.9`   | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin           | `1.9.24` | `gradle/libs.versions.toml` |
| compileSdk / targetSdk | `35` | `app/build.gradle.kts` |
| minSdk           | `26`    | `app/build.gradle.kts` |
| JVM (JDK)        | `17`    | `app/build.gradle.kts` (`JavaVersion.VERSION_17`) |
| NDK              | **NOT required** | none referenced; `arm64-v8a` only via `abiFilters` |

**Compatibility check (all OK, no edits made):** AGP 8.5.2 requires Gradle 8.9
and JDK 17 — these match exactly, so the build is internally consistent.

---

## 2. Prerequisites (local machine)

1. **JDK 17** (e.g. Eclipse Temurin 17).
   ```bash
   java -version   # must report 17
   ```
2. **Android SDK command-line tools** (`cmdline-tools`).
3. **SDK packages** matching `compileSdk = 35`:
   ```bash
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```
4. Either create `android/local.properties` with your SDK path:
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
   **or** export the location so Gradle can find it:
   ```bash
   export ANDROID_SDK_ROOT=$HOME/Android/Sdk
   export ANDROID_HOME=$HOME/Android/Sdk
   ```
5. The **`gradle-wrapper.jar` must be present** at
   `android/gradle/wrapper/gradle-wrapper.jar`. Android Studio regenerates it on
   first sync, or run `gradle wrapper` once. *(See §4 — it is currently missing.)*

---

## 3. Local build

```bash
cd android
chmod +x gradlew
./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

Other useful commands:
```bash
./gradlew lintDebug          # static analysis
./gradlew testDebugUnitTest  # unit tests
./gradlew assembleRelease    # release APK (needs signing, see §5)
```

---

## 4. Cloud build (GitHub Actions)

`.github/workflows/build.yml` compiles the APK on **GitHub's cloud runners**
(`ubuntu-latest`) — that is the "cloud compilation" of the client.

- On every **push to the default branch** (or any PR touching `android/**`),
  the workflow runs automatically in the **Actions** tab.
- It sets up JDK 17 (Temurin), installs the Android SDK, caches `~/.gradle`,
  then runs `lintDebug` (non-fatal), `testDebugUnitTest`, and `assembleDebug`.
- The debug APK is uploaded as a workflow **artifact** (`app-debug`).
- A **release** APK is built and uploaded (`app-release`) **only if** the four
  signing secrets below are configured; otherwise that stage is skipped.

### To activate cloud builds (what you must do)
1. **Connect your GitHub repo.** In this environment the repo is not linked to
   GitHub, so the workflow cannot execute until you push it to a GitHub
   repository you own (or create one and `git push`).
2. **`gradle-wrapper.jar` is absent — but CI is unaffected.** ⚠️ The wrapper
   binary is not committed, so local `./gradlew` will not run. However, this
   workflow invokes `gradle` directly (Gradle 8.9 is downloaded in-run), so the
   **cloud build works regardless**. For local `./gradlew` you only need to
   regenerate the jar (Android Studio sync, or `gradle wrapper` once you have
   Gradle on your machine).
3. **(Optional) Configure release signing secrets** — see §5.

---

## 5. Signing setup (release builds)

Generate an upload keystore locally:
```bash
keytool -genkeypair -v \
  -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```
Base64-encode and store as **GitHub repo secrets** (`Settings → Secrets → Actions`):

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w0 release-key.jks` |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | key password |
| `KEYSTORE_PASSWORD` | keystore password |

The workflow decodes the keystore and passes the four values to Gradle as env
vars: `KEYSTORE_PATH`, `KEY_ALIAS`, `KEY_PASSWORD`, `KEYSTORE_PASSWORD`.

`app/build.gradle.kts` **already defines** a `signingConfigs { create("release") { … } }`
block that reads those four env vars, and applies it to the `release` build type
**only when `KEYSTORE_PATH` is set** (`hasReleaseKey`). With no key present it builds
an unsigned release — so local/CI release signing is fully driven by the secrets.

---

## 6. Flagged concerns (documented, not fixed)

| # | Concern | Status |
|---|---------|--------|
| 1 | **`gradle-wrapper.jar` missing** in `android/gradle/wrapper/` | Does **NOT** block CI (workflow calls `gradle` directly). Blocks local `./gradlew` only. Action: commit the jar (Android Studio sync / `gradle wrapper`) or build locally with `gradle`. |
| 2 | ~~**`app/build.gradle.kts` lacks `signingConfigs`**~~ | ✅ RESOLVED — `signingConfigs` reading `KEYSTORE_PATH`/`KEY_ALIAS`/`KEY_PASSWORD`/`KEYSTORE_PASSWORD` now present; release signing is secret-driven. |
| 3 | **Spec said `platforms;android-34`** but `compileSdk = 35` | Workflow installs `android-35`/`build-tools;35.0.0` to match. Correct per repo. |
| 4 | AGP 8.5.2 / Gradle 8.9 / JDK 17 | ✅ Compatible; no change needed. |
