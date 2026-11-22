"""Unit tests for the independent Python DSU decoder."""

import unittest
from probe import decode, request


class ProbeTests(unittest.TestCase):
    def test_fixed_version_response(self) -> None:
        fixture = bytes.fromhex("44535553e90306005231611b7856341200001000e903")
        self.assertEqual(bytes.fromhex("e903"), decode(fixture, 0x100000))

    def test_requests_use_client_magic_and_correct_size(self) -> None:
        self.assertEqual(b"DSUC", request(0x100000)[:4])
        self.assertEqual(28, len(request(0x100002, bytes(8))))

    def test_reject_corrupt_short_or_wrong_type_packets(self) -> None:
        fixture = bytes.fromhex("44535553e90306005231611b7856341200001000e903")
        for packet, message_type in [(b"", 0x100000), (fixture[:-1], 0x100000),
                                     (fixture, 0x100002), (fixture[:-1] + b"\xff", 0x100000)]:
            with self.assertRaises(ValueError):
                decode(packet, message_type)


if __name__ == "__main__":
    unittest.main()
