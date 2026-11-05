package com.seaweed.grandatlas.world;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/** Which way a river's water runs, in eight compass directions, or still (a lake, a pool). */
public enum RiverFlow implements StringRepresentable {
    NONE("none", 0, 0),
    NORTH("north", 0, -1),
    NORTH_EAST("north_east", 1, -1),
    EAST("east", 1, 0),
    SOUTH_EAST("south_east", 1, 1),
    SOUTH("south", 0, 1),
    SOUTH_WEST("south_west", -1, 1),
    WEST("west", -1, 0),
    NORTH_WEST("north_west", -1, -1);

    private static final RiverFlow[] COMPASS = {EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST, NORTH, NORTH_EAST};

    private final String name;
    private final Vec3 vector;

    RiverFlow(String name, int dx, int dz) {
        this.name = name;
        this.vector = new Vec3(dx, 0, dz).normalize();
    }

    /** The nearest of the eight directions to (dx, dz), or NONE for (0, 0). */
    public static RiverFlow of(double dx, double dz) {
        if (dx * dx + dz * dz < 1e-6) return NONE;
        int k = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return COMPASS[Math.floorMod(k, 8)];
    }

    /** Unit vector downstream (zero when still). */
    public Vec3 vector() {
        return vector;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
