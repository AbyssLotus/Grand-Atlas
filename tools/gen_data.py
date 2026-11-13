"""Generate Grand Atlas's worldgen data from the game's own vanilla files.

    python tools/gen_data.py                    Minecraft 26.3 (run from the project root after one gradle build)
    python tools/gen_data.py --target 1.21.1    the backport: writes mc1211/src/main/resources from the
                                                1.21.1 game's files, in 1.21.1's formats (see LEGACY below)

Reads vanilla's overworld noise settings, dimension type and density functions out of the dev
jar under build/moddev/artifacts, patches them, and writes:

  data/minecraft/dimension_type/overworld.json        height -512..1024
  data/minecraft/worldgen/noise_settings/overworld.json
      noise height -512..1024; every minecraft:overworld/* function it names (router,
      aquifers, spawn target, debug) points at our grandatlas:overworld/* set instead
  data/minecraft/dimension/overworld.json             noise generator + grandatlas:atlas biome source
                                                      (biome table read from core/BiomeKind.java)
  data/grandatlas/worldgen/material_rule/overworld[/surface].json
      our surface rules (salt flat crust) ahead of all of vanilla's minecraft:overworld rule
  data/minecraft/tags/fluid/water.json                river water is water
  assets/grandatlas/blockstates/river_water.json      drawn with vanilla's water model
  data/grandatlas/worldgen/biome/*.json               our biomes (salt_flat: vanilla desert minus
                                                      structures and vegetation), plus is_overworld
                                                      tag entries and lang names
  data/grandatlas/worldgen/density_function/overworld/
      final_density              vanilla's, with grandatlas terrain in place of sloped_cheese,
                                 the slides moved to the new floor and ceiling, and cave
                                 damping added to the caves
      terrain                    cache(grandatlas:terrain)
      cave_damping               cache(grandatlas:cave_damping): thins vanilla caves inside tall mountains
      caverns, caves_fine        Grand Atlas caves: caverns in the deep mix, pits and fissures per block
      preliminary_surface_level  the core's exact surface height (replaces vanilla's top-down scan)
      chunk_surface_level        the same estimate, direct (no 16-block interpolation)
      temperature, vegetation, continents, erosion, depth, ridges   climate from the core

  data/minecraft/worldgen/placed_feature/*.json       ores and cave features re-spread for the deeper
                                                      world (M7a; ORES and CAVE_BANDS below)
  data/minecraft/worldgen/structure/{ancient_city,trial_chambers}.json   moved down: the abyss, the deep
  data/minecraft/tags/block/{base_stone_overworld,deepslate_ore_replaceables}.json   + blackstone, basalt

Vanilla caves, aquifers, ore veins and surface (material) rules stay as they are. Every
patch is counted and asserted, so a vanilla format change fails loudly here instead of
quietly generating vanilla terrain. Don't hand-edit the outputs; change this script.
"""
import glob
import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
# LEGACY: the 1.21.1 backport (docs/BACKPORT_1.21.1_PLAN.md). Everything below is written once, in
# 26.3's shapes; for 1.21.1 the vanilla inputs come from the 1.21.1 game and write() translates
# each output (legacy()): density functions and features to the old layouts, the surface rules
# inlined into the noise settings (1.21.1 has no rule registry), biome colours and particles moved
# into "effects", loot conditions spelled out.
LEGACY = "--target" in sys.argv and sys.argv[sys.argv.index("--target") + 1] == "1.21.1"
PROJECT = ROOT / "mc1211" if LEGACY else ROOT
RES = PROJECT / "src" / "main" / "resources"

MIN_Y, HEIGHT = -512, 1536            # world -512 .. 1023 (M7a: the user's hard requirement)
VANILLA_MIN_Y = -64
TOP_SLIDE = (992, 1008)               # vanilla 240 -> 256: terrain fades to air just under the limit

NOISE_SETTINGS = "data/minecraft/worldgen/noise_settings/overworld.json"
DIMENSION_TYPE = "data/minecraft/dimension_type/overworld.json"
VANILLA_DF = "data/minecraft/worldgen/density_function/overworld/{}.json"
OURS = "grandatlas:overworld/{}"
CLIMATE = ["temperature", "vegetation", "continents", "erosion", "depth", "ridges"]
# Every vanilla overworld function the noise settings may name, and what replaces it.
REDIRECTED = CLIMATE + ["final_density", "preliminary_surface_level", "chunk_surface_level"]


def vanilla_jar():
    for jar in sorted(glob.glob(str(PROJECT / "build" / "moddev" / "artifacts" / "*.jar"))):
        with zipfile.ZipFile(jar) as z:
            if NOISE_SETTINGS in z.namelist():
                return jar
    sys.exit("No dev jar with vanilla worldgen data under build/moddev/artifacts; run a gradle build first.")


def load(jar, name):
    with zipfile.ZipFile(jar) as z:
        return json.loads(z.read(name))


RULES = {}  # LEGACY: the surface rules by id, inlined into the noise settings at the end


def state(s):
    """A block state in 1.21.1's spelling."""
    if isinstance(s, str):
        return {"Name": s}
    out = {"Name": s["id"]}
    if s.get("properties"):
        out["Properties"] = s["properties"]
    return out


def stone_depth(surface, add, secondary=0):
    return {"type": "minecraft:stone_depth", "add_surface_depth": add, "offset": 0, "secondary_depth_range": secondary, "surface_type": surface}


# 26.3 names these conditions; 1.21.1 spells them out (values from 26.3's material_condition files).
NAMED_CONDITIONS = {
    "minecraft:on_floor": stone_depth("floor", False),
    "minecraft:under_floor": stone_depth("floor", True),
    "minecraft:deep_under_floor": stone_depth("floor", True, 6),
    "minecraft:very_deep_under_floor": stone_depth("floor", True, 30),
    "minecraft:on_ceiling": stone_depth("ceiling", False),
    "minecraft:under_ceiling": stone_depth("ceiling", True),
}


def legacy_condition(c):
    if isinstance(c, str):
        return NAMED_CONDITIONS[c]
    c = dict(c)
    kind = c["type"]
    if kind == "minecraft:noise_threshold":
        if c.pop("is_3d", False):
            c["type"] = "grandatlas:noise_3d"  # world/Noise3d.java: 1.21.1's own reads the noise at y 0 only
    elif kind == "minecraft:biome":
        if isinstance(c["biome_is"], str):
            c["biome_is"] = [c["biome_is"]]
    elif kind == "minecraft:not":
        c["invert"] = legacy_condition(c["invert"])
    return c


def legacy_rule(node, vanilla):
    """A 26.3 material rule as a 1.21.1 surface rule, with every reference inlined."""
    if isinstance(node, str):
        if node == "minecraft:overworld":
            return vanilla
        return legacy_rule(RULES[node], vanilla)
    kind = node["type"]
    if kind == "minecraft:sequence":
        return {"type": kind, "sequence": [legacy_rule(n, vanilla) for n in node["sequence"]]}
    if kind == "minecraft:condition":
        return {"type": kind, "if_true": legacy_condition(node["if_true"]), "then_run": legacy_rule(node["then_run"], vanilla)}
    if kind == "minecraft:block":
        return {"type": kind, "result_state": state(node["result_state"])}
    if kind == "grandatlas:cave_gate":
        return {"type": kind, "rule": legacy_rule(node["rule"], vanilla)}
    assert kind in ("grandatlas:strata", "grandatlas:rainbow", "minecraft:bandlands"), kind
    return node


def legacy_density(node):
    def fix(n):
        if isinstance(n, dict) and n.get("type") == "minecraft:cache" and "input" in n:
            return {"type": "minecraft:cache_once", "argument": n["input"]}
        if isinstance(n, dict) and "left" in n and "right" in n:
            n = dict(n)
            n["argument1"], n["argument2"] = n.pop("left"), n.pop("right")
        return n
    return transform(node, fix)


def legacy_feature(d):
    if "config" in d:
        return d  # read from the 1.21.1 game: already in its layout
    if d["type"] == "minecraft:simple_block":
        return {"type": d["type"], "config": {"to_place": {"type": "minecraft:simple_state_provider", "state": state(d["to_place"])}}}
    assert list(d) == ["type"], d
    return {"type": d["type"], "config": {}}


SILK_TOUCH = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def legacy_loot(node):
    def fix(n):
        if isinstance(n, dict) and "condition" in n and "name" in n:
            n = dict(n)
            c = n.pop("condition")
            n["conditions"] = [SILK_TOUCH if c == "minecraft:tool/can_silk_touch" else {"condition": c["type"]}]
        return n
    return transform(node, fix)


BIOME_COLOURS = {"minecraft:visual/fog_color": "fog_color", "minecraft:visual/sky_color": "sky_color",
                 "minecraft:visual/water_fog_color": "water_fog_color"}


def legacy_biome(b):
    """Colours and particles back in "effects", as whole numbers (26.3 keeps them in "attributes")."""
    effects = b["effects"]
    for key, value in b.pop("attributes", {}).items():
        if key in BIOME_COLOURS:
            effects[BIOME_COLOURS[key]] = value
        elif key == "minecraft:visual/ambient_particles":
            # 1.21.1 allows one drifting particle per biome: the biome's own (the last listed; the
            # aurora vault's five coloured motes ahead of it are 26.3 only).
            last = value["argument"][-1]
            effects["particle"] = {"options": last["particle"], "probability": last["probability"]}
        else:
            raise AssertionError(key)
    for key, value in list(effects.items()):
        if isinstance(value, str) and value.startswith("#"):
            effects[key] = int(value[1:], 16)
    return b


# Single-block features 26.3 has and 1.21.1 doesn't (its own are whole patches); ours are written
# below. None: needs a feature that only a backport mod has, so it is left out.
LEGACY_FEATURES = {"minecraft:red_mushroom": "grandatlas:red_mushroom", "minecraft:brown_mushroom": "grandatlas:brown_mushroom",
                   "minecraft:large_fern": "grandatlas:large_fern", "minecraft:fallen_oak_tree": None}


def parity_base_amplitude(mods):
    """The base amplitude at which a 26.3 noise equals 1.21.1's noise with these amplitudes
    (26.3's NormalNoise.computeParityBaseAmplitude). 26.3 scales a noise so its spread matches its
    stated amplitude; 1.21.1 scales by the span of its octaves. A 26.3 noise of base amplitude b is
    therefore 1.21.1's noise times b over this, which is how the amplitudes are written for 1.21.1:
    the same pattern at the same strength, so the same thresholds cut the same patches."""
    n = len(mods)
    first = 2.0 ** (n - 1) / (2.0 ** n - 1.0)
    amps = [first * 0.5 ** i * m for i, m in enumerate(mods) if m != 0.0]
    used = [i for i, m in enumerate(mods) if m != 0.0]
    new = (sum(abs(a) for a in amps) / 3.0) / (0.2702247831245211 * sum(a * a for a in amps) ** 0.5 * 2.0 ** 0.5)
    old = 0.5 / 3.0 / (0.1 * (1.0 + 1.0 / (used[-1] - used[0] + 1)))
    return old / new


