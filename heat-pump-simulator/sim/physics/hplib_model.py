from __future__ import annotations

import logging
from dataclasses import dataclass
from functools import lru_cache

from hplib import hplib as hpl

# IDM is not in hplib's Keymark database (verified: 0/9953 rows), so we use the
# "Generic / Outdoor Air/Water / Regulated" fit (group_id=1) instead of a named model.
GENERIC_AIR_WATER_REGULATED_GROUP = 1

# "Generic" models are fitted around one design point rather than shipping fixed
# reference values. We don't have the real IDM datasheet numbers, so this is a
# placeholder rating point for a mid-size residential air/water heat pump
# (EN 14511 style: -7 C outside air, 52 C flow temperature, 10 kW thermal).
DESIGN_POINT_OUTDOOR_TEMP_C = -7.0
DESIGN_POINT_FLOW_TEMP_C = 52.0
DESIGN_POINT_THERMAL_POWER_W = 10_000.0

logger = logging.getLogger(__name__)

@dataclass(frozen=True)
class OperatingPoint:
    p_el: float
    p_th: float


@lru_cache(maxsize=1)
def _heat_pump() -> hpl.HeatPump:
    parameters = hpl.get_parameters(
        "Generic",
        group_id=GENERIC_AIR_WATER_REGULATED_GROUP,
        t_in=DESIGN_POINT_OUTDOOR_TEMP_C,
        t_out=DESIGN_POINT_FLOW_TEMP_C,
        p_th=DESIGN_POINT_THERMAL_POWER_W,
    )
    return hpl.HeatPump(parameters)


def flow_return_spread() -> float:
    logger.info("Ermittelte Spreizung (delta_t): %.2f K", _heat_pump().delta_t)
    return float(_heat_pump().delta_t)


def evaluate(t_ambient: float, t_return: float) -> OperatingPoint:
    logger.info(
        "Simuliere Betriebspunkt -> t_ambient=%.2f°C, t_return=%.2f°C",
        t_ambient,
        t_return,
    )
    result = _heat_pump().simulate(
        t_in_primary=t_ambient,
        t_in_secondary=t_return,
        t_amb=t_ambient,
        mode=1,
    )

    p_el = float(result["P_el"])
    p_th = float(result["P_th"])

    logger.info(
        "Simulation erfolgreich abgeschlossen: P_el=%.2fW, P_th=%.2fW",
        p_el,
        p_th,
    )

    return OperatingPoint(
        p_el=p_el,
        p_th=p_th,
    )
