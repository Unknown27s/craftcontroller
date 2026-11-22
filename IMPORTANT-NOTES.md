# Important Notes Before Building

Implementation update: the user has now authorized an Android prototype and
selected DSU Manager as the target, with the default app/package identity and
Android 8.0+ minimum. The original planning cautions below remain applicable;
the old planning-only restriction in section 8 is superseded for this build.

## 1. Keep the implementation independent

- Implement the public protocol with our own code and UI.
- Do not copy decompiled application code, artwork, branding, or proprietary layouts.
- Check the actual license of any dependency or reference implementation before reuse.
- A public GitHub download or guides page does not make the app open-source.
- No hosted backend is needed, including for local layout customization.

## 2. Confirm the receiving PC software first

- DSU is a network protocol, not automatically a system-wide gamepad driver.
- Some emulators use DSU primarily for motion input. Full button/stick support
  must be tested with the specific receiver.
- Ordinary PC games may require a separate connector that exposes a virtual
  controller. Linux typically uses uinput; Windows has different driver needs.
- The supplied DSU Manager could be tested as a receiver, but custom extensions
  or version differences may affect interoperability.
- Our own PC connector is a separate later project, not required for the first
  Android protocol prototype.

## 3. Verify the wire format, not just the function names

Planning baseline for the standard DSU packet header:

| Offset | Field |
|---|---|
| 0 | Four-byte magic: DSUC request or DSUS response |
| 4 | Little-endian uint16 protocol version |
| 6 | Little-endian uint16 length after the 16-byte header |
| 8 | Little-endian uint32 CRC32 |
| 12 | Little-endian uint32 sender identifier |
| 16 | Little-endian uint32 message type |
| 20 | Message-specific content |

- Verify this baseline against a public specification and an independent client.
- CRC is calculated over the entire packet with its CRC field zeroed.
- CRC detects corruption; it does not authenticate a peer.
- Core types: version 0x100000, controller info 0x100001, controller data 0x100002.
- Protocol version 1001 and UDP port 26760 are compatibility starting points,
  not proof that every receiver uses them.
- Rumble and LED commands found in the inspected app are extensions; defer them
  until a target receiver's support is verified.
- Subscription registration, refresh, expiry, and destination endpoints must be
  implemented; merely broadcasting controller state is not enough.

## 4. Motion is easy to get subtly wrong

- Confirm required units, axis signs, coordinate system, and timestamps.
- Map device rotation explicitly so changing screen orientation does not swap axes.
- Do not assume rotation-vector orientation is the same as angular velocity.
- Check accelerometer/gyroscope availability and actual sensor sampling behavior.
- Document calibration and measured behavior against a known-good receiver.
- Never invent a sensor scale or copy a number without establishing its units.

## 5. Lifecycle and touch safety

- Release all pressed buttons when a touch is cancelled or the app loses focus.
- Define behavior for lock screen, backgrounding, network loss, and reconnection.
- Clear active subscriptions when stopping or changing the network.
- Avoid a stale pressed state or replaying old input after reconnecting.
- Keep socket work and sensor processing off the Compose main thread.
- Use foreground-only operation initially; background operation needs an explicit
  Android-version-specific design, not just a wake lock.

## 6. Network and security

- Start on a trusted local network. Do not expose the UDP listener to the internet.
- Validate packet size, CRC, version, message type, payload length, and slot counts.
- Bound subscription storage and outgoing packet rates.
- Respond to validated requests/subscriptions rather than unsolicited targets.
- Stop sending data to expired or explicitly disconnected peers.
- Offer an explicit server toggle; avoid enabling an unauthenticated listener silently.
- Wi-Fi, tethering, VPNs, guest-network isolation, and IPv6 need separate tests.
- Earlier testing showed an IP on wlan1 while the original app displayed
  "Unable to obtain IP address." Root cause was not proven. Do not hardcode wlan0.

## 7. Be precise about evidence

- Earlier UDP probes received no successful live response. End-to-end compatibility
  is still unverified.
- Earlier scripts included malformed/incomplete requests before being corrected.
  Do not promote those experiments directly into production code or golden tests.
- Decompiled pseudo-JS is not the original source and may contain reconstruction errors.
- Library markers in an APK do not prove that its own screens or features use them.
- A generated packet passing our own decoder is not independent interoperability evidence.
- Do not claim latency, sampling rate, battery life, or compatibility without measurements.

## 8. Project boundaries

- Initial app generation/build is now authorized. Driver installation and changes
  to phone/network settings remain outside that authorization.
- Do not commit or publish anything unless requested.
- Select app branding and package identity before creating the Android project.
