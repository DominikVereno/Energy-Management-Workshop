import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sim.model import RegisterMap
from sim.physics.damped_ambient import DampedAmbient
from sim.physics.ambient_source import AmbientSource
from sim.physics.demand import Demand, OperatingMode
from sim.physics.heating_curve import (
    MODE_ECO,
    MODE_MANUAL_HEATING,
    MODE_OFF,
    CircuitSettings,
    circuit_setpoint,
    curve_setpoint,
    wants_heat,
)
from sim.physics.plant import Plant
from sim.physics.runtime.binding import (
    CIRCUIT_MODE_ADDR,
    CIRCUIT_SETPOINT_ADDR,
    CURVE_SLOPE_ADDR,
    FLOW_SETPOINT_ADDR,
    HEAT_REQUEST_ADDR,
    HEATING_LIMIT_ADDR,
    MEAN_OUTDOOR_TEMP_ADDR,
    ROOM_SETPOINT_ECO_ADDR,
    ROOM_SETPOINT_NORMAL_ADDR,
    PhysicsBinding,
)
from sim.physics.synthetic_registers import synthetic_registers
from sim.profiles import get_profile
from sim.register.register_storage import RegisterStorage
from sim.register.simulation_registers import SimulationRegisters

_failures = 0

NORMAL_MODE = 2


def check(name, condition):
    global _failures
    status = "ok" if condition else "FAIL"
    if not condition:
        _failures += 1
    print(f"[{status}] {name}")


def close(actual, expected, tolerance=1e-6):
    return abs(actual - expected) <= tolerance


def settings(**overrides):
    base = dict(
        operating_mode=NORMAL_MODE,
        slope=0.6,
        heating_limit_c=15.0,
        room_setpoint_normal_c=22.0,
        room_setpoint_eco_c=18.0,
        constant_setpoint_c=45.0,
    )
    base.update(overrides)
    return CircuitSettings(**base)


def build_binding():
    profile = get_profile("idm")
    registers = RegisterMap(list(profile.load_registers()) + synthetic_registers())
    storage = RegisterStorage(registers, profile)
    sim_registers = SimulationRegisters(storage, registers, profile)
    return PhysicsBinding(sim_registers), sim_registers


def test_curve_shape():
    check("slope 0.6 at -10 C outside gives 41.2 C", close(curve_setpoint(0.6, 22.0, -10.0), 41.2))
    check("slope 0.6 at +15 C outside gives 26.2 C", close(curve_setpoint(0.6, 22.0, 15.0), 26.2))
    check("a steeper curve asks for more", curve_setpoint(1.2, 22.0, -10.0) > curve_setpoint(0.6, 22.0, -10.0))
    check("result never drops below the 20 C register minimum", close(curve_setpoint(0.6, 22.0, 40.0), 20.0))
    check("result never exceeds the 90 C register maximum", close(curve_setpoint(3.5, 30.0, -30.0), 90.0))


def test_circuit_decisions():
    check("eco mode uses the lower room setpoint",
          circuit_setpoint(settings(operating_mode=MODE_ECO), 0.0)
          < circuit_setpoint(settings(), 0.0))
    check("manual heating ignores the curve and uses Reg1449",
          close(circuit_setpoint(settings(operating_mode=MODE_MANUAL_HEATING), -10.0), 45.0))
    check("manual heating is clamped to the 90 C maximum too",
          close(circuit_setpoint(settings(operating_mode=MODE_MANUAL_HEATING, constant_setpoint_c=255.0), -10.0), 90.0))
    check("manual heating is clamped to the 20 C minimum too",
          close(circuit_setpoint(settings(operating_mode=MODE_MANUAL_HEATING, constant_setpoint_c=-40.0), -10.0), 20.0))
    check("heat below the heating limit", wants_heat(settings(), 10.0))
    check("no heat above the heating limit", not wants_heat(settings(), 16.0))
    check("no heat when the circuit is off", not wants_heat(settings(operating_mode=MODE_OFF), -10.0))
    check("manual heating ignores the heating limit",
          wants_heat(settings(operating_mode=MODE_MANUAL_HEATING), 30.0))


