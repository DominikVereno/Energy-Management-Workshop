from __future__ import annotations

import math

SECONDS_PER_DAY = 24 * 60 * 60

# Daily outdoor temperature as a sine around a mean. Placeholder shape, not
# measured data: coldest before sunrise, warmest in the afternoon.
#
# The mean sits on the heating limit (Reg1442, 15 C by default) so the damped
# temperature crosses it twice a day and both sides of the curve are visible:
# below it the circuit heats and the flow setpoint follows the slope, above it
# the heating limit shuts the circuit off. The swing has to carry the crossing
# on its own, and the damping costs it a factor of about six -- a sine of one
# day fed through a mean of one day comes out at 1/sqrt(1 + (2*pi)^2) of its
# amplitude. Hence 10 K raw for the roughly 1.6 K that reach Reg1002.
DEFAULT_MEAN_TEMP_C = 5.0
DEFAULT_SWING_C = 10.0

# Clock time of the daily minimum. The sine reaches its low here, so the mean is
# crossed a quarter day earlier/later and the high sits opposite at 15:00.
COLDEST_HOUR = 3.0


class AmbientSource:
    def __init__(self, mean_c: float = DEFAULT_MEAN_TEMP_C, swing_c: float = DEFAULT_SWING_C):
        self._mean_c = mean_c
        self._swing_c = swing_c

    def temperature(self, sim_time: float) -> float:
        seconds_into_day = sim_time % SECONDS_PER_DAY
        coldest_offset = COLDEST_HOUR / 24.0
        phase = 2 * math.pi * (seconds_into_day / SECONDS_PER_DAY - coldest_offset)
        return self._mean_c - self._swing_c * math.cos(phase)
