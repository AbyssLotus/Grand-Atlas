package com.seaweed.grandatlas.core.micro;

import java.util.Locale;

/** The micro features (8-100 blocks) a meso site can hold. See docs/MICRO_FEATURES.md. */
public enum MicroKind {
    /** A small lake in a hollow of its own, filled to just under its lowest rim. */
    POND,
    /** A round, sheer-walled pit with a bell-shaped undercut below its lip; dry floor. */
    SINKHOLE,
    /** A sinkhole that reaches the water table: the same pit with a pool at the bottom. */
    CENOTE,
    /** A pillar of rock with a narrow neck under a wider caprock. */
    HOODOO,
    /** A small volcanic cone of cinders with a crater on top. */
    CINDER_CONE,
    /** A cluster of upright basalt columns, like the ends of a lava flow's joints. */
    BASALT_COLUMNS,
    /** A great boulder a glacier left behind, half sunk in the ground. */
    ERRATIC,
    /** A stack of rounded granite boulders weathered out of a hilltop. */
    TOR,
    /** A small flat-topped hill of banded rock with a stepped side and a talus apron. */
    BUTTE,
    /** An ancient tree, far bigger than the forest round it: buttress roots, great branches, a broad crown. */
    GIANT_TREE,
    /** A giant red or brown mushroom on a tall stem. */
    GIANT_MUSHROOM,
    /** A dead tree still standing: a bare trunk with broken stubs of branches. */
    SNAG,
    /** A winding shallow ravine through the woods, floored with ferns and moss. */
    GULLY,
    /**
     * A collapsed cave (Hamilton Pool): a round pit holding a turquoise pool, half roofed by a great
     * overhanging shelf of rock with a dry ledge under it, a spring-fed stream pouring off the rim.
     */
    GROTTO,
    /**
     * Terraced cascades (Kuang Si): a chain of wide turquoise pools stepping down a hillside, each held
     * by a curved travertine rim that the water spills over in a sheet into the pool below.
     */
    TRAVERTINE,
    /**
     * A lone crag tower (the user's BotW twin peaks): a great stepped rock tower rising sheer out of
     * open country, its top split by a cleft into two summits.
     */
    SPIRE,
    /**
     * A caldera: a volcano's rim round a lake, sheer cliffs over talus inside, long ribbed flanks
     * outside. Its lake stands well over the country round it.
     */
    CALDERA,
    // ---- M6 fantasy (built whole in world/MicroDetail)
    /** A leaning hexagonal spire of aurora crystal. r: half-width; h: length; r2: lean heading; extra: colour 0-4. */
    CRYSTAL_SPIRE,
    /** A standing stone cut with runes (extra 0), a fallen one (1), or the altar at the ring's heart (2). r2: facing. */
    MONOLITH,
    /** A boulder floating over the ground, a hanging root of rock under it. r: radius; h: height over the ground. */
    FLOAT_ROCK,
    /** A giant's rib cage: a spine along the ground with ribs arching over it, a skull at one end. h: height; r2: length; extra: heading. */
    RIB_CAGE,
    /** A trunk turned to stone: standing (extra -1) or fallen toward the heading in extra. r: radius; h: length. */
    PETRIFIED_TRUNK;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