assert abs(parity_base_amplitude([1.0, 0.0, 1.0]) - 1.1817507833955654) < 1e-9  # vanilla's sulfur_cave_gradient


def legacy_placement(mods):
    """1.21.1 caps a count at 256 (the deep world's counts go past it) and has no fixed offset."""
    out = []
    for m in mods:
        if m["type"] == "minecraft:count":
            c = m["count"]
            top = c if isinstance(c, int) else c["max_inclusive"]
            if top > 256:
                # two counts multiply: each of the first's tries is repeated by the second
                k = -(-top // 256)
                first = round(c / k) if isinstance(c, int) else dict(c, max_inclusive=round(c["max_inclusive"] / k),
                                                                      min_inclusive=round(c["min_inclusive"] / k))
                out += [{"type": "minecraft:count", "count": first}, {"type": "minecraft:count", "count": k}]
                continue
        elif m["type"] == "minecraft:offset":
            assert m["x"] == 0 and m["z"] == 0, m
            m = {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": m["y"]}
        out.append(m)
    return out


def legacy(rel, data):
    """(path, data) for 1.21.1, or (None, None) when the file has no 1.21.1 counterpart."""
    if "/worldgen/material_rule/" in rel:
        RULES["grandatlas:" + rel.split("/material_rule/", 1)[1][:-5]] = data
        return None, None
    if "/worldgen/feature/" in rel:
        return rel.replace("/worldgen/feature/", "/worldgen/configured_feature/"), legacy_feature(data)
    if "/worldgen/density_function/" in rel:
        return rel, legacy_density(data)
    if "/worldgen/noise/" in rel:
        mods = data.get("amplitude_modifiers") or [1.0] * data["octave_count"]
        scale = data.get("base_amplitude", 1.0) / parity_base_amplitude(mods)
        return rel, {"firstOctave": data["base_octave"], "amplitudes": [m * scale for m in mods]}
    if "/loot_table/" in rel:
        return rel, legacy_loot(data)
    if "/worldgen/placed_feature/" in rel:
        return rel, dict(data, placement=legacy_placement(data["placement"]))
    if "/worldgen/biome/" in rel and "/tags/" not in rel:
        return rel, legacy_biome(data)
    return rel, data


def write(rel, data):
    if LEGACY:
        rel, data = legacy(rel, data)
        if rel is None:
            return
    path = RES / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print("wrote", path.relative_to(ROOT))


def ours_df(name, data):
    write(f"data/grandatlas/worldgen/density_function/overworld/{name}.json", data)


def transform(node, fn):
    """Rebuilds a JSON tree bottom-up; fn(node) may return a replacement."""
    if isinstance(node, list):
        node = [transform(n, fn) for n in node]
    elif isinstance(node, dict):
        node = {fn(k) if isinstance(k, str) else k: transform(v, fn) for k, v in node.items()}
    return fn(node)


def main():
    jar = vanilla_jar()
    print("vanilla data from", pathlib.Path(jar).name)
    counts = {}

    def hit(what):
        counts[what] = counts.get(what, 0) + 1

    # ---- dimension type: just the height. Cloud height (an attribute since 1.21.11) stays at
    # vanilla's 192, so tall peaks rise through a cloud sea.
    dim_type = load(jar, DIMENSION_TYPE)
    dim_type.update(min_y=MIN_Y, height=HEIGHT, logical_height=HEIGHT)
    write(DIMENSION_TYPE, dim_type)

    # ---- noise settings: our height, our function set.
    ns = load(jar, NOISE_SETTINGS)
    ns["noise"].update(min_y=MIN_Y, height=HEIGHT)
    vanilla_final = None
    if LEGACY:
        # 1.21.1's router: the climate slots name vanilla functions or hold them inline, the final
        # density is inline, and the preliminary surface is found by scanning
        # initial_density_without_jaggedness (world/ClimateDensity "initial", mixin/NoiseChunkMixin).
        router = ns["noise_router"]
        for channel in CLIMATE:
            assert channel in router, channel
            router[channel] = OURS.format(channel)
        assert "initial_density_without_jaggedness" in router, list(router)
        router["initial_density_without_jaggedness"] = {"type": "grandatlas:climate", "channel": "initial"}
        vanilla_final = router["final_density"]
        router["final_density"] = OURS.format("final_density")
        counts["redirect"] = len(CLIMATE) + 2

    def redirect(node):
        if isinstance(node, str) and node.startswith("minecraft:overworld/"):
            name = node.split("/", 1)[1]
            assert name in REDIRECTED, f"noise settings names an unexpected vanilla function: {node}"
            hit("redirect")
            return OURS.format(name)
        return node

    if not LEGACY:
        ns = transform(ns, redirect)
    leftover = re.findall(r'"minecraft:overworld/[^"]+"', json.dumps(ns["noise_router"]))
    assert not leftover, f"unredirected vanilla functions: {leftover}"
    assert ns["noise_router"]["final_density"] == OURS.format("final_density"), ns["noise_router"]
    if not LEGACY:
        assert ns["aquifers"]["surface_level"] == OURS.format("preliminary_surface_level"), ns["aquifers"]
        write(NOISE_SETTINGS, ns)

    # ---- final density: vanilla's caves and slides around our terrain.
    def patch_final(node):
        if node == "minecraft:overworld/sloped_cheese":
            hit("sloped_cheese")
            return OURS.format("terrain")
        if isinstance(node, dict) and node.get("type") == "minecraft:y_clamped_gradient":  # 1.21.1's spelling
            node = dict(node)
            span = (node["from_y"], node["to_y"])
            if span == (VANILLA_MIN_Y, -40):
                node["from_y"], node["to_y"] = MIN_Y, MIN_Y + 24
                hit("bottom_slide")
            elif span == (240, 256):
                node["from_y"], node["to_y"] = TOP_SLIDE
                hit("top_slide")
            return node
        if isinstance(node, dict) and node.get("type") == "minecraft:gradient" and node.get("axis") == "y":
            node = dict(node)
            span = (node["from_coordinate"], node["to_coordinate"])
            if span == (VANILLA_MIN_Y, -40):
                node["from_coordinate"], node["to_coordinate"] = MIN_Y, MIN_Y + 24
                hit("bottom_slide")
            elif span == (240, 256):
                node["from_coordinate"], node["to_coordinate"] = TOP_SLIDE
                hit("top_slide")
        return node

    final = transform(vanilla_final if LEGACY else load(jar, VANILLA_DF.format("final_density")), patch_final)

    # ---- cave damping: vanilla caves would hollow out 600-block mountains. Add the core's
    # bias to the three places caves carve: the surface entrances (range_choice in range, the
    # top ~16 blocks), the whole deep cave mix (out of range), and the noodles. The terrain
    # term itself is never biased, so the surface doesn't move.
    def damped(ref, which="cave_damping"):
        return {"type": "minecraft:add", "left": ref, "right": OURS.format(which)}

    def damp_caves(node):
        if isinstance(node, dict) and node.get("type") == "minecraft:range_choice" \
                and node.get("input") == OURS.format("terrain"):
            hit("damp_deep_caves")
            node = dict(node)
            node["when_in_range"] = transform(node["when_in_range"], lambda n: damped(n)
                                              if n == "minecraft:overworld/caves/entrances" and not hit("damp_entrances")
                                              else n)
            # Grand Atlas caverns join the deep cave mix (they're big enough for the 4x8 grid).
            node["when_out_of_range"] = {"type": "minecraft:min", "left": damped(node["when_out_of_range"]),
                                         "right": OURS.format("caverns")}
        elif node == "minecraft:overworld/caves/noodle":
            hit("damp_noodle")
            # kept off the surface too: noodles scored grooves in it. Ours reach the floor (below).
            return damped(OURS.format("noodle"), "noodle_damping")
        return node

    final = transform(final, damp_caves)
    # Pits and fissures are thin, so they join per block at the top level, beside vanilla's
    # noodles and outside the interpolation: final = add(min(min(interpolated, noodle), fine), beardifier).
    if LEGACY:
        # 1.21.1 adds the beardifier in code, so the whole function is min(interpolated, noodle).
        assert final["type"] == "minecraft:min", final["type"]
        final = {"type": "minecraft:min", "left": final, "right": OURS.format("caves_fine")}
    else:
        assert final["type"] == "minecraft:add" and final["right"]["type"] == "minecraft:beardifier", final["type"]
        final["left"] = {"type": "minecraft:min", "left": final["left"], "right": OURS.format("caves_fine")}
    hit("fine_caves")
    # M6: the sky isles (core/SkyIsles) are solid over air, so they are the larger of the two, added
    # last: no river cut, cave or slide holes them.
    if LEGACY:
        final = {"type": "minecraft:max", "left": final, "right": OURS.format("sky_isles")}
    else:
        final["left"] = {"type": "minecraft:max", "left": final["left"], "right": OURS.format("sky_isles")}
    ours_df("sky_isles", {"type": "grandatlas:sky_isles"})
    ours_df("final_density", final)
    # Vanilla's noodles stop at y -60 (and its 2D spaghetti tunnels hug -64..64), so below that the
    # 0.5 deep had only sparse cheese caves (1-5% open). Until M7b's passage networks, the noodles
    # run down to the floor: vanilla's density of thin tunnels at every depth.
    def deep_noodles(node):
        if isinstance(node, dict) and node.get("type") == "minecraft:range_choice" and node.get("input") == "minecraft:y":
            assert (node["min_inclusive"], node["max_exclusive"]) == (-60.0, 321.0), node
            hit("noodle_floor")
            node = dict(node)
            node["min_inclusive"] = float(MIN_Y + 8)
        return node

    ours_df("noodle", transform(load(jar, VANILLA_DF.format("caves/noodle")), deep_noodles))
    ours_df("cave_damping", {"type": "minecraft:cache", "input": {"type": "grandatlas:cave_damping"}})
    ours_df("noodle_damping", {"type": "minecraft:cache", "input": {"type": "grandatlas:cave_damping", "noodle": True}})
    ours_df("caverns", {"type": "minecraft:cache", "input": {"type": "grandatlas:caves", "mode": "caverns"}})
    ours_df("caves_fine", {"type": "grandatlas:caves", "mode": "fine"})

    ours_df("terrain", {"type": "minecraft:cache", "input": {"type": "grandatlas:terrain", "jagged": True}})
    if not LEGACY:
        ours_df("preliminary_surface_level", {"type": "grandatlas:climate", "channel": "surface"})

    # Vanilla interpolates the preliminary surface across 16-block cells for the surface rules,
    # which overshoots on tall walls and leaves bare stone; ours is computed per 4-block cell already.
    if not LEGACY:  # 1.21.1 has no such function: mixin/SurfaceContextMixin does this job there
        vanilla_chunk_surface = load(jar, VANILLA_DF.format("chunk_surface_level"))
        assert vanilla_chunk_surface.get("type") == "minecraft:interpolated", vanilla_chunk_surface
        hit("chunk_surface")
        ours_df("chunk_surface_level", {"type": "grandatlas:climate", "channel": "surface"})

    for channel in CLIMATE:
        ours_df(channel, {"type": "grandatlas:climate", "channel": channel})

    expected = {"sloped_cheese": 3, "bottom_slide": 1, "top_slide": 1, "chunk_surface": 1,
                "damp_deep_caves": 1, "damp_entrances": 1, "damp_noodle": 1, "fine_caves": 1, "noodle_floor": 4}
    if LEGACY:
        del expected["chunk_surface"]
    got = {k: v for k, v in counts.items() if k != "redirect"}
    assert got == expected, f"vanilla functions changed shape: patched {got}, expected {expected}"
    print(f"redirected {counts['redirect']} vanilla function references in the noise settings")

    # ---- surface (material) rules: ours first, then all of vanilla's overworld rule. Nothing of
    # vanilla's is overridden; the noise settings just point at our wrapper.
    if not LEGACY:
        ns_path = RES / NOISE_SETTINGS
        ns = json.loads(ns_path.read_text(encoding="utf-8"))
        assert ns["material_rule"] == "minecraft:overworld", ns["material_rule"]
        ns["material_rule"] = "grandatlas:overworld"
        write(NOISE_SETTINGS, ns)
    write("data/grandatlas/worldgen/material_rule/overworld.json", {
        "type": "minecraft:sequence",
        "sequence": [
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"},
             "then_run": "grandatlas:overworld/surface"},
            # M7e cave biomes: their rock, floors and ceilings (cave_rules below), ahead of the strata.
            "grandatlas:overworld/caves",
            # The abyss's tuff layers and blackstone base (world/Strata.java); null above y -200
            # and in the bedrock, so vanilla's rule (deepslate, bedrock, ore veins) does the rest.
            {"type": "grandatlas:strata"},
            "minecraft:overworld",
        ],
    })
    write("data/grandatlas/worldgen/material_rule/overworld/surface.json", {
        "type": "minecraft:sequence",
        "sequence": [salt_flat_surface(), red_desert_surface(), river_bed_surface(), salt_marsh_surface(), pebble_beach_surface(),
                     *fantasy_surfaces()],
    })

    # ---- perched water: our feature, once per chunk in every overworld biome.
    write("data/grandatlas/worldgen/feature/perched_water.json", {"type": "grandatlas:perched_water"})
    write("data/grandatlas/worldgen/placed_feature/perched_water.json", {"feature": "grandatlas:perched_water", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/perched_water.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:perched_water",
        "step": "lakes",
    })

    # ---- the underground's water (world/DeepWater.java, M7d): cavern lakes, seas, flooded caves.
    # Placed late in decoration (see the step below).
    write("data/grandatlas/worldgen/feature/deep_water.json", {"type": "grandatlas:deep_water"})
    write("data/grandatlas/worldgen/placed_feature/deep_water.json", {"feature": "grandatlas:deep_water", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/deep_water.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:deep_water",
        # After geodes, dungeons and ores, which open holes in the rock, so it plugs theirs too.
        "step": "fluid_springs",
    })

    # ---- set-piece rock (world/SetPieceDressing.java, M7e): bone, geode walls, banded islands.
    write("data/grandatlas/worldgen/feature/set_piece_dressing.json", {"type": "grandatlas:set_piece_dressing"})
    write("data/grandatlas/worldgen/placed_feature/set_piece_dressing.json", {"feature": "grandatlas:set_piece_dressing", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/set_piece_dressing.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:set_piece_dressing",
        "step": "local_modifications",
    })

    # ---- cave biome dressing (world/CaveDressing.java, M7e): roots, icicles, crystal beams.
    write("data/grandatlas/worldgen/feature/cave_dressing.json", {"type": "grandatlas:cave_dressing"})
    write("data/grandatlas/worldgen/placed_feature/cave_dressing.json", {"feature": "grandatlas:cave_dressing", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/cave_dressing.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:cave_dressing",
        "step": "underground_decoration",
    })

    # ---- meso and micro detail: boulders, logs, pond and sinkhole dressing (world/MicroDetail.java),
    # after perched_water fills the water and before trees and plants.
    write("data/grandatlas/worldgen/feature/micro_detail.json", {"type": "grandatlas:micro_detail"})
    write("data/grandatlas/worldgen/placed_feature/micro_detail.json", {"feature": "grandatlas:micro_detail", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/micro_detail.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:micro_detail",
        "step": "local_modifications",
    })

    # ---- waterfall dressing (world/FallDetail.java): moss, vines and ferns round the falls.
    write("data/grandatlas/worldgen/feature/fall_detail.json", {"type": "grandatlas:fall_detail"})
    write("data/grandatlas/worldgen/placed_feature/fall_detail.json", {"feature": "grandatlas:fall_detail", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/fall_detail.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:fall_detail",
        "step": "local_modifications",
    })

    # ---- crag floaters (world/FloaterCleanup.java): rock the crags' undercuts cut loose.
    write("data/grandatlas/worldgen/feature/floater_cleanup.json", {"type": "grandatlas:floater_cleanup"})
    write("data/grandatlas/worldgen/placed_feature/floater_cleanup.json", {"feature": "grandatlas:floater_cleanup", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/floater_cleanup.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:floater_cleanup",
        "step": "local_modifications",
    })

    # ---- steep ground (world/SteepRock.java): bare rock where soil wouldn't hold.
    write("data/grandatlas/worldgen/feature/steep_rock.json", {"type": "grandatlas:steep_rock"})
    write("data/grandatlas/worldgen/placed_feature/steep_rock.json", {"feature": "grandatlas:steep_rock", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/steep_rock.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:steep_rock",
        "step": "local_modifications",
    })

    # ---- country detail (world/CountryDetail.java): boulders, logs and ground cover between the meso sites.
    write("data/grandatlas/worldgen/feature/country_detail.json", {"type": "grandatlas:country_detail"})
    write("data/grandatlas/worldgen/placed_feature/country_detail.json", {"feature": "grandatlas:country_detail", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/country_detail.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:country_detail",
        # A step after local_modifications, so it always follows steep_rock and micro_detail there
        # (it dresses the rock they leave), and still before trees and plants.
        "step": "surface_structures",
    })

    # ---- hand-built assets (world/AtlasAssets.java): pack trees, god trees, rock formations (docs/CREDITS.md).
    # At the strongholds step: after country_detail (surface_structures), before trees and plants, so the
    # game's own trees fill in round the big ones.
    write("data/grandatlas/worldgen/feature/atlas_assets.json", {"type": "grandatlas:atlas_assets"})
    write("data/grandatlas/worldgen/placed_feature/atlas_assets.json", {"feature": "grandatlas:atlas_assets", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/atlas_assets.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:atlas_assets",
        "step": "strongholds",
    })

    # ---- river water (world/RiverWater.java): water to the game and to vanilla water, drawn as water.
    write("data/minecraft/tags/fluid/water.json", {"replace": False, "values": ["grandatlas:river_water"]})
    write("assets/grandatlas/blockstates/river_water.json", {"variants": {"": {"model": "minecraft:block/water"}}})

    # ---- Grand Atlas biomes.
    desert = load(jar, "data/minecraft/worldgen/biome/desert.json")
    write("data/grandatlas/worldgen/biome/salt_flat.json", salt_flat_biome(desert))
    write("data/grandatlas/worldgen/biome/red_desert.json", red_desert_biome(desert))
    write("data/grandatlas/worldgen/biome/river.json", river_biome(load(jar, "data/minecraft/worldgen/biome/river.json")))
    # Smooth patches (a few dozen blocks) for the shingle beach's cobble and stone.
    write("data/grandatlas/worldgen/noise/shingle.json", {"base_amplitude": 1.0, "base_octave": -5, "octave_count": 2})
    write("data/grandatlas/worldgen/biome/salt_marsh.json", salt_marsh_biome(load(jar, "data/minecraft/worldgen/biome/swamp.json")))
    write("data/grandatlas/worldgen/biome/pebble_beach.json", pebble_beach_biome(load(jar, "data/minecraft/worldgen/biome/beach.json")))
    write("data/grandatlas/worldgen/biome/crater_lake.json", crater_lake_biome(river_biome(load(jar, "data/minecraft/worldgen/biome/river.json"))))
    write("data/minecraft/tags/worldgen/biome/is_overworld.json", {"values": [k for _, k in our_biomes()]})
    lang = RES.parent / "resources" / "assets" / "grandatlas" / "lang" / "en_us.json"
    lang.parent.mkdir(parents=True, exist_ok=True)
    names = {f"biome.{k.replace(':', '.')}": name for name, k in our_biomes()}
    names["block.grandatlas.river_water"] = "River Water"
    # the Grand Atlas guide book (mc1211 atlas/; 1.21.1 only)
    names["item.grandatlas.grand_atlas"] = "Grand Atlas"
    names["item.grandatlas.ripped_map"] = "Ripped Map"
    names.update({f"block.{NS}.{n}": v[3] for n, v in NEW_BLOCKS.items()})
    lang.write_text(json.dumps(names, indent=2) + "\n", encoding="utf-8")
    print("wrote", lang.relative_to(ROOT))

    # ---- M6 fantasy (world/FantasyDetail.java): lava in the volcanoes, falls and hanging plants on the sky isles.
    write("data/grandatlas/worldgen/feature/fantasy_detail.json", {"type": "grandatlas:fantasy_detail"})
    write("data/grandatlas/worldgen/placed_feature/fantasy_detail.json", {"feature": "grandatlas:fantasy_detail", "placement": []})
    write("data/grandatlas/neoforge/biome_modifier/fantasy_detail.json", {
        "type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": "grandatlas:fantasy_detail",
        "step": "local_modifications",
    })
    fantasy_biomes(jar)

    respread(jar)
    new_blocks(jar)
    cave_biomes(jar)
    if LEGACY:
        # The surface rules, inline: ours, then the game's own rule exactly as it ships.
        ns["surface_rule"] = legacy_rule("grandatlas:overworld", ns["surface_rule"])
        write(NOISE_SETTINGS, ns)

    # ---- the overworld itself: vanilla noise generator, our settings, our biome source.
    write("data/minecraft/dimension/overworld.json", {
        "type": "minecraft:overworld",
        "generator": {
            "type": "minecraft:noise",
            "settings": "minecraft:overworld",
            "biome_source": {"type": "grandatlas:atlas", "biomes": dict(biome_kinds())},
        },
    })


def U(lo, hi):
    return {"type": "minecraft:uniform", "max_inclusive": {"absolute": hi}, "min_inclusive": {"absolute": lo}}


def T(lo, hi):
    return {"type": "minecraft:trapezoid", "max_inclusive": {"absolute": hi}, "min_inclusive": {"absolute": lo}}


# ---- M7e new blocks (world/ModBlocks.java; textures and LabPBR specular maps by tools/textures.py)

NS = "grandatlas"
# name -> (model kind, textures, random variants?, display name, tool tag)
NEW_BLOCKS = {
    "selenite_block": ("cube", ["selenite_block"], False, "Selenite", "pickaxe"),
    "selenite_shard": ("facing_cross", ["selenite_shard"], False, "Selenite Shard", "pickaxe"),
    "glowworm_silk": ("silk", ["glowworm_silk", "glowworm_silk_plant"], False, "Glowworm Silk", "hoe"),
    "glowcap": ("cross", ["glowcap"], False, "Glowcap", None),
    "glowcap_block": ("cube", ["glowcap_block", "glowcap_block_1", "glowcap_block_2"], True, "Glowcap Block", "axe"),
    "icicle": ("icicle", ["icicle_tip", "icicle_middle", "icicle_base"], False, "Icicle", "pickaxe"),
    "hoarfrost": ("cube", ["hoarfrost", "hoarfrost_1", "hoarfrost_2"], True, "Hoarfrost", "pickaxe"),
    "abyssal_sand": ("cube", ["abyssal_sand"], True, "Abyssal Sand", "shovel"),
    "cinder_moss": ("cube", ["cinder_moss", "cinder_moss_1", "cinder_moss_2"], True, "Cinder Moss", "hoe"),
    "cinder_moss_carpet": ("carpet", ["cinder_moss", "cinder_moss_1", "cinder_moss_2"], True, "Cinder Moss Carpet", "hoe"),
}
NEW_BLOCKS["amber_sap"] = ("cube", ["amber_sap", "amber_sap_1", "amber_sap_2"], True, "Amber Sap", "axe")
AURORA_COLOURS = ["green", "cyan", "blue", "violet", "rose"]
for _c in AURORA_COLOURS:
    NEW_BLOCKS[f"aurora_crystal_{_c}"] = ("cube", [f"aurora_crystal_{_c}", f"aurora_crystal_{_c}_1", f"aurora_crystal_{_c}_2"], True,
                                          f"{_c.capitalize()} Aurora Crystal", "pickaxe")
    NEW_BLOCKS[f"aurora_shard_{_c}"] = ("facing_cross", [f"aurora_shard_{_c}"], False, f"{_c.capitalize()} Aurora Shard", "pickaxe")
    NEW_BLOCKS[f"aurora_veil_{_c}"] = ("silk", [f"aurora_veil_{_c}_tip", f"aurora_veil_{_c}"], False, f"{_c.capitalize()} Aurora Veil", None)
    NEW_BLOCKS[f"shattered_aurora_crystal_{_c}"] = ("cube", [f"shattered_aurora_crystal_{_c}", f"shattered_aurora_crystal_{_c}_1",
                                                    f"shattered_aurora_crystal_{_c}_2"], True, f"Shattered {_c.capitalize()} Aurora Crystal", "pickaxe")


def assets(rel, data):
    path = RES / "assets" / NS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


# LEGACY: 1.21.1 draws a block solid unless its model says otherwise (26.3 reads it off the texture).
SEE_THROUGH = {"selenite_block": "minecraft:translucent", "amber_sap": "minecraft:translucent"}


def model(name, parent, textures):
    data = {"parent": parent, "textures": textures}
    if LEGACY:
        if parent == "minecraft:block/cross":
            data["render_type"] = "minecraft:cutout"
        elif name in SEE_THROUGH:
            data["render_type"] = SEE_THROUGH[name]
    assets(f"models/block/{name}.json", data)
    return f"{NS}:block/{name}"


def turned(models):
    """Every model at each of the four turns: one picked at random per block, as vanilla's sand."""
    return [{"model": m, "y": r} if r else {"model": m} for m in models for r in (0, 90, 180, 270)]


def new_blocks(jar):
    lang = {}
    for name, (kind, texes, varied, title, tool) in NEW_BLOCKS.items():
        item_model = None
        if kind == "cube":
            models = [model(tx if tx != name else name, "minecraft:block/cube_all", {"all": f"{NS}:block/{tx}"}) for tx in texes]
            assets(f"blockstates/{name}.json", {"variants": {"": turned(models) if varied else {"model": models[0]}}})
            item_model = models[0]
        elif kind == "carpet":
            models = [model(f"{name}{tx[len('cinder_moss'):]}", "minecraft:block/carpet", {"wool": f"{NS}:block/{tx}"}) for tx in texes]
            assets(f"blockstates/{name}.json", {"variants": {"": turned(models)}})
            item_model = models[0]
        elif kind == "cross":
            m = model(name, "minecraft:block/cross", {"cross": f"{NS}:block/{texes[0]}"})
            assets(f"blockstates/{name}.json", {"variants": {"": {"model": m}}})
        elif kind == "facing_cross":
            m = model(name, "minecraft:block/cross", {"cross": f"{NS}:block/{texes[0]}"})
            rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
            assets(f"blockstates/{name}.json", {"variants": {f"facing={f}": dict({"model": m}, **r) for f, r in rot.items()}})
        elif kind == "silk":
            tip = model(name, "minecraft:block/cross", {"cross": f"{NS}:block/{texes[0]}"})
            body = model(f"{name}_plant", "minecraft:block/cross", {"cross": f"{NS}:block/{texes[1]}"})
            assets(f"blockstates/{name}.json", {"variants": {"tip=true": {"model": tip}, "tip=false": {"model": body}}})
        elif kind == "icicle":
            ms = {part: model(f"icicle_{part}", "minecraft:block/cross", {"cross": f"{NS}:block/icicle_{part}"}) for part in ("tip", "middle", "base")}
            assets(f"blockstates/{name}.json", {"variants": {f"part={part}": {"model": m} for part, m in ms.items()}})
        if item_model is None:  # plants show as a flat item, as vanilla's do
            assets(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{texes[0] if kind != 'icicle' else 'icicle_middle'}"}})
            item_model = f"{NS}:item/{name}"
        if not LEGACY:
            assets(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": item_model}})
        elif item_model != f"{NS}:item/{name}":  # 1.21.1: an item's model is models/item/<name>
            assets(f"models/item/{name}.json", {"parent": item_model})
        lang[f"block.{NS}.{name}"] = title
        if name.startswith("aurora_crystal_"):
            # Mined, the perfect crystal shatters (the user, 2026-11-09); Silk Touch keeps it whole.
            write(f"data/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:alternatives", "children": [
                    {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"{NS}:{name}"},
                    {"type": "minecraft:item", "condition": {"type": "minecraft:survives_explosion"}, "name": f"{NS}:shattered_{name}"}]}]}]})
        else:
            write(f"data/{NS}/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for tool in ("pickaxe", "shovel", "hoe", "axe"):
        write(f"data/minecraft/tags/block/mineable/{tool}.json", {"replace": False, "values": [f"{NS}:{n}" for n, v in NEW_BLOCKS.items() if v[4] == tool]})
    write("data/minecraft/tags/block/climbable.json", {"replace": False, "values": [f"{NS}:glowworm_silk"]})
    # The giant glowcap: vanilla's huge red mushroom with our glowing cap (bone meal grows it too).
    if LEGACY:
        huge = load(jar, "data/minecraft/worldgen/configured_feature/huge_red_mushroom.json")
        assert "cap_provider" in huge["config"], huge
        huge["config"]["cap_provider"] = {"type": "minecraft:simple_state_provider", "state": {"Name": f"{NS}:glowcap_block"}}
        huge["config"]["foliage_radius"] = 3
        # 26.3's single-block features, under our ids (LEGACY_FEATURES)
        write(f"data/{NS}/worldgen/feature/red_mushroom.json", {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:red_mushroom"}})
        write(f"data/{NS}/worldgen/feature/brown_mushroom.json", {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:brown_mushroom"}})
        write(f"data/{NS}/worldgen/feature/large_fern.json", {"type": "minecraft:simple_block",
              "to_place": {"id": "minecraft:large_fern", "properties": {"half": "lower"}}})
    else:
        huge = load(jar, "data/minecraft/worldgen/feature/huge_red_mushroom.json")
        huge["cap_provider"] = {"id": f"{NS}:glowcap_block"}
        huge["foliage_radius"] = 3
    write(f"data/{NS}/worldgen/feature/huge_glowcap.json", huge)
    write(f"data/{NS}/worldgen/feature/glowcap_patch.json", {"type": "minecraft:simple_block", "to_place": {"id": f"{NS}:glowcap"}})
    for d in ("up", "down"):
        write(f"data/{NS}/worldgen/feature/selenite_shard_{d}.json", {"type": "minecraft:simple_block",
              "to_place": {"id": f"{NS}:selenite_shard", "properties": {"facing": d, "waterlogged": "false"}}})
    return lang


# ---- M7e cave biomes (core/AtlasCore caveBiome picks them in 3D; docs/M7_UNDERGROUND_PLAN.md)

def floor_placed(feature, count, lo, hi, offset=1):
    """Placed on a cave floor: a random height in the band, then down through air to the ground."""
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(lo, hi)},
        {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 16,
         "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
         "target_condition": {"type": "minecraft:solid"}},
        {"type": "minecraft:offset", "x": 0, "y": offset, "z": 0}, {"type": "minecraft:biome"}]}


def ceiling_placed(feature, count, lo, hi):
    """Placed under a cave ceiling: up through air to the rock, then a block down."""
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(lo, hi)},
        {"type": "minecraft:environment_scan", "direction_of_search": "up", "max_steps": 16,
         "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
         "target_condition": {"type": "minecraft:solid"}},
        {"type": "minecraft:offset", "x": 0, "y": -1, "z": 0}, {"type": "minecraft:biome"}]}


