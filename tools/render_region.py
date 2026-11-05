"""Render generated dev-world chunks to PNGs for checking worldgen without a client.

    python tools/render_region.py <dimension dir> <x0> <z0> <x1> <z1> [slice_z]

<dimension dir> is the folder holding region/, which in 26.x is
run/world/dimensions/minecraft/overworld.

Writes run/render_top.png (top-down, highest block, shaded by height) and, with
slice_z, run/render_slice.png (a vertical cut through the world at that z).
"""
import io
import math
import pathlib
import struct
import sys
import zlib

from PIL import Image


# ---- minimal NBT reader
def read_nbt(buf):
    def rd(fmt):
        nonlocal pos
        v = struct.unpack_from(fmt, buf, pos)
        pos += struct.calcsize(fmt)
        return v[0]

    def rstr():
        n = rd(">H")
        nonlocal pos
        s = buf[pos:pos + n].decode("utf-8", "replace")
        pos += n
        return s

    def payload(t):
        nonlocal pos
        if t == 1: return rd(">b")
        if t == 2: return rd(">h")
        if t == 3: return rd(">i")
        if t == 4: return rd(">q")
        if t == 5: return rd(">f")
        if t == 6: return rd(">d")
        if t == 7:
            n = rd(">i"); v = buf[pos:pos + n]; pos += n; return v
        if t == 8: return rstr()
        if t == 9:
            et = rd(">b"); n = rd(">i"); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = rd(">b")
                if tt == 0: return d
                name = rstr()
                d[name] = payload(tt)
        if t == 11:
            n = rd(">i"); v = struct.unpack_from(f">{n}i", buf, pos); pos += 4 * n; return v
        if t == 12:
            n = rd(">i"); v = struct.unpack_from(f">{n}q", buf, pos); pos += 8 * n; return v
        raise ValueError(t)

    pos = 0
    t = rd(">b"); rstr()
    return payload(t)


def chunks_in_region(path):
    data = path.read_bytes()
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], "big")
        if off == 0: continue
        start = off * 4096
        length = struct.unpack_from(">I", data, start)[0]
        comp = data[start + 4]
        raw = data[start + 5:start + 4 + length]
        yield read_nbt(zlib.decompress(raw) if comp == 2 else raw)


BIOME_COLORS = {
    "pandorawilds:pandora": (40, 180, 155), "pandorawilds:pandora_basin": (30, 150, 205),
    "pandorawilds:pandora_spires": (215, 135, 55), "pandorawilds:pandora_highlands": (130, 230, 205),
    "pandorawilds:pandora_hollows": (150, 90, 210),
}


def section_biomes(sec):
    """4x4x4 biome cells of a section, as a flat list of 64 names (y*16 + z*4 + x)."""
    bs = sec.get("biomes")
    if not bs:
        return None
    pal = bs["palette"]
    if len(pal) == 1 or "data" not in bs:
        return [pal[0]] * 64
    bits = max(1, math.ceil(math.log2(len(pal))))
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for word in bs["data"]:
        wv = word & 0xFFFFFFFFFFFFFFFF
        for k in range(per):
            if len(out) == 64:
                break
            out.append(pal[(wv >> (k * bits)) & mask])
    return out


def palette_name(p):
    """Block id from a palette entry. 26.x writes bare strings (wrapped as {"": "minecraft:stone"}
    inside a mixed-type list) and {"id": ..., "properties": ...} for blocks with properties.
    Older versions wrote {"Name": ..., "Properties": ...}."""
    if isinstance(p, dict):
        p = p.get("id") or p.get("Name") or p[""]
    return p.split("[", 1)[0]


def section_blocks(sec):
    bs = sec.get("block_states")
    if not bs: return None
    pal = [palette_name(p) for p in bs["palette"]]
    if len(pal) == 1 or "data" not in bs:
        return [pal[0]] * 4096
    bits = max(4, math.ceil(math.log2(len(pal))))
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for word in bs["data"]:
        w = word & 0xFFFFFFFFFFFFFFFF
        for k in range(per):
            if len(out) == 4096: break
            out.append(pal[(w >> (k * bits)) & mask])
    return out


