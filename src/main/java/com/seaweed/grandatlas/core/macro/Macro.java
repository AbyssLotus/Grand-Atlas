package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * A macro landform kind (design §6): a rare, kilometres-wide foundation that ordinary terrain
 * then generates on. A macro never decides the terrain inside itself. It decides:
 * <ul>
 *   <li>its <b>footprint</b> (radius; the shape is a warped blob from {@link MacroField}),</li>
 *   <li>the <b>frame</b> its interior builds on (datum, relief scale and budget, climate, water),</li>
 *   <li>the <b>family</b> of landforms its sub-mosaic may use, and</li>
 *   <li>its <b>edge</b>: how its interior meets the ordinary terrain outside.</li>
 * </ul>
 */
public abstract class Macro {
    /** What {@link #combine} produces for a column. */
    public static final class Combined {
        public double height;
        /** 0..1, how much of the column is the macro's interior (≥ 0.5 means its terrain wins). */
        public double inside;
        public MacroZone zone;
        /** Extra 3D roughness, e.g. on a cliff face. */
        public double roughness;
        /** A Great Plateau's wall here, for what it cuts per block; null elsewhere. */
        public Wall wall;
    }

    /**
     * The macro's own terrain before landmarks, for landmarks that need to look around (a river
     * tracing its course). Fills {@code out} with {height, blocks inside the edge}; false outside
     * this site.
     */
    @FunctionalInterface
    public interface Terrain {
        boolean sample(double x, double z, double[] out);
    }

    /** What {@link #landmarks} produces for a column. */
    public static final class Landmarks {
        /** In: the macro's own terrain height. Out: with landmarks. */
        public double height;
        /** In: the datum the macro's terrain builds on. */
        public double datum;
        /** Water surface over the column (a lake, a river), or NaN. */
        public double water;
        /** Extra 3D roughness (a summit's crags). */
        public double roughness;
        /** Overrides the macro zone when not null. */
        public MacroZone zone;

        public void reset(double height, double datum) {
            this.height = height;
            this.datum = datum;
            this.water = Double.NaN;
            this.roughness = 0;
            this.zone = null;
        }
    }

    protected final long seed;

    protected Macro(long seed) {
        this.seed = seed;
    }

    public abstract String id();

    /** Preview colour for macro outlines. */
    public abstract int color();

    /** Weight for hosting this kind at a macro cell with this centre context; 0 means never. */
    public abstract double weight(WorldField.Point centre);

    public abstract double radius(MacroSite site);

    /** How far beyond the footprint radius, in blocks, this macro still shapes terrain. */
    public abstract double reachBlocks(MacroSite site);

    /**
     * How much of the frame's lift above the world datum biomes ignore (0..1). The climate table
     * reads absolute height, so a 300-block plateau would be alpine from edge to edge; a plateau
     * discounts most of its lift, so its top grows its own climate's lowland biomes and only its
     * summit reaches the snow. 0 (the default) keeps a massif's dome alpine.
     */
    public double altitudeDiscount() {
        return 0;
    }

    /** Province cell size of this macro's sub-mosaic. */
    public abstract double subCell();

    /**
     * Weight of a landform in this macro's family for a sub-province's context (which already
     * carries the macro's climate offsets); 0 = not in the family. A null context asks whether
     * the landform can ever appear.
     */
    public abstract double familyWeight(Landform landform, WorldField.Point context);

    /** The frame the macro's interior builds on at this column. */
    public abstract Frame frame(MacroField.Hit hit, WorldField.Point world);

    /**
     * True when the column is so far inside that the ordinary terrain outside can't show, so
     * the outer blend can be skipped.
     */
    public abstract boolean covers(MacroField.Hit hit);

    /**
     * Landmarks on the macro's own terrain, applied before {@link #combine}: features that belong
     * to this one site rather than to a landform (design §6.4). None by default.
     */
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {}

    /** Combines the ordinary terrain outside ({@code outerH}) with the macro's own ({@code innerH}). */
    public abstract void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out);

    /**
     * 0..1: how close the direction from the centre is to one of {@code count} spokes (ramps up
     * a cliff, passes through a rim). Spokes sit at random angles per site; the width is in
     * cosine units (0.04 is about ±16°).
     */
    protected static double spokes(MacroField.Hit hit, int count, int salt, double width) {
        double best = 0;
        for (int k = 0; k < count; k++) {
            double angle = hit.site.param(salt + k) * Math.PI * 2;
            double dot = hit.dirX * Math.cos(angle) + hit.dirZ * Math.sin(angle);
            best = Math.max(best, smooth(1 - width, 1 - width * 0.15, dot));
        }
        return best;
    }

    protected long noiseSeed(int salt) {
        return GradientNoise.fork(seed, salt);
    }

    protected static double smooth(double edge0, double edge1, double v) {
        double t = Math.max(0, Math.min(1, (v - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
