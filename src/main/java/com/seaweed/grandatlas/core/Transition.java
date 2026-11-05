package com.seaweed.grandatlas.core;

import java.util.Locale;

/**
 * How a column sits relative to province boundaries (design §7). The blender doesn't look these
 * up from a table. They're labels for what its continuous rules produced at that spot, so
 * {@code /grandatlas where} and the preview can show them.
 */
public enum Transition {
    /** Well inside one province. */
    INTERIOR,
    /** A gentle blend between similar landforms. */
    SOFT,
    /** A high landform meets a low one: a narrow, steep cliff band. */
    ESCARPMENT,
    /** Two raised landforms taper down to a valley along their shared edge. */
    VALLEY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
