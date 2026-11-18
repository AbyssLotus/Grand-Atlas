"""Seamless-tiling previews of the shattered aurora crystal, as it looks laid out in a wall.

    python tools/tiling_preview.py

Writes dist/texture-sheet/tiling/: one 12x12-block tiling per colour (variants picked at random,
like in game) and shattered_all.png, the five colours mixed in one wall.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BLOCK = ROOT / "src/main/resources/assets/grandatlas/textures/block"
OUT = ROOT / "dist/texture-sheet/tiling"
COLOURS = ("blue", "cyan", "green", "rose", "violet")
N, SCALE = 12, 4


def variants(colour):
    name = f"shattered_aurora_crystal_{colour}"
    return [Image.open(BLOCK / f"{name}{s}.png").convert("RGBA") for s in ("", "_1", "_2", "_3")]


def wall(pick):
    im = Image.new("RGBA", (16 * N, 16 * N))
    for y in range(N):
        for x in range(N):
            im.alpha_composite(pick(x, y), (16 * x, 16 * y))
    return im.resize((16 * N * SCALE, 16 * N * SCALE), Image.NEAREST)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    tex = {c: variants(c) for c in COLOURS}
    rng = random.Random(7)
    for c in COLOURS:
        wall(lambda x, y: rng.choice(tex[c])).save(OUT / f"shattered_{c}.png")
    wall(lambda x, y: rng.choice(tex[rng.choice(COLOURS)])).save(OUT / "shattered_all.png")
    print(f"wrote {len(COLOURS) + 1} tilings to {OUT}")


if __name__ == "__main__":
    main()
