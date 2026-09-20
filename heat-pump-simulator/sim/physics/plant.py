from __future__ import annotations

import logging
from dataclasses import dataclass

from sim.physics import hplib_model

from sim.physics.building_model import BuildingThermalMass
from sim.physics.controller import ModulatingController
from sim.physics.demand import Demand, OperatingMode

logger = logging.getLogger(__name__)

@dataclass(frozen=True)
class PlantState:
    room_temp: float
    return_temp: float
    compressor_on: bool
    modulation: float
    p_el: float
    p_th: float
    t_flow: float
    mode: OperatingMode


class Plant:
    def __init__(self, initial_room_temp: float = 20.0):
        self._building = BuildingThermalMass(initial_temp=initial_room_temp)
        self._controller = ModulatingController()

    def step(self, dt: float, t_ambient: float, demand: Demand) -> PlantState:
        t_return = self._building.return_temp
        logger.info("Building Temp: %.2f ", t_return)
        logger.info(f"Demand-Mode: {demand.mode}")
        logger.info("Demand: %.2f ", demand.target_flow_temp)
        logger.info("Dt: %.2f ", dt)

        # 1. Determine target supply temperature based on heat pump flow/return spread
        spread = self._sign(demand.mode) * hplib_model.flow_return_spread()
        outlet_temp = t_return + spread
        logger.info("Outlet-Temp: %.2f ", outlet_temp)

        # 2. Update modulation and compute compressor operating point
        modulation = self._controller.update(dt, demand, outlet_temp)
        logger.info("Modulation: %.2f ", modulation)

        p_el, p_th = self._operating_point(modulation, t_ambient, t_return, demand.mode)
        logger.info(
            "P_el=%.2fW, P_th=%.2fW",
            p_el,
            p_th,
        )

        compressor_on = modulation > 0.0
        if compressor_on:
            t_flow_target = demand.target_flow_temp
        else:
            t_flow_target = t_return

        p_to_building = self._building.step(
            dt=dt,
            t_outdoor=t_ambient,
            heat_pump_power_w=p_th,
            t_flow_target=t_flow_target,
            internal_gains_w=0.0,
        )

        t_flow = (
            t_flow_target
            if compressor_on
            else self._building.return_temp
        )

        return PlantState(
            room_temp=self._building.temp,
            return_temp=self._building.return_temp,
            compressor_on=compressor_on,
            modulation=modulation,
            p_el=p_el,
            p_th=p_th,
            t_flow=t_flow,
            mode=demand.mode,
        )

    def _operating_point(
        self, modulation: float, t_ambient: float, t_return: float, mode: OperatingMode
    ) -> tuple[float, float]:
        if modulation <= 0.0:
            return 0.0, 0.0
        point = hplib_model.evaluate(t_ambient, t_return)
        return modulation * point.p_el, self._sign(mode) * modulation * point.p_th

    @staticmethod
    def _sign(mode: OperatingMode) -> float:
        return -1.0 if mode is OperatingMode.COOLING else 1.0
