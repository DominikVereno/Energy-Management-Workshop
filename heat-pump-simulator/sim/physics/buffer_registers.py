from __future__ import annotations

from sim.model import Register

# Simulation-only sensors. A real IDM buffer has a single tank probe (B38), so the
# layer temperatures of the two-node model have no counterpart in the parameter
# list. They are exposed anyway because the split between them is the whole point
# of the stratified model and is otherwise invisible from outside.
# FLOAT occupies two registers, hence the gap between the addresses.

BUFFER_TOP_ADDR = 9010  # buffer tank, top layer, °C
BUFFER_BOTTOM_ADDR = 9012  # buffer tank, bottom layer, °C


def _layer_register(address: int, name: str) -> Register:
    return Register(
        address=address,
        datatype="FLOAT",
        access="RO",
        readable=True,
        writable=False,
        name=name,
        unit="°C",
        minimum=None,
        maximum=None,
        default=None,
    )


def buffer_registers() -> list[Register]:
    return [
        _layer_register(BUFFER_TOP_ADDR, "Pufferspeicher oben (Simulation)"),
        _layer_register(BUFFER_BOTTOM_ADDR, "Pufferspeicher unten (Simulation)"),
    ]
