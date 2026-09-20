from __future__ import annotations

import time
from typing import Callable

# Real time to simulated time. 1.0 = real time; 3600.0 makes one real second
# advance the simulation by one hour, so a full daily cycle plays out in 24 s.
# The running simulation gets its factor from Reg9000 (see clock_register), so
# this default only covers the ticks before the first sync.
DEFAULT_FACTOR = 1.0


class SimulationClock:
    def __init__(
        self,
        factor: float = DEFAULT_FACTOR,
        real_time_source: Callable[[], float] = time.monotonic,
    ):
        self._factor = factor
        self._real_time = real_time_source
        self._sim_time = 0.0
        self._last_real = self._real_time()

    def now(self) -> float:
        real_now = self._real_time()
        self._sim_time += (real_now - self._last_real) * self._factor
        self._last_real = real_now
        return self._sim_time

    def set_factor(self, factor: float) -> None:
        self.now()
        self._factor = factor

    @property
    def factor(self) -> float:
        return self._factor
