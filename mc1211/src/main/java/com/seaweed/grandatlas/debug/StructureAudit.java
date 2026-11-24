package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.world.CoreHandle;
import com.seaweed.grandatlas.world.StructureGround;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * The dev "structureAudit" run (system property grandatlas.structureAudit), M8 integration: for every
 * structure registered (vanilla's and every structure mod's), whether Grand Atlas's biome source can
 * ever give it a biome it accepts, then, over the candidate chunks its structure set's placement picks
 * in 25 windows spread over 48 km (so every climate is in it), how many land in a biome it accepts and
 * how many of those the ground check ({@link StructureGround}) lets it be built on. Placement-only (no
 * structure is assembled), so it takes seconds: asking Minecraft to locate each structure tried every
 * candidate's generation and ran for over an hour (2026-11-23).
 * Writes run-structures/grandatlas/structure_audit.txt, then stops.
 */
public final class StructureAudit {
    private StructureAudit() {}

    /** Half the side of each window searched, in chunks. */
    private static final int RADIUS = 48;
    /** Window centres, in chunks: a 5 x 5 grid, 12 km apart (climate zones run tens of km). */
    private static final int WINDOW_STEP = 750, WINDOWS = 2;
    /** Heights the biome is looked up at for structures below the surface (caves, the deep dark at -350). */
    private static final int[] DEEP = {0, -60, -200, -350};

    public static void run(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        ServerLevel level = server.overworld();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        AtlasCore core = CoreHandle.coreOf(level);
        Set<Holder<Biome>> possible = source.possibleBiomes();
        Registry<Structure> structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<StructureSet> sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);

        // Which set places each structure.
        Map<Structure, StructureSet> setOf = new LinkedHashMap<>();
        for (StructureSet set : sets) for (StructureSet.StructureSelectionEntry e : set.structures()) setOf.put(e.structure().value(), set);

