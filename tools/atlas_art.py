"""Paint the Grand Atlas guide book's art from code (1.21.1 build).

    python tools/atlas_art.py

Writes mc1211/src/main/resources/assets/grandatlas/textures/
    item/grand_atlas.png   the book: teal leather, gold corners and a gold compass rose
    item/ripped_map.png    a torn scrap of an old map with a red cross on it
    gui/atlas_book.png     the open book, 400x250 at 1:1 on a 512x256 sheet, in the game's own GUI style
Item icons keep to small palettes and shade in clusters, as tools/textures.py does.
"""
import math
import pathlib
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "mc1211" / "src" / "main" / "resources" / "assets" / "grandatlas" / "textures"


def save(im, rel):
    p = OUT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    im.save(p)
    print("wrote", p.relative_to(ROOT))


def grand_atlas():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    leather = [(20, 52, 62), (28, 72, 86), (38, 92, 108), (52, 112, 128)]
    page = [(201, 185, 143), (232, 220, 181)]
    gold = [(122, 82, 22), (196, 148, 52), (240, 204, 104)]
    rnd = random.Random(3)
    # cover 2..13 x 1..14, pages showing on the right edge and the bottom
    for y in range(1, 15):
        for x in range(2, 14):
            shade = 1 + (1 if (x + y * 3 + rnd.randint(0, 2)) % 7 == 0 else 0)
            if x == 2 or y == 14:
                shade = 0
            elif x == 3:
                shade = 2  # the spine's lit ridge
            px[x, y] = leather[shade] + (255,)
    for y in range(2, 15):
        px[14, y] = page[1 if y % 2 else 0] + (255,)
    for x in range(3, 15):
        px[x, 15] = page[0] + (255,)
    px[14, 15] = page[0] + (255,)
    # spine bands
    for x in range(2, 5):
        px[x, 3] = gold[1] + (255,)
        px[x, 12] = gold[1] + (255,)
    # gold corners
    for (cx, cy, dx, dy) in ((13, 1, -1, 1), (13, 14, -1, -1)):
        px[cx, cy] = gold[2] + (255,)
        px[cx + dx, cy] = gold[1] + (255,)
        px[cx, cy + dy] = gold[1] + (255,)
    # compass rose: a four-point star in gold, a dark centre
    cx, cy = 8, 7
    for d in range(1, 4):
        c = gold[2] if d < 3 else gold[1]
        for (ox, oy) in ((0, -d), (0, d), (-d, 0), (d, 0)):
            px[cx + ox, cy + oy] = c + (255,)
    for (ox, oy) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        px[cx + ox, cy + oy] = gold[0] + (255,)
    px[cx, cy] = (110, 30, 24, 255)
    save(im, "item/grand_atlas.png")


def ripped_map():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    paper = [(150, 122, 74), (190, 164, 112), (222, 204, 154), (238, 224, 182)]
    rnd = random.Random(7)
    # a ragged edge: each row ends at its own torn x, each column starts at its own torn y
    right = [12 + rnd.choice([0, 1, 1, 2, -1]) for _ in range(16)]
    topy = [2 + rnd.choice([0, 0, 1, -1]) for _ in range(16)]
    for y in range(1, 15):
        for x in range(1, 15):
            if x > right[y] or y < topy[x] or (x + y > 26):
                continue
            edge = x == right[y] or y == topy[x] or x == 1 or y == 14
            v = 1 if edge else (3 if (x * 7 + y * 13) % 11 < 3 else 2)
            px[x, y] = paper[v] + (255,)
    # a river, a patch of wood and a red cross
    for (x, y) in ((3, 12), (4, 11), (4, 10), (5, 9), (6, 9), (7, 8), (8, 8), (9, 7), (10, 6)):
        if px[x, y][3]:
            px[x, y] = (66, 104, 150, 255)
    for (x, y) in ((3, 5), (4, 5), (3, 6), (4, 4), (5, 5)):
        if px[x, y][3]:
            px[x, y] = (78, 110, 52, 255)
    for (x, y) in ((8, 10), (10, 10), (9, 11), (8, 12), (10, 12)):
        if px[x, y][3]:
            px[x, y] = (168, 36, 28, 255)
    save(im, "item/ripped_map.png")


