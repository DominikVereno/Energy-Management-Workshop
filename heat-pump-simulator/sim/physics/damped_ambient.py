from __future__ import annotations

# Simulated seconds over which the mean follows a step in the outdoor
# temperature. Roughly one day, so the daily swing is averaged out while a real
# change in the weather still arrives within a plausible time.
DAMPING_TIME_CONSTANT_S = 24 * 60 * 60


class DampedAmbient:
    """Slow-moving mean of the outdoor temperature, reported as Reg1002.

    A heating curve fed with the raw sensor would chase the daily swing and move
    the flow setpoint around all day. Real controllers average the sensor first,
    so the setpoint follows the weather rather than the hour.
    """

    def __init__(self, time_constant_s: float = DAMPING_TIME_CONSTANT_S):
        self._time_constant_s = time_constant_s
        self._value: float | None = None

    def update(self, dt: float, t_ambient: float) -> float:
        if self._value is None:
            self._value = t_ambient
        else:
            self._value += (t_ambient - self._value) * self._weight(dt)
        return self._value

    def _weight(self, dt: float) -> float:
        return min(dt / self._time_constant_s, 1.0)
