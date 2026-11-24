package com.seaweed.grandatlas.entity;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Grand Atlas's mobs (1.21.1 only). The Potted Monster: the user's first Blockbench mob, a
 * snapping plant in a terracotta pot that lives in the fungal forests of the deep. Its natural
 * spawns are in the fungal_forest biome's JSON (tools/gen_data.py, MOBS).
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, GrandAtlas.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandAtlas.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PottedMonster>> POTTED_MONSTER = ENTITIES.register("potted_monster",
            () -> EntityType.Builder.of(PottedMonster::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.5F)
                    .eyeHeight(1.2F)
                    .clientTrackingRange(8)
                    .build(GrandAtlas.MOD_ID + ":potted_monster"));

    /** Terracotta and the purple of its jaws, from the user's texture. */
    public static final DeferredItem<Item> POTTED_MONSTER_SPAWN_EGG = ITEMS.register("potted_monster_spawn_egg",
            () -> new DeferredSpawnEggItem(POTTED_MONSTER, 0xB5654B, 0x4B2C8C, new Item.Properties()));

    /** The Crystal Eel: a small blue eel of the underground waters (entity/CrystalEel). */
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalEel>> CRYSTAL_EEL = ENTITIES.register("crystal_eel",
            () -> EntityType.Builder.of(CrystalEel::new, MobCategory.UNDERGROUND_WATER_CREATURE)
                    .sized(0.6F, 0.45F)
                    .eyeHeight(0.25F)
                    .clientTrackingRange(4)
                    .build(GrandAtlas.MOD_ID + ":crystal_eel"));

    /** The blue and the olive bands of the user's texture. */
    public static final DeferredItem<Item> CRYSTAL_EEL_SPAWN_EGG = ITEMS.register("crystal_eel_spawn_egg",
            () -> new DeferredSpawnEggItem(CRYSTAL_EEL, 0x0A6AF0, 0x7A7A1A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        com.seaweed.grandatlas.debug.MobCheck.register();
        modBus.addListener(EntityAttributeCreationEvent.class, e -> {
            e.put(POTTED_MONSTER.get(), PottedMonster.createAttributes().build());
            e.put(CRYSTAL_EEL.get(), CrystalEel.createAttributes().build());
        });
        modBus.addListener(RegisterSpawnPlacementsEvent.class, e -> {
            e.register(POTTED_MONSTER.get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModEntities::pottedMonsterCanSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            e.register(CRYSTAL_EEL.get(), SpawnPlacementTypes.IN_WATER,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CrystalEel::checkCrystalEelSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                e.accept(POTTED_MONSTER_SPAWN_EGG.get());
                e.accept(CRYSTAL_EEL_SPAWN_EGG.get());
            }
        });
    }

    /**
     * Any light (fungal forests glow with shroomlight and glowcaps, so the usual monster darkness
     * rule would all but rule it out), but only underground: never where the sky is in view.
     */
    static boolean pottedMonsterCanSpawn(EntityType<PottedMonster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        return MobSpawnType.isSpawner(reason) || reason == MobSpawnType.SPAWN_EGG || reason == MobSpawnType.COMMAND || !level.canSeeSky(pos);
    }
}
