from __future__ import annotations

from sim.physics.buffer_registers import BUFFER_BOTTOM_ADDR, BUFFER_TOP_ADDR
from sim.physics.cop_register import COP_ADDR, coefficient_of_performance
from sim.physics.demand import Demand, OperatingMode
from sim.physics.heating_curve import CircuitSettings, circuit_setpoint, wants_heat
from sim.physics.plant import PlantState
from sim.register.simulation_registers import SimulationRegisters

# Controller -> physics: what the outside world asks the plant to do.
HEAT_REQUEST_ADDR = 1710  # "Anforderung Heizen" (heating request), 0/1
COOL_REQUEST_ADDR = 1711  # "Anforderung Kuehlen" (cooling request), 0/1
FLOW_SETPOINT_ADDR = 1449  # "Sollvorlauftemperatur HK A" (constant flow setpoint), °C
COOLING_SETPOINT_ADDR = 1491  # "Sollvorlauftemperatur Kuehlen HK A" (cooling flow setpoint), °C

# Heating circuit A: the weather-compensated alternative to the constant setpoint.
CIRCUIT_MODE_ADDR = 1393  # "Betriebsart Heizkreis A" (operating mode of circuit A), 0..5
CURVE_SLOPE_ADDR = 1429  # "Heizkurve HK A" (heating curve slope), 0.1..3.5
HEATING_LIMIT_ADDR = 1442  # "Heizgrenze HK A" (heating limit), °C
ROOM_SETPOINT_NORMAL_ADDR = 1401  # "Raumsolltemperatur Heizen Normal HK A" (room setpoint, normal), °C
ROOM_SETPOINT_ECO_ADDR = 1415  # "Raumsolltemperatur Heizen Eco HK A" (room setpoint, eco), °C

# Physics -> Modbus image: what the running plant reports back.
HEAT_REQUEST_STATUS_ADDR = 1091  # "Heizanforderung" (heating request feedback), 0/1
COOL_REQUEST_STATUS_ADDR = 1092  # "Kuehlanforderung" (cooling request feedback), 0/1
OUTDOOR_TEMP_ADDR = 1000  # "Aussentemperatur" (outdoor temperature, B32), °C
MEAN_OUTDOOR_TEMP_ADDR = 1002  # "Gemittelte Aussentemperatur" (damped outdoor temperature), °C
CIRCUIT_SETPOINT_ADDR = 1378  # "Heizkreis A Sollvorlauftemperatur" (curve flow setpoint), °C
BUFFER_TEMP_ADDR = 1008  # "Waermespeichertemperatur" (buffer temperature, B38), °C — mean of both layers
ROOM_TEMP_ADDR = 1364 # "Heizkreis A Raumtemperatur" (B61), °C
FLOW_TEMP_ADDR = 1050  # "Waermepumpen Vorlauftemperatur" (flow temperature, B33), °C
RETURN_TEMP_ADDR = 1052  # "Waermepumpen Ruecklauftemperatur" (return temperature, B34), °C
ELECTRIC_POWER_ADDR = 4122  # "Aktuelle Leistungsaufnahme" (electrical power draw), kW
THERMAL_POWER_ADDR = 4126  # "Thermische Leistung" (thermal power, flow sensor), kW
MOMENTARY_POWER_ADDR = 1790  # "Momentanleistung" (momentary thermal power), kW
COMPRESSOR_STATUS_ADDR = 1100  # "Status Verdichter 1" (compressor status), 0/1
LOAD_ADDR = 1104  # "Status Ladepumpe" (charge pump, M73), %, tracks the modulating load

# hplib reports power in watts; the IDM power registers are scaled in kilowatts.
WATTS_PER_KILOWATT = 1000.0
PERCENT_PER_FRACTION = 100.0