def block(b, **props):
    return {"type": "minecraft:block", "result_state": {"id": b, "properties": props} if props else b}


def patch(lo, hi, then, noise="grandatlas:cave_patch"):
    """A smooth 3D patch of the cave patch noise (no speckle)."""
    return {"type": "minecraft:condition",
            "if_true": {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi, "is_3d": True},
            "then_run": then}


def when(cond, then):
    return {"type": "minecraft:condition", "if_true": cond, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


INF = 1.0e308
CAVE_RULES = {
    # Ice caves: packed ice with veins of blue ice, snow underfoot.
    "grandatlas:ice_caves": seq(
        when("minecraft:on_floor", seq(patch(-0.2, INF, block("minecraft:snow_block")), block("minecraft:packed_ice"))),
        when("minecraft:on_ceiling", block("minecraft:packed_ice")),
        patch(0.3, INF, block("minecraft:blue_ice")),
        patch(0.12, 0.3, block("grandatlas:hoarfrost")),
        patch(-INF, -0.5, block("minecraft:ice")),
        block("minecraft:packed_ice")),  # ice through and through
    # Sandstone caves: sand floors, sandstone with smooth-sandstone bands.
    "grandatlas:sandstone_caves": seq(
        when("minecraft:on_floor", block("minecraft:sand")),
        when("minecraft:under_floor", block("minecraft:sand")),
        patch(0.25, INF, block("minecraft:smooth_sandstone")),
        block("minecraft:sandstone")),
    # Crystal caves: calcite, with amethyst in veins and on the ceilings.
    "grandatlas:crystal_caves": seq(
        when("minecraft:on_floor", seq(patch(0.4, INF, block("minecraft:amethyst_block")), block("minecraft:calcite"))),
        when("minecraft:on_ceiling", seq(patch(0.0, INF, block("minecraft:amethyst_block")), block("minecraft:calcite"))),
        patch(0.35, INF, block("minecraft:amethyst_block")),
        patch(-0.3, 0.35, block("minecraft:calcite"))),
    # Magma chambers: basalt and blackstone, magma underfoot.
    "grandatlas:magma_chambers": seq(
        when("minecraft:on_floor", seq(patch(0.15, INF, block("minecraft:magma_block")), patch(-0.2, 0.05, block("grandatlas:cinder_moss")),
                                       block("minecraft:basalt", axis="y"))),
        patch(0.1, INF, block("minecraft:basalt", axis="y")),
        patch(-INF, -0.3, block("minecraft:blackstone"))),
    # Abyssal shores: tuff and smooth basalt, gravel beaches with glowing stones.
    "grandatlas:abyssal_shores": seq(
        when("minecraft:on_floor", seq(patch(0.55, INF, block("minecraft:verdant_froglight", axis="y")),
                                       patch(-0.2, 0.55, block("grandatlas:abyssal_sand")), block("minecraft:tuff"))),
        when("minecraft:under_floor", seq(patch(-0.2, 0.55, block("grandatlas:abyssal_sand")))),
        patch(0.2, INF, block("minecraft:smooth_basalt")),
        block("minecraft:tuff")),
    # Fungal forests: mycelium over dirt.
    "grandatlas:fungal_forest": seq(
        when("minecraft:on_floor", block("minecraft:mycelium")),
        when("minecraft:under_floor", block("minecraft:dirt"))),
    # Root caves: moss and rooted earth underfoot, rooted earth overhead for the roots to hang from.
    "grandatlas:root_caves": seq(
        when("minecraft:on_floor", seq(patch(0.2, INF, block("minecraft:moss_block")), patch(-0.25, 0.2, block("minecraft:rooted_dirt")),
                                       block("minecraft:coarse_dirt"))),
        when("minecraft:on_ceiling", block("minecraft:rooted_dirt")),
        when("minecraft:under_ceiling", block("minecraft:rooted_dirt"))),
    # Glowworm grottos: damp moss and clay underfoot (the silk hangs from the ceilings: world/CaveDressing).
    "grandatlas:glowworm_grotto": seq(
        when("minecraft:on_floor", seq(patch(0.0, INF, block("minecraft:moss_block")), block("minecraft:clay")))),
    # The Hollow: moss over the islands' tops and the shores.
    # The Aurora Vault: a pale calcite floor (it takes the crystals' colours) with tuff in patches.
    "grandatlas:worldroot_hall": seq(
        when("minecraft:on_floor", seq(patch(-0.15, INF, block("minecraft:moss_block")), block("minecraft:rooted_dirt"))),
        when("minecraft:on_ceiling", block("minecraft:rooted_dirt")),
        when("minecraft:under_floor", block("minecraft:dirt"))),
    "grandatlas:hanging_peaks": seq(
        when("minecraft:on_floor", seq(patch(0.15, INF, block("minecraft:gravel")), block("minecraft:stone")))),
    "grandatlas:aurora_vault": seq(
        when("minecraft:on_floor", seq(patch(0.25, INF, block("minecraft:tuff")), block("minecraft:calcite"))),
        when("minecraft:under_floor", block("minecraft:calcite"))),
    "grandatlas:the_hollow": seq(
        when("minecraft:on_floor", block("minecraft:moss_block")),
        when("minecraft:under_floor", block("minecraft:dirt"))),
    # Titan's grave: moss and clay underfoot, moss overhead in patches (bone is laid after this, but
    # only into bare stone, so moss grown over the bones stays).
    "grandatlas:titans_grave": seq(
        when("minecraft:on_floor", seq(patch(-0.3, INF, block("minecraft:moss_block")), block("minecraft:clay"))),
        when("minecraft:on_ceiling", seq(patch(0.0, INF, block("minecraft:moss_block")))),
        when("minecraft:under_floor", block("minecraft:dirt"))),
    # Vanilla 26.3's sulfur caves keep their own bands (below -160 the strata would otherwise win).
    "minecraft:sulfur_caves": "minecraft:overworld/sulfur_cave_bands",
}

# Our own placed features (vanilla configured ones, our ids, so no biome's feature order clashes).
# Counts are high on purpose: a try lands at a random height and counts only if that is open cave
# within reach of a floor or ceiling, a few percent of the rock (vanilla's cave plants use 100-250).
CAVE_PLACED = {
    "fungal_red_mushroom": floor_placed("minecraft:huge_red_mushroom", 50, -480, 0),
    "fungal_brown_mushroom": floor_placed("minecraft:huge_brown_mushroom", 40, -480, 0),
    "fungal_small_red": floor_placed("minecraft:red_mushroom", 120, -480, 0),
    "fungal_small_brown": floor_placed("minecraft:brown_mushroom", 120, -480, 0),
    "fungal_shroomlight": ceiling_placed("grandatlas:shroomlight", 50, -480, 0),
    "fungal_glowcap": floor_placed("grandatlas:glowcap_patch", 160, -480, 0),
    "fungal_huge_glowcap": floor_placed("grandatlas:huge_glowcap", 40, -480, 0),
    "root_moss_ceiling": ceiling_placed("minecraft:moss_patch_ceiling", 200, -10, 260),
    "ice_spires": floor_placed("minecraft:ice_spike", 120, -110, 260),
    "ice_blue": ceiling_placed("minecraft:blue_ice", 220, -110, 260),
    "crystal_cluster_floor": floor_placed("grandatlas:amethyst_cluster_up", 220, -500, 0),
    "crystal_cluster_ceiling": ceiling_placed("grandatlas:amethyst_cluster_down", 220, -500, 0),
    "crystal_bud_floor": floor_placed("grandatlas:amethyst_bud_up", 80, -500, 0),
    "crystal_selenite_floor": floor_placed("grandatlas:selenite_shard_up", 160, -500, 0),
    "crystal_selenite_ceiling": ceiling_placed("grandatlas:selenite_shard_down", 160, -500, 0),
    "magma_small_columns": floor_placed("minecraft:small_basalt_columns", 80, -500, -250),
    "magma_large_columns": floor_placed("minecraft:large_basalt_columns", 25, -500, -250),
    "magma_delta": floor_placed("minecraft:delta", 120, -500, -250),
    "hollow_glowcap": floor_placed("grandatlas:glowcap_patch", 200, -500, -150),
    "hollow_huge_glowcap": floor_placed("grandatlas:huge_glowcap", 30, -500, -150),
    # The root hall's floor, filled (the user: "tons of trees, rocks, small pools and other stuff").
    "root_azalea": floor_placed("minecraft:azalea_tree", 70, -480, -100),
    "root_dark_oak": floor_placed("minecraft:dark_oak", 30, -480, -100),
    "root_glowcap": floor_placed("grandatlas:glowcap_patch", 160, -480, -100),
    "root_huge_glowcap": floor_placed("grandatlas:huge_glowcap", 14, -480, -100),
    "root_rock": floor_placed("minecraft:forest_rock", 40, -480, -100),
    "root_pool": floor_placed("minecraft:clay_pool_with_dripleaves", 70, -480, -100),
    "root_fallen": floor_placed("minecraft:fallen_oak_tree", 24, -480, -100),
    "root_large_fern": floor_placed("minecraft:large_fern", 120, -480, -100),
    "root_moss": floor_placed("minecraft:moss_patch", 120, -480, -100),
    "root_glow": {"feature": "minecraft:glow_lichen", "placement": [
        {"type": "minecraft:count", "count": 160}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(-480, 40)}, {"type": "minecraft:biome"}]},
    "peaks_glow": {"feature": "minecraft:glow_lichen", "placement": [
        {"type": "minecraft:count", "count": 420}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(-500, -150)}, {"type": "minecraft:biome"}]},
    "vault_glow": {"feature": "minecraft:glow_lichen", "placement": [
        {"type": "minecraft:count", "count": 120}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(-480, -60)}, {"type": "minecraft:biome"}]},
    "hollow_glow": {"feature": "minecraft:glow_lichen", "placement": [
        {"type": "minecraft:count", "count": 200}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(-500, -150)}, {"type": "minecraft:biome"}]},
    "titan_vines": ceiling_placed("minecraft:cave_vine", 320, -480, 40),
    "titan_moss_floor": floor_placed("minecraft:moss_patch", 220, -480, 40, offset=0),
    "titan_moss_ceiling": ceiling_placed("minecraft:moss_patch_ceiling", 200, -480, 40),
    "titan_azalea": floor_placed("minecraft:azalea_tree", 40, -480, 40, offset=0),
    "titan_spore": ceiling_placed("minecraft:spore_blossom", 80, -480, 40),
    "titan_dripleaf": floor_placed("minecraft:clay_pool_with_dripleaves", 60, -480, 40, offset=0),
    "glowworm_glow": {"feature": "minecraft:glow_lichen", "placement": [
        {"type": "minecraft:count", "count": 120}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(-220, 260)}, {"type": "minecraft:biome"}]},
    "abyssal_glow": {"feature": "minecraft:glow_lichen", "placement": [
        {"type": "minecraft:count", "count": 220}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": U(-500, -250)}, {"type": "minecraft:biome"}]},
}
CAVE_CONFIGURED = {
    "shroomlight": {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:shroomlight"}},
    "amethyst_cluster_up": {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:amethyst_cluster", "properties": {"facing": "up", "waterlogged": "false"}}},
    "amethyst_cluster_down": {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:amethyst_cluster", "properties": {"facing": "down", "waterlogged": "false"}}},
    "amethyst_bud_up": {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:large_amethyst_bud", "properties": {"facing": "up", "waterlogged": "false"}}},
}
# Each biome: (vanilla base, vegetal features of ours, fog colour, drifting particle, its chance, temperature)
CAVE_BIOMES = {
    "root_caves": ("lush_caves", ["root_moss_ceiling"], "#4a3a28", None, 0, 0.6),
    "ice_caves": ("dripstone_caves", ["ice_spires", "ice_blue"], "#a8d8f0", "minecraft:snowflake", 0.004, -0.7),
    "sandstone_caves": ("dripstone_caves", [], "#c8a870", "minecraft:white_ash", 0.002, 1.2),
    "fungal_forest": ("lush_caves", ["fungal_red_mushroom", "fungal_brown_mushroom", "fungal_huge_glowcap", "fungal_small_red", "fungal_small_brown",
                                     "fungal_glowcap", "fungal_shroomlight"],
                      "#5a3050", "minecraft:spore_blossom_air", 0.012, 0.7),
    "crystal_caves": ("dripstone_caves", ["crystal_cluster_floor", "crystal_cluster_ceiling", "crystal_bud_floor", "crystal_selenite_floor",
                                         "crystal_selenite_ceiling"], "#b8a8e8", "minecraft:end_rod", 0.0015, 1.0),
    "magma_chambers": ("dripstone_caves", ["magma_small_columns", "magma_large_columns", "magma_delta"], "#5a1a0a", "minecraft:white_ash", 0.03, 2.0),
    "abyssal_shores": ("dripstone_caves", ["abyssal_glow"], "#1a2230", "minecraft:glow", 0.002, 0.5),
    "glowworm_grotto": ("lush_caves", ["glowworm_glow"], "#0e2a30", "minecraft:glow", 0.004, 0.6),
    "the_hollow": ("lush_caves", ["hollow_glowcap", "hollow_huge_glowcap", "hollow_glow"], "#16302a", "minecraft:spore_blossom_air", 0.006, 0.6),
    # The Aurora Vault (set piece): a hanging lake over a pale floor; its light comes from the crystal bed.
    "aurora_vault": ("lush_caves", ["vault_glow"], "#0b1a2c", "minecraft:glow", 0.002, 0.5),
    # The Worldroot's hall: roots, hanging roots and glow berries over moss and rooted earth.
    "worldroot_hall": ("lush_caves", ["root_glow", "root_azalea", "root_dark_oak", "root_glowcap", "root_huge_glowcap", "root_rock",
                                      "root_pool", "root_fallen", "root_large_fern", "root_moss"], "#21180c", "minecraft:spore_blossom_air", 0.005, 0.7),
    # The Hanging Mountains: cold, pale mist, ice on the peaks.
    "hanging_peaks": ("lush_caves", ["peaks_glow"], "#5e7488", "minecraft:white_ash", 0.012, 0.1),
    # Titan's grave keeps all of lush caves' plants and adds more (the user: "TONS of overgrowth").
    "titans_grave": ("lush_caves", ["titan_vines", "titan_moss_floor", "titan_moss_ceiling", "titan_azalea", "titan_spore", "titan_dripleaf"],
                     "#24361e", "minecraft:spore_blossom_air", 0.012, 0.7),
}
DRIP_ONLY = {"minecraft:dripstone_cluster", "minecraft:pointed_dripstone", "minecraft:large_dripstone",
             # Vanilla geodes generate even in open air: in a big cave or a set piece they came out
             # as floating calcite balls (the Great Geode was full of them), so our cave biomes have none.
             "minecraft:amethyst_geode"}
LUSH_ONLY = {"minecraft:patch_tall_grass_2", "minecraft:lush_caves_ceiling_vegetation", "minecraft:cave_vines", "minecraft:lush_caves_clay",
             "minecraft:lush_caves_vegetation", "minecraft:rooted_azalea_tree", "minecraft:spore_blossom", "minecraft:classic_vines_cave_feature",
             "minecraft:ore_clay"}
SURFACE_ONLY = {"minecraft:trees_plains", "minecraft:flower_plains", "minecraft:patch_grass_plain", "minecraft:brown_mushroom_normal",
                "minecraft:red_mushroom_normal", "minecraft:patch_pumpkin", "minecraft:patch_tall_grass_2"}


def cave_biomes(jar):
    write("data/grandatlas/worldgen/noise/cave_patch.json", {"base_amplitude": 1.0, "base_octave": -4, "octave_count": 2})
    for name, cfg in CAVE_CONFIGURED.items():
        write(f"data/grandatlas/worldgen/feature/{name}.json", cfg)
    dropped = set()
    for name, placed in CAVE_PLACED.items():
        if LEGACY and placed["feature"] in LEGACY_FEATURES:
            if LEGACY_FEATURES[placed["feature"]] is None:
                dropped.add(name)
                continue
            placed = dict(placed, feature=LEGACY_FEATURES[placed["feature"]])
        write(f"data/grandatlas/worldgen/placed_feature/{name}.json", placed)
    rules = dict(CAVE_RULES)
    if LEGACY:
        # 1.21.1 has no sulfur caves of its own. With Vanilla Backport they exist, and it lays their
        # sulfur and cinnabar bands itself, with the same noise as 26.3 (ours on top made a quarter
        # too much sulfur, measured 2026-11-11; world/ForeignRules.java gates its rule). Without it
        # the biome is Dripstone Caves and there is nothing to lay.
        del rules["minecraft:sulfur_caves"]
    for name, (base, ours, fog, particle, chance, temp) in CAVE_BIOMES.items():
        b = json.loads(json.dumps(load(jar, f"data/minecraft/worldgen/biome/{base}.json")))
        keep_roots = name == "root_caves"
        keep_lush = name == "titans_grave"
        feats = []
        for step in b["features"]:
            kept = [f for f in step if f not in DRIP_ONLY and (keep_lush or f not in SURFACE_ONLY) and (keep_roots or keep_lush or f not in LUSH_ONLY)
                    and not (keep_roots and f in {"minecraft:rooted_azalea_tree", "minecraft:lush_caves_vegetation", "minecraft:lush_caves_clay",
                                                  "minecraft:spore_blossom", "minecraft:patch_tall_grass_2", "minecraft:ore_clay"})]
            feats.append(kept)
        assert len(feats) == 11, (name, len(feats))
        feats[9] = feats[9] + [f"grandatlas:{f}" for f in ours if f not in dropped]
        b["features"] = feats
        b["temperature"] = temp
        b["has_precipitation"] = temp > 0.15 and temp < 1.0
        attrs = b.setdefault("attributes", {})
        attrs["minecraft:visual/fog_color"] = fog
        if particle:
            entries = [{"particle": {"type": particle}, "probability": chance}]
            if name == "aurora_vault":
                # Motes in the aurora's colours drifting in the air under the lake.
                entries = [{"particle": {"type": "minecraft:dust", "color": col, "scale": 1.4}, "probability": 0.0035}
                           for col in (0x3AD890, 0x40D0DC, 0x4C86EC, 0x9A62EC, 0xEE62AA)] + entries
            attrs["minecraft:visual/ambient_particles"] = {"argument": entries, "modifier": "append"}
        if name == "magma_chambers" and LEGACY:
            b["spawners"].setdefault("monster", []).append({"type": "minecraft:magma_cube", "weight": 60, "minCount": 1, "maxCount": 3})
        elif name == "magma_chambers":
            spawns = attrs["minecraft:gameplay/natural_mob_spawns"]["argument"]["spawns_by_category"]
            spawns.setdefault("monster", []).append({"type": "minecraft:magma_cube", "count": {"type": "minecraft:uniform", "max_inclusive": 3, "min_inclusive": 1}, "weight": 60})
        write(f"data/grandatlas/worldgen/biome/{name}.json", b)
    # Wrapped in grandatlas:cave_gate (world/CaveGate.java): the rules run only where the core says a
    # cave biome can be, so rock elsewhere never asks its biome (a quarter of chunk generation).
    # A biome added to CAVE_RULES must also be added to CaveGate.RULED.
    # Not in the bottom five levels: those are the bedrock floor, laid by the game's own rule, which
    # runs after these. Without this the deep biomes' rock (sulfur and cinnabar, tuff) replaced
    # bedrock wherever they reached the floor (found 2026-11-11: 9% of the floor under a sulfur cave).
    above_bedrock = {"type": "minecraft:y_above", "anchor": {"above_bottom": 5}, "surface_depth_multiplier": 0, "add_stone_depth": False}
    write("data/grandatlas/worldgen/material_rule/overworld/caves.json", {"type": "grandatlas:cave_gate", "rule": when(above_bedrock, seq(*[
        when({"type": "minecraft:biome", "biome_is": biome}, rule) for biome, rule in rules.items()]))})


# M7a: the world is 8x deeper below 0 than vanilla (-512, not -64). Vanilla's deep ores and cave
# features are placed by count over a height range, so stretched over the new depth they'd thin
# out, and the ones anchored to the bottom would all crowd the new floor. Each entry: feature ->
# (new height range, or None to keep it, (count or rarity, vanilla's value, ours)). Counts keep
# about vanilla's density per level over the new range. Blackstone and basalt take deepslate ores.
ORES = {
    "ore_iron_small":     (U(-512, 72), ("count", 10, 43)),
    "ore_gold":           (T(-300, 32), ("count", 4, 14)),       # peak about -134, in the deep
    "ore_gold_lower":     (U(-512, -400), ("count", {"type": "minecraft:uniform", "max_inclusive": 1, "min_inclusive": 0},
                                            {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0})),
    "ore_lapis":          (T(-200, 32), ("count", 2, 7)),
    "ore_lapis_buried":   (U(-512, 64), ("count", 4, 18)),
    "ore_redstone":       (U(-512, 15), ("count", 4, 27)),
    # ore_redstone_lower stays anchored to the bottom: rich redstone at the floor, as in vanilla.
    "ore_diamond":        (T(-600, 0), ("count", 7, 26)),         # peak about -300, in the abyss
    "ore_diamond_buried": (T(-600, 0), ("count", 4, 15)),
    "ore_diamond_large":  (T(-600, 0), ("rarity", 9, 3)),
    "ore_diamond_medium": (U(-400, -64), ("count", 2, 11)),
    "ore_tuff":           (U(-512, 0), ("count", 2, 8)),         # half vanilla density: the strata add tuff
    "ore_infested":       (U(-512, 63), ("count", 14, 63)),
    "ore_gravel":         (None, ("count", 14, 56)),
    "amethyst_geode":     (U(-506, 30), ("rarity", 24, 4)),
    "fossil_lower":       (None, ("rarity", 64, 7)),
    "monster_room_deep":  (None, ("count", 4, 35)),
    "spring_water":       (None, ("count", 25, 69)),
}
# Cave-biome features, placed from the bottom to y 256 and filtered to their biome: given the
# biome's own band, with the count scaled by the new band over vanilla's 320.
LUSH_DRIPSTONE = ["cave_vines", "classic_vines_cave_feature", "lush_caves_ceiling_vegetation", "lush_caves_clay",
                  "lush_caves_vegetation", "spore_blossom", "rooted_azalea_tree", "ore_clay",
                  "dripstone_cluster", "large_dripstone", "pointed_dripstone"]
DEEP_DARK = ["sculk_patch_deep_dark", "sculk_vein"]
EVERYWHERE = ["glow_lichen", "underwater_magma"]
CAVE_BANDS = [(LUSH_DRIPSTONE, U(-250, 256)), (DEEP_DARK, U(-480, -240)), (EVERYWHERE, U(-512, 256))]
VANILLA_CAVE_RANGE = {"type": "minecraft:uniform", "max_inclusive": {"absolute": 256}, "min_inclusive": {"above_bottom": 0}}


def scaled(count, k):
    if isinstance(count, int):
        return max(1, round(count * k))
    assert count.get("type") == "minecraft:uniform", count
    return {"type": "minecraft:uniform", "max_inclusive": round(count["max_inclusive"] * k),
            "min_inclusive": round(count["min_inclusive"] * k)}


def respread(jar):
    """Vanilla's ores, cave features and deep structures moved for the -512 floor (M7a)."""
    def placed(name):
        return load(jar, f"data/minecraft/worldgen/placed_feature/{name}.json")

    def height_mod(feature):
        mods = [m for m in feature["placement"] if m["type"] == "minecraft:height_range"]
        assert len(mods) == 1, feature
        return mods[0]

    for name, (height, (kind, old, new)) in ORES.items():
        f = placed(name)
        if height is not None:
            height_mod(f)["height"] = height
        key, typ = ("count", "minecraft:count") if kind == "count" else ("chance", "minecraft:rarity_filter")
        mods = [m for m in f["placement"] if m["type"] == typ]
        assert mods and mods[0][key] == old, (name, mods, old)
        mods[0][key] = new
        write(f"data/minecraft/worldgen/placed_feature/{name}.json", f)
    for names, band in CAVE_BANDS:
        k = (band["max_inclusive"]["absolute"] - band["min_inclusive"]["absolute"]) / 320
        for name in names:
            f = placed(name)
            h = height_mod(f)
            assert h["height"] == VANILLA_CAVE_RANGE, (name, h)
            h["height"] = band
            first = next(m for m in f["placement"] if m["type"] == "minecraft:count")
            first["count"] = scaled(first["count"], k)
            write(f"data/minecraft/worldgen/placed_feature/{name}.json", f)

    # Ancient cities into the abyss (the user, 2026-11-03), where the deep dark now is
    # (core/AtlasCore caveBiome, -475..-250); trial chambers into the deep.
    city = load(jar, "data/minecraft/worldgen/structure/ancient_city.json")
    assert city["start_height"] == {"absolute": -27}, city["start_height"]
    city["start_height"] = {"absolute": -350}
    write("data/minecraft/worldgen/structure/ancient_city.json", city)
    # Villages are kept only beside rivers and lakes (mixin/StructureMixin), so the grid they are tried
    # on is made denser: vanilla's one try per 34 chunks square becomes one per 14.
    villages = load(jar, "data/minecraft/worldgen/structure_set/villages.json")
    villages["placement"]["spacing"], villages["placement"]["separation"] = 10, 5
    if not LEGACY:  # 1.21.1 sets this in code (world/VillageDensity.java): it must not apply beside Integrated Villages
        write("data/minecraft/worldgen/structure_set/villages.json", villages)
    # Villages need open biomes, and the biome rebalance made plains scarce (3.5% of land): in forest
    # country the nearest village was 4-6 km off. Each village style may also stand in the wooded
    # biomes of its own climate.
    for style, extra in {
        "village_plains": ["minecraft:forest", "minecraft:birch_forest", "minecraft:flower_forest", "minecraft:cherry_grove",
                           "minecraft:old_growth_birch_forest", "minecraft:sunflower_plains", "minecraft:dark_forest",
                           "grandatlas:silverwood", "grandatlas:glimmer_vale"],
        "village_taiga": ["minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:grove"],
        "village_snowy": ["minecraft:snowy_taiga", "minecraft:ice_spikes"],
        "village_savanna": ["minecraft:savanna_plateau", "minecraft:windswept_savanna", "minecraft:sparse_jungle"],
        "village_desert": ["grandatlas:red_desert", "minecraft:badlands"],
    }.items():
        write(f"data/minecraft/tags/worldgen/biome/has_structure/{style}.json", {"replace": False, "values": extra})
    trial = load(jar, "data/minecraft/worldgen/structure/trial_chambers.json")
    assert trial["start_height"] == U(-40, -20), trial["start_height"]
    trial["start_height"] = U(-160, -60)
    write("data/minecraft/worldgen/structure/trial_chambers.json", trial)

    # The abyss's rock: carved, overgrown and given ores like the rest of the deep stone.
    write("data/minecraft/tags/block/base_stone_overworld.json", {"replace": False, "values": ["minecraft:blackstone", "minecraft:basalt"]})
    write("data/minecraft/tags/block/deepslate_ore_replaceables.json", {"replace": False, "values": ["minecraft:blackstone", "minecraft:basalt"]})


def salt_flat_surface():
    """A white salt crust (calcite) with darker polygon-like ridges, over white terracotta."""
    block = lambda b: {"type": "minecraft:block", "result_state": b}
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:biome", "biome_is": "grandatlas:salt_flat"},
        "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition",
                 "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": -0.035, "max_threshold": 0.035},
                 "then_run": block("minecraft:diorite")},
                block("minecraft:calcite"),
            ]}},
            {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": block("minecraft:white_terracotta")},
        ]},
    }


