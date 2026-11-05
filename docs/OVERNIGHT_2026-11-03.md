# Overnight work, 2026-11-03

You went to bed asking me to keep working. Everything below is installed in your playtest profile (latest jar in its `mods` folder) and noted in [PLAYTEST.md](PLAYTEST.md) under each version. **Make a new world on seed `grand-atlas`**: old chunks keep the old terrain.

I couldn't take in-game screenshots: Windows' capture gets no frames while your screen is off. So every feature was checked from the terrain code (maps, slices, the `checkCore` tests: seams, water containment, determinism) and, for most, in a generated test world by reading the saved blocks. None of it has been seen in a game client. Tell me what looks wrong.

## What's new, and where to look

| Version | What | Tour stop |
|---|---|---|
| 0.2.0 | **Waterfalls on rivers**: where a course drops 10+ in 40 blocks it falls in one drop into a sheer-walled amphitheatre with a plunge pool. About one per km². | 39-42 |
| 0.2.1 | **Grotto** (Hamilton Pool) and **terraced cascades** (Kuang Si), new sites | 43, 44 |
| 0.2.2 | **Fall styles**: basalt punchbowls, red canyon falls, icy falls | 45-47 |
| 0.2.3 | **Crag mountains** (your "aggressive, sometimes"): split towers, overhangs, leaning slabs, in ~17% of megaranges; **crag towers** standing alone in open country | 48, 49 |
| 0.2.4 | **Giant waterfalls**: rivers pouring off sheer mountain faces, up to 420 blocks | 50 |
| 0.2.5 | **Boulder rapids** in fast mountain streams | (any mountain stream) |
| 0.2.6 | **Sea floor**: sandbars, ripples, reefs, submarine canyons, seamounts and guyots, atolls, trenches, blue holes | 51, 52 |
| 0.2.7 | **Ha Long Bay**: warm bays full of jungle-topped karst towers | 53 |
| 0.2.8-0.2.9 | **Fjords**: U-shaped sea troughs winding into cold mountains | 54 |
| 0.3.0 | **Rivers wander**; meanders on flat ground | (any river) |
| 0.3.1 | **Lake districts**: crowded long glacial lakes, islands, rocky shores | 55 |
| 0.3.2 | **Estuaries** and **barrier islands** | 57 |
| 0.3.3 | **River islands** and **braided rivers** | 57 |
| 0.3.4 | **Oxbow lakes** beside meander bends | 56 |
| 0.3.5 | **Calderas** (Crater Lake): a volcano's rim round a deep blue lake, banded cliffs, a cinder-cone island | 58 |

Fixes along the way: the sloped pool banks from 0.1.8 reached too far and held up whole valley walls (now only within 12 blocks of a bank); fjord water is still vanilla sea water.

## Known rough edges

- **Barrier islands** come out short and broken on the dev seed's hilly coasts.
- **Grotto**: a few water blocks leak at one chunk border (cause not found yet).
- **Plateau / old items** still open from before: the plateau fall landing a block short of its pool, tors reading as sculptures.
- **Deltas** (rivers splitting across a fan of islands), valley lakes behind natural dams and kettle lakes are not done.
- The amphitheatre plunge pools are pale under some falls; the bowls' walls are plain stone apart from the styles' dressing.

## Docs

The plan ([M3_M4_PLAN.md](M3_M4_PLAN.md)) now has the epic, hand-crafted goal as its first quality bar ([DESIGN.md](DESIGN.md) §0), and sections for waterfalls and crag mountains from your photos.
