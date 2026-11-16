# Hand-drawn textures

Textures drawn by hand. They win over the ones `tools/textures.py` paints from code.

- Put a 16x16 PNG in `art/block/` with the same name as the texture it replaces
  (see `dist/texture-sheet/textures.csv` after running `python tools/texture_sheet.py`).
- **Aurora crystals, shards and shattered crystal:** draw the **blue** one only (`aurora_crystal_blue.png`,
  `_1`, `_2`, `aurora_shard_blue.png`, `shattered_aurora_crystal_blue.png` and its `_1` to `_3`). Cyan, green, rose and violet are recoloured from it automatically, keeping
  your shading (each colour's palette is in `AURORA` in `tools/textures.py`). Drawing a colour by hand
  as well (say `aurora_crystal_rose.png`) uses yours instead of the recolour.
- Then run `python tools/textures.py`: it writes every texture and rebuilds the `_s` specular maps
  (the shader-pack glow and shine) from your art, then `gradlew :mc1211:build`.

Hand-drawn so far (2026-11-14): the glowcap, the glowcap block and its two variants, and the blue
aurora crystal (three variants) and shard, and the blue shattered crystal (four variants: a new
`_3` was added for it, 2026-11-16).
