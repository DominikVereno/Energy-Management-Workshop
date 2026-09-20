from __future__ import annotations

import math

BUILDING_THERMAL_CAPACITY_J_PER_K = 15_000_000.0  # ~15 MJ/K (approx. 4.17 kWh/K)
BUILDING_LOSS_W_PER_K = 120.0
HEATING_SURFACE_UA_W_PER_K = 1200.0
WATER_SPECIFIC_HEAT_J_PER_KG_K = 4200.0
NOMINAL_MASS_FLOW_KG_S = 0.33
MAX_INTEGRATION_STEP_S = 60.0


class BuildingThermalMass:
    """Models the thermal inertia and heating circuit interface of the building envelope."""

    def __init__(
        self,
        initial_temp: float = 20.0,
        ua_heating_surface_w_per_k: float = HEATING_SURFACE_UA_W_PER_K,
        nominal_mass_flow_kg_s: float = NOMINAL_MASS_FLOW_KG_S,
    ):
        self._temp = initial_temp
        self._t_return = initial_temp
        self.ua_heating_surface = ua_heating_surface_w_per_k
        self.nominal_mass_flow = nominal_mass_flow_kg_s

    @property
    def temp(self) -> float:
        return self._temp

    @property
    def return_temp(self) -> float:
        return self._t_return

    def step(
            self,
            dt: float,
            t_outdoor: float,
            heat_pump_power_w: float,
            t_flow_target: float,
            internal_gains_w: float = 0.0,
    ) -> float:
        mc_dot = self.nominal_mass_flow * WATER_SPECIFIC_HEAT_J_PER_KG_K

        for step_dt in self._substeps(dt):
            ua = self.ua_heating_surface

            emitter_capacity = max(
                0.0,
                ua * (t_flow_target - self._temp)
                / (1.0 + ua / (2.0 * mc_dot))
            )

            p_to_building = min(
                max(0.0, heat_pump_power_w),
                emitter_capacity,
            )

            if p_to_building > 0.0:
                self._t_return = t_flow_target - p_to_building / mc_dot
            else:
                tau = 300.0
                cooling_factor = 1.0 - math.exp(-step_dt / tau)
                self._t_return += (
                                          self._temp - self._t_return
                                  ) * cooling_factor

            self._integrate(
                step_dt,
                t_outdoor,
                p_to_building,
                internal_gains_w,
            )

        return p_to_building

    @staticmethod
    def _substeps(dt: float) -> list[float]:
        count = max(1, int(-(-dt // MAX_INTEGRATION_STEP_S)))
        return [dt / count] * count

    def _integrate(
        self,
        dt: float,
        t_outdoor: float,
        heat_input_w: float,
        internal_gains_w: float,
    ) -> None:
        losses_w = BUILDING_LOSS_W_PER_K * (self._temp - t_outdoor)
        net_power_w = heat_input_w + internal_gains_w - losses_w
        self._temp += net_power_w * dt / BUILDING_THERMAL_CAPACITY_J_PER_K