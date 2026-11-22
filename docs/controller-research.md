# CraftController: controller apps, emulation and customization

Research date: 21 November 2026. This is a documentation review, not a hands-on
comparison. Features below are attributed to published documentation or store
descriptions; they have not been independently tested. Prices, ratings and
performance are deliberately not compared.

Implementation follow-up: 0.4 adds QR scanning, manual entry, validation,
confirmation and PC endpoint storage. The original 0.3 work inventory below is
historical; post-scan pairing is still not implemented. See
[QR findings](manager-qr-findings.md).

## Decision for this project

The product name is **CraftController**. Keep the existing Android application
ID, `io.github.unknown27s.dsucontroller`, so an update can preserve installed app
data and the synthetic controller identity. A matching signing key is also
required for an in-place update. Name/trademark availability has not been checked.

The user selected **the existing Windows DSU Manager receiver**. Do not build a
new Windows/Linux connector, install a driver, or change firewall/input-device
permissions as part of this work. Prioritize the phone-to-Manager integration.

## What “controller emulation” actually means

There are three separate layers:

1. **Phone controls:** how buttons, sticks, motion and layouts produce input.
2. **Transport/receiver:** how that input reaches a PC application. CraftController
   currently serves standard DSU 1001 over UDP.
3. **Game-facing device:** how Windows exposes that input to a game, for example
   through an XInput virtual gamepad created by the receiver's driver/backend.

An Xbox-style layout is not an Xbox USB/Bluetooth device. A Wii-style layout is
not a complete Wii Remote implementation. Renaming buttons cannot provide a
missing virtual driver, pairing handshake, sensor mapping or emulator profile.

The intended path is:

```text
CraftController controls
        |
        +-- Manager pairing/control handshake [NOT IMPLEMENTED]
        |
        +-- DSU 1001 server [IMPLEMENTED, latest UI revision untested]
                    |
             existing Windows DSU Manager
                    |
             its virtual-device backend
                    |
             Windows game / emulator
```

The original DSU guide documents XInput output after pairing with DSU Manager
[S1]. That is evidence of the original application's intended workflow, not
proof that CraftController can already pair with it.

## Product comparison

| Product | Documented function | Customization approach | Other documented functions | Important qualification |
|---|---|---|---|---|
| DSU Controller + DSU Manager | DSU source for emulators; Windows Manager workflow creates virtual XInput [S1] | Web editor: set phone size/orientation, drag components, generate QR, scan on phone | Controller layouts, motion setup, double-tap stick-click; “Magic” combination-key layouts | QR layout import and Manager pairing are different workflows. Some instructions refer to older emulators/drivers. |
| DroidJoy | Windows receiver with XInput; its listing distinguishes older DInput support [S4,S5] | Customize template layouts for different game genres | Two sticks, D-pad, buttons, accelerometer, volume-key input; Wi-Fi/Bluetooth; up to four clients | Lite has limits. Official site documents server 2.1.0 and older Windows versions; this is not a Windows 11 compatibility result. |
| Monect PC Remote | Phone/PC remote-control suite with a desktop receiver [S6,S7] | Custom button layouts; current store notes advertise a Layout Store | Sensors, mouse/keyboard, screen/camera sharing, file transfer, virtual displays; several connection options | Broader than a controller. Store security/performance statements are vendor claims, not verified here. Exact layout-store workflow was not inspected. |
| Steam Input | Controller configuration and gamepad/legacy mappings within Steam [S8] | Action-oriented touch/radial menus with configurable position, size, opacity, labels and icons | Context-specific bindings and input-source modes are linked from the docs | The cited Touch Menu page requires a trackpad. It is not evidence of a standalone Android touchscreen editor. |
| AntiMicroX | Maps an existing gamepad to keyboard, mouse, scripts and macros [S9,S10] | Switchable mapping sets and application profiles; named actions help share understandable profiles | SDL2 configuration generation; auto profiles with a Wayland limitation | Primarily gamepad-to-desktop mapping, not a phone-to-XInput receiver. Its README states limited support and a maintainer search. |

### Important evidence distinction

The DSU guide links issues about external-controller input and locking buttons.
Issue titles/bodies are feature requests [S11,S12], not proof that every proposed
behavior shipped. The user's screenshot shows a local-gamepad toggle, but that
does not establish device compatibility, mapping completeness or supported
connection types. Record these as features to investigate, not verified support.

Likewise, user reviews are not the basis for Monect feature claims in this report;
the comparison uses the developer's description and release-note text.

