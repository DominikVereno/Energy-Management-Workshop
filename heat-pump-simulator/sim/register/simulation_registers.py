from __future__ import annotations

import logging
from math import isfinite
from typing import Optional

from sim.codec import decode, encode
from sim.model import RegisterMap
from sim.profile import HeatPumpProfile
from sim.register.register_storage import RegisterStorage
from sim.register.validation import clamped

logger = logging.getLogger("sim.registers")


class SimulationRegisters:
    def __init__(
        self,
        storage: RegisterStorage,
        registers: RegisterMap,
        profile: HeatPumpProfile,
    ):
        self._storage = storage
        self._registers = registers
        self._profile = profile

    def read(self, address: int) -> Optional[float]:
        register = self._registers.get(address)
        if register is None:
            return None
        return decode(register, self._storage.get(address, register.width), self._profile)

    def write(self, address: int, value: float) -> None:
        register = self._registers.get(address)
        if register is None:
            raise KeyError(f"No register defined at address {address}")
        if not isfinite(value):
            logger.warning("Physics produced %s for register %s (%s) — not written",
                           value, address, register.name)
            return
        self._storage.set(address, encode(register, clamped(register, value), self._profile))
