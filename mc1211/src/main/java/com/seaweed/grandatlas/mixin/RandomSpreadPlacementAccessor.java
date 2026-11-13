package com.seaweed.grandatlas.mixin;

import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code world/VillageDensity} set the grid a structure set is tried on. */
@Mixin(RandomSpreadStructurePlacement.class)
public interface RandomSpreadPlacementAccessor {
    @Mutable
    @Accessor("spacing")
    void grandatlas$setSpacing(int spacing);

    @Mutable
    @Accessor("separation")
    void grandatlas$setSeparation(int separation);
}