def red_desert_surface():
    """The rock desert's floor: red sand over red sandstone (sandstone wherever it would hang over
    air), and the badlands bands on any rock face that falls in this biome."""
    block = lambda b: {"type": "minecraft:block", "result_state": b}
    sand = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": "minecraft:on_ceiling", "then_run": block("minecraft:red_sandstone")},
        block("minecraft:red_sand"),
    ]}
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:biome", "biome_is": "grandatlas:red_desert"},
        "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": sand},
            {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": sand},
            {"type": "minecraft:condition", "if_true": "minecraft:deep_under_floor", "then_run": block("minecraft:red_sandstone")},
            {"type": "minecraft:condition",
             "if_true": {"type": "minecraft:y_above", "anchor": {"absolute": 63}, "surface_depth_multiplier": 0, "add_stone_depth": False},
             "then_run": {"type": "minecraft:bandlands"}},
        ]},
    }


def river_bed_surface():
    """Beds of the core's rivers and lakes (the only river biomes Grand Atlas makes):
    gravel with patches of sand, laid before the water goes in, so vanilla can't see it's wet."""
    block = lambda b: {"type": "minecraft:block", "result_state": b}
    bed = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": "minecraft:on_ceiling", "then_run": block("minecraft:stone")},
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.1, "max_threshold": 1.0e308},
         "then_run": block("minecraft:sand")},
        block("minecraft:gravel"),
    ]}
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:biome", "biome_is": ["grandatlas:river", "minecraft:frozen_river", "grandatlas:crater_lake", "grandatlas:glimmer_river"]},
        "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": bed},
            {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": bed},
        ]},
    }


