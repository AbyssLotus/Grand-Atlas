package com.seaweed.grandatlas.core.macro;

import java.util.Locale;

/** Where a column sits within a macro landform; the top entries of its tag stack (design §6.1). */
public enum MacroZone {
    /** Not part of a macro. */
    NONE,
    /** On the macro's own terrain, away from its edge. */
    INTERIOR,
    /** The top of a cliff wall or stepped edge. */
    RIM,
    /** One of the gentle ways up a cliff wall. */
    RAMP,
    /** The ground at the foot of the edge, still shaped by the macro. */
    FOOT,
    /** A massif's broad rising foothills. */
    FLANK,
    /** The ring of mountains around a basin. */
    RING,
    /** A landmark peak standing on a macro's own terrain (a Great Plateau's Mount Hylia). */
    SUMMIT,
    /** A perched lake on a macro's own terrain. */
    LAKE,
    /** A river on a macro's own terrain, running to its edge. */
    RIVER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
