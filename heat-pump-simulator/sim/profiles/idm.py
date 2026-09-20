from __future__ import annotations

import json
from pathlib import Path
from typing import Optional

from sim.model import Register, RegisterMap
from sim.profile import HeatPumpProfile


PARAMETER_LIST = (
    Path(__file__).resolve().parents[1]
    / "adress_lists"
    / "IDM_Navigator_Modbus_TCP_Parameterliste.json"
)

def _parse_number(cell: object) -> Optional[float]:
    if cell is None or cell == "":
        return None
    if isinstance(cell, (int, float)):
        return float(cell)
    try:
        return float(str(cell).strip().replace(",", "."))
    except ValueError:
        return None


def _register_from_row(row: dict) -> Register:
    access = str(row["Access"]).strip().upper()
    return Register(
        address=int(row["Address"]),
        datatype=str(row["DataType"]).strip().upper(),
        access=access,
        readable="R" in access,
        writable="W" in access,
        name=str(row.get("Name", "")).strip(),
        unit=str(row.get("Unit", "")).strip(),
        minimum=_parse_number(row.get("Min")),
        maximum=_parse_number(row.get("Max")),
        default=_parse_number(row.get("Default")),
        value_list=str(row.get("ValueList", "")).strip(),
        note=str(row.get("Note", "")).strip(),
    )


class IdmProfile(HeatPumpProfile):
    name = "idm"

    # IDM manual ch. 4.2: FLOAT is sent as two registers, low word first, high byte first.
    word_order = "little"
    byte_order = "big"

    def __init__(self, parameter_list: Optional[Path] = None):
        self._parameter_list = Path(parameter_list) if parameter_list else PARAMETER_LIST

    def load_registers(self) -> RegisterMap:
        with open(self._parameter_list, encoding="utf-8") as fh:
            rows = json.load(fh)
        if not isinstance(rows, list):
            raise ValueError(f"Expected a JSON array in {self._parameter_list}")
        return RegisterMap([_register_from_row(row) for row in rows])
