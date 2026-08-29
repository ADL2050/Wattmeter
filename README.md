# Watt Meter

A simple Android app that shows exactly how many watts your phone is currently
charging at (Voltage × Current), updated once per second, plus an optional
persistent notification.

## How to build it (Android Studio)
1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open this folder (`WattMeter/`) as a project — File → Open.
3. Let Gradle sync (Android Studio will auto-download the correct Gradle
   version the first time; you don't need to install Gradle separately).
4. Connect your phone via USB with USB debugging enabled, or use an emulator,
   and press Run ▶.

## How to build it via GitHub (no computer needed)
This project includes `.github/workflows/build.yml`, which builds a debug
APK automatically using GitHub's free build servers.

1. Create a new repository on [github.com](https://github.com/new).
2. Upload everything in this folder to that repo (either drag-and-drop the
   files on the GitHub website, or `git init && git add . && git commit -m "init" && git remote add origin <your-repo-url> && git push -u origin main`).
3. Go to the **Actions** tab on your repo — a workflow run called
   "Build APK" will start automatically (it also runs any time you push
   changes, or you can click "Run workflow" to trigger it manually).
4. Once it finishes (usually 2–4 minutes), open that run and scroll to
   **Artifacts** at the bottom — download `WattMeter-debug-apk`, which
   contains `app-debug.apk`.
5. Transfer that APK to your phone and open it to install (you'll need to
   allow "install from unknown sources" the first time). This is a debug
   build, so no signing setup is required.

No API keys, no root, no special permissions beyond a notification permission
on Android 13+ — everything here uses Android's public `BatteryManager` API.

## How the wattage is calculated
`Watts = Voltage (V) × Current (A)`

- **Voltage** comes from `BatteryManager.EXTRA_VOLTAGE` in the sticky
  `ACTION_BATTERY_CHANGED` broadcast.
- **Current** comes from `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`
  (falling back to `CURRENT_AVERAGE` if the first isn't supported).

Both are official public APIs — no root required.

## About "dual-cell" phones
Phones with 2S (two-cell) battery packs (e.g. many 2023+ fast-charging
flagships) still only expose **one combined pack voltage/current** to
Android, on every OEM. The dual-cell design lives entirely on the hardware
side (it lets the charging IC push higher wattage at lower per-cell voltage);
Android's API surface doesn't change based on cell count, and no app —
rooted or not — can query "cell 1" vs "cell 2" separately through the OS.
So this app already handles dual-cell phones correctly by simply reading the
pack values Android provides; there's no extra cell-specific code needed or
possible without root + OEM-specific kernel drivers (which would only work
on that one OEM anyway).

## Known real-world limitations (by design, not a bug)
- A handful of OEM firmwares (mostly older MediaTek-based budget phones)
  don't implement `CURRENT_NOW`/`CURRENT_AVERAGE` at all. On those, the app
  shows "wattage unavailable on this device" instead of a fabricated number.
- A few OEMs report current in milliamps instead of the standard
  microamps; the app detects and corrects for this automatically.
- Reported wattage is the phone's own charging-IC measurement, the same
  number your phone's own settings/battery app would be able to show —
  this app doesn't have a way to measure watts more precisely than the
  phone's hardware itself reports.
