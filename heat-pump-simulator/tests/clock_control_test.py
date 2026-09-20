import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from sim.model import RegisterMap
from sim.physics.clock.clock import SimulationClock
from sim.physics.clock.clock_control import ClockControl
from sim.physics.clock.clock_register import CLOCK_FACTOR_ADDR
from sim.physics.synthetic_registers import synthetic_registers
from sim.profiles import get_profile
from sim.register.register_storage import RegisterStorage
from sim.register.simulation_registers import SimulationRegisters

_failures = 0


def check(name, condition):
    global _failures
    status = "ok" if condition else "FAIL"
    if not condition:
        _failures += 1
    print(f"[{status}] {name}")


def build_registers():
    profile = get_profile("idm")
    registers = RegisterMap(list(profile.load_registers()) + synthetic_registers())
    storage = RegisterStorage(registers, profile)
    return SimulationRegisters(storage, registers, profile), profile


def main():
    sim_registers, _ = build_registers()

    check("factor register seeded to 60 (default)", sim_registers.read(CLOCK_FACTOR_ADDR) == 60)

    # A controllable time source so we can advance real time deterministically.
    now = [0.0]
    clock = SimulationClock(real_time_source=lambda: now[0])
    control = ClockControl(clock, sim_registers)

    control.sync()
    check("sync adopts register factor 60", clock.factor == 60.0)

    now[0] = 10.0
    check("1 real s -> 60 sim s at factor 60", clock.now() == 600.0)

    # Outside world speeds the simulation up mid-run, to the highest factor the
    # register accepts.
    sim_registers.write(CLOCK_FACTOR_ADDR, 300)
    control.sync()
    check("sync picks up new factor 300", clock.factor == 300.0)

    # No jump at the switch: the 600 s banked at factor 60 stay 600 s.
    now[0] = 11.0
    check("no time jump; new rate applies only forward", clock.now() == 600.0 + 1.0 * 300.0)

    # Beyond the register maximum the factor stops rising.
    sim_registers.write(CLOCK_FACTOR_ADDR, 3600)
    check("factor above the maximum is clamped to 300", sim_registers.read(CLOCK_FACTOR_ADDR) == 300)

    # Pause.
    sim_registers.write(CLOCK_FACTOR_ADDR, 0)
    control.sync()
    now[0] = 20.0
    check("factor 0 pauses simulated time", clock.now() == 900.0)

    if _failures:
        print(f"\n{_failures} check(s) failed")
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
