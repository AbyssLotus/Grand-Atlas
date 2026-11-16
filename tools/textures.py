"""Paint Grand Atlas's block textures (16x16, vanilla style) from code, so they can be redrawn.

    python tools/textures.py            (writes src/main/resources/assets/grandatlas/textures/block/*.png,
                                         each with its LabPBR specular map, name_s.png)
    python tools/textures.py --sheet    (also writes build/texture_sheet.png, an 8x contact sheet)

The bar (the user, 2026-11-04): "vanilla plus", or as good as Obscuria's work. So each texture
keeps to a small palette (4-7 tones), shades in clusters rather than per-pixel noise, has no
outlines, and plants keep a clear silhouette, as vanilla's do.
"""
import math
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
    """Hanging glowworm silk, drawn the way vanilla draws its hanging plants (the user, 2026-11-10:
    the first version, three one-pixel lines in three colours, "doesn't match the vibe of vanilla
    Minecraft which has more gradient work"). Strands two pixels thick that sway, lit from the upper
    left and shaded down the right in a hue-shifted ramp (silver-teal thread, deep teal to pale mint
    droplets), with beads of light swelling along them and a heavy drop at each tip. About a hundred
    pixels of it, like cave vines, and the body tiles top to bottom."""
    # thread: shadow, body, light; droplets: rim, shade, body, light, shine, core
    t_dark, t_mid, t_light = (52, 96, 118), (104, 152, 168), (170, 208, 214)
    d_rim, d_shade, d_body, d_light, d_shine, d_core = (22, 74, 98), (36, 116, 136), (60, 168, 176), (104, 214, 206), (168, 240, 228), (232, 255, 249)
    im = img()

    def strand_x(sx, phase, y):
        return sx + int(round(1.15 * math.sin(2 * math.pi * y / 16.0 + phase)))

    def bead(cx, cy):
        # a small swelling of light: lit upper left, shaded lower right, a dark rim under it
        for dx, dy, c in ((0, 0, d_shine), (1, 0, d_light), (-1, 1, d_light), (0, 1, d_core), (1, 1, d_body), (2, 1, d_shade),
                          (0, 2, d_body), (1, 2, d_shade), (0, 3, d_rim)):
            put(im, cx + dx, cy + dy, c)

    def drop(cx, cy):
        # the heavy drop at a strand's end
        for dx, dy, c in ((0, -1, t_light), (0, 0, d_light), (1, 0, d_body),
                          (-1, 1, d_light), (0, 1, d_shine), (1, 1, d_light), (2, 1, d_shade),
                          (-1, 2, d_body), (0, 2, d_core), (1, 2, d_light), (2, 2, d_shade),
                          (-1, 3, d_shade), (0, 3, d_light), (1, 3, d_body), (2, 3, d_rim),
                          (0, 4, d_shade), (1, 4, d_rim)):
            put(im, cx + dx, cy + dy, c)

    # (x, sway phase, first bead row, where a tip's strand ends)
    strands = [(3, 0.0, 2, 10), (8, 2.2, 6, 14), (12, 4.3, 0, 8)]
    for sx, phase, first, stop in strands:
        bottom = stop if tip else 16
        for y in range(bottom):
            x = strand_x(sx, phase, y)
            nxt = strand_x(sx, phase, y + 1)
            put(im, x, y, t_mid)
            put(im, x + 1, y, t_dark)             # the shaded side
            if (y + sx) % 3 == 0: put(im, x - 1, y, t_light)  # glints down the lit side
            if nxt != x: put(im, min(x, nxt), y, t_light)   # where it bends, the turn catches the light
        for row in range(first, bottom - (5 if tip else 0), 7):
            bead(strand_x(sx, phase, row + 1), row % 16 if not tip else row)
        if tip:
            drop(strand_x(sx, phase, bottom - 1), bottom)
    # fine side threads between the strands, as in a web: short, dim, on the diagonal
    for x0, y0, n, dx in ((5, 4, 3, 1), (10, 9, 2, -1), (6, 12, 2, 1)):
        if tip and y0 > 7: continue
        for k in range(n):
            put(im, x0 + k * dx, y0 + k, t_dark, 200)
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

