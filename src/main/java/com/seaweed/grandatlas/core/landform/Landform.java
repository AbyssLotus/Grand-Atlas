package com.seaweed.grandatlas.core.landform;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * One kind of terrain a province can hold (design §5.4). Implementations are created per
 * world (they hold seeds and per-thread caches) and must be pure functions of position and
 * province, so every chunk computes the same answer.
 */
public interface Landform {
    /** Broad height class; differences between neighbours drive contrast and escarpments. */
    enum Elevation { LOW, MID, HIGH }

    String id();

    Elevation elevation();

    /** Preview colour for landform maps. */
    int color();

    /** Selection weight for a province with this context; 0 means never. */
    double weight(WorldField.Point context);

    /**
     * Weight multiplier for picking this landform next to a neighbour that already has it.
     * 0 (the default, for wonders) means never twice in a row; ordinary country may merge into
     * bigger regions (&lt; 1); ranges and dune seas attract each other into chains and ergs (&gt; 1).
     */
    default double sameNeighbour() {
        return 0;
    }

    /**
     * 0..1: how sharply this landform's edge meets a neighbour of different height. Hard
     * landforms (a canyon plateau) end in cliffs; soft ones (hills) blend over a few hundred blocks.
     */
    default double hardness() {
        return 0.15;
    }

    /**
     * Blocks over which relief fades toward a neighbour with a different landform (ranges into
     * foothills, dunes into a sand sheet). 0 keeps full relief to the edge.
     */
    default double taper() {
        return 0;
    }

    /** Fraction of the relief left at the very edge when tapering. */
    default double taperFloor() {
        return 0;
    }

    /**
     * Relief over {@code frame.datum()} at (x, z), plus zone and roughness.
     *
     * @param edge distance in blocks to the nearest province with a different landform
     *             (negative outside this province); capped at a few hundred blocks
     */
    void shape(Province province, Frame frame, double x, double z, double edge, Shape out);

    /** The biome for a column of this landform. {@code variant} is a slow noise in [-1, 1] for local variety. */
    BiomeKind biome(Zone zone, double temperature, double humidity, double height, double variant);
}
