package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The cave biomes' new blocks (M7e; textures and LabPBR specular maps painted by
 * tools/textures.py, models and data written by tools/gen_data.py). Vanilla's own classes where
 * one fits; {@link GlowwormSilkBlock} and {@link IcicleBlock} where none does.
 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GrandAtlas.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandAtlas.MOD_ID);

    /** Bone meal on a glowcap grows this (tools/gen_data.py). */
    public static final ResourceKey<net.minecraft.world.level.levelgen.feature.Feature> HUGE_GLOWCAP =
            ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath(GrandAtlas.MOD_ID, "huge_glowcap"));

    public static final DeferredBlock<HalfTransparentBlock> SELENITE = BLOCKS.registerBlock("selenite_block", HalfTransparentBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.CHIME)
                    .strength(1.2F).requiresCorrectToolForDrops().sound(SoundType.CALCITE).noOcclusion());
    public static final DeferredBlock<AmethystClusterBlock> SELENITE_SHARD = BLOCKS.registerBlock("selenite_shard",
            p -> new AmethystClusterBlock(9.0F, 3.0F, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).forceSolidOn().noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER).strength(1.2F).lightLevel(s -> 4).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<GlowwormSilkBlock> GLOWWORM_SILK = BLOCKS.registerBlock("glowworm_silk", GlowwormSilkBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).replaceable().noCollision().instabreak()
                    .sound(SoundType.HANGING_ROOTS).lightLevel(s -> 7).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<MushroomBlock> GLOWCAP = BLOCKS.registerBlock("glowcap", p -> new MushroomBlock(HUGE_GLOWCAP, p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).noCollision().randomTicks().instabreak()
                    .sound(SoundType.GRASS).lightLevel(s -> 10).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<Block> GLOWCAP_BLOCK = BLOCKS.registerBlock("glowcap_block", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instrument(NoteBlockInstrument.BASS)
                    .strength(0.2F).sound(SoundType.WOOD).lightLevel(s -> 12));
    public static final DeferredBlock<IcicleBlock> ICICLE = BLOCKS.registerBlock("icicle", IcicleBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.ICE).noOcclusion().sound(SoundType.GLASS)
                    .strength(0.5F).dynamicShape().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<Block> HOARFROST = BLOCKS.registerBlock("hoarfrost", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME)
                    .friction(0.98F).strength(0.5F).sound(SoundType.GLASS));
    public static final DeferredBlock<SandBlock> ABYSSAL_SAND = BLOCKS.registerBlock("abyssal_sand", p -> new SandBlock(new ColorRGBA(0x303642), p),
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.SNARE)
                    .strength(0.5F).sound(SoundType.SAND));
    public static final DeferredBlock<Block> CINDER_MOSS = BLOCKS.registerBlock("cinder_moss", Block::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).strength(0.1F).sound(SoundType.MOSS)
                    .lightLevel(s -> 3).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<CarpetBlock> CINDER_MOSS_CARPET = BLOCKS.registerBlock("cinder_moss_carpet", CarpetBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).strength(0.1F).sound(SoundType.MOSS_CARPET)
                    .lightLevel(s -> 3).pushReaction(PushReaction.POPPED));

    static {
        for (DeferredBlock<?> b : java.util.List.of(SELENITE, SELENITE_SHARD, GLOWWORM_SILK, GLOWCAP, GLOWCAP_BLOCK, ICICLE, HOARFROST,
                ABYSSAL_SAND, CINDER_MOSS, CINDER_MOSS_CARPET)) {
            ITEMS.registerSimpleBlockItem(b);
        }
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
                for (DeferredBlock<?> b : java.util.List.of(SELENITE, SELENITE_SHARD, GLOWWORM_SILK, GLOWCAP, GLOWCAP_BLOCK, ICICLE,
                        HOARFROST, ABYSSAL_SAND, CINDER_MOSS, CINDER_MOSS_CARPET)) {
                    e.accept(b.get());
                }
            }
        });
    }
}
