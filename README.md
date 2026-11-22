# CraftController — Android Prototype

Status: initial implementation. Physical-phone and DSU Manager interoperability
have not yet been validated. This is not a production release.

The current 0.6 revision adds a redesigned Xbox-style interface, a connected
D-pad, proportional control clusters, vector symbols and categorized Settings.
It builds successfully and passes ten focused layout/input checks. Physical-phone
and DSU Manager interoperability remain unverified. QR scanning still only saves
an endpoint; actual Manager pairing is not implemented. See
[0.6 notes](specs/revision-0.6.md) and the earlier
[connection safeguards](specs/revision-0.5.1.md). The earlier [0.1 validation](specs/validation.md)
does not validate the current interface or DSU Manager integration.

**Receiver decision:** reuse the existing Windows **DSU Manager**. No new PC
connector is being built. Its QR/control pairing is not implemented in
CraftController; do not assume that a displayed phone endpoint completes pairing.

## Goal

Build an independent Android controller with a custom UI that sends button,
joystick, touch, and motion data using the DSU/cemuhook UDP protocol.
No accounts, hosted backend, or dependency on the original developer's services.

## Documents

- [Project plan](specs/dsu-controller.spec.md): scope, requirements, phases, and tests.
- [Important notes](IMPORTANT-NOTES.md): compatibility risks and engineering cautions.
- [Controller-app research](docs/controller-research.md): product comparison,
  customization lessons, emulation layers, primary sources and roadmap.
- [Receiver integration specification](specs/craftcontroller-receiver.spec.md):
  implemented helpers, pairing blocker and acceptance criteria for later.
- [Public research page](docs/research.html): browser-friendly summary for Pages.
- [Manager QR findings](docs/manager-qr-findings.md): decoded official example,
  observed URI format and remaining post-scan protocol gap.

## Implementation

- Kotlin / Jetpack Compose, Android 8.0+ (API 26).
- App: **CraftController**, `io.github.unknown27s.dsucontroller` (retained for updates).
- Independent JVM `core` module: DSU 1001 codec and coroutine-based UDP server.
- One controller slot, D-pad, ABXY, shoulders/triggers, two sticks, stick-click
  buttons, Share / Start / Home. Triggers currently report digital 0/255 pressure.
- Dedicated gamepad, grouped Settings and Layout Studio screens.
- Four built-in screen presets (Standard, Compact, Left-hand and Right-hand),
  plus one saved custom layout. Presets do not emulate different hardware protocols.
- On-phone layout editor: move, resize and hide buttons/sticks; save, discard with
  confirmation, or reset. Four accent colors and optional button haptic feedback.
- Local port, stick dead zone, layout, color, and synthetic identity persistence.
- Connected D-pad edits as one unit; version-1 four-part layouts migrate locally.
- Optional floating sticks, four/eight-way D-pad, sticky D-pad and control contrast.
- Connection, Controls, Appearance and Advanced settings; QR/manual entry beside
  the server controls in landscape, with technical details in Advanced.
- Windows **PC setup** screen for your existing DSU Manager: phone address/port,
  explicit refresh/copy/share, subscriber status, official help and driver warning.
- **Scan QR code** reads the documented Manager URI with an on-device camera;
  manual entry is available without camera permission. Review and Save endpoint
  stores a PC address/port, not a pairing session. Forget removes the saved target.
- Foreground only: transient focus loss clears input without dropping UDP;
  pause, default-network/address changes, or Stop stop UDP and clear input.
  Restart explicitly after returning from another app.
- Full-screen play with a small Settings gear; server controls and QR scanning
  live in Settings. Xbox-style offset sticks, colored ABXY and an eight-way
  sliding D-pad. Local control feedback works even while the server is offline.
- Motion, DSU touchpad data, rumble, multiple profiles, layout import/export,
  button remapping, and background streaming are **not implemented**.

No accounts, hosted services, analytics, copied app source, or proprietary assets.
Project license remains undecided; no redistribution license is granted here.

## Build and test

Use JDK 17 and Android SDK platform 36 / build-tools 36.0.0. Set `ANDROID_HOME`
to your SDK directory (or configure `sdk.dir` in untracked `local.properties`).

