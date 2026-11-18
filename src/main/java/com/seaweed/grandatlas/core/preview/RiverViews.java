package com.seaweed.grandatlas.core.preview;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.hydro.Drainage;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Long profiles of mountain rivers, to judge the water rework (docs/WATER_REWORK.md): a few steep
 * upland rivers on the dev seed, each followed down six nodes (~800 blocks). Draws the water's
 * surface (blue) and the bed under it (brown) by block, as the world has them, along the course.
 * Flat pools and steps show as a staircase; a sloped river as a smooth line. Also counts steps:
 * places where the water drops 2+ blocks between neighbouring course points.
 *
 * <pre>gradlew riverViews -Pargs="out-dir [paths-dir]"</pre>
 * With a paths-dir, the courses are read from the paths files a newer build wrote there (so an older
 * Drainage, without {@code coursePoints}, can be drawn along the very same courses).
 */
public final class RiverViews {
    private RiverViews() {}

    private static final int SPOTS = 20, NODES = 6, W = 1400, H = 420;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "preview/rivers");
        File pathsIn = args.length > 1 ? new File(args[1]) : null;
        out.mkdirs();
        AtlasCore core = new AtlasCore(-2953816398943392853L);
        Drainage drainage = core.drainage();
        List<double[][]> courses = new ArrayList<>();
        if (pathsIn == null) {
            int lo = Drainage.cell(-6000), hi = Drainage.cell(6000);
            List<Drainage.Node> steep = new ArrayList<>();
            for (int j = lo; j <= hi; j++) {
                for (int i = lo; i <= hi; i++) {
                    Drainage.Node n = drainage.node(i, j);
                    if (!n.river() || n.level() < 110 || n.catchment() > 400) continue;
                    Drainage.Node next = drainage.next(n);
                    if (next != null && n.level() - next.level() > 10) steep.add(n);
                }
            }
            Collections.shuffle(steep, new Random(11));
            for (int s = 0; s < Math.min(SPOTS, steep.size()); s++) {
                List<double[]> pts = new ArrayList<>();
                Drainage.Node n = steep.get(s);
                for (int k = 0; k < NODES && n != null; k++) {
                    for (double[] p : drainage.coursePoints(n)) pts.add(p);
                    n = drainage.next(n);
                }
                double[][] c = resample(pts.toArray(new double[0][]));
                courses.add(c);
                StringBuilder sb = new StringBuilder();
                for (double[] p : c) sb.append(p[0]).append(' ').append(p[1]).append('\n');
                Files.writeString(new File(out, "path_" + s + ".txt").toPath(), sb);
            }
        } else {
            for (int s = 0; s < SPOTS; s++) {
                File f = new File(pathsIn, "path_" + s + ".txt");
                if (!f.exists()) break;
                List<double[]> pts = new ArrayList<>();
                for (String line : Files.readAllLines(f.toPath())) {
                    String[] v = line.trim().split(" ");
                    if (v.length == 2) pts.add(new double[] {Double.parseDouble(v[0]), Double.parseDouble(v[1])});
                }
                courses.add(pts.toArray(new double[0][]));
            }
        }
        long points = 0, steps = 0, wetPts = 0, gorge = 0, perched = 0;
        double aboveSum = 0;
        double cutSum = 0;
        for (int s = 0; s < courses.size(); s++) {
            double[][] c = courses.get(s);
            int n = c.length;
            double[] water = new double[n], bed = new double[n];
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int k = 0; k < n; k++) {
                ColumnSample col = core.column((int) Math.floor(c[k][0]), (int) Math.floor(c[k][1]));
                bed[k] = Math.floor(col.height());
                // The water as drawn: its top block plus the drawn surface in it.
                water[k] = !Double.isNaN(col.water()) && col.water() > col.height() ? col.water() : Double.NaN;
                lo = Math.min(lo, bed[k]);
                hi = Math.max(hi, Double.isNaN(water[k]) ? bed[k] : water[k]);
                if (!Double.isNaN(water[k]) && !col.biome().name().endsWith("OCEAN")) { // (not fjords: sea troughs by design)
                    // How deep the river runs under the land before rivers: a gorge where it's 20+.
                    double cut = col.uncut() - water[k];
                    wetPts++;
                    cutSum += cut;
                    if (cut > 20) gorge++;
                    // Over the valley: the course's land above the lowest land within 24 blocks of it.
                    double lowest = col.uncut();
                    for (int r = 0; r < 12; r++) {
                        double ang = r * Math.PI / 6;
                        lowest = Math.min(lowest, core.column((int) Math.floor(c[k][0] + 24 * Math.cos(ang)), (int) Math.floor(c[k][1] + 24 * Math.sin(ang))).uncut());
                    }
                    aboveSum += col.uncut() - lowest;
                    if (col.uncut() - lowest > 10) perched++;
                }
                if (k > 0 && !Double.isNaN(water[k]) && !Double.isNaN(water[k - 1])) {
                    points++;
                    if (Math.floor(water[k - 1]) - Math.floor(water[k]) >= 2) steps++;
                }
            }
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 243, 236));
            g.fillRect(0, 0, W, H);
            double sx = (W - 40.0) / Math.max(1, n - 1), sy = (H - 40.0) / Math.max(10, hi - lo + 4);
            g.setColor(new Color(225, 222, 212));
            for (double y = Math.floor(lo / 10) * 10; y <= hi + 2; y += 10) {
                int py = (int) (H - 20 - (y - lo + 2) * sy);
                g.drawLine(20, py, W - 20, py);
                g.drawString(String.valueOf((int) y), 2, py - 2);
            }
            for (int k = 0; k + 1 < n; k++) {
                int x0 = (int) (20 + k * sx), x1 = (int) (20 + (k + 1) * sx);
                int yb = (int) (H - 20 - (bed[k] - lo + 2) * sy);
                g.setColor(new Color(150, 120, 90));
                g.fillRect(x0, yb, Math.max(1, x1 - x0), H - yb);
                if (!Double.isNaN(water[k])) {
                    int yw = (int) (H - 20 - (water[k] - lo + 2) * sy);
                    g.setColor(new Color(60, 120, 220));
                    g.fillRect(x0, yw, Math.max(1, x1 - x0), Math.max(1, yb - yw));
                }
            }
            g.setColor(Color.DARK_GRAY);
            g.setStroke(new BasicStroke(1));
            g.drawString(String.format("course from %d %d, %d blocks; heights exaggerated %.1fx", (int) c[0][0], (int) c[0][1], n, sy / sx), 24, 16);
            g.dispose();
            File f = new File(out, "profile_" + s + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f);
        }
        System.out.printf("water along the courses: %d steps along, %d drop 2+ blocks at once%n", points, steps);
        // Gaps: a course point with no water block while the course 1-4 points either side has some.
        long gaps = 0, wetCourse = 0;
        for (double[][] c : courses) {
            boolean[] w = new boolean[c.length];
            for (int k = 0; k < c.length; k++) {
                // Water anywhere across the river here (3 blocks either side of the course line, which the
                // channel wanders a block or so off), not round about: a break in the water is a dry cross-section.
                int a = Math.max(0, k - 2), b = Math.min(c.length - 1, k + 2);
                double tx = c[b][0] - c[a][0], tz = c[b][1] - c[a][1], tl = Math.max(1e-6, Math.hypot(tx, tz));
                for (double o = -3; o <= 3 && !w[k]; o += 0.5) {
                    w[k] = core.waterTop((int) Math.floor(c[k][0] - tz / tl * o), (int) Math.floor(c[k][1] + tx / tl * o)) != Integer.MIN_VALUE;
                }
            }
            for (int k = 4; k + 4 < c.length; k++) {
                if (w[k]) { wetCourse++; continue; }
                boolean before = false, after = false;
                for (int d = 1; d <= 4; d++) { before |= w[k - d]; after |= w[k + d]; }
                if (before && after) {
                    if (gaps < 8) {
                        int gx = (int) Math.floor(c[k][0]), gz = (int) Math.floor(c[k][1]);
                        ColumnSample g = core.column(gx, gz);
                        System.out.printf("  gap %d %d: height %.2f water %.2f uncut %.2f spill %b%n", gx, gz, g.height(), g.water(), g.uncut(), g.spill());
                    }
                    gaps++;
                }
            }
        }
        System.out.printf("gaps: %d dry points inside wet stretches, per %d wet points%n", gaps, wetCourse);
        System.out.printf("cut under the land: mean %.1f blocks, %.1f%% of the course in a gorge (20+ deep)%n", cutSum / Math.max(1, wetPts), 100.0 * gorge / Math.max(1, wetPts));
        System.out.printf("off the valley floor: course land %.1f over the lowest land within 24 on average, %.1f%% of it 10+ over%n", aboveSum / Math.max(1, wetPts), 100.0 * perched / Math.max(1, wetPts));
    }

    /** Points every block along a polyline. */
    private static double[][] resample(double[][] p) {
        List<double[]> out = new ArrayList<>();
        if (p.length == 0) return new double[0][];
        out.add(p[0]);
        double carry = 0;
        for (int k = 0; k + 1 < p.length; k++) {
            double dx = p[k + 1][0] - p[k][0], dz = p[k + 1][1] - p[k][1], len = Math.hypot(dx, dz);
            double t = 1 - carry;
            while (t <= len) {
                out.add(new double[] {p[k][0] + dx * t / len, p[k][1] + dz * t / len});
                t += 1;
            }
            carry = len - (t - 1);
        }
        return out.toArray(new double[0][]);
    }
}
