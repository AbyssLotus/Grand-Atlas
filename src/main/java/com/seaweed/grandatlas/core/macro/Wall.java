package com.seaweed.grandatlas.core.macro;

/**
 * Where a column sits on a Great Plateau's wall, for what the wall cuts per block: its overhanging
 * lip and its cave mouths (AtlasCore asks {@link GreatPlateau#carveWall}).
 *
 * @param dist      blocks inside the wall's middle (negative outside), rim features included
 * @param arc       position along the wall in blocks
 * @param width     the wall's width (34 on a cliff, wider on a ramp)
 * @param low       the ground at the wall's foot here
 * @param high      the tableland's height here
 * @param ledgeLow  under an overhanging lip: the wall's own surface (air from here up), or NaN
 * @param ledgeHigh under an overhanging lip: the lip's underside (air up to here), or NaN
 * @param seed      the site's seed
 * @param sharp     an overhanging lip or a spire: sheer on purpose, so the seam checks leave it be
 */
public record Wall(double dist, double arc, double width, double low, double high, double ledgeLow, double ledgeHigh, long seed, boolean sharp) {
}
