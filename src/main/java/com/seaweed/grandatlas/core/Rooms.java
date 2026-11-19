package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.List;

/**
 * The room library (cave redesign, docs/CAVE_REDESIGN.md, 2026-11-18). The user: no spheres; rooms
 * up to ~40 across and 30 tall; "a library of 100-200 predefined rooms, all randomized and smashed
 * together".
 *
 * <p>Like Deep Rock Galactic's room templates: each <b>template</b> is a fixed arrangement of
 * primitives (rounded boxes, ellipsoids, capsules, vertical cylinders), some added and some cut
 * away (rubble heaps, pillars, fins, bridges), some <b>optional</b> (switched on or off per room).
 * Templates are made once, from a fixed seed per template, by fourteen archetype builders tied to
 * real cave forms, ten templates each: the same 140 in every world (spongework dropped: the user, 2026-11-18). A <b>room</b> is a template
 * placed in the world: sized, turned, maybe mirrored, its optional parts switched, its walls
 * displaced by smooth noise, and one room in five has a second template smashed into it.
 *
 * <p>Local frame: x and z from -1 to 1 across the room, y from 0 (the floor) to 1 (the top).
 * A room's {@link Room#sdf} is a signed distance in blocks: negative inside the open space.
 */
public final class Rooms {
    private Rooms() {}

    public static final int ELLIPSOID = 0, BOX = 1, CAPSULE = 2, CYLINDER = 3;

    public static final String[] ARCHETYPES = {"breakdown hall", "dome", "canyon hall", "bedding gallery", "pillar hall",
            "ramiform chamber", "terraces", "crossroads", "aven room", "fin hall", "bridge hall", "bowl",
            "keyhole junction", "fissure maze"};
    public static final int BREAKDOWN = 0, DOME = 1, CANYON = 2, GALLERY = 3, PILLARS = 4, RAMIFORM = 5, TERRACES = 6,
            CROSSROADS = 7, AVEN = 8, FINS = 9, BRIDGE = 10, BOWL = 11, KEYHOLE = 12, FISSURES = 13;
    public static final int PER_ARCHETYPE = 10;

    /** One primitive in a template's local frame (sizes are half extents / radii in local units). */
    public static final class Prim {
        public final int type;
        public final boolean sub, optional;
        public final double x, y, z, a, b, c, round, x2, y2, z2, yaw;

        Prim(int type, boolean sub, boolean optional, double x, double y, double z, double a, double b, double c,
             double round, double x2, double y2, double z2, double yaw) {
            this.type = type;
            this.sub = sub;
            this.optional = optional;
            this.x = x;
            this.y = y;
            this.z = z;
            this.a = a;
            this.b = b;
            this.c = c;
            this.round = round;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.yaw = yaw;
        }
    }

    public static final class Template {
        public final int id, archetype;
        public final String name;
        public final List<Prim> prims = new ArrayList<>();
        /** A flat floor across the room (not bowls and terraces). */
        boolean flatFloor = true;
        /** A flat slab ceiling at this height (local y), or NaN. */
        double ceil = Double.NaN;
        /** Typical width (blocks), depth over width, height over width, and the blend (fraction of the smallest size). */
        double wMin = 12, wMax = 30, dMin = 0.7, dMax = 1.0, hMin = 0.5, hMax = 0.8, blend = 0.18;
        /** Where its focal piece goes (local), for the dressing. */
        double fx, fy, fz;

        Template(int id, int archetype, String name) {
            this.id = id;
            this.archetype = archetype;
            this.name = name;
        }
    }

    public static final Template[] LIBRARY;

    static {
        List<Template> all = new ArrayList<>();
        for (int a = 0; a < ARCHETYPES.length; a++) {
            for (int i = 0; i < PER_ARCHETYPE; i++) {
                Template t = new Template(all.size(), a, ARCHETYPES[a] + " " + (i + 1));
                build(t, new Karst.Rng(GradientNoise.mix(0x5EA0L + a * 1009L + i * 7919L)));
                all.add(t);
            }
        }
        LIBRARY = all.toArray(new Template[0]);
    }

    // ---- building templates

    private static double r(Karst.Rng g, double lo, double hi) {
        return lo + (hi - lo) * g.next();
    }

    private static void add(Template t, int type, double x, double y, double z, double a, double b, double c, double round) {
        t.prims.add(new Prim(type, false, false, x, y, z, a, b, c, round, 0, 0, 0, 0));
    }

    private static void box(Template t, boolean sub, boolean opt, double x, double y, double z, double a, double b, double c, double round, double yaw) {
        t.prims.add(new Prim(BOX, sub, opt, x, y, z, a, b, c, round, 0, 0, 0, yaw));
    }

    private static void ell(Template t, boolean sub, boolean opt, double x, double y, double z, double a, double b, double c) {
        t.prims.add(new Prim(ELLIPSOID, sub, opt, x, y, z, a, b, c, 0, 0, 0, 0, 0));
    }

