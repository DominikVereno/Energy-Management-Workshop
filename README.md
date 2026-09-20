# Heat Pump Workshop

Workshop on integrating and controlling a simulated iDM heat pump using OpenEMS.

## Start

Requirement: **Docker Desktop must be running.**

Run the following command from the `stack-delpoy/` directory:

```bash
docker compose up -d --build
```

## Important Links

### OpenEMS Apache Console

http://localhost:8080/system/console/configMgr

- Username: `admin`
- Password: `admin`

### Grafana

http://localhost:3000/d/heatpump-idm/warmepumpe-idm

- Username: `admin`
- Password: `admin`

## Modbus Registers

The iDM Modbus register documentation can be found here:

[`Modbus_TCP_Navigator_2.0_DE.pdf`](Modbus_TCP_Navigator_2.0_DE.pdf)

## Task 1 – Integration

OpenEMS channel definitions:

[`HeatpumpIdm.java`](open-ems/io.openems.edge.heatpump.idm/src/io/openems/edge/heatpump/idm/HeatpumpIdm.java)

Mapping of OpenEMS channels to Modbus registers:

[`HeatpumpIdmImpl.java`](open-ems/io.openems.edge.heatpump.idm/src/io/openems/edge/heatpump/idm/HeatpumpIdmImpl.java)

Uncomment call to control service and register write: 

[`ControllerHeatpumpIdmImpl.java`](open-ems/io.openems.edge.controller.heatpump.idm/src/io/openems/edge/controller/heatpump/idm/ControllerHeatpumpIdmImpl.java)

## Task 2 – Control Algorithm

The control algorithm can be found here:

[`control-service/control.py`](control-service/control.py)

After modifying the control algorithm, only the Control Service needs to be rebuilt. Run the following command from the `stack-delpoy/` directory:

```bash
docker compose up -d --build control-service
```