# Heat Pump Simulator

## Description

Standalone heat pump simulation (hplib), served as a **Modbus TCP** server. Any Modbus client
(OpenEMS, a building management system, `test_client.py`) talks to it exactly like a real unit:
reading measured values and writing configuration parameters. It is driven from two sides:

- **OpenEMS** polls the register image through its Modbus bridge and writes setpoints back,
- **Grafana** plots the resulting curves out of InfluxDB (read-only, downstream of OpenEMS).

The register map, datatypes and wire conventions are **manufacturer-specific and pluggable** —
there is no cross-vendor register standard for heat pumps (unlike SunSpec for inverters). Each
manufacturer is one file under `sim/profiles/`; the generic core stays untouched. The IDM
Navigator 2.0 profile ships first, driven by its published parameter list (663 registers in
`sim/adress_lists/`).

Behind that interface runs a real physical simulation: a modulating compressor (hplib), a
two-layer stratified buffer, a weather-compensated heating curve and a building load, ticked once
a real second. The values a client reads are the output of a running plant, not fixed seeds —
writing a setpoint moves the temperatures the way it would on real hardware, over minutes.

### Three layers, one register storage (important)

The simulator is split into a Modbus side and a physics side that only meet at the register
storage in the middle:

```
              Modbus client (OpenEMS, test_client.py)
                              │  TCP :5020
        ┌─────────────────────▼──────────────────────┐
        │ MODBUS LAYER   server, codec, model,       │
        │                profile(s), ModbusRegisters │
        └─────────────────────┬──────────────────────┘
                              │  raw 16-bit words, access + range checked
                      ┌───────▼────────┐
                      │ RegisterStorage│   dict{address: word}
                      └───────▲────────┘
                              │  decoded values, clamped, no access check
        ┌─────────────────────┴──────────────────────┐
        │ PHYSICS LAYER  loop → binding → plant →    │
        │                controller / buffer / hplib │
        └────────────────────────────────────────────┘
```

Both sides share one storage but see it through different lenses, which is why there are two
accessor classes rather than one with a flag:

| | `ModbusRegisters` (outside) | `SimulationRegisters` (inside) |
| --- | --- | --- |
| sees | raw 16-bit words | decoded numbers |
| access control | yes — rejects writes to read-only, reads of write-only/undefined | none: the physics *is* the sensor, so it must write read-only registers |
| out-of-range value | rejected (`ILLEGAL_VALUE`) | clamped into the register range |
| granularity | whole registers only, atomically | one register per call |

`build_simulation()` in `sim/server.py` is the **single assembly path**. It returns a `Simulation`
whose two halves — the pymodbus server and the physics loop — then run as parallel asyncio tasks.

### Simulated clock

The simulator owns its own clock: `SimulationClock` converts real elapsed time into simulated time
by a factor, exposed live as **Reg9000** (`0` = pause, `1` = real time, `60` = 1 s → 1 min, `300`
= maximum). `ClockControl` applies a changed register value on the next tick, so the time lapse can
be switched from OpenEMS while the simulation runs.

The factor changes **how much simulated time one tick spans, not how often the loop computes** —
the loop always ticks once per real second. Wall-clock timestamps (e.g. in InfluxDB) therefore stay
real; a factor of 60 does not compress the recorded time axis, it makes the plant move 60× faster
along it. Components that integrate (the buffer) subdivide the step internally, because a tick can
span minutes of simulated time and explicit Euler would diverge.

### Value validation

Most writable registers carry a minimum and a maximum in the parameter list, and both sides honour
them — for opposite reasons:

- a **client write** outside the range, a non-finite value, or a BOOL that is not 0/1 is
  **rejected** with a Modbus `ILLEGAL_VALUE` exception and logged with the reason. A write is also
  rejected if it starts inside a register or stops halfway through one, which would leave a FLOAT
  half overwritten. Accepted writes are applied atomically, so a rejected multi-register write
  changes nothing.
- a value the **physics** produces is **clamped** into the range instead (a sensor cannot report an
  illegal reading), and a non-finite one is dropped with a warning rather than written.

## Structure