def book_gui():
    """The open book, drawn 1:1 at GUI scale in the game's own style: hard pixels, a dark outline,
    a bevelled leather cover, cream pages with soft quantised blotches, red stitching at the gutter.
    The book is 400x250 in the top-left of a 512x256 sheet."""
    W, H = 400, 250
    im = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    px = im.load()
    OUT_ = (28, 15, 0)
    LEATHER = {"hi": (148, 70, 40), "base": (117, 50, 30), "mid": (101, 40, 22), "lo": (76, 26, 11)}
    CREAM = [(255, 250, 238), (253, 246, 229), (249, 238, 208), (238, 224, 188), (222, 204, 162)]
    EDGE = [(236, 222, 186), (204, 184, 142)]
    GOLD = [(122, 82, 22), (196, 148, 52), (240, 204, 104)]
    RED = (178, 34, 30)
    rnd = random.Random(11)

    def inside_round(x, y, x0, y0, x1, y1, cut):
        # a pixel-rounded rectangle: corners cut on a diagonal of `cut` pixels
        dx = min(x - x0, x1 - x)
        dy = min(y - y0, y1 - y)
        if dx < 0 or dy < 0:
            return False
        return dx + dy >= cut

    # cover
    for y in range(H):
        for x in range(W):
            if not inside_round(x, y, 0, 0, W - 1, H - 1, 3):
                continue
            edge = not inside_round(x, y, 1, 1, W - 2, H - 2, 3)
            if edge:
                px[x, y] = OUT_ + (255,)
                continue
            c = LEATHER["base"]
            if not inside_round(x, y, 2, 2, W - 3, H - 3, 3):
                c = LEATHER["hi"] if (x < W // 2 and y < H - 3) or y <= 2 else LEATHER["lo"]
                if y >= H - 3 or x >= W - 3:
                    c = LEATHER["lo"]
            else:
                # leather grain: sparse darker and lighter pixels in short runs
                r = rnd.random()
                if r < 0.05:
                    c = LEATHER["mid"]
                elif r < 0.065:
                    c = LEATHER["hi"]
            px[x, y] = c + (255,)
    # gold corner guards on the cover
    for (cx, cy, sx, sy) in ((4, 4, 1, 1), (W - 5, 4, -1, 1), (4, H - 5, 1, -1), (W - 5, H - 5, -1, -1)):
        for i in range(5):
            for j in range(5 - i):
                c = GOLD[2] if i == 0 or j == 0 else GOLD[1]
                if i + j == 4:
                    c = GOLD[0]
                px[cx + sx * i, cy + sy * j] = c + (255,)

    # page block: stacked page edges peeking out under each page
    def page_rect(x0, x1):
        return (x0, 9, x1, H - 12)
    pages = [page_rect(13, W // 2 - 2), page_rect(W // 2 + 1, W - 14)]
    for k, (x0, y0, x1, y1) in enumerate(pages):
        outer = -1 if k == 0 else 1
        for step in range(3, 0, -1):
            for y in range(y0 + step, y1 + step + 1):
                for x in range(x0, x1 + 1):
                    xx = x + outer * step
                    px[xx, y] = (EDGE[step % 2] if step < 3 else OUT_) + (255,)
    # the pages themselves, with an outline and soft blotches
    blot = Image.effect_noise((W // 16 + 2, H // 16 + 2), 34).convert("L").resize((W, H), Image.BICUBIC).load()
    for k, (x0, y0, x1, y1) in enumerate(pages):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if x in (x0, x1) or y in (y0, y1):
                    px[x, y] = (OUT_ if (k == 0 and x == x0) or (k == 1 and x == x1) or y in (y0, y1) else CREAM[3]) + (255,)
                    continue
                n = (blot[x, y] - 128) / 128.0
                g = abs(x - W / 2)             # distance from the gutter
                lvl = 0 if n < 0.25 else (1 if n < 0.6 else 2)
                if g < 4:
                    lvl = 4
                elif g < 9:
                    lvl = max(lvl, 3)
                elif g < 16:
                    lvl = max(lvl, 2)
                # a darker rim round the outer edges, two pixels wide
                if min(x - x0, x1 - x, y - y0, y1 - y) <= 2 and lvl < 2:
                    lvl = 2 if min(x - x0, x1 - x, y - y0, y1 - y) <= 1 else 1
                px[x, y] = CREAM[lvl] + (255,)
    # the gutter seam and its stitching
    for y in range(9, H - 11):
        px[W // 2 - 1, y] = OUT_ + (255,)
        px[W // 2, y] = LEATHER["lo"] + (255,)
    for y in range(16, H - 18, 8):
        for (x, dy) in ((W // 2 - 6, 0), (W // 2 - 6, 1), (W // 2 - 6, 2), (W // 2 + 5, 0), (W // 2 + 5, 1), (W // 2 + 5, 2)):
            px[x, y + dy] = RED + (255,)
    # curled corners on the outer bottom corners, as on the game's own book
    for (cx, sx) in ((pages[0][0] + 1, 1), (pages[1][2] - 1, -1)):
        cy = pages[0][3] - 1
        for i in range(6):
            for j in range(6 - i):
                c = CREAM[3] if i + j < 5 else EDGE[1]
                if i + j == 5:
                    c = OUT_
                px[cx + sx * i, cy - j] = c + (255,)
    # a faint inked compass rose behind the picture box (right page, centred on 293, 96)
    ink = CREAM[2]
    cx, cy, R = 293, 96, 50
    for a in range(0, 360, 1):
        for rr in (R, R - 6):
            x = int(round(cx + math.cos(math.radians(a)) * rr))
            y = int(round(cy + math.sin(math.radians(a)) * rr))
            if px[x, y][:3] in (CREAM[0], CREAM[1]):
                px[x, y] = ink + (255,)
    for k in range(8):
        a = k * math.pi / 4
        L = R - 1 if k % 2 == 0 else R * 0.55
        for t in range(int(L)):
            x = int(round(cx + math.sin(a) * t))
            y = int(round(cy - math.cos(a) * t))
            if px[x, y][:3] in (CREAM[0], CREAM[1]):
                px[x, y] = ink + (255,)
    # a ribbon bookmark hanging out of the bottom of the right page
    rx = W // 2 + 28
    for y in range(H - 13, H + 0):
        if y >= 256:
            break
        for x in range(rx, rx + 7):
            notch = y >= H - 4 and abs(x - (rx + 3)) < (y - (H - 4)) + 1
            if notch:
                continue
            c = (150, 26, 24) if x in (rx, rx + 6) else ((196, 44, 36) if x == rx + 1 else (172, 34, 30))
            px[x, y] = c + (255,)
    save(im, "gui/atlas_book.png")


if __name__ == "__main__":
    grand_atlas()
    ripped_map()
    book_gui()
