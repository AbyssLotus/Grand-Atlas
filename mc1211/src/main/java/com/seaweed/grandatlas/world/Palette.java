package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks newer than Minecraft 1.21.1, found by id when a backport mod supplies them.
 *
 * <p>The 26.3 build uses a handful of blocks 1.21.1 doesn't have: pale oak (1.21.4) and the
 * 1.21.5 ground plants. Vanilla Backport registers all of them under their vanilla ids
 * ({@code minecraft:pale_oak_log} and so on; checked in its 1.2.2 jar, 2026-11-11). Each is looked
 * up once, the first time worldgen asks (long after every mod has registered its blocks); without
 * the mod, the stand-in named here is used instead, and the swap is logged once, so Grand Atlas
 * loads either way (docs/BACKPORT_1.21.1_PLAN.md).
 *
 * <p>A stand-in of air means "place nothing": callers check {@link BlockState#isAir()}.
 */
public final class Palette {
    private Palette() {}

    private static final Map<ResourceLocation, BlockState> FOUND = new ConcurrentHashMap<>();

    public static Block paleOakLog() {
        return block("pale_oak_log", Blocks.DARK_OAK_LOG);
    }

    public static Block leafLitter() {
        return block("leaf_litter", Blocks.AIR);
    }

    public static Block bush() {
        return block("bush", Blocks.FERN);
    }

    public static Block shortDryGrass() {
        return block("short_dry_grass", Blocks.DEAD_BUSH);
    }

    public static Block tallDryGrass() {
        return block("tall_dry_grass", Blocks.DEAD_BUSH);
    }

    public static Block fireflyBush() {
        return block("firefly_bush", Blocks.FERN);
    }

    private static Block block(String path, Block fallback) {
        return stateOr(ResourceLocation.withDefaultNamespace(path), fallback.defaultBlockState()).getBlock();
    }

    /** The default state of the block with this id, or the fallback if no such block is registered. */
    public static BlockState stateOr(ResourceLocation id, BlockState fallback) {
        BlockState known = FOUND.get(id);
        if (known != null) return known;
        BlockState state;
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        } else {
            state = fallback;
            GrandAtlas.LOG.info("Grand Atlas: no block '{}' (newer than 1.21.1; Vanilla Backport adds it); using {}", id,
                    BuiltInRegistries.BLOCK.getKey(fallback.getBlock()));
        }
        FOUND.put(id, state);
        return state;
    }
}
