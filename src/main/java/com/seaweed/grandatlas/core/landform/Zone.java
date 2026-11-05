package com.seaweed.grandatlas.core.landform;

import java.util.Locale;

/**
 * Named sub-areas of a landform. A zone picks the column's biome slot, and it's the bottom
 * entry of the column's tag stack (design §6.1), e.g. canyon_plateau → canyon_wall.
 */
public enum Zone {
    OCEAN, COAST,
    PLAIN, HILL, UPLAND, VALLEY,
    LOW_SLOPE, HIGH_SLOPE, PEAK, CIRQUE,
    MESA_TOP, CANYON_WALL, CANYON_FLOOR, BUTTE,
    DUNE, INTERDUNE, OUTCROP, ROCK, SAND,
    PAN, BASIN_FLOOR, RANGE,
    RIVER, LAKE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
