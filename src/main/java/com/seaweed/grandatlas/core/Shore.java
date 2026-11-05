package com.seaweed.grandatlas.core;

/**
 * Where a column sits on a coast (docs/M3_M4_PLAN.md, phase 1).
 *
 * @param kind       the dominant coast type here
 * @param distance   blocks from the shore line, after bays and headlands: positive inland, negative out to sea
 * @param edge       where a cliff's face stands, in the same distance (a ragged line around zero)
 * @param beachWidth how far inland a beach's sand runs before the land takes over
 * @param cliff      0..1, how much of the cliff profile is in this column's blend
 * @param pebbles    a stony stretch: its beach is shingle rather than sand
 * @param feature    the sea stack or sea cave reaching this column, or null
 * @param stack      the column is a sea stack's top
 * @param sheer      at a stack's sheer side
 */
public record Shore(Kind kind, double distance, double edge, double beachWidth, double cliff, boolean pebbles,
                    CoastFeature feature, boolean stack, boolean sheer) {

    public enum Kind { BEACH, CLIFF, ROCKY, MARSH }

    /** At a sea cliff's face or a stack's side, which are sheer on purpose (the seam checks leave them be). */
    public boolean atFace() {
        return sheer || cliff > 0.02 && Math.abs(distance - edge) < 3;
    }
}
