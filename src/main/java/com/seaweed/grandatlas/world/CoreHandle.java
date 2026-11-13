package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.op.SliceFunction;
import org.jspecify.annotations.Nullable;

/**
 * One {@link AtlasCore} per world seed, shared by every Grand Atlas density function and the
 * biome source.
 *
 * <p>When a density function compiles, Minecraft hands it a {@link DensityFunction.CompileContext}
 * whose {@code createRandom(id)} is seeded from the world seed. We draw the core seed from
 * that, so the same world always gets the same terrain and different worlds differ.
 */
public final class CoreHandle {
    private CoreHandle() {}

    private static final Identifier SEED_ID = Identifier.fromNamespaceAndPath(GrandAtlas.MOD_ID, "core");
    private static final Map<Long, AtlasCore> CORES = new ConcurrentHashMap<>();
    private static volatile @Nullable AtlasCore last;

    /** Implemented by every compiled Grand Atlas sampler so the core can be found from it. */
    public interface CoreSampler extends DensitySampler {
        AtlasCore core();
    }

    public static AtlasCore coreFor(DensityFunction.CompileContext context) {
        long seed = context.createRandom(SEED_ID).nextLong();
        AtlasCore core = CORES.computeIfAbsent(seed, AtlasCore::new);
        last = core;
        return core;
    }

    private record LevelCore(net.minecraft.server.level.ServerLevel level, @Nullable AtlasCore core) {}

    private static volatile @Nullable LevelCore lastLevel;

    /**
     * The core generating a level, or null if the level isn't Grand Atlas's. Every feature asks per
     * chunk, so the last answer is kept (level and core together, so two dimensions generating at
     * once can't read one's level with the other's core).
     */
    public static @Nullable AtlasCore coreOf(net.minecraft.server.level.ServerLevel level) {
        LevelCore c = lastLevel;
        if (c != null && c.level == level) return c.core;
        AtlasCore core = coreFrom(level.getChunkSource().randomState().createClimateSampler(
                net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED));
        lastLevel = new LevelCore(level, core);
        return core;
    }

    /**
     * Drops the cores and the remembered level when a server stops. Without this the last world's
     * ServerLevel (and every core's caches) stayed referenced from here after leaving a
     * singleplayer world, until another world happened to replace them.
     */
    public static void release() {
        lastLevel = null;
        last = null;
        CORES.clear();
    }

    /** The most recently compiled core: a fallback for callers that pass a foreign sampler. */
    public static @Nullable AtlasCore last() {
        return last;
    }

    /**
     * Finds the core behind a climate sampler, or null if the sampler isn't ours. The compiler
     * may wrap our 2D climate samplers in a Y slice or a cache, so those get unwrapped.
     */
    public static @Nullable AtlasCore coreFrom(Climate.Sampler sampler) {
        DensitySampler s = sampler.temperature().sampler();
        for (int depth = 0; depth < 8; depth++) {
            switch (s) {
                case CoreSampler ours -> {
                    return ours.core();
                }
                case SliceFunction.YSampler slice -> s = slice.input();
                case SliceFunction.XSampler slice -> s = slice.input();
                case SliceFunction.ZSampler slice -> s = slice.input();
                case SliceFunction.XzSampler slice -> s = slice.input();
                case CachingDensitySampler cache -> s = cache.input();
                case ContextBoundSampler bound -> s = bound.fallbackSampler();
                default -> {
                    return null;
                }
            }
        }
        return null;
    }
}
