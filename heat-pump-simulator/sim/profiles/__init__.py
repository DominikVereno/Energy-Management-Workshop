from __future__ import annotations

from sim.profile import HeatPumpProfile
from sim.profiles.idm import IdmProfile


PROFILES: dict[str, HeatPumpProfile] = {
    profile.name: profile
    for profile in [IdmProfile()]
}


def get_profile(name: str) -> HeatPumpProfile:
    try:
        return PROFILES[name]
    except KeyError:
        raise ValueError(
            f"Unknown heat pump profile {name!r} (available: {sorted(PROFILES)})"
        ) from None
