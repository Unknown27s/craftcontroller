# Revision 0.6 — touch-first Xbox-style interface

## Implemented

- Original dark controller styling, colored ABXY, offset sticks, wide LB/RB
  bars and distinct LT/RT pads. Stronger filled/outlined pressed states.
- Height-scaled control geometry with left/right grip anchors. ABXY spacing
  stays square on wide phones; rendering, hit regions and editor transforms
  share one geometry model.
- Connected cross-shaped D-pad with one captured pointer and a neutral center.
  Diagonals and sticky outside-bound movement can be configured independently.
- Floating-stick option: initial touch within a stick ring sets its temporary
  center. Release/cancellation restores the saved position.
- Original vector View/Menu/Home/Settings symbols instead of font glyphs.
- Categorized Settings: Connection, Controls, Appearance and Advanced. Server
  and QR/manual entry appear side by side; diagnostics remain in Advanced.
- Appearance preview, four accents, persisted contrast, dead zone and haptics.
- D-pad moves/resizes/hides as one control in Layout studio. Version-1 layouts
  migrate their four D-pad placements to an averaged group placement; other
  controls retain their saved coordinates, scale and visibility. Version-2
  saves use the grouped model. Old separately hidden directions become one
  visible group if any old direction was visible.
- Existing custom layouts are preserved, not replaced. Select Xbox-style
  gamepad in Controls for the new default arrangement.
- Native adaptive vector launcher icon and Android 13+ monochrome variant.

## Design references and icon tooling

Interaction references: PPSSPP touch customization, RetroArch's separation of
visible shapes and touch regions, and Dolphin's grouped controls. No emulator
code, controller artwork or branding was copied. This phone is a dedicated
controller, so controls are solid rather than transparent game overlays.

- https://www.ppsspp.org/docs/settings/controls/
- https://docs.libretro.com/development/retroarch/input/overlay/
- https://github.com/dolphin-emu/dolphin/tree/master/Source/Android/app/src/main/java/org/dolphinemu/dolphinemu/overlay

SnapAI was selected as the preferred external artwork tool (MIT), but was not
installed or invoked: no image-provider API key/budget was supplied. The shipped
icon is an original native vector, not a claim of SnapAI output. No paid image
requests or automatic Expo configuration changes were made to this Kotlin app.

## Verification and bounds

- Build: `:app:assembleDebug`, successful, version 0.6.0-prototype (7).
- One focused unit-test execution: ten checks, zero failures/errors, covering
  layout migration/persistence, presets, proportional spacing, editor inverse
  transforms, D-pad cardinal/diagonal/neutral input and opposing-direction guards.
- Initial compilation caught unsupported Size addition expressions; these were
  corrected before the focused test execution. Final Settings layout adjustments
  were compiled in a package-only build; tests were not repeatedly rerun.
- Emulator-only visual inspection at 2400×1080. The API 37 emulator also showed
  Bluetooth crash/System UI ANR dialogs and its immersive-mode tutorial during
  startup; these are not evidence of a CraftController crash. No AndroidRuntime
  errors were present in the inspected log excerpt. UI Automator could not reach
  idle, so no automated walkthrough is claimed.
- No phone install, full test suite, benchmark, latency measurement, or Windows
  interoperability test. Network transport retains the 0.5.1 safeguards.
- QR saves an endpoint only; the Manager handshake remains unimplemented.
- Digital triggers, no motion/rumble/background streaming. Visual Xbox styling
  does not establish XInput compatibility.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Visual evidence: [controller](../docs/assets/controller-v0.6.png) and
[connection settings](../docs/assets/connection-v0.6.png). These are emulator
captures, not physical-phone or DSU Manager acceptance evidence.

APK SHA-256: `1d91a0e6c20f973ff63eb4e862afd014762870e2e3cf9cd4072ea0a690703058`.
