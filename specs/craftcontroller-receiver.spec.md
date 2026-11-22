# CraftController: existing Windows receiver integration

Status: implementation plus unresolved integration gate. Testing deferred.

## Scope decisions from the user

- Product name: CraftController.
- Priority: PC controller emulation, using the Windows receiver that already exists.
- Therefore reuse DSU Manager; do not create a new desktop connector or install drivers.
- Research customization workflows, document the feature roadmap, and make useful
  phone-side improvements without claiming unverified compatibility.

Research: [comparison and evidence](../docs/controller-research.md).

## Functional requirements (EARS)

| ID | Requirement | State |
|---|---|---|
| C01 | When Android shows this application's name, it shall display CraftController. | Implemented |
| C02 | When an existing signed installation is upgraded, the app shall retain the application ID and existing settings format. | Source retained; upgrade not tested |
| C03 | When the user opens PC setup, the app shall identify the existing Windows DSU Manager as the intended receiver. | Implemented |
| C04 | When the user refreshes connection details, the app shall enumerate usable phone addresses without assuming a Wi-Fi interface name. | Implemented |
| C05 | When the user copies or shares details, the app shall require a valid port and usable IPv4 address and shall identify them as phone listener details. | Implemented |
| C06 | While there are no active subscriptions, the app shall not claim a receiver or Windows gamepad is connected. | Implemented |
| C07 | When there is a DSU subscription, the app shall distinguish it from verified XInput output or game input. | Implemented |
| C08 | While Manager pairing is unsupported, the app shall state that limitation and shall not send guessed pairing/control messages. | Implemented |
| C09 | When the user requests receiver help, the app shall open official setup/release information and disclose the referenced driver's retired status. | Implemented |
| C10 | When a verified Manager pairing contract is available, the app shall validate its version, endpoints and authentication requirements before pairing. | Blocked; not implemented |
| C11 | When a future pairing session fails or expires, the app shall report the actual failure and clear stale state without claiming success. | Blocked; not implemented |

## Non-functional constraints

- No accounts, telemetry or cloud dependency added to the Android app.
- Sharing is explicit. Do not embed QR tokens/passwords in diagnostics or public logs.
- Maintain the current bounded DSU packet/subscription handling.
- No driver installation, input-group change, firewall change or receiver launch.
- No measured latency, sampling rate, battery or compatibility claims.
- Preserve credit to breeze2 while identifying CraftController as independent.

## Acceptance criteria for later validation

1. Given an existing matching-key installation, when it updates, then the launcher
   name changes while saved layout, color and controller identity remain available.
2. Given no usable IPv4 address, when PC setup opens, then Copy/Share are disabled
   and no endpoint or connection success is fabricated.
3. Given a running DSU listener with zero subscribers, when status is shown, then
   it reports listening rather than a connected Windows controller.
4. Given a valid subscription, when it appears, then the screen reports a DSU
   subscriber without asserting receiver identity or Windows device creation.
5. Given the existing Windows Manager's exact version and public pairing contract,
   when pairing is implemented, then protocol evidence and end-to-end input checks
   must precede any “Manager compatible” claim.

## Error handling

| Condition | Required response |
|---|---|
| Missing IPv4 or invalid port | Show source status; disable invalid sharing/start operations. |
| Missing QR/control contract | Explicit unsupported message; no fake scan/connect action. |
| External help/share app unavailable | Do not crash; preserve settings. |
| Retired driver referenced by old guide | Link official lifecycle notice; do not auto-install. |
| DSU traffic but no game input | Distinguish pairing, DSU subscription, virtual device and game binding stages. |

## Implementation checklist

- [x] Rename visible identity; retain Android application ID.
- [x] Document comparison, customization lessons and evidence limits.
- [x] Add existing-receiver setup screen, listener details and explicit copy/share.
- [x] Add truthful status and lifecycle/driver warnings.
- [ ] Obtain user's installed Manager version and pairing contract.
- [ ] Implement receiver-specific pairing only after the contract is established.
- [ ] Validate later, including actual Windows virtual-device output.
- [ ] Return to profiles and editor usability after the receiver integration gate.