    /** A capsule from (x, y, z) to (x2, y2, z2), radius a (across), b (its vertical squash). */
    private static void cap(Template t, boolean sub, boolean opt, double x, double y, double z, double x2, double y2, double z2, double a, double b) {
        t.prims.add(new Prim(CAPSULE, sub, opt, x, y, z, a, b, 0, 0, x2, y2, z2, 0));
    }

    /** A vertical cylinder at (x, z), centre height y, radius a, half height b. */
    private static void cyl(Template t, boolean sub, boolean opt, double x, double y, double z, double a, double b) {
        t.prims.add(new Prim(CYLINDER, sub, opt, x, y, z, a, b, 0, 0, 0, 0, 0, 0));
    }

    /** n ramiform lobes (ellipsoids and turned rounded boxes) scattered in a box: the irregular base most archetypes grow from. */
    private static void lobes(Template t, Karst.Rng g, int n, double sx, double sz, double y0, double y1, double s0, double s1, boolean optTail) {
        for (int k = 0; k < n; k++) {
            double x = r(g, -sx, sx), z = r(g, -sz, sz), y = r(g, y0, y1);
            boolean opt = optTail && k >= 3;
            if (g.next() < 0.5) ell(t, false, opt, x, y, z, r(g, s0, s1), r(g, s0 * 0.9, s1), r(g, s0, s1));
            else box(t, false, opt, x, y, z, r(g, s0 * 0.85, s1 * 0.9), r(g, s0 * 0.9, s1), r(g, s0 * 0.85, s1 * 0.9), 0.2, r(g, -1, 1));
        }
    }

