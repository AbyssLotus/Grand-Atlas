package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.mixin.RandomSpreadPlacementAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * The denser grid vanilla villages are tried on in a Grand Atlas world.
 *
 * <p>Grand Atlas keeps a village only beside a river or lake ({@code mixin/StructureMixin}), so it
 * tries them on a tighter grid to make up for the ones it turns away: one try per 10 chunks, not
 * 34. The 26.3 build does that with a data file. Here it is done in code, because a data file
 * cannot ask what else is installed, and it must not happen with Integrated Villages: those
 * villages keep 6 to 8 chunks clear of every place a vanilla village <em>might</em> stand, and on
 * a 10-chunk grid that is everywhere, so not one of them generated and {@code /locate} searched
 * until the server's watchdog stopped it (found 2026-11-11 with the Create Atlas pack). With that
 * mod present the grid is left as the game has it, and its villages fill the country instead.
 */
@EventBusSubscriber(modid = GrandAtlas.MOD_ID)
public final class VillageDensity {
    private VillageDensity() {}

    private static final int SPACING = 10, SEPARATION = 5;

    @SubscribeEvent
    static void apply(ServerAboutToStartEvent event) {
        var registries = event.getServer().registryAccess();
        LevelStem overworld = registries.registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        if (overworld == null || !(overworld.generator().getBiomeSource() instanceof AtlasBiomeSource)) return;
        if (ModList.get().isLoaded("integrated_villages")) {
            GrandAtlas.LOG.info("Grand Atlas: Integrated Villages is installed, so vanilla villages keep the game's own spacing");
            return;
        }
        Registry<StructureSet> sets = registries.registryOrThrow(Registries.STRUCTURE_SET);
        StructureSet villages = sets.get(BuiltinStructureSets.VILLAGES);
        if (villages != null && villages.placement() instanceof RandomSpreadStructurePlacement spread && spread.spacing() > SPACING) {
            RandomSpreadPlacementAccessor access = (RandomSpreadPlacementAccessor) spread;
            access.grandatlas$setSpacing(SPACING);
            access.grandatlas$setSeparation(SEPARATION);
            GrandAtlas.LOG.info("Grand Atlas: vanilla villages are tried every {} chunks (they are kept only by rivers and lakes)", SPACING);
        }
    }
}
