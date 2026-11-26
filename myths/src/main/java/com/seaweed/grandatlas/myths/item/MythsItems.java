package com.seaweed.grandatlas.myths.item;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.block.MythsBlocks;
import com.seaweed.grandatlas.myths.entity.MythsEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The add-on's own items (block items live with their blocks in MythsBlocks), and its
 * creative-menu tab.
 */
public final class MythsItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandAtlasMyths.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GrandAtlasMyths.MOD_ID);

    /** Found in Daedalus's Labs. 512 blocks of trail. */
    public static final DeferredItem<AriadneThreadItem> ARIADNE_THREAD = ITEMS.register("ariadne_thread",
            () -> new AriadneThreadItem(new Item.Properties().durability(512).rarity(Rarity.UNCOMMON)));

    /**
     * The labrys, the Minoan double axe, dropped by the Minotaur. Netherite-tier mining, and
     * a heavy, slow swing: 13 damage (1 base + 4 netherite + 8) at 0.8 attacks a second.
     */
    public static final DeferredItem<AxeItem> LABRYS = ITEMS.register("labrys",
            () -> new AxeItem(Tiers.NETHERITE, new Item.Properties()
                    .fireResistant()
                    .rarity(Rarity.EPIC)
                    .attributes(AxeItem.createAttributes(Tiers.NETHERITE, 8.0f, -3.2f))));

    /** For testing and creative building; the Minotaur normally lives in the labs. */
    public static final DeferredItem<DeferredSpawnEggItem> MINOTAUR_SPAWN_EGG = ITEMS.register("minotaur_spawn_egg",
            () -> new DeferredSpawnEggItem(MythsEntities.MINOTAUR, 0x3B2A1E, 0xB08D57, new Item.Properties()));

    /** One "Myths & Legends" tab in the creative menu, holding everything. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.grandatlas_myths"))
                    .icon(() -> new ItemStack(LABRYS.get()))
                    .displayItems((params, output) -> {
                        output.accept(MythsBlocks.LABYRINTH_STONE_ITEM.get());
                        output.accept(MythsBlocks.LABYRINTH_BRICKS_ITEM.get());
                        output.accept(MythsBlocks.LABYRINTH_FRIEZE_ITEM.get());
                        output.accept(MythsBlocks.LABYRINTH_TRIM_ITEM.get());
                        output.accept(MythsBlocks.EXIT_HATCH_ITEM.get());
                        output.accept(MythsBlocks.LABYRINTH_MARKER_ITEM.get());
                        output.accept(ARIADNE_THREAD.get());
                        output.accept(LABRYS.get());
                        output.accept(MINOTAUR_SPAWN_EGG.get());
                    })
                    .build());

    private MythsItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
