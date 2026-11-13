# RecSec

Native Android screen-off video recorder prototype for Galaxy S25 Ultra.

## Build APK (no Android Studio)

1. Create a GitHub repository named `RecSec` under `jrobcash-cpu`.
2. Upload all project files **preserving folders**, including `.github/workflows/build-apk.yml`.
3. Go to **Actions → Build RecSec APK → Run workflow** (or push to `main`).
4. When the workflow completes, download the `RecSec-installable-APK` artifact, unzip it, and install `app-debug.apk` on the phone.
5. Allow installation from the app used to open the APK, if Android prompts.

This is a debug-signed APK. It does not require Google Play publication.

## Usage

Grant Camera, Microphone, and Notification permissions. Tap START RECORDING while the app is visible. Wait for the persistent notification to say **Recording video**, then press the power button to switch the display fully off. Unlock and tap STOP RECORDING, or use the notification's Stop action. Videos appear in Movies/RecSec.

## Limitations

This is untested on Android 17/Samsung firmware. Android foreground-service restrictions and manufacturer camera policy can interrupt screen-off recording. Test a short recording and inspect the saved video before relying on it. Recording has a visible notification and system camera/microphone privacy indicators when the screen is on. Do not use it to record people where prohibited by law.

If recording is interrupted, check Settings → Apps → RecSec → Battery and permit unrestricted battery use (if available). The app is not designed to restart recording after a reboot or after Android kills its process.
