package com.seaweed.grandatlas.myths.block;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MythsBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GrandAtlasMyths.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandAtlasMyths.MOD_ID);

    public static final DeferredBlock<Block> LABYRINTH_STONE = BLOCKS.registerSimpleBlock("labyrinth_stone", BlockBehaviour.Properties.of()
            .destroyTime(-1)
            .sound(SoundType.STONE)
            .explosionResistance(740000000)
            .noLootTable()
            .mapColor(MapColor.NONE)
    );
    public static final DeferredItem<BlockItem> LABYRINTH_STONE_ITEM = ITEMS.registerSimpleBlockItem("labyrinth_stone", LABYRINTH_STONE);

    public static void register(IEventBus modBus){
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

}
