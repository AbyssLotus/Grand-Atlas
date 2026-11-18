"""A labelled sheet of every Grand Atlas texture, and a resource-pack kit to remake them.

    python tools/texture_sheet.py

Writes dist/texture-sheet/:
  grand_atlas_textures.png   every colour texture, 8x with hard pixels, grouped by family, labelled
  grand_atlas_specular.png   the matching _s maps (LabPBR specular: shaders read smoothness,
                             reflectance and emission from them), same layout
  grand_atlas_gui.png        the guide book screen and the item icons
  textures.csv               every file: path, size, its _s map, and what it is
  remake-kit/                a resource pack holding copies of all the textures at their real paths.
                             Paint over a file, put the folder in .minecraft/resourcepacks and turn
                             it on: your version replaces ours in game.
"""
import csv
import re
import shutil
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHARED = ROOT / "src/main/resources/assets/grandatlas/textures"
MC1211 = ROOT / "mc1211/src/main/resources/assets/grandatlas/textures"
OUT = ROOT / "dist/texture-sheet"

SCALE = 8
TILE = 16 * SCALE
CELL_W, CELL_H = TILE + 40, TILE + 48
COLS = 8
BG, INK, FAINT, HEAD = (34, 36, 42), (230, 230, 230), (150, 152, 160), (240, 200, 110)