def test_binding_reads_curve():
    binding, registers = build_binding()
    registers.write(HEAT_REQUEST_ADDR, 1)
    registers.write(CIRCUIT_MODE_ADDR, NORMAL_MODE)
    registers.write(CURVE_SLOPE_ADDR, 0.6)
    registers.write(HEATING_LIMIT_ADDR, 15)
    registers.write(ROOM_SETPOINT_NORMAL_ADDR, 22.0)

    cold = binding.read_demand(-10.0)
    check("heating request below the limit heats", cold.mode is OperatingMode.HEATING)
    check("setpoint follows the curve, not Reg1449", close(cold.target_flow_temp, 41.2, 0.01))

    warm = binding.read_demand(18.0)
    check("heating request above the limit stays off", warm.mode is OperatingMode.OFF)

    registers.write(CIRCUIT_MODE_ADDR, MODE_MANUAL_HEATING)
    registers.write(FLOW_SETPOINT_ADDR, 45.0)
    manual = binding.read_demand(-10.0)
    check("manual heating still honours Reg1449", close(manual.target_flow_temp, 45.0, 0.01))


def test_binding_reports_curve():
    binding, registers = build_binding()
    registers.write(CIRCUIT_MODE_ADDR, NORMAL_MODE)
    registers.write(CURVE_SLOPE_ADDR, 0.6)
    registers.write(ROOM_SETPOINT_NORMAL_ADDR, 22.0)
    registers.write(ROOM_SETPOINT_ECO_ADDR, 18.0)

    demand = binding.read_demand(-10.0)
    state = Plant().step(1.0, -10.0, demand)
    binding.write_outputs(state, demand, -10.0, -8.0)

    check("Reg1378 reports the circuit setpoint",
          close(registers.read(CIRCUIT_SETPOINT_ADDR), 41.2, 0.01))
    check("Reg1002 reports the damped outdoor temperature",
          close(registers.read(MEAN_OUTDOOR_TEMP_ADDR), -8.0, 0.01))


def test_damping():
    damped = DampedAmbient(time_constant_s=100.0)
    check("first sample is taken as is", close(damped.update(1.0, 5.0), 5.0))
    check("a step is followed gradually", 5.0 < damped.update(10.0, 15.0) < 7.0)

    # A full day of the daily sine must not move the mean far from the day mean.
    daily = DampedAmbient()
    for _ in range(2000):
        daily.update(60.0, 2.0)
    check("a constant outdoor temperature converges to itself", close(daily.update(60.0, 2.0), 2.0, 0.01))


def test_weather_dependent_load():
    cold = Plant()
    warm = Plant()

    cold_load = cold._building_load_w(OperatingMode.HEATING, -10.0)
    mild_load = warm._building_load_w(OperatingMode.HEATING, 10.0)
    check("colder weather means a bigger draw", cold_load > mild_load)
    # Both samples have to sit above the minimum load, or the floor flattens them
    # into the same value and the comparison says nothing.
    check("cooling load grows with heat, not cold",
          warm._building_load_w(OperatingMode.COOLING, 50.0)
          > warm._building_load_w(OperatingMode.COOLING, 45.0))


# A cold day, independent of the default weather: the point is whether the load
# clears the compressor minimum in the season the setpoints below belong to.
WINTER_MEAN_C = 2.0
WINTER_SWING_C = 5.0


def count_switches(mode, setpoint, hours=24):
    ambient = AmbientSource(mean_c=WINTER_MEAN_C, swing_c=WINTER_SWING_C)
    plant = Plant(initial_room_temp=25.0)
    demand = Demand(mode, setpoint)
    switches = 0
    running = False
    sim_time = 0.0
    while sim_time < hours * 3600:
        state = plant.step(30.0, ambient.temperature(sim_time), demand)
        sim_time += 30.0
        if (state.modulation > 0.0) != running:
            running = not running
            switches += 1
    return switches


def test_no_cycling():
    # The minimum circuit load has to stay clear of the minimum the compressor
    # can deliver; when the two meet, the controller sits on its own switch-off
    # threshold and toggles all day. One switch is the initial start.
    check("cooling does not cycle", count_switches(OperatingMode.COOLING, 18.0) <= 2)
    check("heating does not cycle", count_switches(OperatingMode.HEATING, 34.5) <= 2)


def main():
    test_curve_shape()
    test_circuit_decisions()
    test_binding_reads_curve()
    test_binding_reports_curve()
    test_damping()
    test_weather_dependent_load()
    test_no_cycling()

    if _failures:
        print(f"\n{_failures} check(s) failed")
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