COLORS = {
    "pandorawilds:lumen_moss": (40, 150, 130), "pandorawilds:pandora_leaves": (30, 120, 105),
    "pandorawilds:pandora_log": (70, 85, 120), "pandorawilds:lumen_bulb": (110, 200, 255),
    "pandorawilds:glowfern": (60, 170, 150), "pandorawilds:lumen_bloom": (170, 80, 230),
    "pandorawilds:helicoradian": (230, 110, 40), "pandorawilds:lumen_vine": (80, 170, 220), "pandorawilds:giant_vine": (235, 90, 90),
    "minecraft:water": (40, 90, 200), "minecraft:grass_block": (90, 150, 60), "minecraft:stone": (125, 125, 125),
    "minecraft:andesite": (140, 140, 140), "minecraft:tuff": (128, 130, 108), "minecraft:moss_block": (90, 130, 50),
    "minecraft:dirt": (120, 85, 60), "minecraft:sand": (220, 210, 160), "minecraft:snow_block": (240, 240, 250),
    "minecraft:snow": (240, 240, 250), "minecraft:short_grass": (90, 150, 60),
    "minecraft:light_gray_concrete": (205, 205, 205), "minecraft:gray_concrete": (85, 85, 90),
    "minecraft:smooth_stone": (160, 160, 160), "minecraft:iron_block": (225, 225, 235),
    "minecraft:iron_bars": (150, 155, 165), "minecraft:yellow_concrete": (240, 205, 40),
    "minecraft:copper_block": (200, 115, 70), "minecraft:coarse_dirt": (120, 90, 62),
    "minecraft:gravel": (135, 130, 125), "minecraft:packed_mud": (140, 105, 80),
    "minecraft:glass_pane": (190, 225, 235), "minecraft:lantern": (255, 200, 120),
    "minecraft:polished_andesite": (145, 145, 145), "minecraft:smooth_stone_slab": (175, 175, 175),
    "minecraft:calcite": (228, 228, 222), "minecraft:amethyst_block": (150, 100, 210), "minecraft:amethyst_cluster": (200, 160, 250), "grandatlas:selenite_block": (240, 232, 214), "minecraft:diorite": (188, 186, 186),
    "minecraft:red_sand": (190, 102, 33), "minecraft:terracotta": (152, 94, 67),
    "minecraft:orange_terracotta": (161, 83, 37), "minecraft:white_terracotta": (209, 178, 161),
    "minecraft:coarse_dirt": (119, 85, 59), "minecraft:sandstone": (216, 203, 155),
    # the deep (M7)
    "minecraft:deepslate": (72, 72, 78), "minecraft:blackstone": (38, 32, 40), "minecraft:basalt": (96, 96, 104),
    "minecraft:bedrock": (15, 15, 15), "minecraft:lava": (230, 100, 20), "minecraft:sculk": (12, 52, 66),
    "minecraft:sculk_vein": (20, 70, 85), "minecraft:gray_wool": (150, 150, 150), "minecraft:deepslate_bricks": (95, 95, 100),
    "minecraft:deepslate_tiles": (85, 85, 92), "minecraft:polished_deepslate": (88, 88, 95),
}
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def color(name):
    if name in COLORS: return COLORS[name]
    if "leaves" in name: return (50, 110, 50)
    if "log" in name: return (100, 80, 50)
    h = hash(name) & 0xFFFFFF
    return (90 + (h & 63), 90 + ((h >> 8) & 63), 90 + ((h >> 16) & 63))


def main():
    world = pathlib.Path(sys.argv[1])
    x0, z0, x1, z1 = map(int, sys.argv[2:6])
    slice_z = int(sys.argv[6]) if len(sys.argv) > 6 else None
    cols = {}   # (x,z) -> list of (y, name) top-down
    slice_cols = {}
    biomes = {}
    for rx in range(x0 >> 9, (x1 >> 9) + 1):
        for rz in range(z0 >> 9, (z1 >> 9) + 1):
            p = world / "region" / f"r.{rx}.{rz}.mca"
            if not p.exists(): continue
            for ch in chunks_in_region(p):
                if ch.get("Status") != "minecraft:full": continue
                cx, cz = ch["xPos"] * 16, ch["zPos"] * 16
                if cx + 15 < x0 or cx > x1 or cz + 15 < z0 or cz > z1: continue
                secs = sorted((s for s in ch["sections"] if s.get("block_states")), key=lambda s: -s["Y"])
                decoded = [(s["Y"], section_blocks(s)) for s in secs]
                bio = {s["Y"]: section_biomes(s) for s in ch["sections"]}
                for lx in range(16):
                    for lz in range(16):
                        x, z = cx + lx, cz + lz
                        col = []
                        for sy, blocks in decoded:
                            if blocks is None: continue
                            for ly in range(15, -1, -1):
                                n = blocks[ly * 256 + lz * 16 + lx]
                                if n not in AIR: col.append((sy * 16 + ly, n))
                        cols[(x, z)] = col
                        if col:
                            sy, ly = divmod(col[0][0], 16)
                            cells = bio.get(sy)
                            if cells:
                                biomes[(x, z)] = cells[(ly // 4) * 16 + ((z & 15) // 4) * 4 + ((x & 15) // 4)]
                        if slice_z is not None and z == slice_z and x0 <= x <= x1: slice_cols[x] = col
    w, h = x1 - x0 + 1, z1 - z0 + 1
    top = Image.new("RGB", (w, h), (0, 0, 0))
    ys = [c[0][0] for c in cols.values() if c]
    lo, hi = (min(ys), max(ys)) if ys else (0, 1)
    for (x, z), col in cols.items():
        if not col or not (x0 <= x <= x1 and z0 <= z <= z1): continue
        y, n = col[0]
        shade = 0.55 + 0.45 * (y - lo) / max(1, hi - lo)
        top.putpixel((x - x0, z - z0), tuple(int(c * shade) for c in color(n)))
    out = pathlib.Path(__file__).resolve().parent.parent / "run"
    top.resize((w * 2, h * 2), Image.NEAREST).save(out / "render_top.png")
    if biomes:
        bmap = Image.new("RGB", (w, h), (0, 0, 0))
        for (x, z), name in biomes.items():
            if not (x0 <= x <= x1 and z0 <= z <= z1):
                continue
            bmap.putpixel((x - x0, z - z0), BIOME_COLORS.get(name, (60, 64, 70)))
        bmap.resize((w * 2, h * 2), Image.NEAREST).save(out / "render_biomes.png")
        print("biomes:", {n: sum(1 for v in biomes.values() if v == n) for n in set(biomes.values())
                          if sum(1 for v in biomes.values() if v == n) > 200})
    print(f"top: {len(cols)} columns, surface y {lo}..{hi}")
    if slice_z is not None and slice_cols:
        ymin = min(y for c in slice_cols.values() for y, _ in c)
        ymax = max(y for c in slice_cols.values() for y, _ in c)
        sl = Image.new("RGB", (w, ymax - ymin + 1), (150, 200, 235))
        for x, col in slice_cols.items():
            for y, n in col:
                sl.putpixel((x - x0, ymax - y), color(n))
        sl.resize((w * 2, (ymax - ymin + 1) * 2), Image.NEAREST).save(out / "render_slice.png")
        print(f"slice z={slice_z}: y {ymin}..{ymax}")


main()
