from __future__ import annotations

from dataclasses import dataclass

# Operating modes of a heating circuit (Reg1393). "Zeitprogramm" has no schedule
# in the simulation and behaves like "Normal"; the cooling mode is driven by the
# cooling request instead, so neither needs a constant here.
MODE_OFF = 0
MODE_ECO = 3
MODE_MANUAL_HEATING = 4

# Both paths stay inside the range the constant setpoint register (Reg1449) is
# specified for, so the plant is handed the same kind of value either way. The
# constant path is clamped too rather than trusted: a setpoint that arrives over
# Modbus is only as sane as the master that wrote it.
MIN_FLOW_TEMP_C = 20.0
MAX_FLOW_TEMP_C = 90.0


@dataclass(frozen=True)
class CircuitSettings:
    operating_mode: int
    slope: float
    heating_limit_c: float
    room_setpoint_normal_c: float
    room_setpoint_eco_c: float
    constant_setpoint_c: float


def circuit_setpoint(settings: CircuitSettings, outdoor_temp_c: float) -> float:
    """Flow temperature the circuit aims at, whether or not it currently runs."""
    if settings.operating_mode == MODE_MANUAL_HEATING:
        return _clamped(settings.constant_setpoint_c)
    return curve_setpoint(settings.slope, _room_setpoint(settings), outdoor_temp_c)


def wants_heat(settings: CircuitSettings, outdoor_temp_c: float) -> bool:
    """Whether the circuit asks for heat at this outdoor temperature."""
    if settings.operating_mode == MODE_OFF:
        return False
    if settings.operating_mode == MODE_MANUAL_HEATING:
        return True
    return outdoor_temp_c < settings.heating_limit_c


def curve_setpoint(slope: float, room_setpoint_c: float, outdoor_temp_c: float) -> float:
    """The heating curve itself: the colder it gets, the higher the flow runs.

    Slope 0.6 with a 22 C room setpoint gives 41.2 C at -10 C outside and
    26.2 C at +15 C, matching the shape an IDM curve of that steepness has.
    """
    rise = slope * (room_setpoint_c - outdoor_temp_c)
    return _clamped(room_setpoint_c + rise)


def _room_setpoint(settings: CircuitSettings) -> float:
    if settings.operating_mode == MODE_ECO:
        return settings.room_setpoint_eco_c
    return settings.room_setpoint_normal_c


def _clamped(temp_c: float) -> float:
    return min(max(temp_c, MIN_FLOW_TEMP_C), MAX_FLOW_TEMP_C)