Package only, without running tests:

```sh
./gradlew :app:assembleDebug
```

Broad test suites/lint remain deferred by request; 0.6 ran only ten focused
layout/input checks once. The full existing tasks can be used later:
`./gradlew :core:test :app:testDebugUnitTest :app:lintDebug`.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Website download: `docs/downloads/craftcontroller-debug.apk`.
Debug signing is for local testing, not distribution. No phone is installed to
automatically by building.

## Try it on a trusted LAN

1. Install the debug APK on your phone and keep both devices on the same LAN.
2. Open **PC setup** for phone IPv4 addresses and UDP port (default 26760).
   Start the server explicitly. A listener is not a paired Windows controller.
3. Use your existing Windows DSU Manager. The published release found during
   research is **v0.6.2**; the user's installed version is unknown. The original
   app pairs using a Manager QR code; CraftController does not implement that
   control handshake yet. If it is required, stop here until integration is
   implemented. A generic receiver's direct DSU-source option is not proof that
   DSU Manager accepts the same setup.
4. For an independent standard-DSU check from the PC:

   ```sh
   python3 tools/probe.py PHONE_IPV4 --seconds 15
   ```

5. Press/release buttons, drag both sticks, and watch the probe output. Test
   app switching and screen lock: the server must stop and input must reset.

DSU is not a system-wide gamepad driver. DSU Manager may need platform-specific
virtual-controller permissions/drivers; this project does not configure those.
Do not expose this unauthenticated UDP listener to the internet.

## Protocol bounds and evidence

- Standard version, controller-info and controller-data messages only.
- Up to eight endpoint subscriptions; expire five seconds after the last valid
  matching registration. Slots 1–3 are reported disconnected.
- Send cadence target: at most approximately 60 packets/s per subscriber, not a
  measured phone delivery rate. Global request processing limit: 100 datagrams/s.
- IPv4 listener only; default-network changes stop it. Hotspot/VPN behavior needs
  physical-device testing. No hardcoded Wi-Fi interface name.
- Parser validates magic, version, declared length, CRC, type and payload bounds.
- Tests include fixed Python `struct`/`zlib` version fixtures, packet offsets,
  malformed inputs, and real localhost UDP exchange/expiry/bind failure.
- Public wire-format reference: <https://v1993.github.io/cemuhook-protocol/>.
  It was used as documentation, not copied implementation code.
- Pending: physical multitouch/cancellation checks, accessibility review,
  DSU Manager tests, sensor-axis validation, latency and battery measurements.

The specification is the longer-term plan, not a claim that all requirements
have been implemented or accepted.

## Customize your controller

Tap the **Settings gear → Controls**, select a controller preset, then open
**Layout studio**.
Settings clears held input but keeps the listener running; editing stops UDP.
Drag a control to move it; select one to resize or Hide/Show. **Save layout** stores
it as your Custom layout. **Cancel** asks before discarding edits. **Reset** loads
defaults, which you can then save. Switching presets does not overwrite Custom.
Color, button haptics and dead zone are in Appearance & touch. Restart after editing.

## Website and credits

Website: <https://unknown27s.github.io/craftcontroller/>.
The Pages-only workflow publishes `docs/`; it does not rebuild or test Android.

The GitHub Pages site is in `docs/index.html`. It includes installation,
customization, connection and troubleshooting instructions. See
[`docs/PUBLISHING.md`](docs/PUBLISHING.md) for preview/publishing steps. Nothing
has been published automatically.

Thank you to [breeze2](https://github.com/breeze2), creator of
[DSU Controller](https://github.com/breeze2/dsu-controller-guides) and DSU Manager,
for the inspiration, and to v1993 and the cemuhook-protocol contributors for the
public protocol documentation. This independent implementation is not affiliated
with or endorsed by them.

## Reference supplied by the user

https://github.com/breeze2/dsu-manager-guides

This repository provides guides/downloads. It is not evidence that the Manager's
application source is open-source or licensed for reuse. Compatibility with its
binary releases has not been established.

The extracted APKs and earlier reverse-engineering experiments remain outside
this workspace, under `~/apk/com.dsucontroller/`. They are not project source.