        List<String> never = new ArrayList<>(), unplaced = new ArrayList<>(), rare = new ArrayList<>(), rough = new ArrayList<>(), ok = new ArrayList<>();
        Map<StructureSet, List<ChunkPos>> candidates = new LinkedHashMap<>();
        int done = 0;
        for (var entry : structures.entrySet()) {
            String id = entry.getKey().location().toString();
            Structure s = entry.getValue();
            HolderSet<Biome> wants = s.biomes();
            Set<Holder<Biome>> ours = new HashSet<>();
            for (Holder<Biome> b : wants) if (possible.contains(b)) ours.add(b);
            boolean overworldish = wants.stream().anyMatch(b -> b.is(BiomeTags.IS_OVERWORLD));
            if (ours.isEmpty()) {
                // Nether and End structures land here too: only overworld-biome ones are a gap.
                if (overworldish) never.add(String.format("%-55s wants %d biomes, none of ours: %s", id, wants.size(), names(wants, 6)));
                continue;
            }
            StructureSet set = setOf.get(s);
            if (set == null) {
                unplaced.add(String.format("%-55s in no structure set (only placed by other structures or commands)", id));
                continue;
            }
            List<ChunkPos> cands = candidates.computeIfAbsent(set, k -> candidatesOf(k.placement(), state));
            boolean surface = s.step() == GenerationStep.Decoration.SURFACE_STRUCTURES;
            boolean gated = StructureGround.judged(s, server.registryAccess());
            int valid = 0, passed = 0;
            Map<String, Integer> faults = new LinkedHashMap<>();
            List<String> notes = new ArrayList<>();
            for (ChunkPos c : cands) {
                int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
                int y = core != null ? core.column(x, z).surfaceY() : 64;
                boolean in = ours.contains(source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler));
                if (!in && !surface) {
                    for (int dy : DEEP) {
                        if (ours.contains(source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(dy), QuartPos.fromBlock(z), sampler))) {
                            in = true;
                            break;
                        }
                    }
                }
                if (!in) continue;
                valid++;
                String f = gated && core != null ? StructureGround.fault(core, s, x, z) : null;
                if (f == null) {
                    passed++;
                } else {
                    faults.merge(f, 1, Integer::sum);
                    if (notes.size() < 2) notes.add(String.format("%d %d: %s", x, z, judge(core, x, z)));
                }
            }
            String head = String.format("%-55s %4d candidates, %4d in its biomes, %4d built%s", id, cands.size(), valid, passed,
                    gated ? "" : surface ? " (ground not judged)" : " (underground)");
            if (valid == 0) {
                rare.add(head + String.format(" (%d of our biomes: %s)", ours.size(), names(HolderSet.direct(new ArrayList<>(ours)), 4)));
            } else if (passed * 2 < valid) {
                StringBuilder l = new StringBuilder(head).append(", turned away: ").append(faults);
                for (String n : notes) l.append("\n      ").append(n);
                rough.add(l.toString());
            } else {
                ok.add(head + (faults.isEmpty() ? "" : ", turned away: " + faults));
            }
            if (++done % 50 == 0) GrandAtlas.LOG.info("[structure audit] {} structures checked, {} s", done, (System.currentTimeMillis() - t0) / 1000);
        }
        StringBuilder out = new StringBuilder("Structure audit (M8): ").append(structures.size()).append(" structures, ")
                .append(possible.size()).append(" biomes the overworld source can give; candidates in 25 windows ")
                .append(RADIUS * 32).append(" blocks across, 12 km apart; 'built' = in its biomes and on ground StructureGround accepts\n\n");
        section(out, "Never generates (overworld structure, no Grand Atlas biome it accepts)", never);
        section(out, "No candidate in its biomes anywhere sampled", rare);
        section(out, "Over half turned away by the ground check", rough);
        section(out, "Not in any structure set", unplaced);
        section(out, "Fine", ok);
        if (core != null) {
            // Surface biome shares over 24 km, every 192 blocks: which biomes the rare structures are waiting on.
            Map<String, Integer> share = new java.util.TreeMap<>();
            int n = 0;
            for (int z = -12000; z <= 12000; z += 192) {
                for (int x = -12000; x <= 12000; x += 192) {
                    share.merge(core.column(x, z).biome().id(), 1, Integer::sum);
                    n++;
                }
            }
            out.append("== Surface biome shares over 24 km\n");
            final int total = n;
            share.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(e -> out.append(String.format("  %-28s %5.2f%%%n", e.getKey(), 100.0 * e.getValue() / total)));
        }
        out.append(String.format("%ntook %d s%n", (System.currentTimeMillis() - t0) / 1000));
        try {
            Path f = server.getServerDirectory().resolve("grandatlas").resolve("structure_audit.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, out);
        } catch (Exception e) {
            GrandAtlas.LOG.error("[structure audit] write failed", e);
        }
        GrandAtlas.LOG.info("[structure audit] == done: {} never, {} rare, {} mostly turned away, {} fine, took {} s", never.size(), rare.size(),
                rough.size(), ok.size(), (System.currentTimeMillis() - t0) / 1000);
    }

    private static void section(StringBuilder out, String title, List<String> lines) {
        out.append("== ").append(title).append(": ").append(lines.size()).append('\n');
        lines.forEach(l -> out.append("  ").append(l).append('\n'));
        out.append('\n');
    }

    /** The chunks a placement would start its structures in, in the sampled windows. */
    private static List<ChunkPos> candidatesOf(StructurePlacement placement, ChunkGeneratorStructureState state) {
        List<ChunkPos> out = new ArrayList<>();
        if (placement instanceof RandomSpreadStructurePlacement spread) {
            int spacing = spread.spacing();
            for (int wz = -WINDOWS; wz <= WINDOWS; wz++) {
                for (int wx = -WINDOWS; wx <= WINDOWS; wx++) {
                    int ox = wx * WINDOW_STEP, oz = wz * WINDOW_STEP;
                    for (int gz = Math.floorDiv(oz - RADIUS, spacing); gz <= Math.floorDiv(oz + RADIUS, spacing); gz++) {
                        for (int gx = Math.floorDiv(ox - RADIUS, spacing); gx <= Math.floorDiv(ox + RADIUS, spacing); gx++) {
                            ChunkPos c = spread.getPotentialStructureChunk(state.getLevelSeed(), gx * spacing, gz * spacing);
                            if (Math.abs(c.x - ox) > RADIUS || Math.abs(c.z - oz) > RADIUS) continue;
                            if (placement.isStructureChunk(state, c.x, c.z)) out.add(c);
                        }
                    }
                }
            }
        } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
            List<ChunkPos> ring = state.getRingPositionsFor(rings);
            if (ring != null) out.addAll(ring);
        }
        return out;
    }

    /** The ground round a point, from the core, for the report's examples. */
    private static String judge(AtlasCore core, int x, int z) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dz = -StructureGround.SPAN; dz <= StructureGround.SPAN; dz += StructureGround.SPAN / 2) {
            for (int dx = -StructureGround.SPAN; dx <= StructureGround.SPAN; dx += StructureGround.SPAN / 2) {
                int y = core.column(x + dx, z + dz).surfaceY();
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
            }
        }
        ColumnSample c = core.column(x, z);
        return String.format("varies %d, y %d, %s%s, %s", hi - lo, c.surfaceY(), c.landform().id(),
                c.macro() == null ? "" : " in " + c.macro().id() + "/" + c.macroZone().id(), c.biome().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String names(HolderSet<Biome> set, int max) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Holder<Biome> h : set) {
            if (n++ == max) {
                b.append("...");
                break;
            }
            b.append(h.unwrapKey().map(k -> k.location().toString()).orElse("?")).append(' ');
        }
        return b.toString().trim();
    }
}
