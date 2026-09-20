from __future__ import annotations

from sim.codec import encode
from sim.model import RegisterMap
from sim.profile import HeatPumpProfile


class RegisterStorage:
    def __init__(self, registers: RegisterMap, profile: HeatPumpProfile):
        self._words: dict[int, int] = {}
        for register in registers:
            seed = encode(register, register.initial_value(), profile)
            self.set(register.address, seed)

    def get(self, address: int, count: int) -> list[int]:
        return [self._words[address + offset] for offset in range(count)]

    def set(self, address: int, words: list[int]) -> None:
        for offset, word in enumerate(words):
            self._words[address + offset] = word & 0xFFFF