    private static void build(Template t, Karst.Rng g) {
        switch (t.archetype) {
            case BREAKDOWN -> { // the roof fell: a wide hall under a flat slab, its floor heaped with blocks
                t.wMin = 18; t.wMax = 40; t.dMin = 0.6; t.dMax = 0.95; t.hMin = 0.35; t.hMax = 0.6;
                box(t, false, false, 0, 0.45, 0, r(g, 0.8, 0.95), 0.55, r(g, 0.7, 0.9), r(g, 0.15, 0.3), r(g, -0.2, 0.2));
                if (g.next() < 0.6) box(t, false, true, r(g, -0.6, 0.6), 0.35, r(g, 0.5, 0.8), r(g, 0.25, 0.4), 0.4, 0.3, 0.2, r(g, -0.5, 0.5));
                t.ceil = r(g, 0.75, 0.9);
                int heaps = 2 + (int) (g.next() * 4);
                for (int k = 0; k < heaps; k++) {
                    ell(t, true, k > 1, r(g, -0.7, 0.7), 0, r(g, -0.6, 0.6), r(g, 0.12, 0.3), r(g, 0.12, 0.28), r(g, 0.12, 0.3));
                }
                t.fy = 0.3;
            }
            case DOME -> { // a vaulted room you look up in, sometimes an aven out of the crown
                // (was 0.9-1.3 times as tall as wide; the user marked them "shorten it!", 2026-11-18)
                t.wMin = 12; t.wMax = 28; t.dMin = 0.75; t.dMax = 1.0; t.hMin = 0.6; t.hMax = 0.85;
                box(t, false, false, 0, 0.3, 0, r(g, 0.75, 0.9), 0.32, r(g, 0.75, 0.9), 0.25, 0);
                ell(t, false, false, r(g, -0.1, 0.1), r(g, 0.55, 0.65), r(g, -0.1, 0.1), r(g, 0.6, 0.8), r(g, 0.4, 0.5), r(g, 0.6, 0.8));
                // Now and then a narrow aven leaning up out of the crown (was a fat 5-wide chimney on most
                // domes; the user, 2026-11-18: "what's up with the large chimneys").
                if (g.next() < 0.35) {
                    double ax = r(g, -0.15, 0.15), az = r(g, -0.15, 0.15);
                    cap(t, false, true, ax, 0.9, az, ax + r(g, -0.2, 0.2), r(g, 1.2, 1.4), az + r(g, -0.2, 0.2), r(g, 0.05, 0.08), 1.0);
                }
                if (g.next() < 0.5) ell(t, false, true, r(g, -0.7, 0.7), 0.25, r(g, -0.7, 0.7), 0.3, 0.25, 0.3);
                t.fy = 0;
            }
            case CANYON -> { // a vadose canyon swelled into a hall: long, narrow, tall, ledges along the walls
                t.wMin = 22; t.wMax = 40; t.dMin = 0.3; t.dMax = 0.45; t.hMin = 0.6; t.hMax = 0.9;
                int n = 3 + (int) (g.next() * 3);
                double z = 0;
                for (int k = 0; k < n; k++) {
                    double x = -0.85 + 1.7 * (k + 0.5) / n;
                    z += r(g, -0.25, 0.25);
                    z = Math.max(-0.35, Math.min(0.35, z));
                    box(t, false, false, x, 0.5, z, 1.0 / n + 0.12, 0.5, r(g, 0.3, 0.5), 0.2, r(g, -0.35, 0.35));
                }
                int ledges = 1 + (int) (g.next() * 3);
                for (int k = 0; k < ledges; k++) {
                    double side = g.next() < 0.5 ? -1 : 1;
                    box(t, true, k > 0, r(g, -0.6, 0.6), r(g, 0.3, 0.6), side * 0.55, r(g, 0.15, 0.35), 0.03, 0.2, 0.02, 0);
                }
                t.fx = 0.7;
            }
            case GALLERY -> { // a bedding-plane gallery: very wide, low, a flat roof on scattered pillars
                t.wMin = 24; t.wMax = 40; t.dMin = 0.7; t.dMax = 1.0; t.hMin = 0.14; t.hMax = 0.22; t.blend = 0.35;
                box(t, false, false, 0, 0.5, 0, r(g, 0.85, 0.95), 0.5, r(g, 0.8, 0.95), 0.3, r(g, -0.3, 0.3));
                if (g.next() < 0.6) box(t, false, true, r(g, -0.5, 0.5), 0.45, r(g, -0.9, 0.9), r(g, 0.3, 0.5), 0.45, 0.25, 0.25, r(g, -1, 1));
                t.ceil = 0.95;
                int n = 3 + (int) (g.next() * 5);
                for (int k = 0; k < n; k++) cyl(t, true, k > 2, r(g, -0.65, 0.65), 0.5, r(g, -0.65, 0.65), r(g, 0.04, 0.09), 0.7);
                t.fx = 0.5;
            }
            case PILLARS -> { // a hall of columns where stalactite met stalagmite
                t.wMin = 16; t.wMax = 36; t.dMin = 0.7; t.dMax = 1.0; t.hMin = 0.45; t.hMax = 0.75;
                box(t, false, false, 0, 0.5, 0, 0.9, 0.5, r(g, 0.75, 0.9), r(g, 0.2, 0.35), 0);
                int nx = 2 + (int) (g.next() * 2), nz = 2 + (int) (g.next() * 2);
                for (int i = 0; i < nx; i++) {
                    for (int k = 0; k < nz; k++) {
                        double x = -0.55 + 1.1 * i / (nx - 1) + r(g, -0.12, 0.12), z = -0.5 + 1.0 * k / (nz - 1) + r(g, -0.12, 0.12);
                        double rr = r(g, 0.05, 0.1);
                        cyl(t, true, (i + k) % 2 == 1, x, 0.5, z, rr, 0.7);
                        ell(t, true, true, x, 0, z, rr * 2.2, 0.12, rr * 2.2); // flared foot
                        ell(t, true, true, x, 1, z, rr * 2.2, 0.15, rr * 2.2); // and head
                    }
                }
            }
            case RAMIFORM -> { // a big irregular room grown from a few lobes, short branches off it
                t.wMin = 18; t.wMax = 40; t.dMin = 0.7; t.dMax = 1.0; t.hMin = 0.45; t.hMax = 0.8; t.blend = 0.25;
                int n = 3 + (int) (g.next() * 3);
                for (int k = 0; k < n; k++) {
                    double x = r(g, -0.5, 0.5), z = r(g, -0.5, 0.5);
                    if (g.next() < 0.5) ell(t, false, k > 2, x, r(g, 0.35, 0.55), z, r(g, 0.3, 0.5), r(g, 0.3, 0.45), r(g, 0.3, 0.5));
                    else box(t, false, k > 2, x, r(g, 0.35, 0.5), z, r(g, 0.25, 0.45), r(g, 0.3, 0.45), r(g, 0.25, 0.45), 0.2, r(g, -1, 1));
                }
                for (int k = 0; k < 2; k++) {
                    double a = g.next() * 2 * Math.PI;
                    cap(t, false, true, 0, 0.25, 0, Math.cos(a) * 0.95, r(g, 0.15, 0.35), Math.sin(a) * 0.95, r(g, 0.1, 0.16), 0.9);
                }
            }
            case TERRACES -> { // stepped floors, each step its own lobe: flat underfoot, domed and lumpy overhead
                t.flatFloor = false;
                t.wMin = 18; t.wMax = 38; t.dMin = 0.65; t.dMax = 1.0; t.hMin = 0.6; t.hMax = 0.9; t.blend = 0.25;
                int steps = 3 + (int) (g.next() * 2);
                double x = -0.55, z = r(g, -0.2, 0.2);
                for (int k = 0; k < steps; k++) {
                    double fy = 0.18 * k, sx = r(g, 0.28, 0.45), sz = r(g, 0.3, 0.55);
                    double hh = r(g, 0.12, 0.2);
                    box(t, false, false, x, fy + hh, z, sx, hh, sz, 0.08, r(g, -0.8, 0.8));          // the step: flat floor
                    ell(t, false, false, x + r(g, -0.1, 0.1), fy + 2 * hh + r(g, 0.1, 0.2), z + r(g, -0.1, 0.1),
                            sx * r(g, 0.8, 1.1), r(g, 0.2, 0.35), sz * r(g, 0.8, 1.1));                       // and its vault
                    x += r(g, 0.3, 0.45);
                    z += r(g, -0.3, 0.3);
                    z = Math.max(-0.5, Math.min(0.5, z));
                }
                if (g.next() < 0.6) lobes(t, g, 2, 0.7, 0.6, 0.05, 0.3, 0.2, 0.32, true); // side pockets off the low steps
                t.fx = 0.7;
                t.fy = 0.55;
            }
            case CROSSROADS -> { // where passages meet: a lumpy hub, arms at uneven angles, lengths and heights, bent
                // (the even star was "too organized": the user, 2026-11-18)
                t.wMin = 16; t.wMax = 32; t.dMin = 0.8; t.dMax = 1.0; t.hMin = 0.4; t.hMax = 0.65; t.blend = 0.22;
                lobes(t, g, 2 + (int) (g.next() * 2), 0.25, 0.25, 0.3, 0.45, 0.25, 0.4, false);
                int arms = 3 + (int) (g.next() * 2.5);
                double a = g.next() * 2 * Math.PI;
                for (int k = 0; k < arms; k++) {
                    a += r(g, 0.9, 2.4); // uneven gaps, never a regular star
                    double len = r(g, 0.6, 1.05), rr = r(g, 0.09, 0.2), sv = r(g, 0.7, 1.4);
                    double bend = r(g, -0.6, 0.6), y1 = r(g, 0.15, 0.5), y2 = Math.max(0.1, y1 + r(g, -0.25, 0.3));
                    double mx = Math.cos(a) * len * 0.5, mz = Math.sin(a) * len * 0.5;
                    double ex = Math.cos(a + bend) * len, ez = Math.sin(a + bend) * len;
                    cap(t, false, k >= 3, 0, 0.3, 0, mx, y1, mz, rr * 1.2, sv);
                    cap(t, false, k >= 3, mx, y1, mz, ex, y2, ez, rr, sv);
                }
            }
            case AVEN -> { // an irregular room drained from above: big leaning shafts, sometimes kinked, up out of its roof
                t.wMin = 14; t.wMax = 30; t.dMin = 0.7; t.dMax = 1.0; t.hMin = 0.5; t.hMax = 0.75; t.blend = 0.22;
                lobes(t, g, 3 + (int) (g.next() * 2), 0.45, 0.45, 0.3, 0.5, 0.28, 0.45, true);
                int n = 1 + (int) (g.next() * 2.5);
                for (int k = 0; k < n; k++) {
                    double x = r(g, -0.4, 0.4), z = r(g, -0.4, 0.4), rr = k == 0 ? r(g, 0.14, 0.22) : r(g, 0.07, 0.12);
                    double mx = x + r(g, -0.3, 0.3), my = r(g, 1.1, 1.4), mz = z + r(g, -0.3, 0.3);
                    cap(t, false, k > 0, x, 0.6, z, mx, my, mz, rr, 1.0);
                    if (g.next() < 0.6) cap(t, false, true, mx, my, mz, mx + r(g, -0.35, 0.35), my + r(g, 0.3, 0.6), mz + r(g, -0.35, 0.35), rr * 0.75, 1.0); // a kink higher up
                }
                t.fy = 0;
            }
            case FINS -> { // an irregular hall cut by tall blades of rock along the joints, some hanging from the roof
                t.wMin = 18; t.wMax = 36; t.dMin = 0.7; t.dMax = 1.0; t.hMin = 0.6; t.hMax = 0.9; t.blend = 0.22;
                lobes(t, g, 4 + (int) (g.next() * 2), 0.5, 0.45, 0.4, 0.55, 0.3, 0.48, true);
                double yaw = r(g, -0.6, 0.6);
                int n = 4 + (int) (g.next() * 5);
                for (int k = 0; k < n; k++) {
                    double x = -0.7 + 1.4 * (k + 0.5) / n + r(g, -0.06, 0.06);
                    boolean hang = g.next() < 0.3;
                    double hh = r(g, 0.3, 0.55);
                    box(t, true, k % 3 == 2, x, hang ? 1.1 - hh * 0.6 : hh * 0.6, r(g, -0.35, 0.35), r(g, 0.045, 0.075), hh, r(g, 0.25, 0.6), 0.04,
                            yaw + r(g, -0.2, 0.2));
                }
                t.fx = -0.8;
            }
            case BRIDGE -> { // a tall irregular rift-hall spanned by thick natural bridges of rock
                t.wMin = 20; t.wMax = 38; t.dMin = 0.45; t.dMax = 0.7; t.hMin = 0.8; t.hMax = 1.0; t.blend = 0.22;
                int n = 3 + (int) (g.next() * 2);
                for (int k = 0; k < n; k++) {
                    double x = -0.7 + 1.4 * (k + 0.5) / n + r(g, -0.1, 0.1);
                    if (g.next() < 0.5) ell(t, false, false, x, r(g, 0.45, 0.6), r(g, -0.2, 0.2), r(g, 0.3, 0.45), r(g, 0.45, 0.55), r(g, 0.45, 0.7));
                    else box(t, false, false, x, 0.5, r(g, -0.2, 0.2), r(g, 0.25, 0.4), 0.5, r(g, 0.4, 0.65), 0.25, r(g, -0.5, 0.5));
                }
                int bridges = 1 + (int) (g.next() * 2.2);
                for (int k = 0; k < bridges; k++) {
                    double bx = r(g, -0.5, 0.5), by = k == 0 ? r(g, 0.5, 0.7) : r(g, 0.25, 0.85), sk = r(g, -0.35, 0.35);
                    // thick across its span, sagging a little to the middle: two capsules
                    double th = r(g, 0.09, 0.14);
                    cap(t, true, k > 0, bx - sk, by + 0.03, -1.2, bx, by - 0.04, 0, th, 1.3);
                    cap(t, true, k > 0, bx, by - 0.04, 0, bx + sk, by + 0.03, 1.2, th, 1.3);
                }
            }
            case BOWL -> { // a room whose floor falls away in the middle to a pit or basin
                t.flatFloor = false;
                // (was too tall, the user, 2026-11-18: lower room, shallower pit)
                t.wMin = 16; t.wMax = 34; t.dMin = 0.75; t.dMax = 1.0; t.hMin = 0.32; t.hMax = 0.48; t.blend = 0.22;
                lobes(t, g, 3, 0.3, 0.3, 0.45, 0.6, 0.45, 0.6, false);
                ell(t, false, false, r(g, -0.2, 0.2), r(g, 0.1, 0.2), r(g, -0.2, 0.2), r(g, 0.4, 0.55), r(g, 0.3, 0.4), r(g, 0.4, 0.55));
                // a ledge round the rim to walk on
                box(t, false, false, 0, 0.12, 0, 0.88, 0.04, 0.85, 0.05, 0);
            }
            case KEYHOLE -> { // a phreatic tube with a vadose slot cut below, widening at the junction
                t.wMin = 16; t.wMax = 32; t.dMin = 0.5; t.dMax = 0.8; t.hMin = 0.7; t.hMax = 1.0;
                double zz = r(g, -0.1, 0.1);
                cap(t, false, false, -1, 0.75, zz, 1, 0.75, -zz, r(g, 0.22, 0.3), 0.8);
                box(t, false, false, 0, 0.35, 0, 0.95, 0.35, r(g, 0.1, 0.16), 0.08, r(g, -0.1, 0.1));
                ell(t, false, true, r(g, -0.3, 0.3), 0.5, 0, 0.35, 0.45, 0.45);
            }
            case FISSURES -> { // joints opened into a crossing of tall narrow fissures
                t.wMin = 16; t.wMax = 34; t.dMin = 0.8; t.dMax = 1.0; t.hMin = 0.6; t.hMax = 0.9; t.blend = 0.1;
                int n = 2 + (int) (g.next() * 3);
                double a0 = g.next() * Math.PI;
                for (int k = 0; k < n; k++) {
                    double a = a0 + (k % 2) * Math.PI / 2 + r(g, -0.25, 0.25);
                    box(t, false, k > 1, r(g, -0.3, 0.3), 0.5, r(g, -0.3, 0.3), 0.95, 0.5, r(g, 0.07, 0.12), 0.04, a);
                }
                ell(t, false, true, 0, 0.3, 0, 0.3, 0.3, 0.3);
            }
            default -> throw new IllegalStateException();
        }
    }

