from __future__ import annotations

import logging
from enum import Enum, auto
from typing import Optional

from sim.codec import decode
from sim.model import Register, RegisterMap
from sim.profile import HeatPumpProfile
from sim.register.register_storage import RegisterStorage
from sim.register.validation import rejection_reason

logger = logging.getLogger("sim.registers")

Segment = tuple[Register, list[int]]


class WriteOutcome(Enum):
    OK = auto()
    ILLEGAL_ADDRESS = auto()
    ILLEGAL_VALUE = auto()


class ModbusRegisters:
    """The register image as a Modbus master sees it: whole registers only."""

    def __init__(self, storage: RegisterStorage, registers: RegisterMap, profile: HeatPumpProfile):
        self._storage = storage
        self._registers = registers
        self._profile = profile
        self._readable: set[int] = set()
        self._collect_readable(registers)

    def _collect_readable(self, registers: RegisterMap) -> None:
        for register in registers:
            if register.readable:
                self._readable.update(range(register.address, register.address + register.width))

    def read(self, address: int, count: int) -> Optional[list[int]]:
        slots = range(address, address + count)
        if not all(slot in self._readable for slot in slots):
            return None
        return self._storage.get(address, count)

    def write(self, address: int, values: list[int]) -> WriteOutcome:
        segments = self._segments(address, values)
        if segments is None:
            return WriteOutcome.ILLEGAL_ADDRESS
        if not self._all_values_acceptable(segments):
            return WriteOutcome.ILLEGAL_VALUE
        for register, words in segments:
            self._storage.set(register.address, words)
        return WriteOutcome.OK

    def _segments(self, address: int, values: list[int]) -> Optional[list[Segment]]:
        """Split the request into whole writable registers, or None if it does not fit.

        A write that starts inside a register, or stops halfway through one, would
        leave a FLOAT half overwritten — a value nobody asked for.
        """
        segments: list[Segment] = []
        offset = 0
        while offset < len(values):
            register = self._registers.get(address + offset)
            if register is None or not register.writable:
                return None
            words = values[offset : offset + register.width]
            if len(words) != register.width:
                return None
            segments.append((register, words))
            offset += register.width
        return segments

    def _all_values_acceptable(self, segments: list[Segment]) -> bool:
        for register, words in segments:
            reason = rejection_reason(register, decode(register, words, self._profile))
            if reason is not None:
                logger.info(
                    "Rejected value for register %s (%s): %s",
                    register.address,
                    register.name,
                    reason,
                )
                return False
        return True
