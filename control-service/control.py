def calculate_flow_temperature_setpoint(
    room_temperature: float,
    outdoor_temperature: float,
    flow_temperature: float,
    return_temperature: float,
    electrical_power: float,
    thermal_power: float,
    pv_power: float,
    grid_power: float,
) -> int:

    print(f"PV Power: {pv_power}")

    return 20.0