def font(size):
    for f in ("C:/Windows/Fonts/consola.ttf", "C:/Windows/Fonts/arial.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


F_HEAD, F_LABEL, F_SMALL, F_TITLE = font(22), font(13), font(11), font(30)

# Families, in sheet order: (title, test on the file stem, what it is)
FAMILIES = [
    ("Aurora crystal blocks", lambda s: s.startswith("aurora_crystal_"), "Aurora crystal block (Aurora Vaults, crystal gardens). _1/_2 are random variants."),
    ("Shattered aurora crystal", lambda s: s.startswith("shattered_aurora_crystal_"), "Shattered aurora crystal: the crazed skin near a spire's tip, drops shards."),
    ("Aurora shards", lambda s: s.startswith("aurora_shard_"), "Aurora shard: the small crystal cluster (cross model, like amethyst clusters)."),
    ("Amber sap", lambda s: s.startswith("amber_sap"), "Amber sap: glowing sap veining the Worldroot."),
    ("Cave blocks", lambda s: s.startswith(("abyssal_sand", "selenite_block", "glowcap_block", "cinder_moss", "hoarfrost")),
     "Cave biome ground and blocks."),
    ("Cave plants and hangings", lambda s: s.startswith(("glowcap", "glowworm_silk", "selenite_shard", "icicle_")),
     "Cave plants and hanging blocks."),
]
WHAT = {
    "abyssal_sand": "Abyssal sand: dark beaches of the underground seas.",
    "selenite_block": "Selenite block: the crystal beams of the crystal caves.",
    "selenite_shard": "Selenite shard: small selenite cluster.",
    "glowcap": "Glowcap: small glowing mushroom (plant).",
    "glowcap_block": "Glowcap block: the cap of a giant glowcap.",
    "glowworm_silk": "Glowworm silk: the glowing threads hanging in glowworm grottos (top part).",
    "glowworm_silk_plant": "Glowworm silk: the thread's body below the top.",
    "cinder_moss": "Cinder moss: moss on volcanic and magma cave floors.",
    "hoarfrost": "Hoarfrost: frost carpet in ice caves.",
    "icicle_base": "Icicle: top segment.",
    "icicle_middle": "Icicle: middle segment.",
    "icicle_tip": "Icicle: tip.",
    "aurora_ribbon": "Aurora ribbon: the sky aurora's texture (64x64, stretched along the ribbons).",
}


def wrap(d, text, fnt, width):
    """Split a file name at underscores so each line fits the cell."""
    parts, lines, cur = text.split("_"), [], ""
    for i, part in enumerate(parts):
        piece = part if i == len(parts) - 1 else part + "_"
        if cur and d.textlength(cur + piece, font=fnt) > width:
            lines.append(cur)
            cur = piece
        else:
            cur += piece
    lines.append(cur)
    return lines[:2]


def checker(w, h, cell=8):
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(70, 72, 80) if (x // cell + y // cell) % 2 else (58, 60, 68))
    return im


def tile(path, size=TILE):
    src = Image.open(path).convert("RGBA")
    big = src.resize((size, size * src.height // src.width), Image.NEAREST)
    bg = checker(big.width, big.height)
    bg.alpha_composite(big)
    return bg


def describe(stem):
    base = re.sub(r"_(1|2)$", "", stem)
    for key in (stem, base):
        if key in WHAT:
            return WHAT[key]
    for _, test, what in FAMILIES:
        if test(stem):
            return what
    return ""


def draw_sheet(groups, title, subtitle, spec=False):
    """groups: [(family title, [stems])]; spec draws the _s map of each stem instead."""
    rows = sum(1 + (len(stems) + COLS - 1) // COLS for _, stems in groups if stems)
    width = 40 + COLS * CELL_W
    height = 110 + rows * CELL_H - sum(CELL_H - 40 for _, stems in groups if stems) + 20
    sheet = Image.new("RGBA", (width, height), BG)
    d = ImageDraw.Draw(sheet)
    d.text((20, 18), title, font=F_TITLE, fill=INK)
    d.text((20, 58), subtitle, font=F_SMALL, fill=FAINT)
    y = 100
    for fam, stems in groups:
        if not stems:
            continue
        d.text((20, y), fam, font=F_HEAD, fill=HEAD)
        y += 34
        for i, stem in enumerate(stems):
            col, row = i % COLS, i // COLS
            x0, y0 = 20 + col * CELL_W, y + row * CELL_H
            p = SHARED / "block" / (stem + ("_s" if spec else "") + ".png")
            if p.exists():
                sheet.alpha_composite(tile(p), (x0, y0))
            else:
                d.rectangle((x0, y0, x0 + TILE - 1, y0 + TILE - 1), outline=FAINT)
                d.text((x0 + 8, y0 + 56), "no _s map", font=F_SMALL, fill=FAINT)
            for k, line in enumerate(wrap(d, stem + ("_s" if spec else "") + ".png", F_LABEL, CELL_W - 12)):
                d.text((x0, y0 + TILE + 4 + 16 * k), line, font=F_LABEL, fill=INK)
        y += ((len(stems) + COLS - 1) // COLS) * CELL_H + 6
    return sheet.crop((0, 0, width, y + 10))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    stems = sorted(p.stem for p in (SHARED / "block").glob("*.png") if not p.stem.endswith("_s"))
    used, groups = set(), []
    for fam, test, _ in FAMILIES:
        g = [s for s in stems if test(s) and s not in used]
        used.update(g)
        groups.append((fam, g))
    rest = [s for s in stems if s not in used]
    if rest:
        groups.append(("Other blocks", rest))

    n_colour = sum(len(g) for _, g in groups)
    draw_sheet(groups, "Grand Atlas textures",
               f"{n_colour} block textures, all 16x16, shown 8x. Every file is in assets/grandatlas/textures/block/. "
               "Variants _1/_2 are picked at random per block. Checkerboard = transparent.").save(OUT / "grand_atlas_textures.png")
    draw_sheet(groups, "Grand Atlas specular maps (_s)",
               "LabPBR specular maps, read only by shader packs: red = smoothness, green = reflectance, "
               "alpha = emission (glow). In assets/grandatlas/textures/block/, same layout as the texture sheet.", spec=True).save(OUT / "grand_atlas_specular.png")

    # GUI and items: the book screen at 2x, the item icons at 8x, the sky ribbon at 2x
    book = Image.open(MC1211 / "gui/atlas_book.png").convert("RGBA").crop((0, 0, 400, 250))
    items = sorted((MC1211 / "item").glob("*.png"))
    ribbon = SHARED / "misc/aurora_ribbon.png"
    W = 40 + 800
    sheet = Image.new("RGBA", (W, 100 + 500 + 60 + TILE + 60 + 128 + 80), BG)
    d = ImageDraw.Draw(sheet)
    d.text((20, 18), "Grand Atlas: book, items and sky", font=F_TITLE, fill=INK)
    d.text((20, 58), "The guide book screen is drawn 1:1 at GUI scale: 400x250 in the top-left of a 512x256 file. Shown 2x.",
           font=F_SMALL, fill=FAINT)
    sheet.alpha_composite(book.resize((800, 500), Image.NEAREST), (20, 90))
    d.text((20, 594), "gui/atlas_book.png (512x256, art in the top-left 400x250)", font=F_LABEL, fill=INK)
    y = 630
    for i, p in enumerate(items):
        x0 = 20 + i * CELL_W
        sheet.alpha_composite(tile(p), (x0, y))
        d.text((x0, y + TILE + 4), p.stem, font=F_LABEL, fill=INK)
        d.text((x0, y + TILE + 22), "item/" + p.name, font=F_SMALL, fill=FAINT)
    x0 = 20 + len(items) * CELL_W + 20
    if ribbon.exists():
        sheet.alpha_composite(tile(ribbon, 128), (x0, y))
        d.text((x0, y + 132), "aurora_ribbon (64x64)", font=F_LABEL, fill=INK)
        d.text((x0, y + 150), "misc/aurora_ribbon.png", font=F_SMALL, fill=FAINT)
    sheet.crop((0, 0, W, y + TILE + 50)).save(OUT / "grand_atlas_gui.png")

    # Index
    with open(OUT / "textures.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["path", "size", "specular map", "what it is"])
        for root, sub in ((SHARED, "block"), (SHARED, "misc"), (MC1211, "item"), (MC1211, "gui")):
            for p in sorted((root / sub).glob("*.png")):
                if p.stem.endswith("_s"):
                    continue
                im = Image.open(p)
                spec = p.with_name(p.stem + "_s.png")
                what = describe(p.stem) or {"grand_atlas": "The Grand Atlas guide book item.",
                                            "ripped_map": "Ripped map item.",
                                            "atlas_book": "The open guide book screen."}.get(p.stem, "")
                w.writerow([f"assets/grandatlas/textures/{sub}/{p.name}", f"{im.width}x{im.height}",
                            f"{sub}/{spec.name}" if spec.exists() else "", what])

    # Remake kit: a resource pack with every texture at its real path
    kit = OUT / "remake-kit"
    shutil.rmtree(kit, ignore_errors=True)
    for root in (SHARED, MC1211):
        for p in root.rglob("*.png"):
            dest = kit / "assets/grandatlas/textures" / p.relative_to(root)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dest)
    (kit / "pack.mcmeta").write_text(
        '{\n  "pack": {\n    "pack_format": 34,\n    "description": "Grand Atlas textures, remade"\n  }\n}\n', encoding="utf-8")
    (kit / "README.txt").write_text(
        "Grand Atlas remake kit\n\n"
        "Every Grand Atlas texture, at the path the game loads it from. Paint over any file\n"
        "(keep the same name and size; 16x16 for blocks and items), then copy this whole folder into\n"
        ".minecraft/resourcepacks (in the Modrinth App: the instance's folder, resourcepacks) and turn it on.\n"
        "Your files replace Grand Atlas's in game. pack_format 34 is Minecraft 1.21.1.\n\n"
        "Files ending _s are specular maps for shader packs (LabPBR): red = smoothness,\n"
        "green = reflectance, alpha = emission. Delete any you don't remake and the original stays.\n"
        "See textures.csv next to this folder for what each texture is.\n", encoding="utf-8")
    n = sum(1 for _ in kit.rglob("*.png"))
    print(f"sheets: {OUT}\nkit: {n} textures in {kit}")


if __name__ == "__main__":
    main()
