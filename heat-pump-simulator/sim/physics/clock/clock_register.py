from __future__ import annotations

from sim.model import Register

# The clock's own simulation-only register. It is collected together with the
# other synthetic registers in sim.physics.synthetic_registers; this module only
# defines it, so the clock package stays free of anything but timekeeping.
#
# DEFAULT_CLOCK_FACTOR is the simulation-wide default; SimulationClock itself
# starts at real time and adopts this value from the register on the first tick.

CLOCK_FACTOR_ADDR = 9000  # time lapse: 0 = pause, 1 = real time, 3600 = 1 s -> 1 h

DEFAULT_CLOCK_FACTOR = 60  # 1 s real -> 1 min simulated; overridable via env REAL_TIME_FACTOR

def clock_factor_register(factor: int = DEFAULT_CLOCK_FACTOR) -> Register:
    return Register(
        address=CLOCK_FACTOR_ADDR,
        datatype="WORD",
        access="RW",
        readable=True,
        writable=True,
        name="Zeitraffer-Faktor (Simulation)",
        unit="",
        minimum=0,
        maximum=300,
        default=factor,
    )

