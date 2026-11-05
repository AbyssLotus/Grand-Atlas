package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.landform.Zone;
import com.seaweed.grandatlas.core.macro.Macro;
import com.seaweed.grandatlas.core.macro.MacroZone;
import com.seaweed.grandatlas.core.macro.Wall;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the core decides about one (x, z) column. Immutable, so it can be shared
 * between the density functions, the biome source and the climate router slots.
 *
 * @param height          surface height in blocks (fractional; the density gradient crosses zero here)
 * @param datum           the frame's base elevation (design §6.2); {@code relief} is measured from it
 * @param relief          blended landform relief over the datum
 * @param continentalness ocean (&lt; 0) to deep interior (≈ 1)
 * @param temperature     roughly [-1, 1], cold to hot
 * @param humidity        roughly [-1, 1], dry to wet
 * @param mountain        0 (flat) to 1 (high mountains); drives router erosion and deep dark placement
 * @param erosion         vanilla-style erosion for the router, -1 (rugged) to 1 (flat)
 * @param weirdness       vanilla-style weirdness for the router (kept clear of the river band on land)
 * @param roughness       0..1, strength of 3D surface roughness (ledges, overhangs)
 * @param macro           the macro landform this column belongs to or borders, or null
 * @param macroZone       where the column sits within that macro
 * @param macroInside     0..1, how much the macro's own terrain shows here (at least 0.5: it wins)
 * @param landform        the dominant province's landform (a macro's sub-province when inside one)
 * @param zone            the zone within that landform
 * @param transition      where the column sits relative to province edges
 * @param provinceX       the dominant province's cell
 * @param provinceZ       the dominant province's cell
 * @param biome           the surface biome
 * @param rock            wind-carved rock standing on the ground here: its height over the sand under it (0 if none)
 * @param rockInside      blocks inside that rock's outline
 * @param rockSize        that rock's radius in blocks
 * @param water           surface of perched water over this column (a plateau's lake or river), or NaN
 * @param nearWater       a river or lake is over or within a few blocks of this column: no caves open here
 * @param flowX           downstream direction of the river water here (both zero in still water)
 * @param flowZ           downstream direction of the river water here
 * @param spill           at a pool's lip, where the river pours over into the next pool down
 * @param uncut           what the world interpolates: the surface before rivers and hollows cut it, and over a
 *                        hoodoo's surroundings its full height (the per-block cut trims both back to {@code height})
 * @param site            the meso site this column is part of, or null
 * @param micro           the micro feature shaping this column, or null
 * @param wall            where the column sits on a Great Plateau's wall, or null
 * @param shore           where the column sits on a coast, or null away from the sea
 */
public record ColumnSample(
        double height,
        double datum,
        double relief,
        double continentalness,
        double temperature,
        double humidity,
        double mountain,
        double erosion,
        double weirdness,
        double roughness,
        Macro macro,
        MacroZone macroZone,
        double macroInside,
        Landform landform,
        Zone zone,
        Transition transition,
        int provinceX,
        int provinceZ,
        BiomeKind biome,
        double rock,
        double rockInside,
        double rockSize,
        double water,
        boolean nearWater,
        double flowX,
        double flowZ,
        boolean spill,
        double uncut,
        MesoSite site,
        MicroFeature micro,
        Wall wall,
        Shore shore) {

    public int surfaceY() {
        return (int) Math.floor(height);
    }

    public boolean isOcean() {
        return height < AtlasCore.SEA_LEVEL - 1;
    }

    /** The column's tag stack, largest scale first (design §6.1): macro, its zone, landform, zone, meso site, micro feature. */
    public List<String> tags() {
        List<String> tags = new ArrayList<>();
        if (macro != null) {
            tags.add(macro.id());
            tags.add(macroZone.id());
        }
        tags.add(landform.id());
        tags.add(zone.id());
        if (site != null) tags.add(site.kind().id());
        if (micro != null && micro.present()) tags.add(micro.kind.id());
        return tags;
    }
}