## CraftController feature inventory

| Function | Current implementation | What remains |
|---|---|---|
| Buttons, D-pad, two sticks | Standard DSU state generation | Physical multitouch and receiver checks later |
| Shoulder/trigger controls | Digital 0/255 pressure | Variable analog-trigger interaction is not implemented |
| Stick-click | Separate L3/R3 controls | Double-tap-to-click is not implemented |
| Layout editing | Move, resize, hide/show, reset and discard confirmation | Undo/redo, snapping, overlap guidance |
| Templates | Four built-in screen presets and one saved custom layout | Named per-game custom profiles |
| Appearance/touch | Four colors, dead zone and optional system button haptics | Per-control labels, binding UI and accessibility review |
| Connection help | Windows receiver guide, endpoint copy/share, status counters | Verified DSU Manager pairing/control handshake |
| Multiple receivers | Up to eight endpoint subscriptions to the same slot | This is not eight independent players/controllers |
| Sharing | Share phone connection text only | Layout/profile import and export are not implemented |
| Motion, rumble, physical passthrough | Not implemented | Receiver contract and device-specific handling |
| Account/cloud | Not required by the Android app | External help links open the browser only on request |

“Implemented” describes source availability, not a passed runtime check. No new
testing was performed for this revision.

## How these products make customization easier

### Start with a template instead of an empty canvas

DroidJoy documents configurable templates. The original DSU app supplies several
named layouts. CraftController already provides Standard, Compact, Left-hand and
Right-hand presets. Keep the hardware-protocol caveat beside these choices.

Next improvement: let a user name and save a separate layout for each game,
duplicate an existing one, and switch without overwriting another layout.

### Make direct manipulation available where the user plays

The DSU web editor has a component palette and a sized canvas, then transfers the
result with a QR code. CraftController's on-phone editor avoids a mandatory
browser/service round trip for basic moving, resizing and hiding.

Next improvements: undo/redo, optional grid snapping, an always-visible control
list for hidden controls, overlap warnings, and a single-control reset. Overlap
warnings should not silently move a carefully arranged layout.

### Show action meaning separately from button identity

Steam Input and AntiMicroX show the value of readable action names. A visible
label such as “Jump” and the transmitted DSU button “A” are different properties.
CraftController should display the output binding explicitly before adding
relabel/remap controls. Never imply a label change modifies a game's bindings.

### Make transfer and recovery explicit

The original DSU app's QR layout flow lowers transfer friction. A future
CraftController format should be versioned JSON with bounded size, strict control
IDs, validated ranges and explicit import confirmation. Import should create a
new profile rather than overwrite one silently. QR import must not auto-open URLs
or mix a layout document with Manager credentials.

### Separate finding the PC from choosing a layout

DroidJoy documents a server-search step; the original DSU workflow uses Manager
QR pairing. CraftController should have a dedicated PC setup screen rather than
bury connection errors among color/size controls. Do not show “Connected” after
merely opening a socket, copying an IP, or observing a generic DSU subscription.

## Windows DSU Manager integration: current evidence and blockers

The published Manager release identified through its GitHub release API is
**v0.6.2**, with Windows x64 setup EXE and MSI assets [S3]. The user's installed
version is still unknown. The Manager README points to the Controller guide; it
does not publish a pairing/control API [S2].

The original Windows instructions say: open Manager, press Start, display a QR
code, then scan it using the original phone app [S1]. A follow-up review decoded
the official screenshot and a public issue's QR: both use
`dsu-controller://connect?ip=<PC_IPV4>&port=<PORT>`, with different advertised
ports. See [QR findings and scanner contract](manager-qr-findings.md). This resolves
the basic observed URI shape, but not the post-scan control exchange or all
current-version behavior. Reports also mention manual
connection in some versions [S13]. This does **not** establish a supported UI for
adding any arbitrary phone DSU endpoint directly.

We still need:

- The exact installed Windows DSU Manager version and architecture.
- The control handshake and version-specific QR validation rules. The basic
  observed URI shape is now known, but it does not prove the subsequent messages
  or authentication behavior.
- Which address is advertised: PC listener, phone DSU endpoint, or another
  control service; required ports/transports; expiry/reconnect behavior.
- Whether the receiver consumes standard DSU buttons/sticks directly and which
  extensions, if any, it expects.
- Later validation of discovery/pairing, DSU subscription, Windows virtual device
  creation, button/axis mapping and a real game, as separate stages.

