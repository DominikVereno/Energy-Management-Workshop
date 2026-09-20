# grafana - config

Visualisation of the heat-pump stack. This repository holds the **configuration** of the data
source and the dashboard — Grafana itself comes unchanged from the official image.

Within the heat-pump project Grafana is purely **read-only**: it shows what OpenEMS Edge has
written into InfluxDB from the IDM heat pump. Control happens elsewhere, through the OpenEMS
REST API (`:8084`) and the Felix web console (`:8080`).

## > Structure

```
grafana/
├── Dockerfile                            # FROM grafana/grafana:11.2.0 + COPY of both folders
├── provisioning/
│   ├── datasources/influxdb.yml          # data source "InfluxDB" (uid: influxdb-openems, Flux)
│   └── dashboards/dashboards.yml         # file provider, reads /etc/grafana/dashboards
├── dashboards/
│   └── heatpump.json                     # dashboard "Wärmepumpe IDM" (uid: heatpump-idm)
└── .gitlab-ci.yml                        # Kaniko build -> registry (sha, branch, latest tag)
```

Both folders are **copied** into the image (no bind mount) — see "Deploying dashboard changes".

## > Data source

`provisioning/datasources/influxdb.yml` defines a single data source:

| Field | Value |
| ----- | ----- |
| Name / uid | `InfluxDB` / `influxdb-openems` (isDefault) |
| URL | `http://influxdb:8086` (container network, `access: proxy`) |
| Query language | **Flux** |
| Organisation / bucket | `docs` / `openems` |
| Token | `$INFLUXDB_TOKEN` |

**Pitfall:** `$INFLUXDB_TOKEN` is resolved by Grafana at runtime from the *container's
environment*. So the variable has to be set on the `grafana` service in the compose file (it is,
in `stack-delpoy`); otherwise the data source ends up without a token and all panels stay empty.

## > The dashboard

A single file, `dashboards/heatpump.json` — **"Wärmepumpe IDM"** (`uid: heatpump-idm`),
auto-refresh **10 s**, default range **last 6 h**. All panels are `timeseries` and query the same
measurement `data` in the bucket `openems`, one field per channel in the format
`heatpump0/Reg<address>`.

| id | Title | Channels | Shows |
| -- | ----- | -------- | ----- |
| 1 | Flow control (actual vs. setpoint) | Reg1050, Reg1378, Reg1449, Reg1491 | actual flow temperature against the three setpoint sources: heating curve (Reg1378), fixed flow setpoint (Reg1449, effective **only** with circuit A operating mode 4 "manual heating") and cooling flow setpoint (Reg1491) |
| 4 | Return & ambient | Reg1052, Reg1000, Reg1002 | return (B34), outdoor temperature (B32) and the **damped** outdoor temperature (Reg1002) — the latter is the input of the heating curve and the reference for the heating limit |
| 2 | Power | Reg1790, Reg4122 | conversion power (thermal) against power draw (electrical) — the ratio is the COP, available ready-made as Reg9014 |
| 3 | Modulation level | Reg1104 | compressor modulation in %. If it jumps between 0 and 100, the plant is cycling (too little load on the buffer) |
| 5 | Buffer stratification | Reg9010, Reg9012, Reg1008 | buffer tank top / bottom (synthetic registers of the simulator) and the measured mean value B38 |

Every query renames its series via `set(key: "_field", value: "…")` so that the legend shows
meaningful names instead of `heatpump0/Reg1050`.

### Known gap: no panel for the price control

All five panels show `heatpump0/Reg…` exclusively. For the control mode `PRICE_OPTIMIZED` there
is **no panel**, even though the data is in InfluxDB and demonstrably arrives there:
`price0/CurrentPrice`, `ctrlHeatpumpIdm0/HeatingAllowedByPrice`, `ctrlHeatpumpIdm0/ActiveMode`
and `heatpump0/Reg1710`. The core behaviour of the price control is therefore only visible via
REST or an Influx query, not on the dashboard.

Left open deliberately — a missing feature, not a bug. The obvious extension would be price and
threshold in one panel, with the release flag and Reg1710 below it as a status track.

Important for that: those fields are **not** named `heatpump0/…` but carry the component ID of
the respective component (`price0/…`, `ctrlHeatpumpIdm0/…`). The step "check the priority" below
does not apply to them, since it only concerns Modbus registers of the heat pump.

### Adding new channels

1. Check whether the register address is listed in `HIGH_PRIORITY_ADDRS` in
   `open-ems/io.openems.edge.heatpump.idm/generator/generate.py`. If it is not, the Modbus bridge
   reads it only about every 58 s (instead of every 2 s) — the curve becomes coarse and sluggish.
   Add the address if needed and rebuild the edge image.
2. Add a target in `dashboards/heatpump.json` following the pattern of the existing ones
   (bucket `openems`, `_measurement == "data"`, `_field == "heatpump0/Reg<addr>"`).
3. Rebuild the image and **force-recreate** the container (see below).

## > Deploying dashboard changes

Dashboards live **inside the image** (`COPY dashboards /etc/grafana/dashboards`). So a plain
`docker compose up -d` does not pick changes up:

```bash
docker compose up -d --build --force-recreate grafana     # built locally (stack-delpoy)
docker compose pull grafana && docker compose up -d --force-recreate grafana   # after a CI build
```

For quick iteration `docker cp dashboards/heatpump.json grafana:/etc/grafana/dashboards/` works
too — the file provider picks it up within ~30 s (`updateIntervalSeconds: 30`), but that state is
gone on the next recreate.

`allowUiUpdates: true` allows editing in the web interface; such changes are equally volatile
unless they are exported and written back into the repository.

### Exporting a dashboard

- open the dashboard
- top right: *Export > Export as code*
- choose model **Classic**, download the file
- check it in as `dashboards/heatpump.json`

## > Operations

- Reachable at `http://localhost:3000`, login `admin` / `admin` (from the compose file).
- The volume `grafana-data` holds sessions and interface state.
- If a panel is empty, check in this order: is OpenEMS writing at all (`Timedata.InfluxDB`
  active?) → is the field in InfluxDB (Data Explorer, bucket `openems`, measurement `data`) →
  is the field name in the query correct → does the container have the token.
