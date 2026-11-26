package com.seaweed.grandatlas.myths.entity;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The add-on's entity types. An ENTITY TYPE is to entities what a Block is to placed blocks:
 * one shared description (size, category, how far clients track it) for every individual mob.
 */
public final class MythsEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, GrandAtlasMyths.MOD_ID);

    /** Ravager-sized (1.95 x 2.2), so it fits the 3-wide, 5-tall corridors. */
    public static final DeferredHolder<EntityType<?>, EntityType<Minotaur>> MINOTAUR = ENTITIES.register("minotaur",
            () -> EntityType.Builder.of(Minotaur::new, MobCategory.MONSTER)
                    .sized(1.95f, 2.2f)
                    .clientTrackingRange(10)
                    .build(GrandAtlasMyths.MOD_ID + ":minotaur"));

    private MythsEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(MythsEntities::onAttributes);
    }

    /** MOD bus: every living entity type must have its attributes (health, damage...) registered. */
    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MINOTAUR.get(), Minotaur.createAttributes().build());
    }
}
