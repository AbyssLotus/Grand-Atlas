package com.seaweed.grandatlas.mortus;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * {@code grandatlas:mortus}: Domus Mortus's biomes, one per region of MortusMap
 * ({@code grandatlas:<region id>}). Tartarus owns its whole pit, all the way down.
 */
public final class MortusBiomeSource extends BiomeSource {
    public static final MapCodec<MortusBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, MortusBiomeSource::new));

    private final HolderGetter<Biome> getter;
    private final Map<MortusMap.Region, Holder<Biome>> biomes = new EnumMap<>(MortusMap.Region.class);

    public MortusBiomeSource(HolderGetter<Biome> getter) {
        this.getter = getter;
        for (MortusMap.Region r : MortusMap.Region.values()) {
            biomes.put(r, getter.getOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("grandatlas", r.id))));
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.values().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        return biomes.get(MortusMap.region(qx * 4 + 2, qz * 4 + 2));
    }
}
