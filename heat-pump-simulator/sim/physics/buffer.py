from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

WATER_SPECIFIC_HEAT_J_PER_KG_K = 4200.0
WATER_DENSITY_KG_PER_LITER = 1.0

# Heat storage buffer: 300 L of water, split into two equally sized layers.
BUFFER_VOLUME_LITERS = 300.0
NODE_VOLUME_LITERS = BUFFER_VOLUME_LITERS / 2.0
NODE_THERMAL_MASS_J_PER_K = (
    NODE_VOLUME_LITERS * WATER_DENSITY_KG_PER_LITER * WATER_SPECIFIC_HEAT_J_PER_KG_K
)

# Standby loss of the tank to its surroundings, shared evenly by the two layers.
# Rough estimate for an insulated residential buffer, not a measured value.
BUFFER_LOSS_W_PER_K = 3.0
NODE_LOSS_W_PER_K = BUFFER_LOSS_W_PER_K / 2.0

# Vertical heat exchange between the layers: conduction through water and wall
# plus the mixing no real tank avoids. This is what erodes stratification while
# the tank sits idle; a larger value makes the tank behave like a single mixed
# volume. Estimated for a 300 L tank, not measured.
STRATIFICATION_CONDUCTANCE_W_PER_K = 2.0

# The layers exchange their whole content within a few minutes at full flow, so
# explicit Euler needs a far shorter step than the clock factor may hand us
# (a tick can span minutes of simulated time). The step is therefore subdivided
# rather than trusted.
MAX_INTEGRATION_STEP_S = 10.0


class Node(Enum):
    TOP = "top"
    BOTTOM = "bottom"

    @property
    def other(self) -> "Node":
        return Node.BOTTOM if self is Node.TOP else Node.TOP


@dataclass(frozen=True)
class Circuit:
    mass_flow_kg_s: float
    temp_change_k: float


class StratifiedBuffer:
    def __init__(self, initial_temp: float = 30.0):
        self._temps = {Node.TOP: initial_temp, Node.BOTTOM: initial_temp}

    def temp(self, node: Node) -> float:
        return self._temps[node]

    @property
    def top_temp(self) -> float:
        return self._temps[Node.TOP]

    @property
    def bottom_temp(self) -> float:
        return self._temps[Node.BOTTOM]

    @property
    def mean_temp(self) -> float:
        return (self._temps[Node.TOP] + self._temps[Node.BOTTOM]) / 2.0

    def step(
        self,
        dt: float,
        t_ambient: float,
        heat_pump: Circuit,
        load: Circuit,
        supply_node: Node,
    ) -> None:
        for step_dt in self._substeps(dt):
            self._integrate(step_dt, t_ambient, heat_pump, load, supply_node)

    @staticmethod
    def _substeps(dt: float) -> list[float]:
        count = max(1, int(-(-dt // MAX_INTEGRATION_STEP_S)))
        return [dt / count] * count

    def _integrate(
        self,
        dt: float,
        t_ambient: float,
        heat_pump: Circuit,
        load: Circuit,
        supply_node: Node,
    ) -> None:
        return_node = supply_node.other
        t_supply = self._temps[supply_node]
        t_return = self._temps[return_node]

        supply_w, return_w = self._advected_power(heat_pump, load, t_supply, t_return)
        exchange_w = self._internal_exchange_w(t_supply, t_return)

        supply_w += -exchange_w - self._ambient_loss_w(t_supply, t_ambient)
        return_w += exchange_w - self._ambient_loss_w(t_return, t_ambient)

        self._temps[supply_node] = t_supply + supply_w * dt / NODE_THERMAL_MASS_J_PER_K
        self._temps[return_node] = t_return + return_w * dt / NODE_THERMAL_MASS_J_PER_K

    @staticmethod
    def _advected_power(
        heat_pump: Circuit, load: Circuit, t_supply: float, t_return: float,
    ) -> tuple[float, float]:
        cp = WATER_SPECIFIC_HEAT_J_PER_KG_K
        heat_pump_inlet = t_return + heat_pump.temp_change_k
        load_inlet = t_supply + load.temp_change_k

        net_flow = heat_pump.mass_flow_kg_s - load.mass_flow_kg_s
        internal_temp = t_supply if net_flow > 0.0 else t_return
        internal_w = net_flow * cp * internal_temp

        supply_w = (
            heat_pump.mass_flow_kg_s * cp * heat_pump_inlet
            - load.mass_flow_kg_s * cp * t_supply
            - internal_w
        )
        return_w = (
            load.mass_flow_kg_s * cp * load_inlet
            - heat_pump.mass_flow_kg_s * cp * t_return
            + internal_w
        )
        return supply_w, return_w

    @staticmethod
    def _internal_exchange_w(t_supply: float, t_return: float) -> float:
        return STRATIFICATION_CONDUCTANCE_W_PER_K * (t_supply - t_return)

    @staticmethod
    def _ambient_loss_w(t_node: float, t_ambient: float) -> float:
        return NODE_LOSS_W_PER_K * (t_node - t_ambient)