    // ---- rooms in the world

    /** A template placed in the world, its primitives scaled to blocks in the room's own frame. */
    public static final class Room {
        public final Template template;
        public final double cx, floorY, cz, w, d, h;
        final double cos, sin;
        final boolean mirror;
        final double[][] adds, subs; // per primitive: type, x, y, z, a, b, c, round, x2, y2, z2, cosYaw, sinYaw
        final double k, ceilY, amp;
        final long noise;
        /** A second template smashed into this one, or null. */
        public final Room extra;
        public double minX, maxX, minY, maxY, minZ, maxZ;

        Room(Template t, double cx, double floorY, double cz, double w, double d, double h, double yaw, boolean mirror, long on, long noise, Room extra) {
            this.template = t;
            this.cx = cx;
            this.floorY = floorY;
            this.cz = cz;
            this.w = w;
            this.d = d;
            this.h = h;
            this.cos = Math.cos(yaw);
            this.sin = Math.sin(yaw);
            this.mirror = mirror;
            this.noise = noise;
            this.extra = extra;
            double sx = w / 2, sz = d / 2, sy = h, sm = Math.min(Math.min(sx, sz), sy);
            List<double[]> a = new ArrayList<>(), s = new ArrayList<>();
            int i = 0;
            for (Prim p : t.prims) {
                boolean use = !p.optional || (on >>> (i++ & 63) & 1) == 1;
                if (!use) continue;
                double ra = switch (p.type) {
                    case CAPSULE, CYLINDER -> p.a * Math.min(sx, sz);
                    default -> p.a * sx;
                };
                double rb = switch (p.type) {
                    case CAPSULE -> p.b; // vertical squash, unitless
                    default -> p.b * sy;
                };
                double[] q = {p.type, p.x * sx, p.y * sy, p.z * sz, ra, rb, p.c * sz, p.round * sm, p.x2 * sx, p.y2 * sy, p.z2 * sz, Math.cos(p.yaw), Math.sin(p.yaw)};
                (p.sub ? s : a).add(q);
            }
            adds = a.toArray(new double[0][]);
            subs = s.toArray(new double[0][]);
            k = Math.max(1.5, t.blend * Math.min(Math.min(w, d), h));
            ceilY = Double.isNaN(t.ceil) ? Double.NaN : t.ceil * h;
            amp = Math.max(0.5, Math.min(2.2, 0.06 * Math.min(w, h * 1.5)));
            // bounds, from the added primitives' boxes turned into the world
            double lx0 = 1e9, lx1 = -1e9, ly0 = 1e9, ly1 = -1e9, lz0 = 1e9, lz1 = -1e9;
            for (double[] q : adds) {
                double ex, ey, ez;
                switch ((int) q[0]) {
                    case CAPSULE -> {
                        double rr = q[4];
                        lx0 = Math.min(lx0, Math.min(q[1], q[8]) - rr);
                        lx1 = Math.max(lx1, Math.max(q[1], q[8]) + rr);
                        lz0 = Math.min(lz0, Math.min(q[3], q[10]) - rr);
                        lz1 = Math.max(lz1, Math.max(q[3], q[10]) + rr);
                        ly0 = Math.min(ly0, Math.min(q[2], q[9]) - rr * q[5]);
                        ly1 = Math.max(ly1, Math.max(q[2], q[9]) + rr * q[5]);
                        continue;
                    }
                    case CYLINDER -> {
                        ex = q[4];
                        ey = q[5];
                        ez = q[4];
                    }
                    default -> {
                        double r = Math.max(q[4], q[6]);
                        ex = q[0] == BOX ? r * 1.42 : q[4];
                        ez = q[0] == BOX ? r * 1.42 : q[6];
                        ey = q[5];
                    }
                }
                lx0 = Math.min(lx0, q[1] - ex);
                lx1 = Math.max(lx1, q[1] + ex);
                ly0 = Math.min(ly0, q[2] - ey);
                ly1 = Math.max(ly1, q[2] + ey);
                lz0 = Math.min(lz0, q[3] - ez);
                lz1 = Math.max(lz1, q[3] + ez);
            }
            double pad = amp + 1.5;
            double rx = Math.max(Math.abs(lx0), Math.abs(lx1)), rz = Math.max(Math.abs(lz0), Math.abs(lz1)), rh = Math.hypot(rx, rz) + pad;
            minX = cx - rh;
            maxX = cx + rh;
            minZ = cz - rh;
            maxZ = cz + rh;
            minY = floorY + (t.flatFloor ? -1 : ly0) - pad;
            maxY = floorY + (Double.isNaN(ceilY) ? ly1 : Math.min(ly1, ceilY + 1)) + pad;
            if (extra != null) {
                minX = Math.min(minX, extra.minX);
                maxX = Math.max(maxX, extra.maxX);
                minY = Math.min(minY, extra.minY);
                maxY = Math.max(maxY, extra.maxY);
                minZ = Math.min(minZ, extra.minZ);
                maxZ = Math.max(maxZ, extra.maxZ);
            }
        }

