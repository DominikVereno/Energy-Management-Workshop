from fastapi import FastAPI
from pydantic import BaseModel

from control import calculate_flow_temperature_setpoint

app = FastAPI()


class ControlInput(BaseModel):
    room_temperature: float
    outdoor_temperature: float
    flow_temperature: float
    return_temperature: float
    electrical_power: float
    thermal_power: float
    pv_power: float
    grid_power: float


class ControlOutput(BaseModel):
    flow_temperature_setpoint: int


@app.post("/control")
def control(data: ControlInput) -> ControlOutput:

    setpoint = calculate_flow_temperature_setpoint(
        room_temperature=data.room_temperature,
        outdoor_temperature=data.outdoor_temperature,
        flow_temperature=data.flow_temperature,
        return_temperature=data.return_temperature,
        electrical_power=data.electrical_power,
        thermal_power=data.thermal_power,
        pv_power=data.pv_power,
        grid_power=data.grid_power,
    )

    return ControlOutput(
        flow_temperature_setpoint=setpoint
    )