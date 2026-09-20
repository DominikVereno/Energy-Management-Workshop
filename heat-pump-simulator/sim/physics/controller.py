from __future__ import annotations

from sim.physics.demand import Demand, OperatingMode

# PI gains on the flow-temperature error (K). Kp alone reaches full modulation at
# a 2 K deficit; Ki removes the steady-state offset a pure P controller leaves.
PROPORTIONAL_GAIN_PER_K = 0.5
INTEGRAL_GAIN_PER_K_S = 0.005

# A modulating compressor cannot run below its minimum speed. Below this demand
# it switches off instead of running slower.
MIN_MODULATION = 0.3
MAX_MODULATION = 1.0

# Minimum time in a state before switching on/off again, to protect the compressor.
MIN_RUNTIME_S = 300.0
MIN_OFF_TIME_S = 300.0


class ModulatingController:
    def __init__(self) -> None:
        self._integral = 0.0
        self._modulation = 0.0
        self._time_in_state = 0.0
        self._mode = OperatingMode.HEATING

    def update(self, dt: float, demand: Demand, measured_temp: float) -> float:
        self._time_in_state += dt
        self._modulation = self._apply_switching(self._demand_level(dt, demand, measured_temp))
        return self._modulation

    def _demand_level(self, dt: float, demand: Demand, measured_temp: float) -> float:
        if demand.mode is not self._mode:
            self._integral = 0.0
            self._mode = demand.mode
        if demand.mode is OperatingMode.OFF:
            return 0.0
        return self._pi_demand(dt, self._temperature_error(demand, measured_temp))

    @staticmethod
    def _temperature_error(demand: Demand, measured_temp: float) -> float:
        if demand.mode is OperatingMode.COOLING:
            return measured_temp - demand.target_flow_temp
        return demand.target_flow_temp - measured_temp

    def _pi_demand(self, dt: float, error: float) -> float:
        candidate = self._integral + error * dt
        raw = PROPORTIONAL_GAIN_PER_K * error + INTEGRAL_GAIN_PER_K_S * candidate
        if 0.0 <= raw <= MAX_MODULATION:
            self._integral = candidate
        return raw

    #def _pi_demand(self, dt: float, error: float) -> float:
    #    proportional = PROPORTIONAL_GAIN_PER_K * error

    #    integral_candidate = (
    #            self._integral
    #            + INTEGRAL_GAIN_PER_K_S * error * dt
    #    )

    #    raw = proportional + integral_candidate

    #    if raw > MAX_MODULATION:
    #        raw = MAX_MODULATION
    #        if error < 0.0:
    #            self._integral = integral_candidate
    #    elif raw < 0.0:
    #        raw = 0.0
    #        if error > 0.0:
    #            self._integral = integral_candidate
    #    else:
    #        self._integral = integral_candidate
    #
    #    return raw

    def _apply_switching(self, demand: float) -> float:
        running = self._modulation > 0.0
        if not self._held_long_enough(running):
            return self._modulation
        if running and demand < MIN_MODULATION:
            return self._switch_to(0.0)
        if not running and demand >= MIN_MODULATION:
            return self._switch_to(min(demand, MAX_MODULATION))
        if running:
            return min(max(demand, MIN_MODULATION), MAX_MODULATION)
        return 0.0

    def _held_long_enough(self, running: bool) -> bool:
        floor = MIN_RUNTIME_S if running else MIN_OFF_TIME_S
        return self._time_in_state >= floor

    def _switch_to(self, modulation: float) -> float:
        self._time_in_state = 0.0
        return modulation