```
sim/
  model.py                  vendor-neutral: Register + RegisterMap, datatype widths, seeding
  codec.py                  value <-> Modbus words (FLOAT/WORD/UCHAR/BOOL, word/byte order)
  profile.py                HeatPumpProfile base interface
  server.py                 pymodbus wiring + build_simulation() (the only assembly path)
  register/
    register_storage.py     the words themselves, seeded from the parameter list
    modbus_registers.py     outside view: access control, whole-register atomic writes
    simulation_registers.py inside view: decoded read/write for the physics
    validation.py           the range/finite/BOOL rules both views share
  profiles/
    idm.py                  IDM Navigator 2.0 profile (JSON parameter list -> registers)
    __init__.py             profile registry (get_profile)
  adress_lists/             manufacturer parameter lists (JSON, 663 IDM registers)
  physics/
    demand.py               OperatingMode + Demand — the contract "what should the plant do"
    ambient_source.py       outdoor temperature as a daily sine
    damped_ambient.py       slow mean of the outdoor temperature (Reg1002)
    heating_curve.py        weather-compensated flow setpoint + heating limit
    controller.py           modulating PI controller with minimum runtime/off time
    hplib_model.py          thermodynamic core: hplib wrapper (P_el, P_th, COP)
    buffer.py               two-layer stratified heat storage buffer
    plant.py                the plant: controller + hplib + buffer + building load
    buffer_registers.py     simulation-only buffer layer sensors (9010/9012)
    cop_register.py         simulation-only coefficient of performance (9014)
    synthetic_registers.py  all simulation-only registers in one list
    clock/
      clock.py              SimulationClock: real time -> simulated time
      clock_register.py     the time-lapse register (9000)
      clock_control.py      applies Reg9000 to the clock, live
    runtime/
      binding.py            the address map: which register means which quantity
      loop.py               the tick: clock -> weather -> demand -> plant -> registers
heatpump_sim_modbus.py      server entry point (CLI + env config)
test_client.py              manual read/write CLI
tests/                      modbus_test.py, heating_curve_test.py, clock_control_test.py
```

### The tick

`SimulationLoop._tick()`, once per real second:

1. `ClockControl.sync()` — pick up a changed time-lapse factor from Reg9000.
2. Ask the clock how much *simulated* time has passed (`dt`). `dt <= 0` (factor 0 = pause) → skip.
3. `AmbientSource` → outdoor temperature for that moment (Reg1000).
4. `DampedAmbient` → its slow mean (Reg1002), which is what the heating curve is fed.
5. `PhysicsBinding.read_demand()` → read the request registers and the circuit settings, run the
   heating curve → `Demand(mode, target_flow_temp)`.
6. `Plant.step()` → PI controller → modulation → hplib operating point → buffer integration.
7. `PhysicsBinding.write_outputs()` → write the resulting measured values back.

### Physical model

| Component | File | Behaviour and headline parameters |
| --------- | ---- | --------------------------------- |
| Weather | `ambient_source.py` | Daily sine, mean **15 °C**, swing **±10 K**, coldest at 03:00. Chosen so the damped mean crosses the heating limit twice a day. |
| Damped outdoor temp | `damped_ambient.py` | PT1 over ~1 day. Feeds the curve so the setpoint follows the weather, not the hour. Damping weakens the daily amplitude by ~6.4×, so effectively the mean is what moves it. |
| Heating curve | `heating_curve.py` | `flow = room + slope·(room − outdoor)`, clamped to 20…90 °C. Slope 0.6 at 22 °C room → 41.2 °C at −10 °C outside. Setpoint and "does the circuit want heat" are separate functions, so Reg1378 stays meaningful while the plant is off. |
| Compressor control | `controller.py` | PI on the flow temperature error (Kp = 0.5/K → full modulation at 2 K, Ki = 0.005) with anti-windup. Minimum modulation **0.3** — below it the compressor switches off instead of running slower. Minimum runtime and off time **300 s** each. |
| Thermodynamics | `hplib_model.py` | hplib `Generic / Outdoor Air/Water / Regulated`; IDM is not in the Keymark database (0/9953 rows). Rating point −7 °C / 52 °C / 10 kW_th (placeholder, not IDM datasheet values). Modulation scales the full-load point linearly, COP is kept constant. |
| Buffer | `buffer.py` | 300 L in **two layers**, 630 kJ/K each, 3 W/K standby loss, 2 W/K vertical exchange (this is what erodes stratification). Integration step subdivided to ≤ 10 s. Heating charges the top layer, cooling the bottom. |
| Building | `plant.py` | 250 W/K heat loss against a 20 °C room (~7.5 kW at −10 °C), 7 K circuit spread, minimum draw 3 kW so a forced mode still clears the compressor minimum instead of cycling. |

Cooling is heating mirrored: `_sign()` and `_supply_node()` in `plant.py` flip the spread, the
thermal power, the building load and which buffer layer is charged — there is no separate cooling
branch.

