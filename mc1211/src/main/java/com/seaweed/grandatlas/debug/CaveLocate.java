package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.Passages;
import com.seaweed.grandatlas.core.Rooms;
import com.seaweed.grandatlas.core.micro.Features;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import com.seaweed.grandatlas.core.micro.MicroKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * {@code /grandatlas locate} for the cave redesign's things (the user couldn't find them to test,
 * 2026-11-19): spiral galleries, the upper caves' rock (chalk, clay, soil), cenotes with their side
 * passage, cave mouths, great halls, and the found places (colossus, expedition, fungal grove, root
 * cathedral). Each answer is a spot to stand, for the command's clickable /tp.
 */
public final class CaveLocate {
    private CaveLocate() {}

    public static final Set<String> IDS = Set.of("spiral_gallery", "chalk_caves", "clay_caves", "soil_caves", "cenote", "cave_mouth",
            "great_hall", "colossus", "expedition", "fungal_grove", "root_cathedral");

    /** The answer line ("Nearest ...: x y z (n blocks) ..."), or null with nothing found. */
    public static @Nullable String find(AtlasCore core, String id, int px, int pz) {
        return switch (id) {
            case "spiral_gallery" -> spiral(core, px, pz);
            case "chalk_caves" -> rock(core, px, pz, Passages.ROCK_CHALK, "chalk caves");
            case "clay_caves" -> rock(core, px, pz, Passages.ROCK_CLAY, "clay caves");
            case "soil_caves" -> rock(core, px, pz, Passages.ROCK_SOIL, "soil caves");
            case "cenote" -> cenote(core, px, pz);
            case "cave_mouth" -> mouth(core, px, pz);
            default -> room(core, px, pz, id);
        };
    }

    private static String line(String what, double x, double y, double z, int px, int pz, String extra) {
        return String.format("Nearest %s: %d %d %d (%d blocks)%s", what, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                (int) Math.hypot(x - px, z - pz), extra);
    }

    private static @Nullable String spiral(AtlasCore core, int px, int pz) {
        List<double[]> sps = new ArrayList<>();
        for (int r = 800; r <= 4000 && sps.isEmpty(); r *= 2) core.deep().spirals(px - r, pz - r, px + r, pz + r, sps); // (outward: the whole 8 km square took 15 s)
        double[] best = null;
        for (double[] f : sps) if (best == null || Math.hypot(f[0] - px, f[2] - pz) < Math.hypot(best[0] - px, best[2] - pz)) best = f;
        if (best == null) return null;
        // on the ledge where the top turn starts (the entrance side)
        return line("spiral gallery", best[0] + best[4], best[1] + 1, best[2], px, pz,
                String.format(", the void %.0f deep; the ledge spirals down from here", best[1] - best[3]));
    }

    /** A roomy passage under the ground in a region of that rock, searched outward in rings. */
    private static @Nullable String rock(AtlasCore core, int px, int pz, int prov, String what) {
        for (int ring = 0; ring < 60; ring++) {
            int n = Math.max(1, ring * 8);
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n, rx = px + Math.cos(a) * ring * 64, rz = pz + Math.sin(a) * ring * 64;
                if (core.passages().rockProvince((int) rx, (int) rz) != prov) continue;
                List<double[]> pieces = new ArrayList<>();
                core.passages().piecesNear(rx, rz, 40, pieces);
                for (double[] g : pieces) {
                    double mx = (g[0] + g[3]) / 2, my = (g[1] + g[4]) / 2, mz = (g[2] + g[5]) / 2;
                    double ground = core.column((int) mx, (int) mz).height();
                    double maxDepth = prov == Passages.ROCK_SOIL ? 30 : 400;
                    if (g[6] > 1 || g[7] < 2.5 || my < -60 || my > ground - 12 || ground - my > maxDepth) continue;
                    if (core.passages().rockProvince((int) mx, (int) mz) != prov) continue;
                    return line(what, mx, my, mz, px, pz, String.format(", %.0f under the ground", ground - my));
                }
            }
        }
        return null;
    }

    private static @Nullable String cenote(AtlasCore core, int px, int pz) {
        MicroFeature best = null;
        int ci = Features.cell(px), cj = Features.cell(pz);
        for (int r = 0; r <= 14 && best == null; r++) {
            for (int dj = -r; dj <= r; dj++) {
                for (int di = -r; di <= r; di++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue;
                    MesoSite site = core.features().site(ci + di, cj + dj);
                    if (site == null) continue;
                    for (MicroFeature m : site.micros()) {
                        if (m.kind != MicroKind.CENOTE) continue;
                        if (best == null || Math.hypot(m.x - px, m.z - pz) < Math.hypot(best.x - px, best.z - pz)) best = m;
                    }
                }
            }
        }
        if (best == null) return null;
        double pool = best.ground - best.h + best.extra;
        return line("cenote", best.x, pool + 5, best.z, px, pz,
                String.format(", the pool at y %.0f; a side passage leaves the wall about 3 blocks over the water", pool));
    }

    /** A passage that opens to the surface (its open piece's higher end). */
    private static @Nullable String mouth(AtlasCore core, int px, int pz) {
        for (int ring = 0; ring < 40; ring++) {
            int n = Math.max(1, ring * 8);
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n, rx = px + Math.cos(a) * ring * 64, rz = pz + Math.sin(a) * ring * 64;
                List<double[]> pieces = new ArrayList<>();
                core.passages().piecesNear(rx, rz, 40, pieces);
                for (double[] g : pieces) {
                    if (g[8] != 1) continue; // open: may break the surface
                    boolean bHigher = g[4] > g[1];
                    double tx = bHigher ? g[3] : g[0], ty = bHigher ? g[4] : g[1], tz = bHigher ? g[5] : g[2];
                    double ground = core.column((int) tx, (int) tz).height();
                    if (ground - ty > 6) continue; // its top must reach the ground
                    return line("cave mouth", tx, ground + 2, tz, px, pz, "");
                }
            }
        }
        return null;
    }

    private static @Nullable String room(AtlasCore core, int px, int pz, String id) {
        String kind = switch (id) {
            case "colossus" -> "colossus";
            case "expedition" -> "expedition";
            case "fungal_grove" -> "fungal grove";
            case "root_cathedral" -> "root cathedral";
            default -> null; // great_hall
        };
        for (int r = 600; r <= 2400; r *= 2) {
            List<Rooms.Room> rooms = new ArrayList<>();
            core.passages().rooms(px - r, pz - r, px + r, pz + r, true, rooms);
            Rooms.Room best = null;
            for (Rooms.Room rm : rooms) {
                boolean ok = kind == null ? rm.great() : kind.equals(com.seaweed.grandatlas.world.DeepDetail.storyOf(core, rm));
                if (ok && (best == null || Math.hypot(rm.cx - px, rm.cz - pz) < Math.hypot(best.cx - px, best.cz - pz))) best = rm;
            }
            if (best != null) {
                double[] f = best.focal();
                return line(kind == null ? "great hall" : kind, f[0], f[1] + 2, f[2], px, pz,
                        String.format(", a %s %.0f across", best.template.name, best.w));
            }
        }
        return null;
    }
}
