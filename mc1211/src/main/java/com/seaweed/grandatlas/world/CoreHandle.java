package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * One {@link AtlasCore} per world seed, shared by every Grand Atlas density function and the
 * biome source.
 *
 * <p>1.21.1 has no compile step that hands a density function the world's random source (26.3
 * does). Here {@code mixin/RandomStateMixin} marks the moment a world's noise router is being
 * wired and passes that world's positional random; a Grand Atlas function that is visited then
 * ({@code mapAll}) returns a copy of itself bound to the core for that seed. The core seed is
 * drawn exactly as the 26.3 build draws it ({@code random.fromHashOf(grandatlas:core)}), so the
 * same world seed gives the same terrain on both versions.
 */
public final class CoreHandle {
    private CoreHandle() {}

    private static final ResourceLocation SEED_ID = ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "core");
    private static final Map<Long, AtlasCore> CORES = new ConcurrentHashMap<>();
    private static final ThreadLocal<PositionalRandomFactory> WIRING = new ThreadLocal<>();
    /** The core a Grand Atlas function bound to during the wiring in progress on this thread, if one did. */
    private static final ThreadLocal<AtlasCore> WIRED = new ThreadLocal<>();
    private static volatile @Nullable AtlasCore last;

    /** Implemented by every Grand Atlas density function so the core can be found from it. */
    public interface CoreFunction {
        @Nullable AtlasCore boundCore();
    }

    /** Called by the mixin as a world's noise router starts being wired, on that thread. */
    public static void beginWiring(PositionalRandomFactory random) {
        WIRING.set(random);
        WIRED.remove();
    }

    /** Ends the wiring; returns the core this world's functions bound to, or null if none of ours were in its router. */
    public static @Nullable AtlasCore endWiring() {
        WIRING.remove();
        AtlasCore core = WIRED.get();
        WIRED.remove();
        return core;
    }

    /** The core of the world whose router is being wired on this thread, or null outside wiring. */
    public static @Nullable AtlasCore wiring() {
        PositionalRandomFactory random = WIRING.get();
        if (random == null) return null;
        long seed = random.fromHashOf(SEED_ID).nextLong();
        AtlasCore core = CORES.get(seed);
        if (core == null) {
            core = CORES.computeIfAbsent(seed, AtlasCore::new);
            GrandAtlas.LOG.info("Grand Atlas core seed {}", seed);
        }
        last = core;
        WIRED.set(core);
        return core;
    }

    /** A function's own core, or the most recent one if it was never bound (a foreign caller). */
    public static AtlasCore require(@Nullable AtlasCore bound) {
        if (bound != null) return bound;
        AtlasCore c = last;
        if (c == null) throw new IllegalStateException("A Grand Atlas density function was sampled before any world was wired");
        return c;
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
        AtlasCore core = coreFrom(level.getChunkSource().randomState());
        lastLevel = new LevelCore(level, core);
        return core;
    }

    /** Drops the cores and the remembered level when a server stops. */
    public static void release() {
        lastLevel = null;
        last = null;
        CORES.clear();
    }

    /** The most recently wired core: a fallback for callers that pass a foreign sampler. */
    public static @Nullable AtlasCore last() {
        return last;
    }

    /**
     * The core a world's random state was wired to, or null if the world isn't Grand Atlas's. This
     * is the lookup to use wherever a {@code RandomState} is to hand: it is recorded when the world
     * is built, so it holds whatever another mod later does to the density functions. (C2ME
     * compiles the router into its own classes, which {@link #coreFrom(DensityFunction)} cannot see
     * through: with it installed every lookup by function came back empty and the mod's features,
     * rules and shortcuts all switched themselves off. Found 2026-11-11.)
     */
    public static @Nullable AtlasCore coreFrom(net.minecraft.world.level.levelgen.RandomState randomState) {
        return ((AtlasRandomState) (Object) randomState).grandatlas$core();
    }

    /** Finds the core behind a climate sampler, or null if the sampler isn't ours. */
    public static @Nullable AtlasCore coreFrom(Climate.Sampler sampler) {
        return coreFrom(sampler.temperature());
    }

    /** Finds the core behind a density function, looking through references, caches and markers. */
    public static @Nullable AtlasCore coreFrom(DensityFunction f) {
        for (int depth = 0; depth < 8; depth++) {
            if (f instanceof CoreFunction ours) return ours.boundCore();
            if (f instanceof DensityFunctions.HolderHolder holder) {
                f = holder.function().value();
            } else if (f instanceof DensityFunctions.MarkerOrMarked marker) {
                f = marker.wrapped();
            } else {
                return null;
            }
        }
        return null;
    }
}
