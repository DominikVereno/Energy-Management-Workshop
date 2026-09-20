# InfluxDB - config

Time-series database of the heat-pump stack. Stores the channel values that OpenEMS Edge reads
from the IDM heat pump; Grafana reads from this database, read-only.

The repository contains **no database**, only the image recipe: the official `influxdb:2.7`
plus an init script.

## > Structure

```
influxdb/
├── Dockerfile          # FROM influxdb:2.7 + COPY init /docker-entrypoint-initdb.d
├── init/
│   └── setup.sh        # creates the bucket "openems" in the org "docs" on first start
└── .gitlab-ci.yml      # Kaniko build -> registry (sha, branch, latest tag)
```

`init/setup.sh` only runs on the **first start** of an empty volume (`influxdb-data`), i.e. only
when `DOCKER_INFLUXDB_INIT_MODE=setup` applies. It is guarded with `|| true` and therefore
idempotent.

## > Configuration

Everything comes from the compose file of the `stack-delpoy` repository:

| Variable | Value in the stack | Meaning |
| -------- | ------------------ | ------- |
| `DOCKER_INFLUXDB_INIT_MODE` | `setup` | one-time setup on first start |
| `DOCKER_INFLUXDB_INIT_ORG` | `docs` | organisation |
| `DOCKER_INFLUXDB_INIT_BUCKET` | `openems` | bucket OpenEMS writes into |
| `DOCKER_INFLUXDB_INIT_ADMIN_TOKEN` | `${INFLUXDB_TOKEN}` | token used **by OpenEMS and Grafana as well** |

Host port: **8086** (`http://localhost:8086`), login `admin` / `admin123`.
Persistence: named volume `influxdb-data`.

## > Who writes, who reads

| Side | Path | Details |
| ---- | ---- | ------- |
| **Write** | OpenEMS Edge, built-in component `Timedata.InfluxDB` (`influx0`) | `url=http://influxdb:8086`, `org=docs`, `bucket=openems`, `measurement=data`, `persistencePriority=LOW` |
| **Read** | Grafana data source `influxdb-openems` | Flux, `http://influxdb:8086`, bucket `openems` |

Data points live in the measurement **`data`**, one field per channel, named
`<component>/<channel>` — e.g. `heatpump0/Reg1050`. Exactly these field names show up again in
the Flux queries of the Grafana dashboards.

Persisted is **not just the heat pump**: every component with a matching `persistencePriority`
writes too. So next to `heatpump0/Reg…` the bucket also holds the channels of the controller and
its data sources, such as `ctrlHeatpumpIdm0/ActiveMode`,
`ctrlHeatpumpIdm0/HeatingAllowedByPrice` and `price0/CurrentPrice`. That matters because the
Grafana dashboard so far queries `heatpump0` fields only — the price-control data is there, but
unvisualised.

The time lapse of the simulation (Reg9000) does **not** change the timestamps: writing always
happens in real time, only the physics runs faster.

## > Token

The stack uses a single token stored in `stack-delpoy/.env` (`INFLUXDB_TOKEN`) as the InfluxDB
admin token, as the `apiKey` of the OpenEMS timedata component and as the Grafana data-source
token. So it does not have to be created by hand.

If a separate token is needed after all:

- InfluxDB: *Load Data > API Tokens > Generate API Token > Custom API Token*, bucket `openems`
  with read permission, set a description — the token is shown **only once**.
- Grafana: *Connections > Data sources > InfluxDB > InfluxDB Details > Token*, then *Save & test*.
- OpenEMS: Felix web console `http://localhost:8080/system/console/configMgr`, component
  `Timedata.InfluxDB`, field `apiKey`.

## > Build / deploy

The image is built by the GitLab CI (Kaniko) on every push to
`…/heat-pump-simulator/influxdb:latest`. Locally:

```bash
docker compose up -d --build influxdb      # from stack-delpoy
```

After a CI build, pull and recreate instead:

```bash
docker compose pull influxdb && docker compose up -d --force-recreate influxdb
```

Careful: changes to `init/setup.sh` only take effect on an **empty** volume. An existing
`influxdb-data` skips the init script.
