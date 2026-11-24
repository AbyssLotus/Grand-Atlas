package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * {@code grandatlas:atlas}: biomes come straight from the terrain core, so they always match
 * the landform underneath. The {@code biomes} table maps each {@link BiomeKind} id to a
 * biome. Any kind left out falls back to {@code plains}.
 */
public final class AtlasBiomeSource extends BiomeSource {
    // 1.21.1: the table is read as ids and looked up here, not parsed as biomes, so an id that
    // only a backport mod supplies (Pale Garden, Sulfur Caves) can be missing without the whole
    // dimension failing to load. A missing one takes its stand-in below.
    public static final MapCodec<AtlasBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).fieldOf("biomes").forGetter(s -> s.ids),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, AtlasBiomeSource::new));

    /** What a biome newer than 1.21.1 becomes when no mod supplies it. */
    private static final Map<String, String> STAND_INS = Map.of("pale_garden", "dark_forest", "sulfur_caves", "dripstone_caves");

    private final Map<String, ResourceLocation> ids;
    private final Map<String, Holder<Biome>> table = new java.util.HashMap<>();
    private final EnumMap<BiomeKind, Holder<Biome>> byKind = new EnumMap<>(BiomeKind.class);
    private final Holder<Biome> fallback;

    public AtlasBiomeSource(Map<String, ResourceLocation> ids, HolderGetter<Biome> biomes) {
        this.ids = ids;
        for (Map.Entry<String, ResourceLocation> e : ids.entrySet()) {
            java.util.Optional<Holder.Reference<Biome>> found = biomes.get(ResourceKey.create(Registries.BIOME, e.getValue()));
            if (found.isEmpty() && STAND_INS.containsKey(e.getKey())) {
                ResourceLocation standIn = ResourceLocation.withDefaultNamespace(STAND_INS.get(e.getKey()));
                found = biomes.get(ResourceKey.create(Registries.BIOME, standIn));
                GrandAtlas.LOG.info("Grand Atlas: no biome '{}' (newer than 1.21.1; Vanilla Backport adds it); using {}", e.getValue(), standIn);
            }
            found.ifPresent(h -> table.put(e.getKey(), h));
        }
        Holder<Biome> plains = table.get(BiomeKind.PLAINS.id());
        this.fallback = plains != null ? plains : table.values().iterator().next();
        for (BiomeKind kind : BiomeKind.values()) {
            Holder<Biome> biome = table.get(kind.id());
            if (biome == null) {
                GrandAtlas.LOG.warn("Grand Atlas biome table has no entry for '{}'; using {}", kind.id(), fallback);
                biome = fallback;
            }
            byKind.put(kind, biome);
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    /** Other mods' biomes on our land (TerraBlender regions), filled in as the server starts. */
    private volatile ModdedBiomes modded = ModdedBiomes.NONE;
    private volatile java.util.Set<Holder<Biome>> possible;

    /** Reads the biome mods' TerraBlender regions (GrandAtlas, as the server starts, before any chunk). */
    public void harvestModded(net.minecraft.core.Registry<Biome> registry, long seed) {
        modded = ModdedBiomes.harvest(registry, seed);
        possible = null;
    }

    public ModdedBiomes modded() {
        return modded;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(byKind.values().stream(), modded.all().stream()).distinct();
    }

    /** Rebuilt once the modded biomes are in (the game's own copy is worked out once, maybe earlier). */
    @Override
    public java.util.Set<Holder<Biome>> possibleBiomes() {
        java.util.Set<Holder<Biome>> p = possible;
        if (p == null) {
            java.util.Set<Holder<Biome>> s = new java.util.LinkedHashSet<>();
            collectPossibleBiomes().forEach(s::add);
            possible = p = java.util.Collections.unmodifiableSet(s);
        }
        return p;
    }

    /** The core behind the last sampler asked with: one world's sampler asks millions of times. */
    private volatile Climate.Sampler lastSampler;
    private volatile AtlasCore lastCore;

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        AtlasCore core = lastCore;
        if (sampler != lastSampler || core == null) {
            AtlasCore found = CoreHandle.coreFrom(sampler);
            core = found != null ? found : CoreHandle.last(); // some callers pass a foreign sampler
            if (core == null) return fallback;
            lastCore = core;
            lastSampler = sampler;
        }
        int x = QuartPos.toBlock(quartX), y = QuartPos.toBlock(quartY), z = QuartPos.toBlock(quartZ);
        BiomeKind kind = core.biomeAt(core.column(x, z), x, y, z);
        ModdedBiomes m = modded;
        if (m != ModdedBiomes.NONE) {
            Holder<Biome> other = m.pick(kind, x, z);
            if (other != null) return other;
        }
        return byKind.get(kind);
    }
}
