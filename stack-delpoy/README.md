# Stack Deploy

Docker Compose stack of the heat-pump environment. Ties simulator, EMS, time-series database and
visualisation together and holds the persistent OpenEMS state.

## > Services

| Service | Host port | Origin | Role |
| ------- | --------- | ------ | ---- |
| `heatpump-sim` | **5020** | `../heat-pump-simulator` | IDM Navigator simulator: Modbus TCP server + physics (hplib, buffer, heating curve, time lapse). Stand-in for the real heat pump. |
| `openems-edge` | **8080**, **8084** | `../open-ems` (`tools/docker/edge/Dockerfile`) | The EMS. Reads/writes the registers over the Modbus bridge, runs the IDM controller, persists to InfluxDB. 8080 = Felix web console, 8084 = REST API. |
| `influxdb` | **8086** | `../influxdb` | Time-series storage (bucket `openems`, org `docs`). |
| `grafana` | **3000** | `../grafana` | Dashboard "Wärmepumpe IDM", reads from InfluxDB only. |

OpenEMS is operated through the REST API (`:8084`) and the Felix web console (`:8080`), and
observed through Grafana.

### Not in the stack: `price-service` and Wattson's inverter proxy

The control mode `PRICE_OPTIMIZED` needs the spot price from the `price-service`. That one lives
in a **separate repository outside this one** and runs in a **separate compose stack** — it is
deliberately absent from this `docker-compose.yml`.

Consequence: the two stacks hang in different Docker networks, the service name `price-service`
is **not resolvable** from the edge. That is why the instance `price0` points at
`http://host.docker.internal:8200`, i.e. across the host port.

The same goes for the Fronius data: it comes from Wattson's `solar-api-inverter-proxy`, which
runs in the **Wattson compose stack** and publishes port 8734 on the host. The edge reaches it
under the name `wattson-proxy`, which `docker-compose.yml` maps to the host gateway via
`extra_hosts`. The host name and port are set in `.env` (`WATTSON_PROXY_HOST`,
`WATTSON_PROXY_PORT`).

If the container is not running, the controller silently falls back to **fail-open** and keeps
heating normally — nothing reports it except the channel `PriceNotAvailable`. So before hunting
for bugs in the edge, check whether the `price-service` is running at all.

### Topology

```
heatpump-sim :5020  ──Modbus TCP──►  openems-edge  ──HTTP──►  influxdb :8086
   (simulator)         (modbus0)      (heatpump0,               (bucket openems)
                                       ctrlHeatpumpIdm0)              │
                                            ▲                         │ Flux (read-only)
                                            │ REST :8084              ▼
                                         operation                grafana :3000
```

Inside the compose network the containers address each other by service name
(`ip=heatpump-sim`, `url=http://influxdb:8086`) — the host ports exist only for operation and
debugging.

## > Prerequisites

Docker + Docker Compose. Nothing else — the sources of all four images are part of the same
repository, because the compose file builds them from the neighbouring directories:

```
heat-pump/
├── heat-pump-simulator/   # simulator source + Dockerfile
├── open-ems/              # OpenEMS fork including the four in-house bundles
├── influxdb/              # InfluxDB image + bucket init
├── grafana/               # data source + dashboard
└── stack-delpoy/          # this directory (docker-compose.yml, .env, data/)
```

So a single clone of `heat-pump` is enough:

```bash
cd stack-delpoy && docker compose up -d --build
```

## > Configuration

All four images are built **locally** from the neighbouring directories — nothing is pulled from a
registry. The image names are fixed local tags in the `docker-compose.yml`
(`<service>:local`), and every service carries `pull_policy: build`, so Docker builds
instead of looking for the tag in a registry.

`.env` next to the `docker-compose.yml` therefore only supplies the InfluxDB token:

```env
INFLUXDB_TOKEN=<token>
```

`INFLUXDB_TOKEN` is used three times: as the InfluxDB admin token, as the `apiKey` of the OpenEMS
component `Timedata.InfluxDB` and as the token of the Grafana data source. If it changes, all
three places have to follow.

Service environment:

| Service | Variable | Default | Description |
| ------- | -------- | ------- | ----------- |
| `heatpump-sim` | `MODBUS_HOST` | `0.0.0.0` | bind address of the Modbus server |
| | `MODBUS_PORT` | `5020` | Modbus TCP port inside the container |
| | `HEATPUMP_PROFILE` | `idm` | device profile (register layout) |
| | `LOG_LEVEL` | `INFO` | log level |
| | `REAL_TIME_FACTOR` | *(unset)* | initial value of the time lapse; without it the default 60 applies. Switchable at runtime via Reg9000. |
| `influxdb` | `DOCKER_INFLUXDB_INIT_*` | see compose | org `docs`, bucket `openems`, admin `admin`/`admin123` |
| `grafana` | `GF_SECURITY_ADMIN_*` | `admin`/`admin` | Grafana login |

## > Persistent state: `data/`

```
data/
├── openems-config/   # Felix Configuration Admin: one .config file per component
└── openems-data/     # RRD4J database (one file per channel) — gitignored
```

`data/openems-config/` is deliberately **versioned**: it holds the commissioned plant. The
relevant instances:

