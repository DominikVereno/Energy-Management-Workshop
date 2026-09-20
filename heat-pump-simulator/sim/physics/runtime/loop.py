from __future__ import annotations

import asyncio
import logging

from sim.physics.ambient_source import AmbientSource
from sim.physics.clock.clock import SimulationClock
from sim.physics.clock.clock_control import ClockControl
from sim.physics.damped_ambient import DampedAmbient
from sim.physics.plant import Plant
from sim.physics.runtime.binding import PhysicsBinding

logger = logging.getLogger("sim.physics.runtime.loop")

# Real seconds between two physics ticks. The clock decides how much simulated
# time that spans, so a faster clock factor covers more of the daily curve per
# tick without changing how often we compute.
TICK_INTERVAL_S = 1.0


class SimulationLoop:
    def __init__(
        self,
        clock: SimulationClock,
        ambient: AmbientSource,
        plant: Plant,
        binding: PhysicsBinding,
        clock_control: ClockControl,
        damped_ambient: DampedAmbient | None = None,
        tick_interval_s: float = TICK_INTERVAL_S,
    ):
        self._clock = clock
        self._ambient = ambient
        self._plant = plant
        self._binding = binding
        self._clock_control = clock_control
        self._damped_ambient = damped_ambient or DampedAmbient()
        self._tick_interval_s = tick_interval_s
        self._last_sim_time = clock.now()

    async def run(self) -> None:
        while True:
            self._tick()
            await asyncio.sleep(self._tick_interval_s)

    def _tick(self) -> None:
        self._clock_control.sync()
        dt, sim_time = self._elapsed_sim_time()
        if dt <= 0.0:
            return
        t_ambient = self._ambient.temperature(sim_time)
        mean_outdoor_temp = self._damped_ambient.update(dt, t_ambient)
        demand = self._binding.read_demand(mean_outdoor_temp)
        state = self._plant.step(dt, t_ambient, demand)
        self._binding.write_outputs(state, demand, t_ambient, mean_outdoor_temp)

    def _elapsed_sim_time(self) -> tuple[float, float]:
        sim_time = self._clock.now()
        dt = sim_time - self._last_sim_time
        self._last_sim_time = sim_time
        return dt, sim_time
