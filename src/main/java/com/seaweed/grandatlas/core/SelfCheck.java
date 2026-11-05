package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.hydro.Drainage;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.micro.Features;
import com.seaweed.grandatlas.core.micro.MesoKind;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import com.seaweed.grandatlas.core.micro.MicroKind;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.province.ProvinceMosaic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Framework-free checks on the core (the offline Gradle cache had no JUnit). Run with
 * {@code gradlew checkCore}; it is also wired into {@code gradlew check}. Exits non-zero if
 * anything fails.
 *
 * <p>Checks that roam the whole world (bounds, land fraction, macro edges, cross-seed
 * determinism) use the terrain before rivers: with rivers, every far-flung point builds the
 * drainage of its whole river basin. Rivers get their own checks on a 24 km square, and the
 * seam and cache checks run with them.
 */
public final class SelfCheck {
    private SelfCheck() {}

    private static int failures = 0;

    public static void main(String[] args) {
        determinism();
        cacheMatchesDirect();
        bounds();
        noSeams();
        landFraction();
        neighbours();
        coverage();
        macroPlacement();
        macroEdges();
        rivers();
        riverContainment();
        mesoSites();
        passages();
        megaCaverns();
        if (failures > 0) {
            System.err.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("All core checks passed");
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok    " : "  FAIL  ") + what);
        if (!ok) failures++;
    }

    private static boolean same(ColumnSample a, ColumnSample b) {
        return a.height() == b.height() && a.biome() == b.biome() && a.zone() == b.zone()
                && a.landform().id().equals(b.landform().id()) && a.transition() == b.transition();
    }

    /** Two cores from the same seed agree exactly; a different seed differs. */
    private static void determinism() {
        AtlasCore a = new AtlasCore(42), b = new AtlasCore(42), c = new AtlasCore(43);
        boolean identical = true, differs = false;
        for (int i = 0; i < 2000; i++) {
            int x = i * 7919 - 5_000_000, z = i * 104_729 % 9_000_000 - 4_500_000;
            identical &= same(a.sampleBase(x, z), b.sampleBase(x, z));
            differs |= a.sampleBase(x, z).height() != c.sampleBase(x, z).height();
        }
        for (int i = 0; i < 2000; i++) { // rivers too, near the origin
            int x = (i % 50) * 97 - 2400, z = (i / 50) * 113 - 2200;
            identical &= same(a.sample(x, z), b.sample(x, z)) && Double.compare(a.sample(x, z).water(), b.sample(x, z).water()) == 0;
        }
        check(identical, "same seed gives identical columns");
        check(differs, "different seed gives different terrain");
    }

    /** The per-thread cache must return what a direct computation returns. */
    private static void cacheMatchesDirect() {
        AtlasCore core = new AtlasCore(7);
        boolean ok = true;
        for (int i = 0; i < 20000; i++) {
            int x = (i % 300) * 4 - 600, z = (i / 300) * 4 - 100;
            ok &= same(core.column(x, z), core.sample(x, z));
        }
        check(ok, "column cache matches direct sampling");
    }

    private static void bounds() {
        AtlasCore core = new AtlasCore(99);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int z = -60_000; z <= 60_000; z += 197) {
            for (int x = -60_000; x <= 60_000; x += 997) {
                double h = core.sampleBase(x, z).height();
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        check(lo >= AtlasCore.TERRAIN_FLOOR + 16 && hi <= AtlasCore.PEAK_CAP,
                String.format("heights within [%d, %d] (saw %.0f .. %.0f)", AtlasCore.TERRAIN_FLOOR + 16, AtlasCore.PEAK_CAP, lo, hi));
    }

    /**
     * No seams. Escarpments are meant to be steep, so a big step between neighbouring blocks
     * isn't a failure by itself. A continuous surface spreads its rise when you look closer,
     * while a seam keeps its whole jump however finely you sample. So every step over 12 blocks
     * is bisected down to 1/256 of a block, and fails if more than 1.5 blocks of rise remain.
     */
    private static void noSeams() {
        AtlasCore core = new AtlasCore(5);
        // On the terrain before rivers: river banks are meant to be sharp, and riverContainment()
        // checks what matters about them.
        double worstStep = 0, worstResidual = 0;
        int steep = 0;
        String at = "";
        for (int line = 0; line < 24; line++) {
            int z = line * 1501 - 18_000;
            double prev = core.sampleBase(-12_000, z).height();
            for (int x = -11_999; x <= 12_000; x++) {
                double h = core.sampleBase(x, z).height();
                double step = Math.abs(h - prev);
                worstStep = Math.max(worstStep, step);
                if (step > 12) {
                    steep++;
                    double residual = residualJump(core, x - 1, x, z);
                    if (residual > worstResidual) {
                        worstResidual = residual;
                        at = x + ", " + z;
                    }
                }
                prev = h;
            }
        }
        check(worstResidual < 1.5, String.format(
                "no seams: %d steep steps (max %.1f blocks/block), worst jump left after bisection %.2f%s",
                steep, worstStep, worstResidual, at.isEmpty() ? "" : " at " + at));
    }

    private static double residualJump(AtlasCore core, double x0, double x1, double z) {
        double h0 = core.sampleBase(x0, z).height(), h1 = core.sampleBase(x1, z).height();
        for (int i = 0; i < 8; i++) {
            double mid = (x0 + x1) / 2, hm = core.sampleBase(mid, z).height();
            if (Math.abs(hm - h0) > Math.abs(h1 - hm)) {
                x1 = mid;
                h1 = hm;
            } else {
                x0 = mid;
                h0 = hm;
            }
        }
        if (sharp(core, x0, z) || sharp(core, x1, z)) return 0; // a plateau's lip or spire: sheer on purpose
        return Math.abs(h1 - h0);
    }

    private static void landFraction() {
        AtlasCore core = new AtlasCore(11);
        int land = 0, n = 0;
        for (int z = -80_000; z <= 80_000; z += 400) {
            for (int x = -80_000; x <= 80_000; x += 400) {
                n++;
                if (!core.sampleBase(x, z).isOcean()) land++;
            }
        }
        double pct = 100.0 * land / n;
        check(pct > 35 && pct < 80, String.format("land fraction %.1f%% in (35, 80)", pct));
    }

    /** A wonder should almost never sit next to itself (ordinary country may merge, ranges chain). */
    private static void neighbours() {
        AtlasCore core = new AtlasCore(3);
        ProvinceMosaic mosaic = core.mosaic();
        int pairs = 0, duplicates = 0;
        for (int cz = -40; cz < 40; cz++) {
            for (int cx = -40; cx < 40; cx++) {
                Province p = mosaic.province(cx, cz);
                Landform l = p.landform();
                if (l.id().equals("ocean") || l.sameNeighbour() > 0) continue;
                for (int[] d : new int[][] {{1, 0}, {0, 1}, {1, 1}, {1, -1}}) {
                    Province q = mosaic.province(cx + d[0], cz + d[1]);
                    if (q.landform().id().equals("ocean")) continue;
                    pairs++;
                    if (q.landform() == l) duplicates++;
                }
            }
        }
        double pct = 100.0 * duplicates / Math.max(1, pairs);
        check(pct < 8, String.format("wonders next to themselves: %.1f%% of their neighbour pairs (< 8%%)", pct));
    }

    /** Every macro kind turns up across a large area, and no two macros' reaches overlap. */
    private static void macroPlacement() {
        AtlasCore core = new AtlasCore(23);
        var field = core.macros();
        Map<String, Integer> counts = new HashMap<>();
        int overlaps = 0, sites = 0;
        for (int cz = -15; cz < 15; cz++) {
            for (int cx = -15; cx < 15; cx++) {
                var site = field.site(cx, cz);
                if (site == null) continue;
                sites++;
                counts.merge(site.kind().id(), 1, Integer::sum);
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        var other = field.site(cx + dx, cz + dz);
                        if (other != null && Math.hypot(other.x() - site.x(), other.z() - site.z())
                                < other.reachBlocks() + site.reachBlocks()) {
                            overlaps++;
                        }
                    }
                }
            }
        }
        boolean all = core.macros().kinds().stream().allMatch(k -> counts.getOrDefault(k.id(), 0) > 0);
        check(all && overlaps == 0, String.format("macros in a 240 km square: %d %s, overlapping reaches %d",
                sites, counts, overlaps));
    }