## Register map (IDM profile)

Written by a client, read by the physics:

| Register | Meaning | Range / values |
| -------- | ------- | -------------- |
| 1710 | Anforderung Heizen | 0 / 1 |
| 1711 | Anforderung Kühlen | 0 / 1 |
| 1393 | Betriebsart Heizkreis A | 0 off, 3 eco, 4 constant setpoint, else normal |
| 1429 | Heizkurve HK A (slope) | 0,1 … 3,5 |
| 1442 | Heizgrenze HK A | 0 … 50 °C |
| 1401 | Raumsolltemperatur Normal HK A | 15 … 30 °C |
| 1415 | Raumsolltemperatur Eco HK A | 10 … 25 °C |
| 1449 | Sollvorlauftemperatur HK A | 20 … 90 °C — **only effective at Reg1393 = 4** |
| 1491 | Sollvorlauftemperatur Kühlen HK A | 8 … 30 °C |
| 9000 | Zeitraffer-Faktor (simulation only) | 0 … 300 |

Written by the physics, read by a client:

| Register | Meaning |
| -------- | ------- |
| 1000 | Außentemperatur (B32) |
| 1002 | Gemittelte Außentemperatur (damped) |
| 1008 | Wärmespeichertemperatur (B38) — mean of both layers |
| 1050 / 1052 | Wärmepumpen Vor-/Rücklauftemperatur (B33 / B34) |
| 1091 / 1092 | Heiz- / Kühlanforderung (feedback) |
| 1100 | Status Verdichter 1 |
| 1104 | Status Ladepumpe (M73), % — the modulation degree |
| 1378 | Heizkreis A Sollvorlauftemperatur (curve result) |
| 1790 | Momentanleistung (thermal), kW |
| 4122 / 4126 | Leistungsaufnahme / thermische Leistung, kW |
| 9010 / 9012 | buffer top / bottom layer, °C (simulation only) |
| 9014 | coefficient of performance, thermal / electrical power (simulation only) |

`physics/runtime/binding.py` is the **only** place where concrete addresses meet physical
quantities — a second vendor needs its own map there.

Both requests set at once: **heating wins** (a simulator decision, not specified by the manual).
Heating additionally requires the circuit to want heat — operating mode ≠ off and the damped
outdoor temperature below the heating limit. Cooling bypasses the curve and uses Reg1491 directly.

Registers 9000 / 9010 / 9012 / 9014 do not exist on real hardware. They sit well above the real address
range and are deliberately kept out of the parameter list, which stays a faithful copy of the
manufacturer spec. `physics/synthetic_registers.py` is their single source, so the Modbus server
and the OpenEMS channel generator can never disagree about which of them exist.

Reg9014 exists because the parameter list reports electrical and thermal power (4122 / 4126) but
no efficiency, so every consumer would divide the two curves itself and get a meaningless spike
whenever the compressor is off. The register carries `|P_th| / P_el`, is `0` while the compressor
is off, and reports the magnitude — so the cooling branch shows its EER there instead of a
negative COP.

### Modbus conventions

| Datatype | Registers | Notes |
| -------- | --------- | ----- |
| FLOAT | 2 | 32-bit IEEE 754, low word first, high byte first (IDM manual ch. 4.2) |
| WORD | 1 | signed 16-bit |
| UCHAR | 1 | unsigned 16-bit |
| BOOL | 1 | 0 / 1 |

Access is checked per word slot, so the second half of a FLOAT is protected too. Read-only
registers reject writes, write-only and undefined addresses reject reads — a Modbus exception is
returned, matching a real controller.

### Behaviour worth knowing before calling something a bug

- **Setpoints are not persistent.** The storage is in-memory, so every restart re-seeds all
  registers from the parameter list defaults (Reg1449 → 45, Reg1429 → 0,6, Reg1442 → 15). The
  simulator starts in standby (1710 = 1711 = 0), like a real unit. Note that the OpenEMS REST
  `ApiWorker` re-pushes written values every second, so a non-default value after a restart proves
  nothing about persistence.
- **Writes act slowly, not instantly.** The controller holds a state for at least 300 s
  (compressor protection) and the buffer then moves at roughly 0.4 K/min.
- **Reg1449 does nothing unless Reg1393 = 4.** In every other mode the heating curve owns the
  setpoint. A slope of 3,5 clamps the curve at 90 °C and drives the COP towards 1.
