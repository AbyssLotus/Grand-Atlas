"""Bakes the user's painted Domus Mortus layout into mc1211/src/main/resources/data/grandatlas/domus/layout.bin.

The user paints the realm in biomes in a flat world (the save "Domus Mortus"), each biome standing for a
place (see mortus/MortusMap). This reads the biome at the surface of every 4x4 column of that save and
writes 640 x 640 cell codes (gzip), centred on 0,0. Run after repainting:

    python tools/domus/bake_layout.py "<path to the save>"
"""
import gzip
import io
import math
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path

SAVE = Path(sys.argv[1] if len(sys.argv) > 1 else Path.home() / "AppData" / "Roaming" / ".minecraft" / "saves" / "Domus Mortus")
CODES = {"minecraft:plains": 0, "minecraft:river": 1, "minecraft:jagged_peaks": 2, "minecraft:mangrove_swamp": 3,
         "minecraft:ocean": 4, "minecraft:cherry_grove": 5, "minecraft:basalt_deltas": 6, "minecraft:frozen_river": 7}
N, OFF = 640, 320


def nbt(buf):
    def rd(t):
        if t == 1: return struct.unpack(">b", buf.read(1))[0]
        if t == 2: return struct.unpack(">h", buf.read(2))[0]
        if t == 3: return struct.unpack(">i", buf.read(4))[0]
        if t == 4: return struct.unpack(">q", buf.read(8))[0]
        if t == 5: return struct.unpack(">f", buf.read(4))[0]
        if t == 6: return struct.unpack(">d", buf.read(8))[0]
        if t == 7: return buf.read(struct.unpack(">i", buf.read(4))[0])
        if t == 8: return buf.read(struct.unpack(">H", buf.read(2))[0]).decode("utf-8", "replace")
        if t == 9:
            et = buf.read(1)[0]
            return [rd(et) for _ in range(struct.unpack(">i", buf.read(4))[0])]
        if t == 10:
            d = {}
            while True:
                ct = buf.read(1)[0]
                if ct == 0: return d
                name = buf.read(struct.unpack(">H", buf.read(2))[0]).decode("utf-8", "replace")
                d[name] = rd(ct)
        if t == 11:
            n = struct.unpack(">i", buf.read(4))[0]
            return list(struct.unpack(">%di" % n, buf.read(4 * n)))
        if t == 12:
            n = struct.unpack(">i", buf.read(4))[0]
            return list(struct.unpack(">%dq" % n, buf.read(8 * n)))
        raise ValueError(t)
    t = buf.read(1)[0]
    buf.read(struct.unpack(">H", buf.read(2))[0])
    return rd(t)


def unpack(data, bits, count):
    out, per, mask = [], 64 // bits, (1 << bits) - 1
    for v in data:
        v &= (1 << 64) - 1
        for k in range(per):
            out.append((v >> (k * bits)) & mask)
            if len(out) == count: return out
    return out


biome_at = {}
for f in sorted((SAVE / "region").glob("r.*.mca")):
    raw = f.read_bytes()
    for i in range(1024):
        off = int.from_bytes(raw[i * 4:i * 4 + 3], "big") * 4096
        if off == 0: continue
        ln, comp = int.from_bytes(raw[off:off + 4], "big"), raw[off + 4]
        try:
            c = nbt(io.BytesIO(zlib.decompress(raw[off + 5:off + 4 + ln]) if comp == 2 else raw[off + 5:off + 4 + ln]))
        except Exception:
            continue
        cx, cz = c["xPos"], c["zPos"]
        secs = {s["Y"]: s for s in c.get("sections", []) if "biomes" in s}
        if not secs: continue
        s = secs.get(4) or secs[max(secs)]  # the painted surface (a flat world at y 64)
        pal = s["biomes"]["palette"]
        idx = [0] * 64 if len(pal) == 1 else unpack(s["biomes"]["data"], max(1, (len(pal) - 1).bit_length()), 64)
        for qz in range(4):
            for qx in range(4):
                biome_at[(cx * 4 + qx, cz * 4 + qz)] = pal[idx[qz * 4 + qx]]

grid = bytearray(N * N)
for qz in range(-OFF, OFF):
    for qx in range(-OFF, OFF):
        c = CODES.get(biome_at.get((qx, qz)), 1)
        if c == 4 and math.hypot(qx * 4 + 2, qz * 4 + 2) > 1110:
            c = 1  # stray ocean outside the mountain ring is the outer sea
        grid[(qz + OFF) * N + (qx + OFF)] = c
out = Path(__file__).resolve().parents[2] / "mc1211" / "src" / "main" / "resources" / "data" / "grandatlas" / "domus" / "layout.bin"
out.write_bytes(gzip.compress(bytes(grid), 9))
print(out, dict(Counter(grid)))
