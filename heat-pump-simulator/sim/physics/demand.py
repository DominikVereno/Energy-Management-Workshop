from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class OperatingMode(Enum):
    OFF = "off"
    HEATING = "heating"
    COOLING = "cooling"


@dataclass(frozen=True)
class Demand:
    mode: OperatingMode
    target_flow_temp: float