def salt_marsh_surface():
    """Coastal salt marsh (core coast model): grass with bare mud patches on the flat, mud under
    its creeks, pans and the tidal flats in front of it."""
    block = lambda b: {"type": "minecraft:block", "result_state": b}
    dry = {"type": "minecraft:water", "offset": 0, "surface_depth_multiplier": 0, "add_stone_depth": False}
    floor = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": dry}, "then_run": block("minecraft:mud")},
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.18, "max_threshold": 1.0e308},
         "then_run": block("minecraft:mud")},
        {"type": "minecraft:condition",
         "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.1, "max_threshold": 0.18},
         "then_run": block("minecraft:coarse_dirt")},
        block("minecraft:grass_block"),
    ]}
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:biome", "biome_is": "grandatlas:salt_marsh"},
        "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": floor},
            {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": dry}, "then_run": block("minecraft:mud")},
                block("minecraft:dirt"),
            ]}},
        ]},
    }


def pebble_beach_surface():
    """A shingle beach: gravel, with broad smooth patches of cobble and of stone (the user: keep the
    stone and cobble, but laid out by a noise pattern rather than speckled), over stone so no gravel
    hangs over a cave."""
    block = lambda b: {"type": "minecraft:block", "result_state": b}
    patch = lambda lo, hi, b: {"type": "minecraft:condition",
                               "if_true": {"type": "minecraft:noise_threshold", "noise": "grandatlas:shingle", "min_threshold": lo, "max_threshold": hi},
                               "then_run": block(b)}
    shingle = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": "minecraft:on_ceiling", "then_run": block("minecraft:stone")},
        patch(0.32, 1.0e308, "minecraft:cobblestone"),
        patch(-1.0e308, -0.32, "minecraft:stone"),
        patch(-0.32, -0.24, "minecraft:andesite"),
        block("minecraft:gravel"),
    ]}
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:biome", "biome_is": "grandatlas:pebble_beach"},
        "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": shingle},
            {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": block("minecraft:stone")},
        ]},
    }