# The aurora crystals that line an inverted lake's bed (user, 2026-11-09: "multi colored crystals" so
# the light through the water glows like an aurora). One design, five jewel palettes, dark to white.
AURORA = {
    "green": [(14, 84, 64), (26, 140, 98), (58, 198, 134), (138, 240, 182), (220, 255, 234)],
    "cyan": [(10, 78, 98), (20, 138, 160), (54, 196, 208), (148, 236, 238), (226, 255, 255)],
    "blue": [(22, 42, 116), (36, 82, 188), (76, 134, 236), (158, 192, 255), (230, 240, 255)],
    "violet": [(56, 24, 110), (98, 50, 176), (148, 98, 232), (200, 162, 255), (244, 232, 255)],
    "rose": [(104, 24, 70), (172, 48, 116), (226, 94, 164), (255, 164, 208), (255, 234, 246)],
}


def aurora_crystal(colour, variant=0):
    """A mass of grown crystal, read like vanilla's amethyst: a few large flat facets in mid tones
    (each lit by its own tilt), bevelled edges lit on the upper left and shaded on the lower right,
    one facet glowing from a bright heart, a sparkle. Three variants turned at random so a lake bed
    of it doesn't tile."""
    import math
    pal = AURORA[colour]
    rnd = random.Random(7100 + 37 * variant)
    seeds = [(rnd.random() * N, rnd.random() * N) for _ in range(6)]
    tone = [rnd.choice((1, 2, 2, 2, 3)) for _ in seeds]
    heart = rnd.randrange(len(seeds))
    tone[heart] = 2
    def facet(x, y):
        best, bi = 1e9, 0
        for i, (sx, sy) in enumerate(seeds):
            dx = min(abs(x - sx), N - abs(x - sx)); dy = min(abs(y - sy), N - abs(y - sy))
            # stretched along a diagonal so the facets read as crystal faces, not cells
            d = math.hypot(dx * 0.8 + dy * 0.35, dy)
            if d < best:
                best, bi = d, i
        return bi
    f = [[facet((x + 0.5) % N, (y + 0.5) % N) for x in range(N)] for y in range(N)]
    im = img()
    for y in range(N):
        for x in range(N):
            k = tone[f[y][x]]
            if f[(y - 1) % N][(x - 1) % N] != f[y][x]:
                k = min(4, k + 1)  # lit edge
            elif f[(y + 1) % N][(x + 1) % N] != f[y][x]:
                k = max(0, k - 1)  # shaded edge
            sx, sy = seeds[f[y][x]]
            if f[y][x] == heart:
                dx = min(abs(x + 0.5 - sx), N - abs(x + 0.5 - sx)); dy = min(abs(y + 0.5 - sy), N - abs(y + 0.5 - sy))
                r = math.hypot(dx, dy)
                if r < 1.3:
                    k = 4
                elif r < 2.6:
                    k = max(k, 3)
            put(im, x, y, pal[k])
    put(im, rnd.randrange(N), rnd.randrange(N), pal[4])
    return im


def aurora_shattered(colour, variant=0):
    """Aurora crystal once it's been mined: broken into many small shards at every angle, bright
    fracture faces among them (what an aurora crystal drops without Silk Touch, as stone drops
    cobblestone). Three variants turned at random."""
    import math
    pal = AURORA[colour]
    rnd = random.Random(7100 + 37 * variant)
    seeds = []
    for _ in range(9):
        a = rnd.random() * math.pi * 2
        seeds.append((rnd.random() * N, rnd.random() * N, math.cos(a + 0.8), rnd.random() < 0.22))
    def near(x, y):
        best = []
        for i, (sx, sy, _, _) in enumerate(seeds):
            dx = min(abs(x - sx), N - abs(x - sx)); dy = min(abs(y - sy), N - abs(y - sy))
            best.append((math.hypot(dx, dy), i))
        best.sort()
        return best[0], best[1]
    im = img()
    for y in range(N):
        for x in range(N):
            (d1, i), (d2, j) = near(x + 0.5, y + 0.5)
            sx, sy, tilt, glow = seeds[i]
            k = 1 if tilt < -0.35 else 2 if tilt < 0.4 else 3
            if glow:
                k = 3 if d1 > 2.2 else 4
            if d2 - d1 < 0.9 and seeds[j][2] < tilt:
                k = min(4, k + 1)
            elif d2 - d1 < 0.9 and k > 1 and tilt < 0:
                k -= 1
            put(im, x, y, pal[k])
    for _ in range(2):
        put(im, rnd.randrange(N), rnd.randrange(N), pal[4])
    return im


def aurora_shard(colour):
    """Crystal points growing from the base, stubbier and more jewel-like than selenite: faceted,
    deep at the root, glowing at the tips."""
    pal = AURORA[colour]
    im = img()
    for bx, by, ang, length, half in ((8.2, 16.2, 0.08, 13, 2.2), (4.6, 16.2, -0.5, 9, 1.8), (11.8, 16.2, 0.46, 9, 1.7),
                                      (2.4, 16.2, -0.95, 5, 1.2), (14.0, 16.2, 0.9, 5, 1.2)):
        blade(im, bx, by, ang, length, half, [pal[0], pal[1], pal[2], pal[3]], pal[4])
    return im