# Fallbacks when a controller has not written the input registers yet. They
# repeat the defaults of the IDM parameter list, which seeds the same values.
DEFAULT_FLOW_SETPOINT_C = 45.0
DEFAULT_COOLING_SETPOINT_C = 18.0
DEFAULT_CIRCUIT_MODE = 1
DEFAULT_CURVE_SLOPE = 0.3
DEFAULT_HEATING_LIMIT_C = 15.0
DEFAULT_ROOM_SETPOINT_NORMAL_C = 22.0
DEFAULT_ROOM_SETPOINT_ECO_C = 18.0


class PhysicsBinding:
    def __init__(self, registers: SimulationRegisters):
        self._registers = registers

    def read_demand(self, mean_outdoor_temp: float) -> Demand:
        settings = self._circuit_settings()
        setpoint = circuit_setpoint(settings, mean_outdoor_temp)
        requested = self._requested_mode()
        if requested is OperatingMode.COOLING:
            return Demand(requested, self._read(COOLING_SETPOINT_ADDR, DEFAULT_COOLING_SETPOINT_C))
        if requested is OperatingMode.HEATING and wants_heat(settings, mean_outdoor_temp):
            return Demand(OperatingMode.HEATING, setpoint)
        return Demand(OperatingMode.OFF, setpoint)

    def write_outputs(
        self, state: PlantState, demand: Demand, t_ambient: float, mean_outdoor_temp: float,
    ) -> None:
        values = self._output_values(state, t_ambient)
        values[MEAN_OUTDOOR_TEMP_ADDR] = mean_outdoor_temp
        values[CIRCUIT_SETPOINT_ADDR] = demand.target_flow_temp
        for address, value in values.items():
            self._registers.write(address, value)

    def _circuit_settings(self) -> CircuitSettings:
        return CircuitSettings(
            operating_mode=int(self._read(CIRCUIT_MODE_ADDR, DEFAULT_CIRCUIT_MODE)),
            slope=self._read(CURVE_SLOPE_ADDR, DEFAULT_CURVE_SLOPE),
            heating_limit_c=self._read(HEATING_LIMIT_ADDR, DEFAULT_HEATING_LIMIT_C),
            room_setpoint_normal_c=self._read(ROOM_SETPOINT_NORMAL_ADDR, DEFAULT_ROOM_SETPOINT_NORMAL_C),
            room_setpoint_eco_c=self._read(ROOM_SETPOINT_ECO_ADDR, DEFAULT_ROOM_SETPOINT_ECO_C),
            constant_setpoint_c=self._read(FLOW_SETPOINT_ADDR, DEFAULT_FLOW_SETPOINT_C),
        )

    def _read(self, address: int, fallback: float) -> float:
        value = self._registers.read(address)
        return fallback if value is None else value

    def _requested_mode(self) -> OperatingMode:
        if self._is_requested(HEAT_REQUEST_ADDR):
            return OperatingMode.HEATING
        if self._is_requested(COOL_REQUEST_ADDR):
            return OperatingMode.COOLING
        return OperatingMode.OFF

    def _is_requested(self, address: int) -> bool:
        return self._read(address, 0.0) >= 1.0

    def _output_values(self, state: PlantState, t_ambient: float) -> dict[int, float]:
        return {
            HEAT_REQUEST_STATUS_ADDR: float(state.mode is OperatingMode.HEATING),
            COOL_REQUEST_STATUS_ADDR: float(state.mode is OperatingMode.COOLING),
            OUTDOOR_TEMP_ADDR: t_ambient,
            ROOM_TEMP_ADDR: state.room_temp,
            FLOW_TEMP_ADDR: state.t_flow,
            RETURN_TEMP_ADDR: state.return_temp,
            ELECTRIC_POWER_ADDR: self._kilowatts(state.p_el),
            THERMAL_POWER_ADDR: self._kilowatts(state.p_th),
            MOMENTARY_POWER_ADDR: self._kilowatts(state.p_th),
            COMPRESSOR_STATUS_ADDR: float(state.compressor_on),
            LOAD_ADDR: state.modulation * PERCENT_PER_FRACTION,
            COP_ADDR: coefficient_of_performance(state.p_el, state.p_th),
        }

    @staticmethod
    def _kilowatts(watts: float) -> float:
        return watts / WATTS_PER_KILOWATT