def salt_marsh_biome(swamp):
    """Vanilla swamp's climate and frogs, but open: no trees, lily pads, mushrooms or pumpkins,
    just grass, reeds and seagrass, under fresher colours than a swamp's."""
    biome = json.loads(json.dumps(swamp))
    veg = biome["features"][9]
    assert "minecraft:trees_swamp" in veg, veg
    drop = {"minecraft:trees_swamp", "minecraft:flower_swamp", "minecraft:patch_waterlily", "minecraft:patch_pumpkin",
            "minecraft:brown_mushroom_swamp", "minecraft:red_mushroom_swamp", "minecraft:brown_mushroom_normal",
            "minecraft:red_mushroom_normal", "minecraft:patch_dead_bush"}
    biome["features"][9] = [f for f in veg if f not in drop]
    effects = biome["effects"]
    effects.pop("grass_color_modifier", None)
    effects["grass_color"] = "#8fa25a"
    effects["foliage_color"] = "#7d9150"
    effects["water_color"] = "#4a7f86"
    biome.setdefault("attributes", {})["minecraft:visual/water_fog_color"] = "#2c4a4e"
    return biome


def crater_lake_biome(river):
    """A caldera's lake (core/micro, MesoKind.CALDERA): our river, under Crater Lake's deep blue."""
    biome = json.loads(json.dumps(river))
    biome["effects"]["water_color"] = "#2a4ad0"
    biome.setdefault("attributes", {})["minecraft:visual/water_fog_color"] = "#0c1d5a"
    return biome


