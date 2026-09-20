from __future__ import annotations

from sim.model import RegisterMap


class HeatPumpProfile:
    name: str = "base"
    word_order: str = "little"
    byte_order: str = "big"

    def load_registers(self) -> RegisterMap:
        raise NotImplementedError
