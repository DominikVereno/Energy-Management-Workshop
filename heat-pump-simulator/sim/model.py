from __future__ import annotations

from dataclasses import dataclass
from typing import Iterator, Optional


CANONICAL_DATATYPES: frozenset[str] = frozenset({"FLOAT", "WORD", "UCHAR", "BOOL"})

REGISTER_WIDTH: dict[str, int] = {
    "FLOAT": 2,
    "WORD": 1,
    "UCHAR": 1,
    "BOOL": 1,
}

FALLBACK_VALUE_BY_UNIT: dict[str, float] = {
    "°C": 20.0,
    "%rF": 50.0,
    "%": 50.0,
    "kW": 1.0,
    "kWh": 100.0,
}

@dataclass(frozen=True)
class Register:
    address: int
    datatype: str
    access: str
    readable: bool
    writable: bool
    name: str
    unit: str
    minimum: Optional[float]
    maximum: Optional[float]
    default: Optional[float]
    value_list: str = ""
    note: str = ""

    def __post_init__(self) -> None:
        if self.datatype not in CANONICAL_DATATYPES:
            raise ValueError(
                f"Register {self.address}: unsupported datatype {self.datatype!r}"
            )

    @property
    def width(self) -> int:
        return REGISTER_WIDTH[self.datatype]

    @property
    def is_float(self) -> bool:
        return self.datatype == "FLOAT"

    def initial_value(self) -> float | int:
        seed = self.default
        if seed is None:
            seed = self.minimum
        if seed is None:
            seed = FALLBACK_VALUE_BY_UNIT.get(self.unit, 0.0)
        return float(seed) if self.is_float else int(seed)


class RegisterMap:
    def __init__(self, registers: list[Register]):
        self._registers = sorted(registers, key=lambda r: r.address)
        self._by_address = {r.address: r for r in self._registers}

    def get(self, address: int) -> Optional[Register]:
        return self._by_address.get(address)

    def __iter__(self) -> Iterator[Register]:
        return iter(self._registers)