    /**
     * No seams across macro edges: walk spokes from macro centres out past their reach, block by
     * block, and bisect every steep step as in {@link #noSeams}. Cliff walls are meant to be steep.
     */
    private static void macroEdges() {
        AtlasCore core = new AtlasCore(23);
        var field = core.macros();
        double worstStep = 0, worstResidual = 0;
        int steep = 0, spokes = 0;
        String at = "";
        Map<String, Integer> perKind = new HashMap<>();
        for (int cz = -15; cz < 15; cz++) {
            for (int cx = -15; cx < 15; cx++) {
                var site = field.site(cx, cz);
                if (site == null || perKind.getOrDefault(site.kind().id(), 0) >= 3) continue;
                perKind.merge(site.kind().id(), 1, Integer::sum);
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4 + 0.3, ux = Math.cos(a), uz = Math.sin(a);
                    double r0 = site.radius() * 0.6, r1 = site.reachBlocks() + 400;
                    double prev = core.sampleBase(site.x() + ux * r0, site.z() + uz * r0).height();
                    spokes++;
                    for (double r = r0 + 1; r <= r1; r += 1) {
                        double x = site.x() + ux * r, z = site.z() + uz * r;
                        double h = core.sampleBase(x, z).height();
                        double step = Math.abs(h - prev);
                        worstStep = Math.max(worstStep, step);
                        if (step > 12) {
                            steep++;
                            double residual = residualJumpAlong(core, site.x() + ux * (r - 1), site.z() + uz * (r - 1), x, z);
                            if (residual > worstResidual) {
                                worstResidual = residual;
                                at = String.format("%.0f, %.0f (%s)", x, z, site.kind().id());
                            }
                        }
                        prev = h;
                    }
                }
            }
        }
        check(worstResidual < 1.5, String.format(
                "no seams across macro edges: %d spokes, %d steep steps (max %.1f blocks/block), worst jump left %.2f%s",
                spokes, steep, worstStep, worstResidual, at.isEmpty() ? "" : " at " + at));
    }

    private static double residualJumpAlong(AtlasCore core, double x0, double z0, double x1, double z1) {
        double h0 = core.sampleBase(x0, z0).height(), h1 = core.sampleBase(x1, z1).height();
        for (int i = 0; i < 8; i++) {
            double mx = (x0 + x1) / 2, mz = (z0 + z1) / 2, hm = core.sampleBase(mx, mz).height();
            if (Math.abs(hm - h0) > Math.abs(h1 - hm)) {
                x1 = mx;
                z1 = mz;
                h1 = hm;
            } else {
                x0 = mx;
                z0 = mz;
                h0 = hm;
            }
        }
        if (sharp(core, x0, z0) || sharp(core, x1, z1)) return 0;
        return Math.abs(h1 - h0);
    }

    private static boolean sharp(AtlasCore core, double x, double z) {
        ColumnSample c = core.sampleBase(x, z);
        return c.wall() != null && c.wall().sharp() || c.shore() != null && c.shore().atFace();
    }

    /** Every M1 landform turns up, in plausible shares, across a large area. */
    private static void coverage() {
        AtlasCore core = new AtlasCore(17);
        ProvinceMosaic mosaic = core.mosaic();
        Map<String, Integer> counts = new HashMap<>();
        int land = 0;
        for (int cz = -60; cz < 60; cz++) {
            for (int cx = -60; cx < 60; cx++) {
                String id = mosaic.province(cx, cz).landform().id();
                if (id.equals("ocean")) continue;
                land++;
                counts.merge(id, 1, Integer::sum);
            }
        }
        StringBuilder shares = new StringBuilder();
        boolean all = true;
        for (String id : List.of("megarange", "canyon_plateau", "dune_sea", "rock_desert", "playa", "lowlands", "hills", "highlands")) {
            int n = counts.getOrDefault(id, 0);
            all &= n > 0;
            shares.append(String.format(" %s %.1f%%", id, 100.0 * n / Math.max(1, land)));
        }
        check(all, "every landform appears in a 192 km square:" + shares);
    }

    /**
     * The river network on a 24 km square: every river node drains to the sea or a closed-basin
     * lake without looping, its water never rises downstream, and most rivers reach the sea.
     */
    private static void rivers() {
        AtlasCore core = new AtlasCore(5);
        Drainage drainage = core.drainage();
        int lo = Drainage.cell(-12_000), hi = Drainage.cell(12_000);
        int rivers = 0, sea = 0, lake = 0, loops = 0, uphill = 0, longest = 0;
        for (int j = lo; j <= hi; j += 3) {
            for (int i = lo; i <= hi; i += 3) {
                Drainage.Node node = drainage.node(i, j);
                if (!node.river()) continue;
                rivers++;
                HashSet<Long> seen = new HashSet<>();
                for (int step = 0; ; step++) {
                    if (!seen.add(((long) node.i() << 32) | (node.j() & 0xFFFFFFFFL)) || step > 20_000) {
                        loops++;
                        break;
                    }
                    Drainage.Node next = drainage.next(node);
                    if (next == null) {
                        if (node.down() == -2) sea++;
                        else lake++;
                        longest = Math.max(longest, step);
                        break;
                    }
                    if (next.level() > node.level() + 1e-3) uphill++;
                    node = next;
                }
            }
        }
        check(rivers > 100 && loops == 0 && uphill == 0 && sea > 0.6 * rivers, String.format(
                "rivers: %d sampled, %d reach the sea, %d end in closed-basin lakes, %d loops, %d uphill steps, longest %d nodes",
                rivers, sea, lake, loops, uphill, longest));
    }

    /**
     * Rivers and lakes hold their water (playtest rounds 3 and 3b found walls of water over valleys
     * and water standing over the plain): around 16 river spots on the dev seed, held water almost
     * never meets open air (a dry neighbour's ground under its surface), and drops of 2+ blocks
     * from water to lower water are almost all at pool lips, where the lip pours as a waterfall.
     */
    private static void riverContainment() {
        AtlasCore core = new AtlasCore(-2953816398943392853L); // the dev seed, where the walls were seen
        Drainage drainage = core.drainage();
        int lo = Drainage.cell(-6000), hi = Drainage.cell(6000);
        List<Drainage.Node> high = new ArrayList<>(), low = new ArrayList<>();
        for (int j = lo; j <= hi; j++) {
            for (int i = lo; i <= hi; i++) {
                Drainage.Node n = drainage.node(i, j);
                if (n.river()) (n.terrain() > 120 ? high : low).add(n);
            }
        }
        Random random = new Random(7);
        Collections.shuffle(high, random);
        Collections.shuffle(low, random);
        List<Drainage.Node> spots = new ArrayList<>(high.subList(0, Math.min(8, high.size())));
        spots.addAll(low.subList(0, Math.min(8, low.size())));
        long wet = 0, open = 0, lip = 0, wall = 0;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Drainage.Node spot : spots) {
            for (int z = (int) spot.z() - 96; z < (int) spot.z() + 96; z++) {
                for (int x = (int) spot.x() - 96; x < (int) spot.x() + 96; x++) {
                    ColumnSample c = core.column(x, z);
                    int top = waterTop(c);
                    if (top == Integer.MIN_VALUE) continue;
                    wet++;
                    for (int[] o : around) {
                        ColumnSample n = core.column(x + o[0], z + o[1]);
                        int next = waterTop(n);
                        if (next == Integer.MIN_VALUE) {
                            if (n.surfaceY() < top && top >= AtlasCore.SEA_LEVEL) open++;
                        } else if (top - next >= 2) {
                            if (c.spill()) lip++;
                            else wall++;
                        }
                    }
                }
            }
        }
        // Up to 3 per 1000: which spots get sampled shifts whenever terrain anywhere near changes, and
        // the remaining faces are one-block lake-shore and confluence steps that placement turns into trickles.
        check(wet > 20_000 && open * 1000 <= 3 * wet && wall * 1000 <= 3 * wet, String.format(
                "river water contained: %d water columns, %d open faces, %d drops at a lip, %d elsewhere", wet, open, lip, wall));
    }

    /**
     * Meso sites (docs/MICRO_FEATURES.md) on a 24 km square of the dev seed (rare kinds like fern gullies need the room): every kind turns up,
     * a second core finds the same sites, and the water in ponds and cenotes is held (no dry
     * neighbour's ground under its surface).
     */
    private static void mesoSites() {
        long seed = -2953816398943392853L;
        AtlasCore core = new AtlasCore(seed), again = new AtlasCore(seed);
        Map<MesoKind, Integer> count = new java.util.EnumMap<>(MesoKind.class);
        boolean same = true;
        long wet = 0, open = 0;
        int calderas = 0;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int cj = Features.cell(-12000); cj <= Features.cell(12000); cj++) {
            for (int ci = Features.cell(-12000); ci <= Features.cell(12000); ci++) {
                MesoSite s = core.features().site(ci, cj), t = again.features().site(ci, cj);
                same &= s == null ? t == null : t != null && s.kind() == t.kind() && s.x() == t.x() && s.micros().size() == t.micros().size();
                if (s == null) continue;
                count.merge(s.kind(), 1, Integer::sum);
                for (MicroFeature m : s.micros()) {
                    if (m.kind == MicroKind.CALDERA) {
                        core.column((int) m.x, (int) m.z); // checks it
                        if (!m.wet() || calderas++ > 0) continue; // one is enough: they're big
                    } else if (m.kind != MicroKind.POND && m.kind != MicroKind.CENOTE && m.kind != MicroKind.GROTTO && m.kind != MicroKind.TRAVERTINE) continue;
                    int reach = (int) m.reach();
                    for (int z = (int) m.z - reach; z <= m.z + reach; z++) {
                        for (int x = (int) m.x - reach; x <= m.x + reach; x++) {
                            // A caldera's lake can only leak at its wall: only the ring round the shore.
                            if (m.kind == MicroKind.CALDERA && Math.abs(Math.hypot(x - m.x, z - m.z) - (m.r - 30)) > 45) continue;
                            ColumnSample c = core.column(x, z);
                            int top = waterTop(c);
                            if (top == Integer.MIN_VALUE) continue;
                            wet++;
                            for (int[] o : around) {
                                ColumnSample n = core.column(x + o[0], z + o[1]);
                                if (waterTop(n) == Integer.MIN_VALUE && n.surfaceY() < top) open++;
                            }
                        }
                    }
                }
            }
        }
        check(same && count.size() == MesoKind.values().length && wet > 2000 && open * 1000 <= 2 * wet, String.format(
                "meso sites: %s, deterministic %b; pond and cenote water %d columns, %d open faces", count, same, wet, open));
    }

    /** The top block of water over a column, if it has any water blocks at all (else MIN_VALUE). */
    /**
     * M7b passages: the same on a fresh core, open enough to be a cave system but no Swiss
     * cheese (0.3-6% of the rock between -450 and 0), and never open within the cover under
     * water: no column next to a river or lake is opened in its top 26 blocks.
     */
    private static void passages() {
        AtlasCore core = new AtlasCore(11), again = new AtlasCore(11);
        long open = 0, all = 0, wetOpen = 0, mouths = 0;
        boolean same = true;
        for (int z = -3000; z < 3000; z += 23) {
            for (int x = -3000; x < 3000; x += 23) {
                ColumnSample c = core.column(x, z);
                for (int y = -450; y < Math.min(0, c.height() - 12); y += 3) {
                    all++;
                    double d = core.passages().carve(c, x, y, z);
                    if (d < 0) open++;
                    if ((y & 63) == 0 && d != again.passages().carve(again.column(x, z), x, y, z)) same = false;
                }
                if (c.isOcean()) continue;
                boolean wet = c.nearWater() || !Double.isNaN(c.water());
                for (int y = c.surfaceY() - 25; y <= c.surfaceY(); y++) {
                    if (core.passages().carve(c, x, y, z) < 0) {
                        if (wet) wetOpen++;
                        else if (y >= c.surfaceY() - 2) mouths++;
                        break;
                    }
                }
            }
        }
        double pct = 100.0 * open / all;
        check(same && pct > 0.3 && pct < 6 && wetOpen == 0, String.format(
                "passages: %.2f%% of the deep open, %d mouth columns, %d opened under water, deterministic %s", pct, mouths, wetOpen, same));
    }

    /** M7c mega-caverns: present, the same on a fresh core, and roofed by 40+ blocks of rock. */
    private static void megaCaverns() {
        AtlasCore core = new AtlasCore(13), again = new AtlasCore(13);
        int sites = 0, shallow = 0;
        boolean same = true;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int z = -20000; z <= 20000; z += 1700) {
            for (int x = -20000; x <= 20000; x += 1700) {
                double[] m = core.megaCaverns().nearest(x, z);
                if (m == null || !seen.add((int) m[0] + "," + (int) m[1])) continue;
                sites++;
                for (int k = 0; k < 40; k++) {
                    int px = (int) (m[0] + (k % 8 - 3.5) * m[2] / 9), pz = (int) (m[1] + (k / 8 - 2) * m[2] / 9);
                    ColumnSample c = core.column(px, pz);
                    for (int y = (int) c.height() - 40; y < c.height(); y += 2) if (core.megaCaverns().carve(px, y, pz) < 0) { shallow++; break; }
                    if (core.megaCaverns().carve(px, (int) m[3], pz) != again.megaCaverns().carve(px, (int) m[3], pz)) same = false;
                }
            }
        }
        check(sites > 60 && shallow == 0 && same, String.format("mega-caverns: %d in a 40 km square, %d opened within 40 of the ground, deterministic %s", sites, shallow, same));
    }

    private static int waterTop(ColumnSample c) {
        if (Double.isNaN(c.water())) return Integer.MIN_VALUE;
        int top = (int) Math.floor(c.water());
        return top > c.surfaceY() ? top : Integer.MIN_VALUE;
    }
}
