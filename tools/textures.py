"""Paint Grand Atlas's block textures (16x16, vanilla style) from code, so they can be redrawn.

    python tools/textures.py            (writes src/main/resources/assets/grandatlas/textures/block/*.png,
                                         each with its LabPBR specular map, name_s.png)
    python tools/textures.py --sheet    (also writes build/texture_sheet.png, an 8x contact sheet)

The bar (the user, 2026-11-04): "vanilla plus", or as good as Obscuria's work. So each texture
keeps to a small palette (4-7 tones), shades in clusters rather than per-pixel noise, has no
outlines, and plants keep a clear silhouette, as vanilla's do.
"""
import pathlib
import random
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "grandatlas" / "textures" / "block"
N = 16


def img():
    return Image.new("RGBA", (N, N), (0, 0, 0, 0))


def put(im, x, y, c, a=255):
    if 0 <= x < N and 0 <= y < N:
        im.putpixel((x, y), tuple(c[:3]) + (a,))


def value_noise(seed, scale):
    """Smooth tileable value noise on the 16x16 grid, 0..1."""
    rnd = random.Random(seed)
    g = max(1, N // scale)
    grid = [[rnd.random() for _ in range(g)] for _ in range(g)]

    def at(x, y):
        fx, fy = x / scale, y / scale
        x0, y0 = int(fx) % g, int(fy) % g
        x1, y1 = (x0 + 1) % g, (y0 + 1) % g
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
        b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
        return a * (1 - ty) + b * ty
    return at


def ramp(colors, v):
    v = max(0.0, min(0.999, v))
    return colors[int(v * len(colors))]


# ---- blocks

def blade(im, bx, by, angle, length, half, pal, tip=None, alpha=255):
    """A tapered crystal (or icicle) from a base at (bx, by) along angle (radians, 0 = up, +
    leaning right): lit on its left face, shaded on its right, a highlight up the axis, a bright
    point. pal is [dark, mid, lit, highlight]."""
    import math
    ux, uy = math.sin(angle), -math.cos(angle)
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - bx, y + 0.5 - by
            along = px * ux + py * uy
            if along < -0.5 or along > length:
                continue
            across = px * -uy + py * ux  # + is the right-hand face
            w = half * (1 - along / length) ** 0.85 + 0.35
            if abs(across) > w:
                continue
            if along > length - 1.2 and tip:
                c = tip
            elif abs(across) < 0.5:
                c = pal[3]
            elif across < 0:
                c = pal[2]
            else:
                c = pal[1] if across < w - 0.8 else pal[0]
            put(im, x, y, c, alpha)


def selenite_block():
    """Fibrous gypsum: pale and translucent, long fibres leaning one way, bright seams, a faint inclusion."""
    pal = [(178, 169, 150), (200, 192, 173), (219, 212, 194), (235, 230, 216), (248, 246, 238)]
    im = img()
    rnd = random.Random(11)
    fib = [rnd.random() for _ in range(N)]
    n = value_noise(12, 4)
    for y in range(N):
        for x in range(N):
            col = (x + y // 4) % N
            v = 0.5 * fib[col] + 0.35 * n(x, y) + 0.15 * fib[(col + 5) % N]
            put(im, x, y, ramp(pal, v), 200 + int(45 * v))
    for sx, c in ((3, pal[4]), (11, pal[4]), (7, pal[0])):
        for y in range(N):
            if c == pal[0] and not 5 <= y <= 12:
                continue
            put(im, (sx + y // 4) % N, y, c, 245)
    return im

def selenite_shard():
    """Crystal blades fanning up from the base, like gypsum swords: faceted, pale, bright-tipped."""
    pal = [(176, 166, 146), (206, 198, 178), (236, 231, 216), (252, 250, 243)]
    tip = (255, 255, 252)
    im = img()
    for bx, by, ang, length, half in ((8.0, 16.2, 0.05, 15, 1.7), (5.2, 16.2, -0.42, 11, 1.4), (11.2, 16.2, 0.36, 10, 1.3),
                                      (2.8, 16.2, -0.75, 6, 1.0), (13.6, 16.2, 0.7, 6, 1.0)):
        blade(im, bx, by, ang, length, half, pal, tip)
    return im

def glowworm_silk(tip):
    """Hanging threads beaded with glowing droplets, as glowworms spin them; the tip ends in big drops."""
    thread = (214, 222, 228)
    rim, glow, core = (54, 160, 180), (110, 226, 222), (214, 255, 249)
    im = img()
    strands = [(4, 1, 13), (8, 3, 15), (11, 0, 10)]  # (x, first bead row, where a tip's strand ends)
    for sx, first, stop in strands:
        bottom = stop if tip else 16
        kink = (sx * 5) % 11 + 2  # one small kink, not a zigzag
        for y in range(bottom):
            x = sx + (1 if y >= kink else 0)
            put(im, x, y, thread, 200)
        for b in range(first, bottom - (2 if tip else 0), 5):
            x = sx + (1 if b >= kink else 0)
            put(im, x, b, core)
            put(im, x, b + 1, glow)
        if tip:
            x = sx + (1 if bottom - 1 >= kink else 0)
            for dx, dy, c in ((0, -1, glow), (-1, 0, glow), (0, 0, core), (1, 0, glow), (0, 1, core), (-1, 1, rim), (1, 1, rim), (0, 2, glow)):
                put(im, x + dx, bottom - 1 + dy, c)
    return im

def glowcap():
    """A small glowing mushroom: pale stem, domed teal cap with bright spots, dark gills."""
    stem_l, stem_d = (226, 222, 206), (186, 180, 162)
    cap = [(36, 104, 122), (58, 156, 168), (98, 208, 206), (176, 246, 236), (232, 255, 250)]
    im = img()
    for y in range(9, 16):
        put(im, 7, y, stem_l)
        put(im, 8, y, stem_d)
    rows = [(5, 10, 4), (4, 11, 5), (3, 12, 6), (3, 12, 7), (4, 11, 8)]  # (x0, x1, y)
    for x0, x1, y in rows:
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            v = 0.75 - 0.5 * t + (0.15 if y <= 5 else 0)
            put(im, x, y, ramp(cap, v))
    for x in range(4, 12):
        put(im, x, 8, cap[0])  # gills under the rim
    for x, y in ((6, 5), (9, 6), (5, 7)):
        put(im, x, y, cap[4])
    return im


def glowcap_block(variant=0):
    """A giant glowcap's cap: an even teal with faint mottling and a few darker freckles, pale spots
    spread over the face but kept clear of the edges. Nothing strong touches an edge and nothing
    gathers in the middle, so blocks turned every which way neither show seams (the user: "too
    many 90 degree turns on a large wall") nor a framed grid; three variants, picked and turned
    at random per block."""
    cap = [(40, 112, 128), (56, 146, 158), (70, 170, 178), (86, 190, 194)]
    ring, spot = (156, 234, 224), (218, 252, 244)
    im = img()
    n = value_noise(31 + variant * 5, 3)
    for y in range(N):
        for x in range(N):
            put(im, x, y, ramp(cap, 0.42 + 0.3 * (n(x, y) - 0.5)))
    rnd = random.Random(300 + variant)
    for _ in range(5):  # freckles, anywhere
        put(im, rnd.randrange(N), rnd.randrange(N), cap[0])
    spots = []
    tries = 0
    while len(spots) < 5 + variant % 2 and tries < 800:
        tries += 1
        r = rnd.choice((0.8, 1.1, 1.5, 1.9))
        lo, hi = r + 0.9, N - r - 0.9  # whole spot and ring inside the block, close to the edge allowed
        cx, cy = rnd.uniform(lo, hi), rnd.uniform(lo, hi)
        if all((cx - sx) ** 2 + (cy - sy) ** 2 > (r + sr + 1.6) ** 2 for sx, sy, sr in spots):
            spots.append((cx, cy, r))
    for cx, cy, r in spots:
        for y in range(N):
            for x in range(N):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                if d < r - 0.6:
                    put(im, x, y, spot)
                elif d < r + 0.25:
                    put(im, x, y, ring)
    return im

def icicle(part):
    """Hanging ice in the pointed-dripstone shape (base, middle, tip): clear, lit on the left,
    a pale streak down its length, little bubbles caught in it."""
    import math
    pal = [(118, 158, 214), (150, 190, 236), (192, 222, 250), (232, 244, 255)]
    im = img()
    rnd = random.Random({"base": 71, "middle": 72, "tip": 73}[part])
    if part == "base":
        tops, bottoms, length = 4.6, 3.0, 16
    elif part == "middle":
        tops, bottoms, length = 3.0, 2.3, 16
    else:
        tops, bottoms, length = 2.3, 0.0, 14
    for y in range(length):
        t_ = y / length
        w = tops + (bottoms - tops) * (t_ if part != "tip" else t_ ** 0.8)
        w += 0.35 * math.sin(y * 1.3 + len(part))  # a little unevenness down its length
        if part == "tip":
            w = max(w, 0.45)  # the point stays joined to the rest
        for x in range(N):
            across = x + 0.5 - 8
            if abs(across) > w + 0.15:
                continue
            if part == "tip" and y >= length - 1:
                c = pal[3]
            elif -0.9 < across < 0.1:
                c = pal[3]
            elif across < 0:
                c = pal[2]
            else:
                c = pal[1] if across < w - 0.9 else pal[0]
            put(im, x, y, c, 210)
    for _ in range(3):  # bubbles, kept out of the thin point
        y = rnd.randrange(1, length - 6 if part == "tip" else length - 2)
        put(im, 8 + rnd.choice((-1, 1)), y, pal[3], 225)
    return im

def hoarfrost(variant=0):
    """Packed ice furred with frost. Feathers grow from a few seeds in random directions and fade,
    so tiled over a wall it shows no grid (the user: "too repetitive when placed in large amounts");
    three variants, picked and turned at random per block."""
    ice = [(122, 162, 224), (136, 176, 232), (150, 189, 240)]
    frost = [(176, 204, 244), (204, 224, 250), (226, 238, 253)]
    im = img()
    n = value_noise(41 + variant * 7, 4)
    for y in range(N):
        for x in range(N):
            put(im, x, y, ramp(ice, n(x, y)))
    rnd = random.Random(400 + variant)
    dirs = [(1, 0), (0, 1), (-1, 0), (0, -1), (1, 1), (-1, 1), (1, -1), (-1, -1)]
    for _ in range(4 + variant % 2):
        x, y = rnd.randrange(N), rnd.randrange(N)
        dx, dy = rnd.choice(dirs)
        for k in range(rnd.randint(4, 7)):
            level = 2 if k < 2 else 1 if k < 4 else 0
            put(im, x % N, y % N, frost[level])
            if k % 2 == 1 and rnd.random() < 0.7:  # a side barb
                bx, by = rnd.choice(((dy, -dx), (-dy, dx)))
                put(im, (x + bx) % N, (y + by) % N, frost[max(0, level - 1)])
            if rnd.random() < 0.25:
                dx, dy = rnd.choice(dirs)  # frost wanders
            x += dx
            y += dy
    for _ in range(2):
        put(im, rnd.randrange(N), rnd.randrange(N), (255, 255, 255))
    return im

def abyssal_sand():
    """Black shore sand: slate grains in soft clusters, a few cold glints."""
    pal = [(36, 40, 50), (48, 53, 64), (60, 66, 78), (76, 82, 95)]
    glint = (112, 190, 204)
    im = img()
    n = value_noise(51, 2)
    rnd = random.Random(52)
    for y in range(N):
        for x in range(N):
            v = 0.7 * n(x, y) + 0.3 * rnd.random()
            put(im, x, y, ramp(pal, v))
    for x, y in ((3, 4), (12, 2), (9, 11), (1, 13), (14, 14)):
        put(im, x, y, glint)
    return im


def cinder_moss(variant=0):
    """Moss that grows by the lava: dark red-black clumps, embers scattered unevenly (some lone
    sparks, some small glowing clusters) so a carpet of it shows no grid; three variants, picked
    and turned at random per block."""
    pal = [(34, 16, 15), (52, 24, 19), (74, 31, 21), (100, 40, 22)]
    ember, hot = (226, 104, 30), (255, 194, 88)
    im = img()
    n = value_noise(61 + variant * 3, 3)
    m = value_noise(62 + variant * 3, 2)
    for y in range(N):
        for x in range(N):
            put(im, x, y, ramp(pal, 0.65 * n(x, y) + 0.35 * m(x, y)))
    rnd = random.Random(600 + variant)
    for _ in range(3 + variant % 2):  # lone sparks
        put(im, rnd.randrange(N), rnd.randrange(N), ember)
    for _ in range(2 + (variant + 1) % 2):  # small clusters, each its own shape
        x, y = rnd.randrange(N), rnd.randrange(N)
        put(im, x, y, hot)
        for _ in range(rnd.randint(1, 3)):
            dx, dy = rnd.choice(((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)))
            put(im, (x + dx) % N, (y + dy) % N, ember)
    return im

def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255


def teal_glow(c):
    """The teal glow colour of droplets, caps and spots: strong green-blue, bright."""
    return c[1] > 150 and c[2] > 150 and c[1] > c[0] + 40


def ember(c):
    return c[0] > 200 and c[1] > 90 and c[2] < 120


# per texture: (smoothness, F0 byte, porosity/SSS byte, emission rule)
MATERIALS = {
    "selenite_block": (0.86, 11, 140, None),
    "selenite_shard": (0.88, 11, 150, lambda c: 60 if lum(c) > 0.95 else 255),
    "glowworm_silk": (0.55, 10, 110, lambda c: 210 if teal_glow(c) and lum(c) > 0.8 else 150 if teal_glow(c) else 255),
    "glowworm_silk_plant": (0.55, 10, 110, lambda c: 210 if teal_glow(c) and lum(c) > 0.8 else 150 if teal_glow(c) else 255),
    "glowcap": (0.42, 10, 150, lambda c: 200 if teal_glow(c) else 255),
    "glowcap_block": (0.36, 10, 140, lambda c: 220 if lum(c) > 0.85 else 90),
    "icicle": (0.93, 5, 210, None),
    "hoarfrost": (0.8, 5, 190, None),
    "abyssal_sand": (0.12, 10, 60, None),
    "cinder_moss": (0.08, 9, 50, lambda c: 230 if lum(c) > 0.6 else 170 if ember(c) else 255),
}


def specular(name, im):
    """A LabPBR specular map driven per pixel by the texture itself (a single value per material
    left the maps flat: the user, "abyssal sand has no LabPBR"). Each material has a base
    smoothness, reflectance and porosity or subsurface value; smoothness and the B channel then
    follow the albedo's light and dark around its own mean, so grains, frost, fibres and caps show
    in a shader. Emission only where it glows."""
    base = next((m for k, m in MATERIALS.items() if name == k or name.startswith(k + "_")), (0.3, 10, 40, None))
    smooth, f0, pb, glow = base
    px = [(x, y, im.getpixel((x, y))) for y in range(N) for x in range(N)]
    lit = [lum(c) for x, y, c in px if c[3] > 0]
    if not lit:
        return Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mean = sum(lit) / len(lit)
    spread = max(0.04, max(lit) - min(lit))
    sss = pb >= 65
    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for x, y, c in px:
        if c[3] == 0:
            continue
        rel = (lum(c) - mean) / spread  # about -0.5 .. 0.5 within this texture
        # Brighter grains read smoother (worn, glassy); darker ones rougher (pitted, dusty).
        s = smooth + 0.22 * rel
        f = f0
        if sss:
            b = pb + 70 * rel  # light passes better through the clearer, brighter parts
            b = max(65, min(255, b))
        else:
            b = pb - 24 * rel  # darker grains hold more water
            b = max(0, min(64, b))
        if name.startswith("hoarfrost") and lum(c) > 0.75:
            s, b = 0.22 + 0.1 * rel, 30  # the frost itself is rough and porous
        if name.startswith("abyssal_sand") and c[2] > 180:
            s, f = 0.9, 40  # mica glints
        if name.startswith("selenite") and lum(c) > 0.93:
            f = 18  # the bright seams and tips catch more light
        out.putpixel((x, y), (int(max(0, min(1, s)) * 255), f, int(b), glow(c) if glow else 255))
    return out


TEXTURES = {
    "selenite_block": selenite_block,
    "selenite_shard": selenite_shard,
    "glowworm_silk": lambda: glowworm_silk(True),
    "glowworm_silk_plant": lambda: glowworm_silk(False),
    "glowcap": glowcap,
    "glowcap_block": glowcap_block,
    "glowcap_block_1": lambda: glowcap_block(1),
    "glowcap_block_2": lambda: glowcap_block(2),
    "icicle_base": lambda: icicle("base"),
    "icicle_middle": lambda: icicle("middle"),
    "icicle_tip": lambda: icicle("tip"),
    "hoarfrost": hoarfrost,
    "hoarfrost_1": lambda: hoarfrost(1),
    "hoarfrost_2": lambda: hoarfrost(2),
    "abyssal_sand": abyssal_sand,
    "cinder_moss": cinder_moss,
    "cinder_moss_1": lambda: cinder_moss(1),
    "cinder_moss_2": lambda: cinder_moss(2),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tiles = []
    for name, fn in TEXTURES.items():
        im = fn()
        im.save(OUT / f"{name}.png")
        specular(name, im).save(OUT / f"{name}_s.png")
        tiles.append((name, im))
        print("wrote", (OUT / f"{name}.png").relative_to(ROOT))
    if "--sheet" in sys.argv:
        scale, pad = 8, 16
        sheet = Image.new("RGBA", (len(tiles) * (N * scale + pad) + pad, N * scale + 2 * pad), (28, 30, 36, 255))
        for i, (name, im) in enumerate(tiles):
            sheet.alpha_composite(im.resize((N * scale, N * scale), Image.NEAREST), (pad + i * (N * scale + pad), pad))
        (ROOT / "build").mkdir(exist_ok=True)
        sheet.save(ROOT / "build" / "texture_sheet.png")
        print("wrote build/texture_sheet.png")


if __name__ == "__main__":
    main()
