# Revision 0.7 — scan and announce

## Why

The 0.6 scanner only saved the PC endpoint. It never told the Manager that a
phone existed, so the Manager had nothing to subscribe to. A second problem made
the first worse: launching the camera activity pauses the app, and `onPause`
stops the foreground server. After scanning, the listener was off even when the
user had started it before.

## Implemented

- `DsuServer.announce(host, port, repeats = 3, spacingMs = 60)` queues a DSU
  version announcement. It is sent from the **bound listener socket**, not a
  throwaway socket, so the PC's subscription returns to the phone's real UDP
  port.
- Announcements are drained on the server's IO coroutine. A 4 ms receive
  timeout keeps the three sends close to their nominal 0 / 60 / 120 ms slots.
- Stopping or failing the listener clears pending announcements, so a later
  restart cannot replay stale traffic.
- Scanner redesign: scanning a valid QR saves the endpoint and announces
  immediately. There is no extra review dialog for a code the user deliberately
  scanned.
- If the camera had paused a running server, it is restarted before the
  announcement is sent. Cancelling the scan also restarts it.
- Manual entry validates the address, saves it and announces on **Connect**.
- The card shows endpoint, live subscriber count and a **Send again** action.
- Receiver setup copy now describes the announcement and subscription instead of
  claiming pairing is unimplemented.

## Evidence basis

From the installed original DSU Controller 3.2.5 bytecode:

- QR handler calls `respondVersion` with the QR address and port.
- `repeatAction` defaults to three calls, 60 ms apart, first one immediate.
- The version response is built as a 22-byte `DSUS` packet with protocol 1001.
- Sends go through the server's existing socket.

## Face-button mapping correction

The Xbox-style layout places Y on top, X on the left, B on the right and A at
the bottom. DSU button bank 2 is named square (0x80), cross (0x40), circle
(0x20), triangle (0x10). The previous mapping was rotated one step, so A sent
circle, B sent cross, X sent triangle and Y sent square.

Corrected:

| Control | DSU bit | Pressure byte |
|---|---:|---:|
| X (left) | 0x80 square | 28 |
| A (bottom) | 0x40 cross | 29 |
| B (right) | 0x20 circle | 30 |
| Y (top) | 0x10 triangle | 31 |

## What this revision does not prove

- That the Windows Manager subscribes after the announcement.
- That it creates a virtual gamepad.
- That a game receives input.
- That the selected button layout maps to the intended Manager buttons.
- Sensor, touch and timestamp fields are still neutral.

Subscriber count greater than zero is the next checkpoint: it proves the PC sent
a valid DSU subscription to the phone. It does not prove Windows output.
