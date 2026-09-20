from __future__ import annotations

from sim.model import Register
from sim.physics.buffer_registers import buffer_registers
from sim.physics.clock.clock_register import DEFAULT_CLOCK_FACTOR, clock_factor_register
from sim.physics.cop_register import cop_register

# Everything the simulation exposes over Modbus that real IDM hardware does not
# have. Kept out of the IDM parameter list (the real-spec truth source) on purpose
# and placed well above the real address range so it can never collide.
#
# One list, so the Modbus server and the OpenEMS generator can never disagree
# about which of these registers exist.


def synthetic_registers(clock_factor: int = DEFAULT_CLOCK_FACTOR) -> list[Register]:
    return [clock_factor_register(clock_factor), *buffer_registers(), cop_register()]
