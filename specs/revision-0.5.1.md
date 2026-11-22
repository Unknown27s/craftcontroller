# Revision 0.5.1 — unexpected disconnect safeguards

## Findings

Source inspection found two unnecessary whole-listener stop paths:

1. Network monitoring compared all IPv4/IPv6 link addresses, despite the listener
   being IPv4-only. Changing an IPv6 address alone could stop the server.
2. A UDP reply or stream send IOException propagated to the server-wide error
   handler. A single failed destination could close every subscriber's listener.

These paths were identified in code, not captured from the user's phone. The
specific cause of their intermittent Windows disconnect remains unconfirmed.

## Changes

- Compare IPv4 host addresses only. IPv6 address rotation/reordering no longer
  invalidates this listener. Actual default-network switches, IPv4 changes and
  network loss still stop it for explicit restart on a trusted LAN.
- Isolate send failures on an open socket. A failed streaming peer is removed;
  other peers continue, and the affected PC can register again. Closed sockets
  and receive/bind failures remain terminal. No automatic untrusted reconnect.
- Expose send-failure counts and the last send error in Settings and PC setup.
- Show the actual stopped reason on the play screen; don't overwrite an existing
  failure with later idle network/pause notifications.
- Changing a layout preset in Settings no longer stops the server. Entering the
  layout editor still deliberately stops it and now names that reason.
- Five-second PC subscription expiry still removes only that subscriber; it does
  not stop the listener. Explain this separately from a stopped server.
- Backgrounding, opening the scanner/another activity, locking the screen and
  explicit Stop still stop this foreground-only server. Return and restart in
  Settings. Transient focus loss only clears input, as in 0.5.

## Verification

One focused test execution, five checks, zero failures/errors:

- Failed version reply leaves the listener running and a later reply succeeds.
- Failed streaming peer is isolated, a healthy peer continues, and the failed
  peer can register again.
- IPv6 rotation leaves the monitored IPv4 address set unchanged.
- IPv4 replacement is detected.
- IPv4 loss is detected.

The initial Gradle invocation failed while compiling the test socket subclass
because `DatagramSocket(null)` was ambiguous there. It executed no tests; the
argument was explicitly typed and the focused checks then passed. No full test
suites, emulator runs, lint or physical-device tests were run.

`:app:assembleDebug` succeeded with JDK 17. APK version 0.5.1-prototype (6):
`app/build/outputs/apk/debug/app-debug.apk`.

DSU Manager's post-QR pairing remains unimplemented. These fixes do not establish
end-to-end Manager compatibility or guarantee no phone/network disconnects.
