from __future__ import annotations

from sim.model import Register

# Simulation-only sensor. The IDM parameter list reports electrical and thermal
# power (4122 / 4126) but no efficiency, so every consumer has to divide the two
# curves itself — and gets a meaningless spike whenever the compressor is off.
# Exposing the ratio here keeps that decision in one place: it is 0 while the
# compressor is off, and it is the magnitude of the ratio, so the cooling branch
# reports its EER on the same register instead of a negative COP.
# FLOAT occupies two registers, hence the gap to the next synthetic address.

COP_ADDR = 9014  # coefficient of performance (EER while cooling)

COMPRESSOR_OFF_COP = 0.0


def cop_register() -> Register:
    return Register(
        address=COP_ADDR,
        datatype="FLOAT",
        access="RO",
        readable=True,
        writable=False,
        name="Leistungszahl COP (Simulation)",
        unit="",
        minimum=None,
        maximum=None,
        default=None,
    )


def coefficient_of_performance(p_el: float, p_th: float) -> float:
    if p_el <= 0.0:
        return COMPRESSOR_OFF_COP
    return abs(p_th) / p_el
