package com.seaweed.grandatlas.world;

import com.mojang.datafixers.util.Pair;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.GrandAtlasConfig;
import com.seaweed.grandatlas.core.BiomeKind;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

/**
 * Other mods' biomes on Grand Atlas's land (M8, DESIGN.md §12): Biomes O' Plenty, Oh The Biomes
 * We've Gone and any other mod that adds its overworld biomes through TerraBlender regions.
 *
 * <p>Each region tells TerraBlender where its biomes go in vanilla's climate space: mostly "this
 * biome where vanilla would put that one". Here each of its entries is traced back to the vanilla
 * biome that holds that spot in vanilla's own table, the <b>host</b>. Wherever Grand Atlas would
 * put the host, a patch of the land may take one of its modded biomes instead, so a maple woods
 * turns up in forest country and a lavender field on plains, as their authors placed them. The
 * odds follow TerraBlender's region weights, so a modded biome is about as common here as in an
 * ordinary TerraBlender world, times the config's {@code biomeMods.weight}.
 *
 * <p>Only vanilla hosts take modded biomes: Grand Atlas's own (salt flats, Glimmer Vale, the
 * wonders) keep theirs, and so does everything the core shapes for a biome by kind (its rivers,
 * rocks and set pieces), since the core still thinks in its own kinds. The patches are cells about
 * 350 blocks across with warped edges. TerraBlender is read by reflection, so Grand Atlas has no
 * dependency on it.
 */
public final class ModdedBiomes {
    /** A modded biome and its share of a host's land (0..1). */
    public record Variant(Holder<Biome> biome, double share) {}

    private final EnumMap<BiomeKind, Variant[]> variants;
    private final long salt;

    private ModdedBiomes(EnumMap<BiomeKind, Variant[]> variants, long salt) {
        this.variants = variants;
        this.salt = salt;
    }

    public static final ModdedBiomes NONE = new ModdedBiomes(new EnumMap<>(BiomeKind.class), 0);

    public boolean isEmpty() {
        return variants.isEmpty();
    }

    /** Every modded biome that can appear (for the biome source's possible biomes). */
    public Set<Holder<Biome>> all() {
        Set<Holder<Biome>> out = new LinkedHashSet<>();
        for (Variant[] vs : variants.values()) for (Variant v : vs) out.add(v.biome());
        return out;
    }

    public Map<BiomeKind, Variant[]> variants() {
        return variants;
    }

    /** Patch size, in blocks. */
    private static final double CELL = 352;

