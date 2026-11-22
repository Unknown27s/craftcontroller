#!/usr/bin/env python3
"""Independent standard-library DSU probe; no Android or Kotlin codec dependency."""

import argparse
import socket
import struct
import time
import zlib


def request(message_type: int, payload: bytes = b"") -> bytes:
    packet = bytearray(struct.pack("<4sHHIII", b"DSUC", 1001, 4 + len(payload), 0, 42, message_type) + payload)
    struct.pack_into("<I", packet, 8, zlib.crc32(packet))
    return bytes(packet)


def decode(packet: bytes, message_type: int) -> bytes:
    if len(packet) < 20:
        raise ValueError("truncated response")
    magic, version, size, crc, _, actual_type = struct.unpack_from("<4sHHIII", packet)
    if (magic, version, size, actual_type) != (b"DSUS", 1001, len(packet) - 16, message_type):
        raise ValueError("invalid response header")
    checked = bytearray(packet)
    checked[8:12] = bytes(4)
    if zlib.crc32(checked) != crc:
        raise ValueError("invalid response CRC")
    return packet[20:]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("host", help="phone's LAN IPv4 address")
    parser.add_argument("--port", type=int, default=26760)
    parser.add_argument("--seconds", type=float, default=10)
    args = parser.parse_args()
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
        client.settimeout(3)
        client.connect((args.host, args.port))
        client.send(request(0x100000))
        version = decode(client.recv(1024), 0x100000)
        assert version == struct.pack("<H", 1001), "wrong version payload"
        print("Version and CRC: OK")
        client.send(request(0x100001, struct.pack("<iB", 1, 0)))
        info = decode(client.recv(1024), 0x100001)
        assert len(info) == 12 and info[:2] == bytes([0, 2]), "slot 0 not connected"
        print("Slot 0 connected; identity:", info[4:10].hex(":"))
        started = time.monotonic()
        refresh = 0.0
        count = 0
        previous = None
        while time.monotonic() - started < args.seconds:
            now = time.monotonic()
            if now >= refresh:
                client.send(request(0x100002, bytes([1, 0]) + bytes(6)))
                refresh = now + 1
            data = decode(client.recv(1024), 0x100002)
            assert len(data) == 80, "invalid controller-data length"
            count += 1
            buttons = tuple(data[16:20])
            sticks = tuple(data[20:24])
            current = (buttons, sticks)
            if current != previous:
                print("buttons:", buttons, "sticks LX/LY/RX/RY:", sticks)
                previous = current
        elapsed = time.monotonic() - started
        print(f"Received {count} valid frames in {elapsed:.2f}s ({count / elapsed:.1f} packets/s)")
        print("This verifies standard DSU only, not DSU Manager interoperability.")


if __name__ == "__main__":
    main()
