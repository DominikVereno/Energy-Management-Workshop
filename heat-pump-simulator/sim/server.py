from __future__ import annotations

import logging
from dataclasses import dataclass

from pymodbus.constants import ExcCodes
from pymodbus.datastore import ModbusServerContext
from pymodbus.server import ModbusTcpServer

from sim.model import RegisterMap
from sim.physics.ambient_source import AmbientSource
from sim.physics.clock.clock import SimulationClock
from sim.physics.clock.clock_control import ClockControl
from sim.physics.clock.clock_register import DEFAULT_CLOCK_FACTOR
from sim.physics.synthetic_registers import synthetic_registers
from sim.physics.plant import Plant
from sim.physics.runtime.binding import PhysicsBinding
from sim.physics.runtime.loop import SimulationLoop
from sim.profile import HeatPumpProfile
from sim.register.modbus_registers import ModbusRegisters, WriteOutcome
from sim.register.register_storage import RegisterStorage
from sim.register.simulation_registers import SimulationRegisters

logger = logging.getLogger("sim.server")


class HeatPumpServerContext(ModbusServerContext):
    def __init__(self, registers: ModbusRegisters):
        self._registers = registers
        self.simdevices = []

    def device_ids(self) -> list[int]:
        return [0]

    async def async_getValues(self, device_id, func_code, address, count=1):
        values = self._registers.read(address, count)
        if values is None:
            logger.info("Illegal read: fc=%s address=%s count=%s", func_code, address, count)
            return ExcCodes.ILLEGAL_ADDRESS
        return values

    async def async_setValues(self, device_id, func_code, address, values):
        outcome = self._registers.write(address, values)
        if outcome is WriteOutcome.ILLEGAL_ADDRESS:
            logger.info("Rejected write: fc=%s address=%s (read-only, undefined or misaligned)",
                        func_code, address)
            return ExcCodes.ILLEGAL_ADDRESS
        if outcome is WriteOutcome.ILLEGAL_VALUE:
            logger.info("Rejected write: fc=%s address=%s (value out of range)", func_code, address)
            return ExcCodes.ILLEGAL_VALUE
        return None


@dataclass(frozen=True)
class Simulation:
    server: ModbusTcpServer
    loop: SimulationLoop


def build_simulation(
    profile: HeatPumpProfile,
    host: str,
    port: int,
    clock_factor: int = DEFAULT_CLOCK_FACTOR,
) -> Simulation:
    registers = registers_with_synthetic(profile, clock_factor)
    storage = RegisterStorage(registers, profile)
    server = _build_server(profile, storage, registers, host, port)
    loop = _build_loop(profile, storage, registers)
    return Simulation(server=server, loop=loop)


def registers_with_synthetic(
    profile: HeatPumpProfile,
    clock_factor: int = DEFAULT_CLOCK_FACTOR,
) -> RegisterMap:
    """Every register the simulator serves: the IDM spec plus the synthetic ones."""
    return RegisterMap(list(profile.load_registers()) + synthetic_registers(clock_factor))


def _build_server(
    profile: HeatPumpProfile,
    storage: RegisterStorage,
    registers: RegisterMap,
    host: str,
    port: int,
) -> ModbusTcpServer:
    modbus_registers = ModbusRegisters(storage, registers, profile)
    logger.info("Serving profile %r on %s:%s", profile.name, host, port)
    return ModbusTcpServer(HeatPumpServerContext(modbus_registers), address=(host, port))


def _build_loop(
    profile: HeatPumpProfile,
    storage: RegisterStorage,
    registers: RegisterMap,
) -> SimulationLoop:
    sim_registers = SimulationRegisters(storage, registers, profile)
    binding = PhysicsBinding(sim_registers)
    clock = SimulationClock()
    clock_control = ClockControl(clock, sim_registers)
    return SimulationLoop(clock, AmbientSource(), Plant(), binding, clock_control)
