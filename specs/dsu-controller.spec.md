# CraftController — Independent Android DSU Controller Specification

## 1. Overview

Provide a customizable touchscreen controller and motion source for compatible
PC software using DSU over a local UDP connection. Own the Android implementation
and local settings; do not depend on a third-party hosted service.

Scope has not been finalized. The following is a proposed staged baseline,
not approval to begin implementation.

## 2. Proposed first version

- One connected controller slot.
- D-pad, standard buttons, triggers, and two virtual analog sticks.
- Motion data when supported by the phone.
- Local server start/stop, configurable port, and visible connection information.
- Basic customization: theme, control scale, and stick dead zone.
- Local persistence of settings.
- Landscape-first interface; exact orientation behavior to be decided.

Deferred: drag-and-drop layout editor, multiple profiles, layout import/export,
multiple controller slots, background streaming, rumble/LED extensions, our own
desktop connector, and internet play.

## 3. Functional requirements (EARS)

| ID | Requirement |
|---|---|
| F01 | When the user starts the server, the app shall attempt to bind the configured UDP port and display the result. |
| F02 | When the user stops the server, the app shall close its socket and clear active subscriptions. |
| F03 | When a supported valid DSU version request arrives, the server shall return a correctly framed version response. |
| F04 | When a valid controller-info request arrives, the server shall report the requested supported controller slots. |
| F05 | When a valid controller-data subscription arrives, the server shall register the peer according to verified protocol rules. |
| F06 | While a subscription is active, the server shall send current controller data using the verified packet layout and refresh/expiry rules. |
| F07 | When a user presses, moves, or releases a control, the app shall update the corresponding controller-state fields. |
| F08 | When a touch is cancelled or control focus is lost, the app shall clear affected pressed controls. |
| F09 | Where required motion sensors are available, the app shall encode their data using the receiver's required units, axes, and timestamps. |
| F10 | Where a required sensor is unavailable, the app shall identify the missing capability and follow a documented fallback policy. |
| F11 | When the network changes or becomes unavailable, the app shall invalidate stale peers and display connection state. |
| F12 | When the user saves customization settings, the app shall store them locally and restore them after restart. |
| F13 | When an invalid or unsupported packet arrives, the server shall reject it without crashing or growing unbounded state. |
| F14 | While the server is stopped, the app shall not stream controller data. |

## 4. Non-functional requirements

- No account, analytics, remote configuration, or cloud dependency in the baseline.
- Networking and packet encoding must be testable without the Android UI.
- Packet parsing must validate bounds before reading any field.
- Maintain bounded peer storage and explicit rate limits; choose limits before implementation.
- Measure update rate, jitter, input-to-send latency, CPU use, and battery impact.
  Numerical acceptance limits are still to be agreed, not assumed.
- Record phone model, Android version, receiver version, and network conditions
  for interoperability and performance results.
- Accessibility labels and minimum control sizing must be reviewed in the UI phase.

## 5. Logical separation

```text
Compose controls + Android sensors
                  |
           Controller state
                  |
        DSU codec + subscriptions
                  |
             UDP transport
                  |
         Verified PC receiver
```

UI layout must not contain packet-building logic. Sensor acquisition must be
separate from unit/axis conversion. Changing appearance should not require
changing protocol code.

## 6. Acceptance criteria

1. Given a known-good version request, when the codec produces a response,
   then an independent parser accepts its lengths, type, and CRC.
2. Given a supported PC receiver on the same LAN, when it queries the phone,
   then it detects the intended controller slot without a hosted service.
3. Given an active subscription, when the user presses/releases each button
   and moves each stick, then the receiver reports the matching input and neutral state.
4. Given documented motion-axis tests, when the phone is rotated along each axis,
   then receiver direction and scale match the agreed mapping.
5. Given active touches, when input is cancelled or the app loses focus,
   then affected controls become released rather than sticking.
6. Given truncated, invalid-CRC, or unsupported packets, when received,
   then they are rejected without a crash or unintended subscription.
7. Given a disconnected peer, when its verified expiry condition is met,
   then streaming to that peer stops.
8. Given saved local settings and no internet, when the app restarts,
   then those settings remain available.

## 7. Error handling

| Condition | Expected response |
|---|---|
| Port already occupied | Show bind failure; do not claim the server is running. |
| No usable LAN address | Show diagnosis; distinguish address detection from socket bind state. |
| Invalid packet | Drop safely; optionally count failures without logging sensitive payloads. |
| Unsupported protocol version/type | Follow documented compatibility behavior; do not guess a response. |
| Missing sensor | Explain missing capability; use the explicitly selected fallback. |
| Lost network | Clear stale session state and show disconnection. |
| Corrupt local settings | Recover safely with a clear reset option. |
| Receiver ignores buttons | Verify receiver capability before blaming Android packet generation. |

## 8. Implementation phases and gates

### Phase 0 — Resolve compatibility
- [ ] Choose first PC target: Fedora emulator, DSU Manager, or another receiver.
- [ ] Obtain its exact version and authoritative protocol documentation.
- [ ] Review licenses for any implementation used as a reference.
- [ ] Establish supported core messages and extensions.
- [ ] Decide app/package name, minimum Android version, and project license.

### Phase 1 — Protocol proof, before polished UI
- [ ] Write independent codec with fixed verified packet fixtures.
- [ ] Test CRC, endianness, lengths, invalid input, and version handling.
- [ ] Implement one slot and bounded subscription lifecycle.
- [ ] Prove live version/info/data exchange with an independent receiver.
- [ ] Save reproducible evidence with versions and test conditions.

### Phase 2 — Android controls
- [ ] Implement foreground server lifecycle and status/error display.
- [ ] Build a basic Compose gamepad with multitouch and cancellation handling.
- [ ] Verify neutral state, dead zones, trigger range, and stick mapping.
- [ ] Test physical phone-to-PC input end to end.

### Phase 3 — Motion and customization
- [ ] Establish sensor units, axes, timestamps, calibration, and availability handling.
- [ ] Test orientation changes and each motion axis independently.
- [ ] Add local theme, scale, and dead-zone preferences.
- [ ] Measure update rate, jitter, CPU use, and battery impact.

### Phase 4 — Hardening and release decision
- [ ] Test screen lock, app switching, reconnect, Wi-Fi/hotspot/VPN scenarios.
- [ ] Test malformed traffic and bounded subscriptions/rates.
- [ ] Review accessibility and dependency licenses.
- [ ] Decide whether advanced layouts or an independent desktop connector come next.

## 9. Decisions requiring user input later

- First PC receiver and target OS beyond the current Fedora machine.
- Buttons-only, motion-only, or both for the first demonstrated milestone.
- Desired visual style and depth of layout customization.
- Foreground-only versus eventual background operation.
- Multi-controller support, rumble, and LED priority.
- Measured performance targets and supported test devices.

Do not block creation of this planning folder on these choices. Resolve them
before their corresponding implementation phase.
