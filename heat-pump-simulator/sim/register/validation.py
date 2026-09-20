from __future__ import annotations

from math import isfinite
from typing import Optional

from sim.model import Register

# The parameter list carries a minimum and a maximum for most writable registers.
# Nothing enforced them, so any master could park a setpoint far outside the range
# the register is specified for, or write a NaN that later kills the physics loop.


def rejection_reason(register: Register, value: float) -> Optional[str]:
    """Why the value must not enter the register, or None if it may."""
    if not isfinite(value):
        return "not a finite number"
    if register.datatype == "BOOL" and value not in (0, 1):
        return "not 0 or 1"
    if register.minimum is not None and value < register.minimum:
        return f"below minimum {register.minimum}"
    if register.maximum is not None and value > register.maximum:
        return f"above maximum {register.maximum}"
    return None


def clamped(register: Register, value: float) -> float:
    """The value the register can actually hold, for values the physics produces."""
    if register.minimum is not None:
        value = max(value, register.minimum)
    if register.maximum is not None:
        value = min(value, register.maximum)
    return value