def amber_sap(variant=0):
    """Glowing tree resin set hard: warm amber in slow, soft swirls (seamless), brightest where it is
    thickest, one or two flecks of bark caught deep in it and a single small bubble, kept apart.
    Three variants turned at random."""
    import math
    pal = [(150, 70, 12), (186, 98, 18), (216, 132, 30), (240, 170, 56), (255, 210, 116)]
    im = img()
    rnd = random.Random(8320 + variant)
    ph = [rnd.random() * math.pi * 2 for _ in range(6)]
    for y in range(N):
        for x in range(N):
            u, v = x / N * math.pi * 2, y / N * math.pi * 2
            # sums of whole-period waves: seamless on all four edges
            w = (math.sin(u + ph[0] + 0.8 * math.sin(v + ph[1])) + 0.7 * math.sin(2 * v + ph[2] + 0.9 * math.sin(u + ph[3]))
                 + 0.4 * math.sin(2 * u + 3 * v + ph[4])) / 2.1
            put(im, x, y, ramp(pal, 0.5 + 0.42 * w), 230 + int(20 * (0.5 + 0.5 * w)))
    fx, fy = rnd.randrange(N), rnd.randrange(N)
    put(im, fx, fy, (96, 46, 12), 250)
    bx, by = (fx + 8) % N, (fy + 5 + rnd.randrange(4)) % N
    put(im, bx, by, (255, 236, 170), 210)
    return im


def aurora_veil(colour, tip):
    """A curtain of aurora light: soft streaks of the colour that sway a little and shimmer along
    their length, translucent, the tip fading to nothing (as an aurora's curtain fades at its lower
    edge). Seamless top to bottom, so a curtain of any length runs on without a seam."""
    import math
    pal = AURORA[colour]
    im = img()
    rnd = random.Random(9100 + len(colour) * 7 + (1 if tip else 0))
    shimmer = value_noise(9200 + len(colour), 4)
    centres = [rnd.random() * N for _ in range(5)]
    widths = [0.8 + 1.4 * rnd.random() for _ in centres]
    for y in range(N):
        sway = 0.6 * math.sin(y / N * math.pi * 2)
        for x in range(N):
            s = 0.0
            for c, w in zip(centres, widths):
                d = min(abs(x + 0.5 - (c + sway) % N), N - abs(x + 0.5 - (c + sway) % N))
                s = max(s, max(0.0, 1 - d / w))
            if s <= 0.05:
                continue
            v = s * (0.55 + 0.45 * shimmer(x, y))
            k = 4 if v > 0.8 else 3 if v > 0.5 else 2
            fade = (1 - y / N) ** 1.4 if tip else 1.0
            a = int((60 + 170 * v) * fade)
            if a < 12:
                continue
            put(im, x, y, pal[k], a)
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
    # The cap glows (bright and blue-leaning: the user's indigo redraw, or the old teal), the stem doesn't.
    "glowcap": (0.42, 10, 150, lambda c: 200 if teal_glow(c) or (lum(c) > 0.55 and c[2] > c[0] + 20) else 255),
    "glowcap_block": (0.36, 10, 140, lambda c: 220 if lum(c) > 0.85 else 90),
    "icicle": (0.93, 5, 210, None),
    "hoarfrost": (0.8, 5, 190, None),
    "abyssal_sand": (0.12, 10, 60, None),
    "cinder_moss": (0.08, 9, 50, lambda c: 230 if lum(c) > 0.6 else 170 if ember(c) else 255),
    # Glassy, a little subsurface, and every pixel glows (brighter tones more).
    "aurora_crystal": (0.9, 12, 170, lambda c: int(min(254, 30 + 225 * lum(c)))),
    "aurora_shard": (0.92, 12, 180, lambda c: int(min(254, 40 + 214 * lum(c)))),
    "aurora_veil": (0.5, 10, 120, lambda c: int(min(254, 120 + 130 * lum(c)))),
    "amber_sap": (0.88, 14, 200, lambda c: int(min(254, 30 + 200 * lum(c)))),
    "shattered_aurora_crystal": (0.7, 12, 150, lambda c: int(min(254, 20 + 200 * lum(c)))),
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
TEXTURES["amber_sap"] = lambda: amber_sap(0)
TEXTURES["amber_sap_1"] = lambda: amber_sap(1)
TEXTURES["amber_sap_2"] = lambda: amber_sap(2)
for _c in AURORA:
    TEXTURES[f"aurora_crystal_{_c}"] = (lambda c: lambda: aurora_crystal(c, 0))(_c)
    TEXTURES[f"aurora_crystal_{_c}_1"] = (lambda c: lambda: aurora_crystal(c, 1))(_c)
    TEXTURES[f"aurora_crystal_{_c}_2"] = (lambda c: lambda: aurora_crystal(c, 2))(_c)
    TEXTURES[f"aurora_shard_{_c}"] = (lambda c: lambda: aurora_shard(c))(_c)
    TEXTURES[f"aurora_veil_{_c}"] = (lambda c: lambda: aurora_veil(c, False))(_c)
    TEXTURES[f"aurora_veil_{_c}_tip"] = (lambda c: lambda: aurora_veil(c, True))(_c)
    TEXTURES[f"shattered_aurora_crystal_{_c}"] = (lambda c: lambda: aurora_shattered(c, 0))(_c)
    TEXTURES[f"shattered_aurora_crystal_{_c}_1"] = (lambda c: lambda: aurora_shattered(c, 1))(_c)
    TEXTURES[f"shattered_aurora_crystal_{_c}_2"] = (lambda c: lambda: aurora_shattered(c, 2))(_c)
    TEXTURES[f"shattered_aurora_crystal_{_c}_3"] = (lambda c: lambda: aurora_shattered(c, 3))(_c)