| File | Component | Key setting |
| ---- | --------- | ----------- |
| `Bridge/Modbus/Tcp/*` | `modbus0` | `ip=heatpump-sim`, `port=5020` |
| `HeatPump/Idm/*` | `heatpump0` | heat pump on `modbus0`, unit ID 1 |
| `Controller/Heatpump/Idm/*` | `ctrlHeatpumpIdm0` | control mode (MANUAL / HEATING_CURVE / PV_OPTIMIZED / PRICE_OPTIMIZED), heating curve, PV and price thresholds |
| `Electrical/Fronius/*` | `fronius0` | PV inverter (read-only), `baseUrl=http://wattson-proxy:8734` — Wattson's Solar API proxy, see below |
| `Price/Service/*` | `price0` | electricity price from the `price-service`, `baseUrl=http://host.docker.internal:8200` |
| `Controller/Api/Rest/ReadWrite/*` | `ctrlApiRest0` | REST API on port 8084 |
| `Timedata/InfluxDB/*` | `influx0` | bucket `openems`, measurement `data` |

**Why the component configs stay versioned:** `docker compose up -d --build` only builds
**images**. Which components exist at all is decided by the Git state alone — if a `.config` is
missing from the repository, the component simply does not exist in a fresh clone. What is
excluded is therefore pure runtime state only:

```gitignore
data/openems-data/                        # RRD4J time series, one binary file per channel
data/openems-config/Core/Meta.config      # Felix revision + _lastChangeAt, noisy on every change
```

**`fronius0` points at `http://wattson-proxy:8734`** — the real source: that is where Wattson's
`solar-api-inverter-proxy` publishes the Fronius Solar API. `wattson-proxy` is not a container of
this stack but an `extra_hosts` entry pointing at the host gateway, so the address resolves as
soon as the Wattson stack runs on the same host — no config change needed.

Deliberately **not** an `external` docker network: that would make every `docker compose up` of
this stack fail whenever the Wattson stack is not up. The route across the host gateway keeps the
heat-pump stack startable on its own; only the PV values are then missing, and the edge logs a
connection error every second.

To point at a different source (another host, or a mock on a different port), change
`WATTSON_PROXY_HOST` / `WATTSON_PROXY_PORT` in `.env` and `baseUrl` in the `fronius0` config.

> **Security note:** `data/openems-config/Timedata/InfluxDB/*.config` contains the real `apiKey`
> and is already in the Git history. Relevant should the repository ever become public.

## > Starting

```bash
docker compose up -d --build
```

Afterwards:

- Heat-pump dashboard: <http://localhost:3000> (`admin`/`admin`)
- OpenEMS REST API: <http://localhost:8084> (`admin`/`admin`)
- Felix web console: <http://localhost:8080/system/console/configMgr> (`admin`/`admin`)
- InfluxDB: <http://localhost:8086> (`admin`/`admin123`)
- Simulator: Modbus TCP on `localhost:5020`

Logs / stopping:

```bash
docker compose logs -f
docker compose down
```

## > Updating images

Everything is built from source, so one command is enough after any code change:

```bash
docker compose up -d --build
```

Without `--build` Docker reuses the local image and the change stays invisible. This concerns
Grafana in particular (dashboards live in the image) and OpenEMS Edge (bundles live in the fat
jar). To force a single service:

```bash
docker compose build --no-cache <service>
docker compose up -d --force-recreate <service>
```

## > Operating

Reading and writing goes through the REST API of the edge:

```bash
curl.exe -u admin:admin http://localhost:8084/rest/channel/heatpump0/Reg1050
curl.exe -u admin:admin -X POST http://localhost:8084/rest/channel/heatpump0/Reg1449 \
  -H "Content-Type: application/json" -d '{"value":45}'
```

Notes:

- In PowerShell always use `curl.exe`, **not** `Invoke-WebRequest` (it hangs in proxy detection).
- Invalid values (range/type/width) are answered with HTTP 500 including the reason.
- While `ctrlHeatpumpIdm0` runs, the controller overwrites manually set registers again after a
  few seconds — for manual experiments disable the controller or switch it to mode MANUAL.
- After a restart of the simulator the reads return `null` for about 2 minutes, until the bridge
  has worked through all task blocks once.
- RW registers of the simulator are not persistent: after a simulator restart the spec defaults
  are back.
- An **empty** response (instead of `null`) to `/rest/channel/<id>/.*` means the component does
  not exist at all — not that it delivers no values.
- The live state is in the REST channels, **not** in `data/openems-config/**`. The config files
  can lag behind.

With the price control running, diagnosis needs three channels, not one:

```bash
curl.exe -u admin:admin http://localhost:8084/rest/channel/price0/CurrentPrice
curl.exe -u admin:admin http://localhost:8084/rest/channel/ctrlHeatpumpIdm0/PriceNotAvailable
curl.exe -u admin:admin http://localhost:8084/rest/channel/ctrlHeatpumpIdm0/HeatingAllowedByPrice
```

`HeatingAllowedByPrice = 1` alone proves nothing: without price data the same 1 is there because
of fail-open. Only `PriceNotAvailable = 0` together with a defined `CurrentPrice` proves a real
price decision.

## > Note on port 5020

Modbus TCP is registered with IANA on **502**, and that is what real hardware answers on. The
stack nevertheless uses `5020` throughout, because `502` is a privileged port
(root/administrator) and was occupied on the development machine.

`MODBUS_PORT`, the port mapping `"5020:5020"` and the `port` of the Modbus bridge in
`data/openems-config/Bridge/Modbus/Tcp/*.config` have to match — otherwise OpenEMS reports
"Modbus Communication failed". By now 5020 is also the default in the simulator itself and in the
Dockerfile; the entries in the compose file are therefore only a confirmation.

When connecting **real** IDM hardware, the port has to be set to `502` explicitly (compose,
environment and bridge config).
