# Revision 0.7.1 — slot 1 and frame pacing

## Why

0.7.0 announced itself and the PC subscribed, but moving the on-screen controls
produced no response. Two Wireshark captures on the same phone and PC found the
cause.

## Capture evidence

Capture A: original DSU Controller 3.2.5. Capture B: CraftController 0.7.0 with
an independent DSU probe also listening.

| Field | Original 3.2.5 | CraftController 0.7.0 |
|---|---|---|
| Manager subscription | `flags=1 slot=1` | `flags=1 slot=1` |
| Accepted | Yes | No — code required slot 0 |
| Data destination | Manager `:53846` | Probe `:60998` |
| Data packets | 4,931 in 80.4 s | 2,178 to probe, 0 to Manager |
| Average rate | ~61.4 Hz | ~52.4 Hz |
| Data slot | 1 | 0 |
| Identity | state 2, model 2, conn 1, `DSU001`, battery 5 | state 2, model 1, conn 0, random MAC, battery 0 |
| Sequence | starts at 1 | starts at 0 |
| Timestamp | live epoch µs | zero |

The probe worked because it subscribed with `flags=0`, a wildcard. The Manager
asked for slot 1 specifically, so the slot check rejected it.

## Implemented

- `DsuCodec.SLOT = 1`. `matches()` accepts `flags=1 slot=1`; `data()` and
  `info(1)` advertise slot 1. Slot 0 is no longer the accepted controller slot.
- Fixed identity `DSU001`, model 2, connection field 1 and battery field 5 to
  match the reference app. Battery 5 is a compatibility constant, not a phone
  reading. Accelerometer and gyroscope values remain zero; no motion input is
  provided.
- DATA carries a live epoch-microsecond timestamp and the first sequence number
  is 1.
- Frame scheduling advances `nextFrame` by `FRAME_NS` instead of restarting from
  the current tick, so the 4 ms receive timeout is no longer added to every
  frame. A resync guard prevents a burst after a real stall.
- Receiver setup text now says slot 1.

## Still unverified

- That the Manager accepts the corrected stream and creates a Windows gamepad.
- That a game receives input.
- The measured rate and jitter after the pacing change.
- Motion: the app advertises model 1 (no gyro) and sends zero accelerometer and
  gyroscope values. It does not claim motion support.

The next checkpoint is the Manager's subscriber count while the controller is
open, then `joy.cpl`, then a game.
