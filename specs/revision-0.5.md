# Revision 0.5 — controller-first interface

## Changes

- Hide system status/navigation bars during play; edge swipes can reveal them.
  Keep the display-cutout safe area. Settings restores normal system bars.
- Remove the play-screen title, layout name and server buttons. Keep one small
  Settings gear and a compact listener/subscriber indicator.
- Put QR scanning directly in Settings; retain manual entry and PC setup.
  QR read/saved endpoint is explicitly not reported as a successful connection.
- Use an Xbox-style default arrangement: offset sticks, green A/red B/blue X/
  yellow Y, LB/LT/RB/RT, menu symbols and a matching original gamepad launcher icon.
  Existing saved custom placements are preserved. Select Xbox-style gamepad in
  Settings to replace a previously customized arrangement for play.
- Replace inward-pointing D-pad paddles with outlined directional tiles.
  Sliding a captured D-pad touch selects cardinal/diagonal directions with a
  neutral center. A single D-pad pointer cannot emit opposing directions.
- Accept local touch input and show pressed feedback even when UDP is offline.
  Only forward snapshots while the listener is running and the window has focus.
- Clear input on transient focus loss rather than destroying UDP subscriptions.
  Window focus return does not replay held input. Actual app pause/backgrounding,
  network changes and explicit Stop still close the listener.
- Settings clears input without stopping the listener. Layout editing still stops it.

## Button-reporting diagnosis and limits

The screenshot shows `Stopped: window lost focus`. Previously play input was
disabled whenever the server was stopped, so even local button feedback vanished.
That gate and transient focus disconnect behavior have been corrected.

This does not establish why input was absent in the user's Windows session.
No phone capture, Manager logs, current Manager version or active subscription
evidence was available. Standard DSU packet mappings were inspected, not changed.
The saved Manager endpoint is still unused by a pairing client: the post-scan
control handshake remains unknown/unimplemented. Do not claim working QR-to-XInput
connection, or infer that an Xbox-looking UI is a Windows Xbox virtual device.

## Verification

- `:app:assembleDebug`: successful with a temporary JDK 17.
- The initial invocation failed before compilation under the shell's Java 25.
- No unit, integration, lint, emulator or physical-device test suites were run,
  respecting the request to avoid repeated tests.
- Full-screen rendering, physical multitouch, lifecycle behavior and Windows
  interoperability remain unverified on a device.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, version 0.5.0-prototype (5).
