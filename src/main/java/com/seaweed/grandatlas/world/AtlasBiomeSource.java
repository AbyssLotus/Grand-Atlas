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
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * {@code grandatlas:atlas}: biomes come straight from the terrain core, so they always match
 * the landform underneath. The {@code biomes} table maps each {@link BiomeKind} id to a
 * biome. Any kind left out falls back to {@code plains}.
 */
public final class AtlasBiomeSource extends BiomeSource {
    public static final MapCodec<AtlasBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Biome.CODEC).fieldOf("biomes").forGetter(s -> s.table)
    ).apply(i, AtlasBiomeSource::new));

    private final Map<String, Holder<Biome>> table;
    private final EnumMap<BiomeKind, Holder<Biome>> byKind = new EnumMap<>(BiomeKind.class);
    private final Holder<Biome> fallback;

    public AtlasBiomeSource(Map<String, Holder<Biome>> table) {
        this.table = table;
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

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return byKind.values().stream().distinct();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        AtlasCore found = CoreHandle.coreFrom(sampler);
        AtlasCore core = found != null ? found : CoreHandle.last(); // some callers pass a foreign sampler
        if (core == null) {
            return (quartX, quartY, quartZ) -> fallback;
        }
        return (quartX, quartY, quartZ) -> {
            int x = QuartPos.toBlock(quartX), y = QuartPos.toBlock(quartY), z = QuartPos.toBlock(quartZ);
            return byKind.get(core.biomeAt(core.column(x, z), x, y, z));
        };
    }
}