def pebble_beach_biome(beach):
    """Vanilla beach (turtles and all) on shingle."""
    return json.loads(json.dumps(beach))


def river_biome(river):
    """Vanilla river, warm enough (0.8) that altitude cooling doesn't freeze it below ~y 600: the
    drainage network's rivers run from mountain springs to the sea."""
    biome = json.loads(json.dumps(river))
    assert biome["temperature"] == 0.5, biome["temperature"]
    biome["temperature"] = 0.8
    return biome


def red_desert_biome(desert):
    """Vanilla desert's climate, spawns and plants (cactus and dead bushes grow on red sand), minus the well."""
    biome = json.loads(json.dumps(desert))
    features = biome["features"]
    assert "minecraft:desert_well" in features[4], features
    features[4] = []
    return biome


def salt_flat_biome(desert):
    """Vanilla desert's climate and spawns, minus everything that grows or stands on the ground."""
    biome = json.loads(json.dumps(desert))
    features = biome["features"]
    assert "minecraft:desert_well" in features[4] and "minecraft:patch_cactus_desert" in features[9], features
    features[4] = []   # surface structures: the desert well
    features[9] = []   # vegetal decoration: grass, dead bushes, cactus, sugar cane...
    biome.setdefault("attributes", {})["minecraft:visual/sky_color"] = "#86c0ff"
    return biome


# ---- M6 fantasy biomes (core/landform/MistyPeaks, VolcanicWastes, Silverwood, GlimmerVale; core/SkyIsles)

def configured_path(name):
    return f"data/minecraft/worldgen/{'configured_feature' if LEGACY else 'feature'}/{name}.json"


def tree_placed(feature, count, extra=0):
    """A tree placed like vanilla's: a count per chunk, on the ground, where a sapling would survive."""
    c = count if not extra else {"type": "minecraft:uniform", "min_inclusive": count, "max_inclusive": count + extra}
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": c},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": {"Name": "minecraft:oak_sapling", "Properties": {"stage": "0"}}}},
        {"type": "minecraft:biome"},
    ]}


