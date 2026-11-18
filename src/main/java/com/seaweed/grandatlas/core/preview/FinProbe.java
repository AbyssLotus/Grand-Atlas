package com.seaweed.grandatlas.core.preview;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds fins beside rivers (the user's "jutting out of the river system"): columns standing 8+ blocks
 * over the ground 6 blocks away on both sides, along x or z, within 120 blocks of the courses
 * RiverViews wrote. Prints how many, and for the worst few what each river and lake did there.
 *
 * <pre>gradlew finProbe -Pargs="preview/rivers/after"</pre>
 */
public final class FinProbe {
    private FinProbe() {}

    public static void main(String[] args) throws Exception {
        AtlasCore core = new AtlasCore(-2953816398943392853L);
        if (args.length >= 3 && args[0].equals("at")) {
            // A line of columns from (x0, z0) to (x1, z1): heights and what shaped each.
            int x0 = Integer.parseInt(args[1]), z0 = Integer.parseInt(args[2]);
            int x1 = args.length >= 5 ? Integer.parseInt(args[3]) : x0, z1 = args.length >= 5 ? Integer.parseInt(args[4]) : z0;
            int n = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) / 8);
            for (int k = 0; k <= n; k++) {
                int x = x0 + (x1 - x0) * k / n, z = z0 + (z1 - z0) * k / n;
                ColumnSample c = core.column(x, z);
                System.out.printf("%d %d: height %.1f uncut %.1f water %.1f biome %s, %s / %s%n", x, z, c.height(), c.uncut(), c.water(), c.biome(),
                        c.site() == null ? "-" : c.site().kind(), c.micro() == null ? "-" : c.micro().kind);
                System.out.print(core.drainage().explain(x, z, c.uncut()));
            }
            return;
        }
        if (args.length >= 1 && args[0].equals("leaks")) {
            // SelfCheck's river containment, printing where the water meets open air.
            com.seaweed.grandatlas.core.hydro.Drainage drainage = core.drainage();
            int lo = com.seaweed.grandatlas.core.hydro.Drainage.cell(-6000), hi = com.seaweed.grandatlas.core.hydro.Drainage.cell(6000);
            List<com.seaweed.grandatlas.core.hydro.Drainage.Node> high = new ArrayList<>(), low = new ArrayList<>();
            for (int j = lo; j <= hi; j++) {
                for (int i = lo; i <= hi; i++) {
                    com.seaweed.grandatlas.core.hydro.Drainage.Node n = drainage.node(i, j);
                    if (n.river()) (n.terrain() > 120 ? high : low).add(n);
                }
            }
            java.util.Random random = new java.util.Random(7);
            java.util.Collections.shuffle(high, random);
            java.util.Collections.shuffle(low, random);
            List<com.seaweed.grandatlas.core.hydro.Drainage.Node> spots = new ArrayList<>(high.subList(0, Math.min(8, high.size())));
            spots.addAll(low.subList(0, Math.min(8, low.size())));
            int shown = 0;
            List<int[]> seenAt = new ArrayList<>();
            for (com.seaweed.grandatlas.core.hydro.Drainage.Node spot : spots) {
                int count = 0;
                for (int z = (int) spot.z() - 96; z < (int) spot.z() + 96; z++) {
                    for (int x = (int) spot.x() - 96; x < (int) spot.x() + 96; x++) {
                        ColumnSample c = core.column(x, z);
                        int top = core.waterTop(x, z);
                        if (top == Integer.MIN_VALUE) continue;
                        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            ColumnSample n = core.column(x + o[0], z + o[1]);
                            boolean nw = core.waterTop(x + o[0], z + o[1]) != Integer.MIN_VALUE;
                            if (nw && top - core.waterTop(x + o[0], z + o[1]) >= 2 && !c.spill() && shown < 14) {
                                shown++;
                                System.out.printf("%nwall %d %d -> %d %d: water %.2f vs %.2f, %s%n", x, z, x + o[0], z + o[1], c.water(), n.water(), c.biome());
                                System.out.print("  hi: " + drainage.explain(x, z, c.uncut()));
                                System.out.print("  lo: " + drainage.explain(x + o[0], z + o[1], n.uncut()));
                            }
                            boolean skimOk = n.surfaceY() == top - 1 && AtlasCore.ninths(c.water()) <= AtlasCore.SKIM;
                            if (!nw && n.surfaceY() < top && top >= AtlasCore.SEA_LEVEL && !skimOk) {
                                count++;
                                boolean near = false;
                                for (int[] q : seenAt) near |= Math.abs(q[0] - x) + Math.abs(q[1] - z) < 30;
                                if (!near && shown < 8) {
                                    seenAt.add(new int[] {x, z});
                                    shown++;
                                    System.out.printf("%nopen face %d %d -> %d %d: water %.2f (top %d) over ground %.2f; dry neighbour ground %.2f water %.2f, %s%n", x, z, x + o[0], z + o[1],
                                            c.water(), top, c.height(), n.height(), n.water(), c.biome());
                                    System.out.print("  wet: " + drainage.explain(x, z, c.uncut()));
                                    System.out.print("  dry: " + drainage.explain(x + o[0], z + o[1], n.uncut()));
                                }
                            }
                        }
                    }
                }
                System.out.printf("spot %d %d: %d open faces%n", (int) spot.x(), (int) spot.z(), count);
            }
            return;
        }
        if (args.length >= 4 && args[0].equals("leaksat")) {
            // Open faces (water over a dry bank more than a skim) and walls of water in a box.
            int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]), r = Integer.parseInt(args[3]);
            int wet = 0, open = 0, walls = 0;
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    int top = core.waterTop(x, z);
                    if (top == Integer.MIN_VALUE) continue;
                    wet++;
                    ColumnSample c = core.column(x, z);
                    for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        ColumnSample n = core.column(x + o[0], z + o[1]);
                        int nt = core.waterTop(x + o[0], z + o[1]);
                        if (nt == Integer.MIN_VALUE) {
                            boolean skim = n.surfaceY() == top - 1 && AtlasCore.ninths(c.water()) <= AtlasCore.SKIM;
                            if (n.surfaceY() < top && !skim) {
                                if (open < 4) System.out.printf("open %d %d -> %d %d: water %.2f top %d ground %.2f | neighbour ground %.2f water %.2f%n%s", x, z, x + o[0], z + o[1],
                                        c.water(), top, c.height(), n.height(), n.water(), core.drainage().explain(x + o[0], z + o[1], n.uncut()));
                                open++;
                            }
                        } else if (top - nt >= 2 && !c.spill()) {
                            if (walls < 3) System.out.printf("wall %d %d -> %d %d: water %.2f vs %.2f%n%s", x, z, x + o[0], z + o[1], c.water(), n.water(), core.drainage().explain(x, z, c.uncut()));
                            walls++;
                        }
                    }
                }
            }
            System.out.printf("within %d of %d %d: %d water columns, %d open faces, %d walls%n", r, cx, cz, wet, open, walls);
            return;
        }
        if (args.length >= 4 && args[0].equals("cliffs")) {
            // Cliffs the carving made: neighbours 6+ apart in height whose ground before rivers was within 2.
            int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]), r = Integer.parseInt(args[3]);
            List<double[]> found = new ArrayList<>();
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    ColumnSample a = core.column(x, z);
                    for (int[] o : new int[][] {{1, 0}, {0, 1}}) {
                        ColumnSample b = core.column(x + o[0], z + o[1]);
                        double jump = Math.abs(a.height() - b.height());
                        if (jump >= 6 && Math.abs(a.uncut() - b.uncut()) < 2) found.add(new double[] {jump, x, z, o[0], o[1]});
                    }
                }
            }
            found.sort((u, v) -> Double.compare(v[0], u[0]));
            System.out.printf("%d carved cliff faces (6+) within %d of %d %d%n", found.size(), r, cx, cz);
            List<double[]> shown = new ArrayList<>();
            for (double[] f : found) {
                boolean near = false;
                for (double[] o : shown) near |= Math.hypot(o[1] - f[1], o[2] - f[2]) < 24;
                if (near) continue;
                shown.add(f);
                for (int side = 0; side < 2; side++) {
                    int x = (int) f[1] + side * (int) f[3], z = (int) f[2] + side * (int) f[4];
                    ColumnSample c = core.column(x, z);
                    System.out.printf((side == 0 ? "%n" : "") + "%d %d: height %.1f uncut %.1f near %b, %s / %s / %s%n", x, z, c.height(), c.uncut(), c.nearWater(), c.landform().id(),
                            c.site() == null ? "-" : c.site().kind(), c.micro() == null ? "-" : c.micro().kind);
                    System.out.print(core.drainage().explain(x, z, c.uncut()));
                }
                if (shown.size() >= 6) break;
            }
            return;
        }
        File dir = new File(args.length > 0 ? args[0] : "preview/rivers/after");
        long cols = 0, fins = 0;
        List<double[]> worst = new ArrayList<>();
        for (int s = 0; ; s++) {
            File f = new File(dir, "path_" + s + ".txt");
            if (!f.exists()) break;
            List<String> lines = Files.readAllLines(f.toPath());
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (int k = 0; k < lines.size(); k += 8) {
                String[] v = lines.get(k).trim().split(" ");
                int cx = (int) Double.parseDouble(v[0]), cz = (int) Double.parseDouble(v[1]);
                for (int dz = -60; dz <= 60; dz += 2) {
                    for (int dx = -60; dx <= 60; dx += 2) {
                        int x = cx + dx, z = cz + dz;
                        if (!seen.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
                        cols++;
                        double h = core.column(x, z).height();
                        double sx = Math.max(core.column(x - 6, z).height(), core.column(x + 6, z).height());
                        double sz = Math.max(core.column(x, z - 6).height(), core.column(x, z + 6).height());
                        double over = h - Math.min(sx, sz);
                        if (over >= 8) {
                            fins++;
                            worst.add(new double[] {over, x, z});
                        }
                    }
                }
            }
        }
        worst.sort((a, b) -> Double.compare(b[0], a[0]));
        System.out.printf("%d columns near the courses, %d stand 8+ over the ground 6 away on both sides%n", cols, fins);
        List<double[]> shown = new ArrayList<>();
        for (double[] w : worst) {
            boolean near = false;
            for (double[] o : shown) near |= Math.hypot(o[1] - w[1], o[2] - w[2]) < 40;
            if (near) continue;
            shown.add(w);
            int x = (int) w[1], z = (int) w[2];
            ColumnSample c = core.column(x, z);
            System.out.printf("%n%d %d: height %.1f, uncut %.1f, %.1f over its sides; sides x %.1f/%.1f z %.1f/%.1f%n", x, z, c.height(), c.uncut(), w[0],
                    core.column(x - 6, z).height(), core.column(x + 6, z).height(), core.column(x, z - 6).height(), core.column(x, z + 6).height());
            System.out.print(core.drainage().explain(x, z, c.uncut()));
            if (shown.size() >= 6) break;
        }
    }
}
