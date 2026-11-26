package com.seaweed.grandatlas.myths.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * World-generation types. Chunk generators are registered by their CODEC: the registry maps
 * the name used in JSON ("grandatlas_myths:labyrinth") to the recipe that builds the generator.
 */
public final class MythsWorldgen {

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, GrandAtlasMyths.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<LabyrinthChunkGenerator>> LABYRINTH =
            CHUNK_GENERATORS.register("labyrinth", () -> LabyrinthChunkGenerator.CODEC);

    private MythsWorldgen() {}

    public static void register(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
    }
}