- **Three FLOATs return garbage** (1392 / 1441 / 1483): the published IDM parameter list overlaps
  their addresses. A known defect of the spec, reproduced faithfully rather than patched.

## Installation

### install required dependencies:

```bash
pip install -r requirements.txt
```

## Usage

### Start the simulator (Modbus TCP):

```bash
python heatpump_sim_modbus.py
```

| Option | Environment variable | Default | Meaning |
| ------ | -------------------- | ------- | ------- |
| `--host` | `MODBUS_HOST` | `0.0.0.0` | bind address |
| `--port` | `MODBUS_PORT` | `5020` | 502 is privileged and was already taken; real hardware stays on 502 |
| `--profile` | `HEATPUMP_PROFILE` | `idm` | manufacturer profile |
| `--factor` | `REAL_TIME_FACTOR` | `60` | initial time-lapse factor, afterwards live via Reg9000 |
| — | `LOG_LEVEL` | `INFO` | |

```bash
python heatpump_sim_modbus.py --port 5020 --profile idm --factor 60
```

### Manual test client

Reads and writes any address by number, decoded by datatype.

```bash
python test_client.py --port 5020 read 1000        # one-shot
python test_client.py --port 5020 write 1401 23.5
python test_client.py --port 5020                  # interactive
```

Interactive commands: `read <addr>`, `write <addr> <value>`, `list [text]`, `readall`, `help`,
`quit`.

### (Alternative) Build and run Docker container:

```bash
docker build -t heat-pump-simulator .
docker run --rm -p 5020:5020 heat-pump-simulator
```

### Testing:

```bash
python tests/modbus_test.py          # end-to-end: reads, writes, access control, range rejection, signed round-trips
python tests/heating_curve_test.py   # curve shape, heating limit, damping, weather-dependent load
python tests/clock_control_test.py   # time-lapse factor applied from Reg9000
```

Each file starts what it needs in-process and has no dependencies beyond `requirements.txt`.

Note: **run the test files directly, never through pytest.** They count their own failures and
report them via the exit code, so pytest would only see one green function and report success even
on a real failure. The CI runs `compileall` plus `tests/modbus_test.py`.

## Deployment

The image is built by the **GitLab CI** (`.gitlab-ci.yml`, kaniko → GitLab registry, pushes the SHA
and branch tags plus `:latest`) and consumed by `stack-delpoy` as `${HEAT_PUMP_SIMULATOR}`,
published on `5020:5020` with the network alias `heatpump-sim`.

`docker compose up` does not pull a newer `:latest` if one already exists locally. After a CI
build:

```bash
docker compose pull heatpump-sim && docker compose up -d --force-recreate heatpump-sim
```

Simulator and OpenEMS bridge must agree on the port, otherwise the Edge reports
"Modbus Communication failed".

## Consumers

- **open-ems** — `io.openems.edge.heatpump.idm` maps the register image onto 667 channels; the
  channel code is generated from the same parameter list by `generator/generate.py`, which also
  carries the min/max validation to the Edge side and marks the registers in `HIGH_PRIORITY_ADDRS`
  as `Priority.HIGH`. That matters: the bridge reads only one LOW-priority task per cycle (~206 of
  them), so a register left at LOW resolves at roughly 58 s instead of 2 s.
  `io.openems.edge.controller.heatpump.idm` writes back, in one of four modes: `MANUAL` drives
  Reg1710/1711 and Reg1449/1491 directly, `HEATING_CURVE` and `PV_OPTIMIZED` parametrise the
  curve and hold Reg1401 (which keeps the curve active), and `PRICE_OPTIMIZED` gates the heat
  request Reg1710 by the electricity price. Whichever is active, the controller rewrites its
  registers every cycle — a value set by hand is gone within seconds. That is the most common
  false diagnosis of "writing does not work"; the other is the non-persistence above.
- **influxdb / grafana** — OpenEMS persists the channels to InfluxDB; the "Wärmepumpe IDM"
  dashboard plots them read-only. A time-lapse factor does not change these timestamps.

## Adding a manufacturer

1. Add the parameter list to `sim/adress_lists/`.
2. Add `sim/profiles/<vendor>.py` with a `HeatPumpProfile` subclass: set `name`, the FLOAT
   `word_order` / `byte_order`, and implement `load_registers()`.
3. Register it in `sim/profiles/__init__.py`.
4. Give it an address map in `physics/runtime/binding.py`.

The Modbus core (`model.py`, `codec.py`, `register/`, `server.py`) needs no change.
