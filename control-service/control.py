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

    setpoint = 30.0

    setpoint += 4.0 * (23.0 - room_temperature)

    if outdoor_temperature < 5.0:
        setpoint += 2.0

    if grid_power < -1.0 and room_temperature < 24.0:
        setpoint += 4.0

    return round(max(20.0, min(42.0, setpoint)))