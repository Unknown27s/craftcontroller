# CraftController 0.4: Manager QR scanner

## Implemented

- Offline camera QR scanning using ZXing Android Embedded 4.3.0 (Apache 2.0).
- Camera access requested only when Scan QR code is selected; denial, unavailable
  camera, scanner cancellation and manual entry paths are presented explicitly.
- Strict parser for the observed published Manager URI:
  `dsu-controller://connect?ip=<PC_IPV4>&port=<PORT>`.
- Bounded input, duplicate/unknown-field rejection, numeric port/range checks,
  IPv4 validation and rejection of loopback/multicast/unspecified endpoints.
- Separate PC Manager and phone DSU listener ports. The Manager port is read from
  the QR, never hardcoded to the phone's UDP listener port.
- Explicit review before saving; cancel keeps the previous endpoint intact.
- Saved endpoint display and Forget endpoint. No endpoint is contacted.
- Camera images are not stored or uploaded; no raw QR logging or URL launching.
- Receiver route and in-progress review/manual text use saved-instance state for
  returning from the scanner. Opening it still pauses the foreground server.

## How to use

1. On Windows, open the existing DSU Manager and display its QR.
2. In CraftController, open **PC setup > Scan QR code**.
3. Grant camera permission if desired; otherwise choose **Enter manually**.
4. Review the PC IPv4 and advertised Manager port; tap **Save endpoint**.
5. The status is **endpoint saved, NOT paired**. Saving a QR does not implement
   the missing Manager pairing/control exchange or establish Windows XInput.

## Scope limits

This build adds the scanner and validated endpoint storage, not complete pairing.
The post-scan transport/control messages remain unresolved. No unit, camera,
emulator, UI, interoperability or latency tests are run, as requested. APK
assembly is packaging only. Runtime behavior and Android-version compatibility
remain unverified.
