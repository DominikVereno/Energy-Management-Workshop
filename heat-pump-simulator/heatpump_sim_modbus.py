from __future__ import annotations

import argparse
import asyncio
import logging
import os
import sys

from sim.physics.clock.clock_register import DEFAULT_CLOCK_FACTOR
from sim.profiles import get_profile
from sim.server import build_simulation


def configure_logging() -> None:
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        force=True,
        stream=sys.stdout,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Heat pump Modbus TCP simulator")
    parser.add_argument("--host", default=os.getenv("MODBUS_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MODBUS_PORT", "5020")))
    parser.add_argument("--profile", default=os.getenv("HEATPUMP_PROFILE", "idm"))
    parser.add_argument("--factor", type=int, default=int(os.getenv("REAL_TIME_FACTOR", str(DEFAULT_CLOCK_FACTOR))))
    return parser.parse_args()


async def serve(host: str, port: int, profile_name: str, factor: int) -> None:
    simulation = build_simulation(get_profile(profile_name), host, port, factor)
    await asyncio.gather(
        simulation.server.serve_forever(),
        simulation.loop.run(),
    )


def main() -> None:
    configure_logging()
    args = parse_args()
    try:
        asyncio.run(serve(args.host, args.port, args.profile, args.factor))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
