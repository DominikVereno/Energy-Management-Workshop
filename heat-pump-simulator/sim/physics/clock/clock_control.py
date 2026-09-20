from __future__ import annotations

from sim.physics.clock.clock import SimulationClock
from sim.physics.clock.clock_register import CLOCK_FACTOR_ADDR
from sim.register.simulation_registers import SimulationRegisters


class ClockControl:
    def __init__(
        self,
        clock: SimulationClock,
        registers: SimulationRegisters,
        address: int = CLOCK_FACTOR_ADDR,
    ):
        self._clock = clock
        self._registers = registers
        self._address = address
        self._last_factor: float | None = None

    def sync(self) -> None:
        factor = self._registers.read(self._address)
        if factor is None or factor == self._last_factor:
            return
        self._clock.set_factor(float(factor))
        self._last_factor = factor
