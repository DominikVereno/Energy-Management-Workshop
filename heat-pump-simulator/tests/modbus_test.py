import asyncio
import logging
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
logging.disable(logging.CRITICAL)

from pymodbus.client import AsyncModbusTcpClient

from sim.codec import decode, encode
from sim.profiles import get_profile
from sim.server import build_simulation


HOST = "127.0.0.1"
PORT = 5555

_failures = 0


def check(name, condition):
    global _failures
    status = "ok" if condition else "FAIL"
    if not condition:
        _failures += 1
    print(f"[{status}] {name}")


async def run() -> None:
    profile = get_profile("idm")
    registers = profile.load_registers()
    server = build_simulation(profile, HOST, PORT).server
    server_task = asyncio.create_task(server.serve_forever())
    await asyncio.sleep(0.5)

    client = AsyncModbusTcpClient(HOST, port=PORT)
    await client.connect()

    ro_float = registers.get(1000)
    read = await client.read_input_registers(address=1000, count=ro_float.width)
    check("read FLOAT RO 1000 via FC4", not read.isError())
    check("decoded value matches seed", decode(ro_float, read.registers, profile) == ro_float.initial_value())

    rw_float = registers.get(1401)
    write = await client.write_registers(address=1401, values=encode(rw_float, 23.5, profile))
    check("write FLOAT RW 1401 via FC16", not write.isError())
    back = await client.read_holding_registers(address=1401, count=rw_float.width)
    check("read back written value", decode(rw_float, back.registers, profile) == 23.5)

    rejected = await client.write_registers(address=1000, values=[0, 0])
    check("write to RO 1000 rejected", rejected.isError())

    write_only = await client.read_holding_registers(address=1999, count=1)
    check("read of W-only 1999 rejected", write_only.isError())

    undefined = await client.read_input_registers(address=999, count=1)
    check("read of undefined 999 rejected", undefined.isError())

    negative_word = next(r for r in registers if r.datatype == "WORD" and r.writable)
    await client.write_registers(address=negative_word.address, values=encode(negative_word, -25, profile))
    neg = await client.read_holding_registers(address=negative_word.address, count=1)
    check("signed WORD round-trip (-25)", decode(negative_word, neg.registers, profile) == -25)

    # A master may only write values the register is specified for. Without this
    # an out-of-range setpoint took effect silently, and a NaN killed the physics.
    setpoint = registers.get(1449)  # UCHAR, 20..90 °C
    await client.write_registers(address=1449, values=encode(setpoint, 45, profile))
    too_high = await client.write_registers(address=1449, values=[200])
    check("write above the register maximum rejected", too_high.isError())
    too_low = await client.write_registers(address=1449, values=[5])
    check("write below the register minimum rejected", too_low.isError())
    unchanged = await client.read_holding_registers(address=1449, count=1)
    check("rejected write left the register untouched", unchanged.registers[0] == 45)

    slope = registers.get(1429)  # FLOAT, 0,1..3,5
    nan_write = await client.write_registers(address=1429, values=encode(slope, float("nan"), profile))
    check("write of NaN rejected", nan_write.isError())
    infinite = await client.write_registers(address=1429, values=encode(slope, float("inf"), profile))
    check("write of infinity rejected", infinite.isError())

    boolean = registers.get(1710)  # BOOL
    not_boolean = await client.write_registers(address=1710, values=[5])
    check("write of 5 into a BOOL rejected", not_boolean.isError())

    # Writes have to cover whole registers: half a FLOAT is a value nobody wrote.
    await client.write_registers(address=1401, values=encode(rw_float, 23.5, profile))
    half = await client.write_registers(address=1402, values=[0xFFFF])
    check("write into the second half of a FLOAT rejected", half.isError())
    truncated = await client.write_registers(address=1401, values=[0])
    check("write of one word into a FLOAT rejected", truncated.isError())
    intact = await client.read_holding_registers(address=1401, count=rw_float.width)
    check("the FLOAT survived both attempts", decode(rw_float, intact.registers, profile) == 23.5)

    # A block covering several whole registers stays legal.
    block = await client.write_registers(address=1449, values=[40, 40])
    check("block write over two whole registers accepted", not block.isError())

    client.close()
    await server.shutdown()
    server_task.cancel()


def main() -> None:
    asyncio.run(run())
    if _failures:
        print(f"\n{_failures} check(s) failed")
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
