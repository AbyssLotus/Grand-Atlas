package com.seaweed.grandatlas.core.landform;

/** Mutable output of {@link Landform#shape}, reused per thread to avoid allocation in the hot path. */
public final class Shape {
    /** Height above the frame's datum (may be negative). */
    public double relief;
    public Zone zone;
    /** 0..1: how strongly the 3D stage roughens this surface (ledges, overhangs). */
    public double roughness;
    /** 0..1: how much of the core's small-scale ground texture applies (0 for a salt pan). */
    public double micro;
    /**
     * Wind-carved rock standing on the ground here (part of {@code relief}): its height over the
     * sand under it, how many blocks inside its outline this column is, and the rock's radius.
     * All 0 where there's no rock. The 3D carving works from these (see {@link Rocks}).
     */
    public double rock, rockInside, rockSize;

    public Shape set(double relief, Zone zone, double roughness) {
        this.relief = relief;
        this.zone = zone;
        this.roughness = roughness;
        this.micro = 1;
        this.rock = 0;
        this.rockInside = 0;
        this.rockSize = 0;
        return this;
    }
}
