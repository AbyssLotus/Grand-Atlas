package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * Whether the ground under a surface structure suits it (M8, docs/ROADMAP.md): the pack's structure
 * mods were built for vanilla's gentle hills, and in Grand Atlas's highlands two in five of their
 * houses, inns and windmills landed on slopes falling 30 to 120 blocks across their footprint,
 * in a river, or against a cliff (the structure audit, 2026-11-23). Such a start is not built;
 * the next candidate is. Decided from the terrain core before the structure is assembled, so
 * locating one stays cheap.
 *
 * <p>Only surface structures are judged, and not ones meant for the sea or shore, or ones that bury
 * themselves. Structures in the {@code grandatlas:any_ground} tag (dragon roosts on the peaks, the
 * mountain ruined portal) go anywhere.
 */
public final class StructureGround {
    private StructureGround() {}

    public static final TagKey<Structure> ANY_GROUND =
            TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "any_ground"));

    /** Half the footprint judged, in blocks. */
    public static final int SPAN = 16;
    /** Most the ground may vary over the footprint: bare, and where the beardifier shapes the ground round it. */
    public static final int RISE_BARE = 14, RISE_BEARD = 26;

    private static final Map<Structure, Boolean> JUDGED = new ConcurrentHashMap<>();

    /** Whether this structure's ground is judged at all. */
    public static boolean judged(Structure s, RegistryAccess registries) {
        return JUDGED.computeIfAbsent(s, k -> {
            if (k.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) return false;
            TerrainAdjustment a = k.terrainAdaptation();
            if (a == TerrainAdjustment.BURY || a == TerrainAdjustment.ENCAPSULATE) return false;
            Holder<Structure> h = registries.registryOrThrow(Registries.STRUCTURE).wrapAsHolder(k);
            if (h.is(ANY_GROUND)) return false;
            // Meant for the sea or the shore: every biome it takes is one.
            boolean wet = true;
            for (Holder<Biome> b : k.biomes()) {
                if (!(b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_RIVER))) {
                    wet = false;
                    break;
                }
            }
            return !wet;
        });
    }

    /** Null if the ground at (x, z) suits the structure, else why not. */
    public static String fault(AtlasCore core, Structure s, int x, int z) {
        int limit = s.terrainAdaptation() == TerrainAdjustment.NONE ? RISE_BARE : RISE_BEARD;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dz = -SPAN; dz <= SPAN; dz += SPAN / 2) {
            for (int dx = -SPAN; dx <= SPAN; dx += SPAN / 2) {
                ColumnSample c = core.column(x + dx, z + dz);
                int y = c.surfaceY();
                lo = Math.min(lo, y);
                hi = Math.max(hi, y);
                if (hi - lo > limit) return "slope";
                boolean centre = Math.abs(dx) < SPAN && Math.abs(dz) < SPAN;
                if (centre && inland(c)) return "water";
                if (centre && (c.wall() != null || c.macroZone() != null
                        && (c.macroZone().name().equals("FACE") || c.macroZone().name().equals("CANYON")))) return "cliff";
            }
        }
        return null;
    }

    /** River or lake water over the ground (the sea is the ocean structures' business). */
    private static boolean inland(ColumnSample c) {
        return !Double.isNaN(c.water()) && c.water() > c.height() && !c.biome().name().endsWith("OCEAN");
    }
}