Until then, a phone address helper is useful but cannot replace QR pairing. The
new app setup screen states this explicitly and never sends guessed control
messages. QR codes may contain tokens or credentials; do not publish them.

### Driver lifecycle

The original guide mentions ViGEmBus. The driver's author states that ViGEmBus
and client libraries are retired and no longer receive updates, and warns about
old updater configuration [S14]. Do not repeat “install the latest ViGEmBus” as
unqualified current advice. Inspect the existing Manager's actual dependency and
refer to the official notice. No driver installation/removal is authorized here.

## CraftController work completed in this pass

- Renamed the app, project and public site to CraftController; kept the Android
  application ID to preserve upgrade continuity.
- Added **PC setup** and a **Windows receiver** settings entry for the existing
  DSU Manager workflow, not a new desktop connector.
- Added selectable phone connection details, explicit Refresh, Copy and Share.
  Copy/share require a usable IPv4 address and valid port. Sharing remains
  user-initiated and opening another app stops the foreground server.
- Added differentiated listener/subscriber status. A subscriber is not labelled
  as a verified Manager, XInput device or working game connection.
- Added official setup/release links and the driver lifecycle warning.
- Documented the missing pairing handshake and kept QR, new drivers and simulated
  success states out of the implementation.

This pass does not add multiple profiles, macros, motion, rumble, QR scanning,
external-controller forwarding or a new PC backend. Those are roadmap items.

## Prioritized roadmap

| Priority | Work | Why / dependency | Current state |
|---|---|---|---|
| P0 | CraftController name and receiver setup UX | User-selected identity and existing Windows receiver | Implemented in source; untested |
| P0 | Establish Manager pairing/control contract | Required before claiming Windows receiver integration | Blocked on version/protocol evidence |
| P0 | Implement verified pairing, cancellation and reconnect | Must follow the actual receiver contract, not guesses | Not implemented |
| P1 | Named game profiles, duplicate/rename/delete | Biggest customization convenience improvement | Planned |
| P1 | Undo/redo, snapping, hidden-control list | Reduces accidental edits and recovery effort | Planned |
| P1 | Versioned JSON export/import | Offline sharing without mandatory cloud service | Planned |
| P2 | Explicit action labels and DSU remapping | Avoid confusing button appearance with output | Planned |
| P2 | Physical controller input | Requires per-device axis, hat, trigger and cancellation rules | Planned |
| P2 | Motion | Requires receiver units/axes/timestamp validation | Planned |
| P3 | Rumble/LED extensions, advanced gestures/macros | Receiver extension support and safe release behavior needed | Deferred |

No unit, UI, emulator, interoperability or performance tests were run for this
pass after the user's instruction to defer testing. APK assembly is packaging,
not runtime validation. The compatibility gate remains open.

## Sources

- **S1:** Original DSU Controller guide, including editor, emulator, Windows Manager
  and Magic-layout workflows: <https://github.com/breeze2/dsu-controller-guides>
- **S2:** Manager guide: <https://github.com/breeze2/dsu-manager-guides>
- **S3:** Published Manager release: <https://github.com/breeze2/dsu-manager-guides/releases/tag/v0.6.2>
- **S4:** DroidJoy official product page: <https://grill2010.github.io/droidJoy.html>
- **S5:** Developer's DroidJoy Lite listing: <https://play.google.com/store/apps/details?id=com.grill.droidjoy_demo&hl=en>
- **S6:** Monect official site: <https://www.monect.com/>
- **S7:** Developer's PC Remote listing, updated 24 October 2026 in the fetched page:
  <https://play.google.com/store/apps/details?id=com.monect.portable&hl=en>
- **S8:** Valve Touch Menus documentation:
  <https://partner.steamgames.com/doc/features/steam_controller/touch_menus>
- **S9:** AntiMicroX README: <https://github.com/AntiMicroX/antimicrox>
- **S10:** AntiMicroX profile tips: <https://github.com/AntiMicroX/antimicrox/blob/master/ProfileTips.md>
- **S11:** External controller feature request: <https://github.com/breeze2/dsu-controller-guides/issues/12>
- **S12:** Button-lock feature request: <https://github.com/breeze2/dsu-controller-guides/issues/25>
- **S13:** Example report mentioning manual connection and differing receiver paths:
  <https://github.com/breeze2/dsu-manager-guides/issues/26>
- **S14:** Driver author's EOL/security notice: <https://docs.nefarius.at/projects/ViGEm/End-of-Life/>

Public documentation is evidence of advertised behavior, not a license to copy
application source, graphics, branding, receiver binaries or driver code.
