package com.seaweed.grandatlas.core.landform;

/**
 * What a parent layer hands its children (design §6.2): they build relief over {@link #datum},
 * scaled by {@link #reliefScale} and kept inside the relief budget. Outside macro landforms
 * every column gets the world frame (datum from the continents, sea-level water). A macro
 * landform supplies a raised, domed or sunken frame with its own scale, budget, climate
 * offsets and water level.
 *
 * @param datum         base elevation children build on
 * @param reliefMin     lowest relief children may cut to (relative to datum, after scaling)
 * @param reliefMax     highest relief children may raise (relative to datum, after scaling)
 * @param reliefScale   multiplier on children's relief, e.g. so a mountain on a plateau stays a
 *                      summit rather than a second range
 * @param tempOffset    added to the world temperature inside this frame
 * @param humidOffset   added to the world humidity inside this frame
 * @param waterLevel    local base level for rivers and lakes (used from M4)
 */
public record Frame(double datum, double reliefMin, double reliefMax, double reliefScale,
                    double tempOffset, double humidOffset, double waterLevel) {

    public static Frame world(double datum, int seaLevel) {
        return new Frame(datum, seaLevel + 2 - datum, 1000, 1, 0, 0, seaLevel);
    }

    public double clampRelief(double relief) {
        return Math.max(reliefMin, Math.min(reliefMax, relief));
    }
}
