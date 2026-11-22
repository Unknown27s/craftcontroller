# Prototype validation

Date: 2026-11-21. Version: 0.1.0-prototype. Not a release acceptance report.

## Passed

- Gradle wrapper 8.13 download and official SHA-256 verification, JDK 17.
- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
- 14 Kotlin/JVM tests: protocol framing, fixed Python-generated version fixtures,
  CRC/bounds/malformed traffic, every button field, neutral sticks, radial dead
  zone, real JVM localhost UDP handshake/stream/expiry, bind failure, immediate
  stop/restart, saved-layout round trips and corrupt-layout recovery.
- Three independent Python probe-decoder tests.
- Android API 37 emulator, read-only session: move, resize and hide L2; select
  Violet; save; restart process; inspect restored editor; Reset then Cancel keeps
  the saved layout. Script: `tools/emulator_smoke.py` (clears this app's data on
  the explicitly selected emulator before testing).
- Actual Android UDP exchange with the independent Python decoder, transported
  through `adb shell -T toybox nc` to guest **loopback**: version/info,
  subscription, neutral state, A press/release, left-stick movement/release,
  foreground/background stop and explicit restart requirement.
- Website rendered in headless Brave/Playwright at desktop 1440px and mobile
  390px widths. No horizontal mobile overflow or JavaScript errors; navigation
  fragment targets and FAQ interaction checked.
- `tools/check_site.py`: 16 website links, packaged APK ZIP integrity and matching
  SHA-256. Download package: `docs/downloads/dsu-controller-debug.apk`.

Screenshots: `docs/assets/layout-editor.png`; additional session screenshots in
`/tmp/opencode/dsu-emulator/`. They are emulator evidence, not physical-phone tests.

## Lint and known limitations

Android lint: **0 errors, 13 warnings**. The warnings cover pinned older
dependencies/target SDK, landscape orientation guidance, optional KTX usage,
the redundant API-26 resource-folder qualifier and missing themed monochrome
launcher icon. They are not suppressed. APK assembly also reports that an
AndroidX native graphics library is packaged without stripping debug symbols.
Detekt/ktlint and numerical coverage measurement are not configured.

An early integration test exposed a socket-cleanup/rebind race; start now waits
for the previous server job to finish. A five-cycle immediate restart regression
test passes. The emulator smoke script synchronizes UI transitions and pauses
the activity before inspecting asynchronous preference writes on disk.

## Not established

- Host-to-emulator UDP forwarding via console `redir` timed out. Guest-loopback
  success does not establish host/LAN routing. No claim about the forwarding
  failure's root cause is made.
- No physical phone was connected. Phone-to-PC LAN, simultaneous physical
  multitouch, network changes, lock screen, VPN/hotspot and device-specific
  orientation behavior still require testing.
- DSU Manager v0.6.2 is the identified published target, not a tested receiver.
  Its exact installed version, extensions and button/stick interoperability have
  not been established. Ordinary games may need a separate virtual controller.
- Motion, rumble, touchpad data, profiles, import/export and button remapping are
  not implemented. Only one local custom layout is saved.
- No measured phone latency, update rate, CPU/battery results or complete
  accessibility review. Layouts can overlap if the user places controls together.
- No release license selected, release signing, GitHub repository creation,
  commits, upload, or GitHub Pages deployment performed.
