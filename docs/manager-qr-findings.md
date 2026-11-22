# DSU Manager QR findings

Research date: 21 November 2026. Public documentation/images only. No application
tests, pairing attempts, driver changes or requests to encoded endpoints.

## Confirmed from published QR images

The Manager README redirects Windows users to the original Controller guide:

<https://github.com/breeze2/dsu-controller-guides#run-with-dsu-manager-on-windows>

That guide includes a downloadable screenshot of the Manager-generated QR:

<https://raw.githubusercontent.com/breeze2/dsu-controller-guides/main/assets/dsu-manager/qrcode.png>

Decoding that image produces a URI with this structure (address redacted):

```text
dsu-controller://connect?ip=<PC_IPV4>&port=<PORT>
```

Observed URI components:

- Scheme: `dsu-controller`.
- Authority/action: `connect`.
- Empty path.
- Query fields: exactly one `ip` and one `port` in these examples.
- The `ip` values have IPv4 shape; the ports are decimal numbers.
- The official example advertises port **50543**. A public QR attached to
  <https://github.com/breeze2/dsu-manager-guides/issues/7> advertises **58457**.
  Neither value is a default to hardcode. The advertised Manager endpoint must
  not be confused with CraftController's phone-side DSU UDP listener at 26760.

The published examples establish this URI shape, not every supported version or
all future fields. Their private IP addresses are deliberately not reproduced.

## What the repository does and does not provide

The Manager repository tree has guide/release files rather than receiver source.
Its `package.json` references `@breeze2/dsu-manager-web` version range `^0.6.2`,
marks the package private, and uses `UNLICENSED`. The exact-version package URL
on the public npm registry returned 404 during this review. This is not proof
that no implementation exists elsewhere, nor permission to reuse its code.

The guide documents the user workflow: start Manager, display its QR, then scan
using the original app. It does not specify the next packet, transport,
WebSocket/HTTP route, message encoding, authentication or reconnect sequence.

## Correction to the earlier blocker

The QR payload format is **no longer entirely unknown**. It was recoverable by
decoding the guide's published screenshot. The remaining blocker is the
post-scan Manager connection/control protocol and current-version compatibility.
Do not keep asking for a user QR merely to discover the basic URI format.

## Scanner implementation contract

An independent CraftController scanner can now be designed to:

1. Request camera access only when Scan is selected; also offer manual entry.
2. Recognize the observed `dsu-controller://connect` URI rather than opening it
   as an arbitrary link. Layout QR payloads must be handled separately.
3. Bound the payload size and validate scheme, action, duplicate fields, address
   and port. Reject unsupported formats with an explanation.
4. Show the parsed PC endpoint for explicit confirmation. Do not scan-and-send
   automatically, log raw payloads, or contact an endpoint during research.
5. Keep the Manager port separate from the phone DSU listener port.
6. Report “QR read” or “endpoint saved” separately from “paired” and from
   “Windows virtual controller available”.
7. Send a connection request only after its transport and wire contract are
   established; successful decoding alone is not a pairing implementation.

Follow-up: version 0.4 implements the scanner, manual entry, validation, review,
save and forget flows described above. It still does not implement the pairing
exchange or claim pairing works. Testing remains deferred at the user's request.

## Resolved in 0.7: the post-scan message is a DSU announcement

Read-only analysis of the installed original app (3.2.5) found that its QR handler
calls the phone server's `respondVersion` with the QR address and port. That is a
22-byte binary DSU 1001 `DSUS` version packet sent by **UDP** from the server's
bound socket, three times at nominal 0 / 60 / 120 ms. It is not HTTP, WebSocket
or an application-level login, and it is not awaited before the app shows its
"connected" message.

CraftController 0.7 therefore scans, saves, restarts the server if the camera
paused it, and announces from the bound socket. Items 4 and 7 above are
superseded for this specific, evidence-backed message. Everything after the
announcement — Manager subscription order, Windows virtual device and game
input — is still unverified. See `specs/revision-0.7.md`.

