from __future__ import annotations

import struct

from sim.model import Register
from sim.profile import HeatPumpProfile


def encode(register: Register, value: float, profile: HeatPumpProfile) -> list[int]:
    if register.is_float:
        return _encode_float(float(value), profile)
    return [_encode_single(register, int(round(value)))]


def decode(register: Register, registers: list[int], profile: HeatPumpProfile) -> float | int:
    if register.is_float:
        return _decode_float(registers, profile)
    return _decode_single(register, registers[0])


def _encode_float(value: float, profile: HeatPumpProfile) -> list[int]:
    high_word, low_word = _split_words(struct.pack(">f", value))
    words = [low_word, high_word] if profile.word_order == "little" else [high_word, low_word]
    return [_bytes_to_register(word, profile.byte_order) for word in words]


def _decode_float(registers: list[int], profile: HeatPumpProfile) -> float:
    pairs = [_register_to_bytes(reg, profile.byte_order) for reg in registers[:2]]
    low, high = pairs if profile.word_order == "little" else reversed(pairs)
    return struct.unpack(">f", high + low)[0]


def _encode_single(register: Register, value: int) -> int:
    if register.datatype == "BOOL":
        return 1 if value else 0
    return value & 0xFFFF


def _decode_single(register: Register, register_value: int) -> int:
    if register.datatype == "WORD" and register_value >= 0x8000:
        return register_value - 0x10000
    return register_value


def _split_words(four_bytes: bytes) -> tuple[bytes, bytes]:
    return four_bytes[:2], four_bytes[2:]


def _bytes_to_register(word: bytes, byte_order: str) -> int:
    return int.from_bytes(word, "big" if byte_order == "big" else "little")


def _register_to_bytes(register_value: int, byte_order: str) -> bytes:
    return register_value.to_bytes(2, "big" if byte_order == "big" else "little")
