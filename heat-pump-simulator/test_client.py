from __future__ import annotations

import argparse
import asyncio
import os

from pymodbus.client import AsyncModbusTcpClient

from sim.codec import decode, encode
from sim.model import Register, RegisterMap
from sim.profile import HeatPumpProfile
from sim.profiles import get_profile
from sim.server import registers_with_synthetic


class HeatPumpTester:
    def __init__(self, client: AsyncModbusTcpClient, registers: RegisterMap, profile: HeatPumpProfile):
        self._client = client
        self._registers = registers
        self._profile = profile

    async def read(self, address: int) -> str:
        register = self._registers.get(address)
        if register is None:
            return f"{address}: unbekannte Adresse"
        if not register.readable:
            return f"{self._label(register)} ist nicht lesbar (nur schreibbar)"
        response = await self._read_registers(register)
        if response.isError():
            return f"{self._label(register)} -> Fehler ({response})"
        value = decode(register, response.registers, self._profile)
        return f"{self._label(register)} = {value} {register.unit}".rstrip()

    async def write(self, address: int, value: float) -> str:
        register = self._registers.get(address)
        if register is None:
            return f"{address}: unbekannte Adresse"
        if not register.writable:
            return f"{self._label(register)} ist nicht schreibbar"
        registers = encode(register, value, self._profile)
        response = await self._client.write_registers(address=address, values=registers)
        if response.isError():
            return f"{self._label(register)} -> Schreibfehler ({response})"
        return await self.read(address)

    def lines(self, needle: str = "") -> list[str]:
        needle = needle.lower()
        return [
            self._label(register)
            for register in self._registers
            if not needle or needle in register.name.lower() or needle in str(register.address)
        ]

    async def read_all(self) -> list[str]:
        return [await self.read(register.address) for register in self._registers if register.readable]

    async def _read_registers(self, register: Register):
        if register.readable and not register.writable:
            return await self._client.read_input_registers(address=register.address, count=register.width)
        return await self._client.read_holding_registers(address=register.address, count=register.width)

    @staticmethod
    def _label(register: Register) -> str:
        return f"{register.address:>4} [{register.datatype} {register.access}] {register.name}"


HELP = """Befehle:
  read  <addr>          Adresse lesen (Alias: r)
  write <addr> <wert>   Adresse schreiben (Alias: w)
  list  [text]          Register auflisten, optional gefiltert (Alias: l)
  readall               alle lesbaren Register lesen
  help                  diese Hilfe
  quit                  beenden (Alias: q, exit)"""


async def run_command(tester: HeatPumpTester, parts: list[str]) -> bool:
    command = parts[0].lower()
    try:
        if command in ("read", "r"):
            print(await tester.read(int(parts[1])))
        elif command in ("write", "w"):
            print(await tester.write(int(parts[1]), float(parts[2])))
        elif command in ("list", "l"):
            print("\n".join(tester.lines(parts[1] if len(parts) > 1 else "")))
        elif command == "readall":
            print("\n".join(await tester.read_all()))
        elif command in ("help", "?"):
            print(HELP)
        elif command in ("quit", "q", "exit"):
            return False
        else:
            print(f"unbekannter Befehl: {command} (help für Hilfe)")
    except (IndexError, ValueError):
        print("ungültige Argumente (help für Hilfe)")
    return True


async def repl(tester: HeatPumpTester) -> None:
    print(HELP)
    while True:
        try:
            line = input("> ").strip()
        except EOFError:
            break
        if not line:
            continue
        if not await run_command(tester, line.split()):
            break


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manueller Test-Client für den Wärmepumpen-Simulator")
    parser.add_argument("--host", default=os.getenv("MODBUS_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MODBUS_PORT", "5020")))
    parser.add_argument("--profile", default=os.getenv("HEATPUMP_PROFILE", "idm"))
    parser.add_argument("command", nargs="*", help="einmaliger Befehl, sonst interaktiv")
    return parser.parse_args()


async def main() -> None:
    args = parse_args()
    profile = get_profile(args.profile)
    client = AsyncModbusTcpClient(args.host, port=args.port)
    await client.connect()
    if not client.connected:
        print(f"keine Verbindung zu {args.host}:{args.port}")
        return
    tester = HeatPumpTester(client, registers_with_synthetic(profile), profile)
    try:
        if args.command:
            await run_command(tester, args.command)
        else:
            await repl(tester)
    finally:
        client.close()


if __name__ == "__main__":
    asyncio.run(main())