# ---- hand-drawn art (art/block/*.png) wins over the painted-from-code textures
#
# The user redraws textures by hand (2026-11-14: the glowcaps, and the blue aurora crystal and shard
# with a gradient look). A file in art/block/ is used as it is; the other colours of a hand-drawn
# aurora crystal, shard or shattered crystal are recoloured from its blue version, so the user draws
# one colour only (2026-11-16: the shattered crystal, with a fourth variant, _3).

HAND = ROOT / "art" / "block"


def recolour(im, src="blue", dst="green"):
    """Recolour hand-drawn aurora art from one colour's palette to another's, keeping every shade
    and gradient: each pixel's hue moves by the palettes' hue difference, and its saturation and
    brightness follow the two palettes shade by shade (matched on brightness), so a dark blue
    becomes the target's dark tone, a highlight its highlight."""
    import colorsys
    if src == dst:
        return im.copy()
    a = [colorsys.rgb_to_hsv(*(v / 255 for v in p)) for p in AURORA[src]]
    b = [colorsys.rgb_to_hsv(*(v / 255 for v in p)) for p in AURORA[dst]]
    # Shades ordered by the source's brightness (the two brightest are both v = 1; use saturation).
    keys = [(av[2] - (0.3 * av[1] if av[2] > 0.99 else 0)) for av in a]

    def at(key):
        if key <= keys[0]:
            return 0, 0, 0.0
        for i in range(len(keys) - 1):
            if keys[i] <= key <= keys[i + 1]:
                return i, i + 1, (key - keys[i]) / max(1e-6, keys[i + 1] - keys[i])
        return len(keys) - 1, len(keys) - 1, 0.0

    out = im.copy().convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, bl, al = px[x, y]
            if al == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, bl / 255)
            i, j, t = at(v - (0.3 * s if v > 0.99 else 0))
            lerp = lambda k: (1 - t) * (b[i][k] - a[i][k]) + t * (b[j][k] - a[j][k]) if k == 0 else \
                (1 - t) * (b[i][k] / max(1e-6, a[i][k])) + t * (b[j][k] / max(1e-6, a[j][k]))
            h2 = (h + lerp(0)) % 1.0
            s2 = max(0.0, min(1.0, s * lerp(1)))
            v2 = max(0.0, min(1.0, v * lerp(2)))
            r2, g2, b2 = colorsys.hsv_to_rgb(h2, s2, v2)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), al)
    return out


def hand_drawn(name):
    """The hand-drawn texture for a name, or one recoloured from its hand-drawn blue version; None if neither."""
    p = HAND / f"{name}.png"
    if p.exists():
        return Image.open(p).convert("RGBA")
    for c in AURORA:
        for family in ("aurora_crystal", "aurora_shard", "shattered_aurora_crystal"):
            if name.startswith(f"{family}_{c}"):
                blue = HAND / (name.replace(f"{family}_{c}", f"{family}_blue") + ".png")
                if blue.exists():
                    return recolour(Image.open(blue).convert("RGBA"), "blue", c)
    return None


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tiles = []
    for name, fn in TEXTURES.items():
        im = hand_drawn(name) or fn()
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