def reskinned(jar, name, swaps):
    """A vanilla tree with its blocks swapped (by id) and its vines and cocoa taken off."""
    text = json.dumps(load(jar, configured_path(name)))
    for a, b in swaps.items():
        assert a in text, (name, a)
        text = text.replace(f'"{a}"', f'"{b}"')
    tree = json.loads(text)
    def strip(n):
        if isinstance(n, dict):
            if "decorators" in n:
                n["decorators"] = []
            for v in n.values():
                strip(v)
        elif isinstance(n, list):
            for v in n:
                strip(v)
    strip(tree)
    return tree


def fantasy_biome(jar, base, temp=None, rain=None, veg=None, grass=None, foliage=None, water=None, water_fog=None, fog=None, sky=None,
                  particle=None, chance=0.0):
    b = json.loads(json.dumps(load(jar, f"data/minecraft/worldgen/biome/{base}.json")))
    assert len(b["features"]) == 11, base
    if veg is not None:
        b["features"][9] = veg(b["features"][9])
    if temp is not None:
        b["temperature"] = temp
    if rain is not None:
        b["has_precipitation"] = rain
    effects = b["effects"]
    effects.pop("grass_color_modifier", None)
    for key, value in (("grass_color", grass), ("foliage_color", foliage), ("water_color", water)):
        if value:
            effects[key] = value
    attrs = b.setdefault("attributes", {})
    for key, value in (("fog_color", fog), ("sky_color", sky), ("water_fog_color", water_fog)):
        if value:
            attrs[f"minecraft:visual/{key}"] = value
    if particle:
        attrs["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle}, "probability": chance}], "modifier": "append"}
    return b


def fantasy_biomes(jar):
    # (Every biome's plants must run in one order the game can merge: our own come first, and no
    # vanilla feature is added to a vanilla list out of its own place, or the game finds a cycle.)
    # Trees: the Silverwood's are birch-barked under oak leaves, which its foliage colour turns gold.
    write("data/grandatlas/worldgen/feature/silver_fancy.json", reskinned(jar, "fancy_oak", {"minecraft:oak_log": "minecraft:birch_log"}))
    write("data/grandatlas/worldgen/feature/silver_giant.json", reskinned(jar, "mega_jungle_tree",
                                                                          {"minecraft:jungle_log": "minecraft:birch_log", "minecraft:jungle_leaves": "minecraft:oak_leaves"}))
    write("data/grandatlas/worldgen/placed_feature/silver_fancy.json", tree_placed("grandatlas:silver_fancy", 5, 2))
    write("data/grandatlas/worldgen/placed_feature/silver_giant.json", tree_placed("grandatlas:silver_giant", 1))
    # The Glimmer Vale: flowering azaleas among its flower-forest trees.
    write("data/grandatlas/worldgen/placed_feature/glimmer_azalea.json", tree_placed("minecraft:azalea_tree", 2, 2))

    drop = lambda *names: (lambda veg: [f for f in veg if f not in names])
    add = lambda *names: (lambda veg: veg + list(names))
    write("data/grandatlas/worldgen/biome/misty_peaks.json", fantasy_biome(
        jar, "windswept_forest", temp=0.9, rain=True, grass="#7fa47a", foliage="#5f8f5c", fog="#cdd8dc", sky="#a9c2d4",
        particle="minecraft:white_ash", chance=0.008))
    write("data/grandatlas/worldgen/biome/cloud_forest.json", fantasy_biome(
        jar, "old_growth_spruce_taiga", temp=0.8, rain=True, grass="#4f8a52", foliage="#3f7a4a", fog="#c2d0d0", sky="#a4bccc",
        particle="minecraft:white_ash", chance=0.01))
    bare = lambda veg: [f for f in veg if f == "minecraft:glow_lichen"]
    write("data/grandatlas/worldgen/biome/volcanic_wastes.json", fantasy_biome(
        jar, "stony_peaks", temp=2.0, rain=False, veg=bare, grass="#8a8060", foliage="#7a7050", water="#4a4a46", water_fog="#1e1c1a",
        fog="#5e4e48", sky="#76665f", particle="minecraft:white_ash", chance=0.025))
    write("data/grandatlas/worldgen/biome/volcano.json", fantasy_biome(
        jar, "stony_peaks", temp=2.0, rain=False, veg=bare, grass="#7a7050", foliage="#6a6040", water="#4a4a46", water_fog="#1e1c1a",
        fog="#4a3a34", sky="#6a5650", particle="minecraft:ash", chance=0.04))
    write("data/grandatlas/worldgen/biome/silverwood.json", fantasy_biome(
        jar, "birch_forest", temp=0.7, veg=lambda veg: ["grandatlas:silver_giant", "grandatlas:silver_fancy"]
        + [f for f in veg if f != "minecraft:trees_birch"],
        grass="#a6c45c", foliage="#e8b444", fog="#e8e4c8", sky="#8cb6ff", particle="minecraft:end_rod", chance=0.0012))
    write("data/grandatlas/worldgen/biome/glimmer_vale.json", fantasy_biome(
        jar, "flower_forest", temp=0.75, veg=lambda veg: ["grandatlas:glimmer_azalea"] + veg, grass="#5fd27a", foliage="#46c482",
        water="#3ad6c8", water_fog="#14585e", fog="#b4f0e0", sky="#86c4ff", particle="minecraft:glow", chance=0.004))
    river = river_biome(load(jar, "data/minecraft/worldgen/biome/river.json"))
    river["features"][9] = river["features"][9] + ["minecraft:sea_pickle"]
    river["effects"]["water_color"] = "#3ad6c8"
    river.setdefault("attributes", {})["minecraft:visual/water_fog_color"] = "#14585e"
    river["attributes"]["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": "minecraft:glow"}, "probability": 0.004}], "modifier": "append"}
    write("data/grandatlas/worldgen/biome/glimmer_river.json", river)
    write("data/grandatlas/worldgen/biome/sky_garden.json", fantasy_biome(
        jar, "flower_forest", temp=1.0, rain=True, grass="#7fd45a", foliage="#5fc050",
        fog="#d4e8ff", sky="#78b4ff", particle="minecraft:spore_blossom_air", chance=0.0015))
    # M5 real-world wonders. The tepui's summit: bare dark rock and moss, ferns and the odd stunted
    # tree in the cracks, mist over it. The rainbow mountains: dry, almost bare. Spotted Lake: clear
    # pale water, so the pools' colours show through it.
    write("data/grandatlas/worldgen/biome/tepui_summit.json", fantasy_biome(
        jar, "sparse_jungle", temp=0.95, rain=True, veg=lambda veg: [f for f in veg if "bamboo" not in f and "melon" not in f],
        grass="#5f8a4a", foliage="#4f7a40", fog="#c4d4cc", sky="#9cbcd0", particle="minecraft:white_ash", chance=0.004))
    write("data/grandatlas/worldgen/biome/rainbow_mountains.json", fantasy_biome(
        jar, "badlands", temp=1.2, rain=False, grass="#b8b070", foliage="#a09a60", fog="#e8d8c8", sky="#88b4e8"))
    write("data/grandatlas/worldgen/biome/spotted_lake.json", fantasy_biome(
        jar, "plains", temp=0.8, rain=True, water="#9fe0d8", water_fog="#4aa0a0"))
    for tag, extra in {"is_forest": ["grandatlas:silverwood", "grandatlas:glimmer_vale", "grandatlas:cloud_forest"],
                       "is_mountain": ["grandatlas:misty_peaks", "grandatlas:volcano", "grandatlas:tepui_summit", "grandatlas:rainbow_mountains"],
                       "is_badlands": ["grandatlas:rainbow_mountains"]}.items():
        write(f"data/minecraft/tags/worldgen/biome/{tag}.json", {"replace": False, "values": extra})


def fantasy_surfaces():
    """Black ground in the volcanic wastes, ash and tuff on the cones, moss in the Glimmer Vale."""
    surf = lambda lo, hi: {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": lo, "max_threshold": hi}
    inside = lambda b, rule: when({"type": "minecraft:biome", "biome_is": b}, rule)
    wastes = seq(
        when("minecraft:on_floor", seq(
            when(surf(-0.03, 0.03), block("minecraft:magma_block")),
            when(surf(0.3, INF), block("minecraft:blackstone")),
            when(surf(-INF, -0.35), block("minecraft:smooth_basalt")),
            block("minecraft:basalt"))),
        when("minecraft:under_floor", block("minecraft:basalt")),
        when("minecraft:deep_under_floor", block("minecraft:blackstone")))
    cone = seq(
        when("minecraft:on_floor", seq(
            when(surf(-0.025, 0.025), block("minecraft:magma_block")),
            when(surf(0.2, INF), block("minecraft:tuff")),
            when(surf(-INF, -0.25), block("minecraft:basalt")),
            block("minecraft:blackstone"))),
        when("minecraft:under_floor", block("minecraft:blackstone")),
        when("minecraft:deep_under_floor", block("minecraft:basalt")))
    vale = when("minecraft:on_floor", when(surf(0.35, INF), block("minecraft:moss_block")))
    forest = when("minecraft:on_floor", when(surf(0.45, INF), block("minecraft:moss_block")))
    # M5: the tepui's summit, dark rock (tuff and blackstone) with moss and grass in the hollows;
    # the rainbow mountains, banded all through (world/RainbowBands), a little dry grass on the flats.
    summit = seq(
        when("minecraft:on_floor", seq(
            when(surf(0.25, INF), block("minecraft:moss_block")),
            when(surf(0.05, 0.25), block("minecraft:grass_block")),
            when(surf(-0.3, -0.12), block("minecraft:blackstone")),
            block("minecraft:tuff"))),
        when("minecraft:under_floor", block("minecraft:tuff")))
    rainbow = {"type": "grandatlas:rainbow"}
    return [inside("grandatlas:volcanic_wastes", wastes), inside("grandatlas:volcano", cone),
            inside("grandatlas:glimmer_vale", vale), inside("grandatlas:cloud_forest", forest),
            inside("grandatlas:tepui_summit", summit), inside("grandatlas:rainbow_mountains", rainbow)]


def biome_kinds():
    """(table key, biome id) for every BiomeKind; the namespace defaults to minecraft."""
    src = (ROOT / "src/main/java/com/seaweed/grandatlas/core/BiomeKind.java").read_text(encoding="utf-8")
    body = src.split("public enum BiomeKind {", 1)[1].split(";", 1)[0]
    kinds = [(name.lower(), f"{ns or 'minecraft'}:{name.lower()}")
             for name, ns in re.findall(r'\b([A-Z][A-Z_]+)\(0x[0-9A-Fa-f]+(?:,\s*"([a-z_]+)")?\)', body)]
    assert len(kinds) > 30, kinds
    return kinds


def our_biomes():
    """(display name, id) for Grand Atlas's own biomes."""
    return [(key.replace("_", " ").title(), biome) for key, biome in biome_kinds() if biome.startswith("grandatlas:")]


if __name__ == "__main__":
    main()