        /** The room's own seed (its dressing plan is drawn from it: world/DeepDetail). */
        public long seed() {
            return noise;
        }

        /** True for a great hall (wider than the normal 40-block cap). */
        public boolean great() {
            return w > 40.5;
        }

        /** The horizontal radius that holds the whole room, from its centre. */
        public double reach() {
            return Math.max(Math.max(cx - minX, maxX - cx), Math.max(cz - minZ, maxZ - cz));
        }

        /** Signed distance in blocks from the open space's surface (negative inside). */
        public double sdf(double wx, double wy, double wz) {
            double v = own(wx, wy, wz);
            if (extra != null) v = smin(v, extra.sdf(wx, wy, wz), 3);
            return v;
        }

        private double own(double wx, double wy, double wz) {
            if (wy < minY || wy > maxY) return 8;
            double dx = wx - cx, dz = wz - cz;
            double lx = dx * cos + dz * sin, lz = -dx * sin + dz * cos;
            if (mirror) lx = -lx;
            double ly = wy - floorY;
            double v = 1e9;
            for (double[] q : adds) v = v > 1e8 ? prim(q, lx, ly, lz) : smin(v, prim(q, lx, ly, lz), k);
            for (double[] q : subs) v = smax(v, -prim(q, lx, ly, lz), Math.min(2, k * 0.5));
            if (template.flatFloor) v = smax(v, 0.6 * GradientNoise.noise2(noise, wx / 18.0, wz / 18.0) - ly, 1.2);
            if (!Double.isNaN(ceilY)) v = smax(v, ly - ceilY - 0.8 * GradientNoise.noise2(noise ^ 0xCE11L, wx / 22.0, wz / 22.0), 1.5);
            if (v < 6) {
                v += amp * GradientNoise.noise3(noise ^ 0x7A1L, wx / 16.0, wy / 11.0, wz / 16.0)
                        + 0.3 * GradientNoise.noise3(noise ^ 0x7A2L, wx / 6.0, wy / 5.0, wz / 6.0);
            }
            return v;
        }