    /** The modded biome taking (x, z) from its host there, or null where the host stays. */
    public Holder<Biome> pick(BiomeKind host, int x, int z) {
        Variant[] vs = variants.get(host);
        if (vs == null) return null;
        // Warped cells: each takes one roll, so a patch is one biome, edges wandering.
        double wx = x + 90 * Math.sin(z / 211.0 + salt % 7) + 50 * Math.sin((x + z) / 97.0);
        double wz = z + 90 * Math.sin(x / 223.0 + salt % 5) + 50 * Math.sin((x - z) / 89.0);
        double fx = wx / CELL, fz = wz / CELL;
        int cx = (int) Math.floor(fx), cz = (int) Math.floor(fz);
        double best = Double.MAX_VALUE;
        long bestCell = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = hash(cx + dx, cz + dz);
                double px = cx + dx + 0.15 + 0.7 * ((h & 0xFFFF) / 65535.0);
                double pz = cz + dz + 0.15 + 0.7 * (((h >>> 16) & 0xFFFF) / 65535.0);
                double d = (px - fx) * (px - fx) + (pz - fz) * (pz - fz);
                if (d < best) {
                    best = d;
                    bestCell = h;
                }
            }
        }
        double roll = ((bestCell >>> 32) & 0xFFFFFF) / (double) 0x1000000;
        for (Variant v : vs) {
            roll -= v.share();
            if (roll < 0) return v.biome();
        }
        return null;
    }

    private long hash(int cx, int cz) {
        long h = salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 32);
    }

    /**
     * Reads every TerraBlender overworld region. Returns {@link #NONE} if TerraBlender isn't
     * installed or no region adds a modded biome.
     */
    public static ModdedBiomes harvest(Registry<Biome> registry, long seed) {
        if (!net.neoforged.fml.ModList.get().isLoaded("terrablender")) return NONE;
        double weight = GrandAtlasConfig.biomeModsWeight();
        if (weight <= 0) {
            GrandAtlas.LOG.info("Grand Atlas: biome mods turned off in the config (biomeMods.weight = 0)");
            return NONE;
        }
        List<String> deny = GrandAtlasConfig.biomeModsDeny();
        Climate.ParameterList<Holder<Biome>> vanilla = new net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList(
                net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, registry.asLookup()).parameters();
        // Per host: sum over regions of (region weight x that biome's share of the host's spots there).
        Map<BiomeKind, Map<ResourceKey<Biome>, Double>> odds = new EnumMap<>(BiomeKind.class);
        double totalWeight = 0;
        Map<String, Integer> perMod = new HashMap<>();
        try {
            Class<?> regions = Class.forName("terrablender.api.Regions");
            Class<?> regionType = Class.forName("terrablender.api.RegionType");
            Class<?> regionClass = Class.forName("terrablender.api.Region");
            Object overworld = regionType.getField("OVERWORLD").get(null);
            List<?> list = (List<?>) regions.getMethod("get", regionType).invoke(null, overworld);
            Method addBiomes = regionClass.getMethod("addBiomes", Registry.class, Consumer.class);
            Method getWeight = regionClass.getMethod("getWeight"), getName = regionClass.getMethod("getName");
            for (Object region : list) {
                int w = (Integer) getWeight.invoke(region);
                ResourceLocation name = (ResourceLocation) getName.invoke(region);
                if (w <= 0) continue;
                totalWeight += w;
                // This region's entries, by host.
                Map<BiomeKind, Map<ResourceKey<Biome>, Integer>> counts = new EnumMap<>(BiomeKind.class);
                Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> sink = p -> {
                    BiomeKind host = hostOf(vanilla, p.getFirst());
                    if (host != null) counts.computeIfAbsent(host, k -> new HashMap<>()).merge(p.getSecond(), 1, Integer::sum);
                };
                try {
                    addBiomes.invoke(region, registry, sink);
                } catch (Exception e) {
                    GrandAtlas.LOG.warn("Grand Atlas: couldn't read TerraBlender region {}: {}", name, e.toString());
                    continue;
                }
                for (Map.Entry<BiomeKind, Map<ResourceKey<Biome>, Integer>> e : counts.entrySet()) {
                    int n = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
                    for (Map.Entry<ResourceKey<Biome>, Integer> b : e.getValue().entrySet()) {
                        odds.computeIfAbsent(e.getKey(), k -> new HashMap<>()).merge(b.getKey(), w * (double) b.getValue() / n, Double::sum);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            GrandAtlas.LOG.warn("Grand Atlas: couldn't read TerraBlender's regions, biome mods left out", e);
            return NONE;
        }
        if (totalWeight <= 0) return NONE;
        EnumMap<BiomeKind, Variant[]> out = new EnumMap<>(BiomeKind.class);
        int kinds = 0;
        for (Map.Entry<BiomeKind, Map<ResourceKey<Biome>, Double>> e : odds.entrySet()) {
            List<Variant> vs = new ArrayList<>();
            double sum = 0;
            for (Map.Entry<ResourceKey<Biome>, Double> b : e.getValue().entrySet()) {
                ResourceKey<Biome> key = b.getKey();
                String ns = key.location().getNamespace();
                // A vanilla biome, or TerraBlender's "whatever vanilla has here": the host stays ours.
                if (ns.equals("minecraft") || ns.equals(GrandAtlas.MOD_ID) || ns.equals("terrablender")) continue;
                if (deny.contains(ns) || deny.contains(key.location().toString())) continue;
                var holder = registry.getHolder(key);
                if (holder.isEmpty()) continue;
                double share = weight * b.getValue() / totalWeight;
                vs.add(new Variant(holder.get(), share));
                sum += share;
                perMod.merge(ns, 1, Integer::sum);
            }
            if (vs.isEmpty()) continue;
            if (sum > 0.9) { // the host keeps at least a tenth of its land
                double k = 0.9 / sum;
                vs.replaceAll(v -> new Variant(v.biome(), v.share() * k));
            }
            vs.sort((a, b) -> a.biome().unwrapKey().get().location().compareTo(b.biome().unwrapKey().get().location()));
            out.put(e.getKey(), vs.toArray(Variant[]::new));
            kinds++;
        }
        if (out.isEmpty()) return NONE;
        GrandAtlas.LOG.info("Grand Atlas: biome mods on {} of our biomes, by mod (host spots): {}", kinds, perMod);
        return new ModdedBiomes(out, seed ^ 0x6D6F6464656442L);
    }

    /**
     * Turns on the biome mods' surface rules (BOP's wasteland ground, BWG's red rock and the like) for
     * a Grand Atlas generator. TerraBlender wraps the noise settings' surface rule so each mod's rules
     * apply to its own biomes, the settings' own (ours) to the rest, but it only does that where the
     * biome source is vanilla's multi-noise one, which ours isn't. Here it is done the same way.
     */
    public static void enableSurfaceRules(net.minecraft.world.level.chunk.ChunkGenerator generator) {
        if (!net.neoforged.fml.ModList.get().isLoaded("terrablender")) return;
        if (!(generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise)) return;
        try {
            Class<?> category = Class.forName("terrablender.api.SurfaceRuleManager$RuleCategory");
            Class<?> extended = Class.forName("terrablender.worldgen.IExtendedNoiseGeneratorSettings");
            Object overworld = category.getField("OVERWORLD").get(null);
            Object settings = noise.generatorSettings().value();
            if (!extended.isInstance(settings)) return;
            extended.getMethod("setRuleCategory", category).invoke(settings, overworld);
            GrandAtlas.LOG.info("Grand Atlas: biome mods' surface rules on");
        } catch (ReflectiveOperationException | RuntimeException e) {
            GrandAtlas.LOG.warn("Grand Atlas: couldn't turn on the biome mods' surface rules", e);
        }
    }

    /** The vanilla biome at the middle of a climate point in vanilla's own table, as one of our kinds. */
    private static BiomeKind hostOf(Climate.ParameterList<Holder<Biome>> vanilla, Climate.ParameterPoint p) {
        Climate.TargetPoint t = new Climate.TargetPoint(mid(p.temperature()), mid(p.humidity()), mid(p.continentalness()),
                mid(p.erosion()), mid(p.depth()), mid(p.weirdness()));
        ResourceLocation id = vanilla.findValue(t).unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        try {
            return BiomeKind.valueOf(id.getPath().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long mid(Climate.Parameter p) {
        return (p.min() + p.max()) / 2;
    }
}
