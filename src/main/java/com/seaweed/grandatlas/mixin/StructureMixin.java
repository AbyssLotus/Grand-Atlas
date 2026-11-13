package com.seaweed.grandatlas.mixin;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.world.CoreHandle;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structures (vanilla's and other mods') may run into Grand Atlas's set pieces: a mineshaft
 * opening into an Aurora Vault or a city arm reaching the Hollow is part of the fun (the user,
 * 2026-11-10). Only the parts nothing may be built through are kept clear: the vault's hanging
 * lake, well and rising fall, the Worldroot's trunk and taproot, Titan's skull
 * ({@link com.seaweed.grandatlas.core.SetPieces#guarded}). A structure keeps every piece that
 * stays out of those; the pieces that would cross them are left out, so a mineshaft simply ends
 * at the rock short of the lake. Worlds that aren't Grand Atlas's are untouched.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
    /**
     * Villages stand on rivers and lakes (the user, 2026-11-10: "make villages generate along
     * rivers"): one whose chunk has no river or lake within about 56 blocks of its middle, or sits in
     * the water itself, is not built. The village grid is made denser to make up for it
     * (tools/gen_data.py, structure_set/villages).
     *
     * <p>Decided here, before the village is assembled. The first version decided afterwards, so every
     * candidate far from water was built in full and thrown away: {@code /locate structure
     * #minecraft:village} from dry country ran for over a minute and the server's watchdog killed it
     * (2026-11-10), and a cartographer's map search would have done the same.
     */
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true, require = 0)
    private void grandatlas$villagesByWater(Holder<Structure> structure, ResourceKey<Level> dimension, RegistryAccess registries,
                                            ChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler, RandomState randomState,
                                            StructureTemplateManager templates, long seed, ChunkPos chunk, int references,
                                            LevelHeightAccessor heights, Predicate<Holder<Biome>> validBiome,
                                            CallbackInfoReturnable<StructureStart> cir) {
        if (!structure.is(net.minecraft.tags.StructureTags.VILLAGE)) return;
        AtlasCore core = CoreHandle.coreFrom(sampler);
        if (core == null) return;
        if (!grandatlas$byWater(core, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())) cir.setReturnValue(StructureStart.INVALID_START);
    }

    @Inject(method = "generate", at = @At("RETURN"), cancellable = true, require = 0)
    private void grandatlas$clearOfSetPieces(Holder<Structure> structure, ResourceKey<Level> dimension, RegistryAccess registries,
                                             ChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler, RandomState randomState,
                                             StructureTemplateManager templates, long seed, ChunkPos chunk, int references,
                                             LevelHeightAccessor heights, Predicate<Holder<Biome>> validBiome,
                                             CallbackInfoReturnable<StructureStart> cir) {
        StructureStart start = cir.getReturnValue();
        if (start == null || !start.isValid()) return;
        AtlasCore core = CoreHandle.coreFrom(sampler);
        if (core == null) return;
        BoundingBox b = start.getBoundingBox();
        if (!core.setPieces().guarded(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) return;
        java.util.List<net.minecraft.world.level.levelgen.structure.StructurePiece> kept = new java.util.ArrayList<>();
        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
            BoundingBox pb = piece.getBoundingBox();
            if (!core.setPieces().guarded(pb.minX(), pb.minY(), pb.minZ(), pb.maxX(), pb.maxY(), pb.maxZ())) kept.add(piece);
        }
        if (kept.size() == start.getPieces().size()) return;
        cir.setReturnValue(kept.isEmpty() ? StructureStart.INVALID_START
                : new StructureStart(start.getStructure(), start.getChunkPos(), start.getReferences(),
                        new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(kept)));
    }

    private static boolean grandatlas$byWater(AtlasCore core, int x, int z) {
        if (grandatlas$water(core.column(x, z))) return false; // not in it
        for (int r = 14; r <= 84; r += 14) {
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                if (grandatlas$water(core.column(x + (int) (Math.cos(a) * r), z + (int) (Math.sin(a) * r)))) return true;
            }
        }
        return false;
    }

    /** A river or lake (not the sea). */
    private static boolean grandatlas$water(com.seaweed.grandatlas.core.ColumnSample c) {
        return !Double.isNaN(c.water()) && !c.biome().name().endsWith("OCEAN") || c.biome() == com.seaweed.grandatlas.core.BiomeKind.RIVER;
    }
}