        /** Where the room's focal piece goes, in the world: {x, y (floor), z}. */
        public double[] focal() {
            double lx = template.fx * w / 2, lz = template.fz * d / 2;
            if (mirror) lx = -lx;
            return new double[] {cx + lx * cos - lz * sin, floorY + template.fy * h, cz + lx * sin + lz * cos};
        }
    }

    static double prim(double[] q, double x, double y, double z) {
        double px = x - q[1], py = y - q[2], pz = z - q[3];
        switch ((int) q[0]) {
            case ELLIPSOID -> {
                double ax = px / q[4], ay = py / q[5], az = pz / q[6];
                double k0 = Math.sqrt(ax * ax + ay * ay + az * az);
                double bx = px / (q[4] * q[4]), by = py / (q[5] * q[5]), bz = pz / (q[6] * q[6]);
                double k1 = Math.sqrt(bx * bx + by * by + bz * bz);
                return k1 < 1e-9 ? -Math.min(q[4], Math.min(q[5], q[6])) : k0 * (k0 - 1) / k1;
            }
            case BOX -> {
                double ux = px * q[11] + pz * q[12], uz = -px * q[12] + pz * q[11];
                double rr = q[7];
                double ex = Math.abs(ux) - (q[4] - rr), ey = Math.abs(py) - (q[5] - rr), ez = Math.abs(uz) - (q[6] - rr);
                double ox = Math.max(ex, 0), oy = Math.max(ey, 0), oz = Math.max(ez, 0);
                return Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(ex, Math.max(ey, ez)), 0) - rr;
            }
            case CAPSULE -> {
                double sv = q[5];
                double ax = q[1], ay = q[2] / sv, az = q[3], bx = q[8], by = q[9] / sv, bz = q[10];
                double yy = y / sv;
                double ex = bx - ax, ey = by - ay, ez = bz - az, l2 = ex * ex + ey * ey + ez * ez;
                double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * ex + (yy - ay) * ey + (z - az) * ez) / l2));
                double ox = x - (ax + ex * t), oy = yy - (ay + ey * t), oz = z - (az + ez * t);
                return (Math.sqrt(ox * ox + oy * oy + oz * oz) - q[4]) * Math.min(1, sv);
            }
            default -> { // vertical cylinder
                double dxz = Math.hypot(px, pz) - q[4], dy = Math.abs(py) - q[5];
                return Math.min(Math.max(dxz, dy), 0) + Math.hypot(Math.max(dxz, 0), Math.max(dy, 0));
            }
        }
    }

    static double smin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    static double smax(double a, double b, double k) {
        return -smin(-a, -b, k);
    }

    // ---- choosing rooms

    /**
     * A room at (x, floorY, z). {@code size} is the wanted width in blocks (the caller draws it from
     * a heavy-tailed distribution); {@code maxH} caps the height (rock cover); {@code archetype} -1
     * for any. Widths stop at 40 and heights at 30 (the user, 2026-11-18).
     */
    public static Room make(long h, double x, double floorY, double z, double size, double maxH, int archetype) {
        return make(h, x, floorY, z, size, maxH, archetype, 2);
    }

    /** {@code smash}: how many more rooms may be smashed into this one (a cluster: big rooms merge into great halls). */
    /** One room in ten is a great hall (the user, 2026-11-18): above the 40 x 30 cap, 40-60 across and up to 45 tall. */
    static final double GREAT_CHANCE = 0.1;
    static final int[] GREAT = {BREAKDOWN, DOME, PILLARS, RAMIFORM, BRIDGE, CANYON, FINS, BOWL, TERRACES};

    static Room make(long h, double x, double floorY, double z, double size, double maxH, int archetype, int smash) {
        // A great hall: the big end of the spectrum, where vanilla has its 30-60 tall cheese caves.
        // Only where the rock gives it room to stand tall, and only of the archetypes that read at that scale.
        boolean great = smash == 2 && maxH >= 28 && u(h, 15) < GREAT_CHANCE;
        if (great) {
            boolean fits = false;
            for (int g : GREAT) fits |= g == archetype;
            if (!fits) archetype = GREAT[(int) (u(h, 16) * GREAT.length)];
        }
        Template t = pick(h, archetype);
        double w = great ? lognormal(h, 17, 48, 0.15, 40, 60) : Math.max(t.wMin * 0.6, Math.min(Math.min(40, t.wMax * 1.15), size));
        double d = w * (t.dMin + (t.dMax - t.dMin) * u(h, 2));
        // Big rooms stand taller for their width (a 40-wide hall at 0.35 would be only 14 high): the
        // big end of the spectrum needs height as well as width (docs/CAVE_REDESIGN.md section 5).
        double tall = 1 + 0.9 * Math.max(0, Math.min(1, (w - 16) / 20));
        double ht = Math.min(Math.min(great ? 45 : 30, maxH), w * (t.hMin + (t.hMax - t.hMin) * u(h, 3)) * tall);
        if (great) ht = Math.max(ht, Math.min(maxH, 24 + 10 * u(h, 18))); // even a low archetype stands tall as a great hall
        if (ht < 3.5) ht = Math.min(3.5, maxH);
        Room extra = null;
        // Smashed together (the user's words): one room in four takes a smaller second room; big rooms
        // often take a second big one, sometimes a third, merging into a hall wider than any one template.
        // A great hall takes at most one, a smaller one, so it stays one space.
        boolean big = w > 26;
        if (smash > 0 && w > 14 && u(h, 4) < (great ? 0.4 : big ? 0.55 : 0.25)) {
            double a = u(h, 5) * 2 * Math.PI, off = w * (0.35 + 0.25 * u(h, 6));
            double w2 = great ? 18 + 12 * u(h, 8) : w * (big ? 0.7 + 0.3 * u(h, 8) : 0.45 + 0.3 * u(h, 8));
            extra = make(GradientNoise.mix(h ^ 0x5A5AL), x + Math.cos(a) * off, floorY + (u(h, 7) - 0.3) * 4, z + Math.sin(a) * off,
                    w2, maxH, big && !great ? archetype : -1, great ? 0 : big ? smash - 1 : 0);
        }
        return new Room(t, x, floorY, z, w, d, ht, u(h, 9) * 2 * Math.PI, u(h, 10) < 0.5, GradientNoise.mix(h ^ 0x0FFL), GradientNoise.mix(h ^ 0x1234L), extra);
    }

    /** The template a hash picks, of an archetype or of any. */
    public static Template pick(long h, int archetype) {
        if (archetype >= 0) return LIBRARY[archetype * PER_ARCHETYPE + (int) (u(h, 1) * PER_ARCHETYPE)];
        return LIBRARY[(int) (u(h, 1) * LIBRARY.length)];
    }

    /** A room of one template at its typical size, for the review artifact (tools/RoomExport). */
    public static Room sample(int id, long variant) {
        Template t = LIBRARY[id];
        long h = GradientNoise.mix(variant * 31 + id);
        double w = variant == 0 ? (t.wMin + t.wMax) / 2 : t.wMin + (t.wMax - t.wMin) * u(h, 11);
        double d = w * (variant == 0 ? (t.dMin + t.dMax) / 2 : t.dMin + (t.dMax - t.dMin) * u(h, 2));
        double ht = Math.min(30, w * (variant == 0 ? (t.hMin + t.hMax) / 2 : t.hMin + (t.hMax - t.hMin) * u(h, 3)));
        // Variant 0 is a typical room (its optional parts switched as a placed room's would be), not all of them on.
        long on = GradientNoise.mix(variant == 0 ? id * 7919L + 3 : h ^ 0x0FFL);
        return new Room(t, 0, 0, 0, w, d, Math.max(3.5, ht), 0, false, on, GradientNoise.mix(h ^ 0x1234L), null);
    }

    /** A unit-interval draw from a lognormal width: median m, spread s (log units), clamped. */
    public static double lognormal(long h, int k, double m, double s, double lo, double hi) {
        double u1 = Math.max(1e-9, u(h, k)), u2 = u(h, k + 1);
        double n = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
        return Math.max(lo, Math.min(hi, m * Math.exp(s * n)));
    }

    static double u(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
