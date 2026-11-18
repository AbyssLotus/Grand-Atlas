package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.Passages;
import com.seaweed.grandatlas.core.SetPieces;
import com.seaweed.grandatlas.core.Waters;
import com.seaweed.grandatlas.core.hydro.Drainage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

/**
 * What Grand Atlas decided at a spot, as short labelled rows in three sections (surface, water,
 * underground). One source for the F3 lines ({@link #f3}) and {@code /grandatlas where}
 * ({@link #chat}), so both say the same thing (cave rework C0, docs/CAVE_RESEARCH.md: the user
 * found the old one-line dump hard to read).
 */
public final class DebugReport {
    private DebugReport() {}

    public enum Section {
        SURFACE("Surface", ChatFormatting.GREEN),
        WATER("Water", ChatFormatting.AQUA),
        UNDERGROUND("Underground", ChatFormatting.GOLD);

        final String title;
        final ChatFormatting colour;

        Section(String title, ChatFormatting colour) {
            this.title = title;
            this.colour = colour;
        }
    }

    /** One row: a short label, its value, and more detail for the hover text (may be empty). */
    public record Row(Section section, String label, String value, String detail) {}

    public static List<Row> rows(ServerLevel level, AtlasCore core, BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        ColumnSample c = core.column(x, z);
        List<Row> rows = new ArrayList<>();

        // ---- surface
        rows.add(new Row(Section.SURFACE, "Place", String.join(" > ", c.tags()),
                String.format("province %d, %d (%s)", c.provinceX(), c.provinceZ(), c.transition().id())));
        rows.add(new Row(Section.SURFACE, "Ground", String.format("y %d", c.surfaceY()),
                String.format("datum %.1f + relief %.1f", c.datum(), c.relief())));
        rows.add(new Row(Section.SURFACE, "Biome", c.biome().id(), String.format("temperature %.2f, humidity %.2f, continentalness %.2f",
                c.temperature(), c.humidity(), c.continentalness())));
        if (y >= c.surfaceY() - 3) rows.add(new Row(Section.SURFACE, "Biome here", core.biomeAt(c, x, y, z).id(), "the biome at your height"));

        // ---- water
        if (!Double.isNaN(c.water())) {
            int top = core.waterTop(x, z);
            rows.add(new Row(Section.WATER, "Water here", String.format("level %.2f", c.water()),
                    top == Integer.MIN_VALUE ? "no water block (under the skim)" : "top block y " + top));
        }
        double[] river = nearestRiver(core, x, z);
        rows.add(river == null
                ? new Row(Section.WATER, "River", "none within ~250", "")
                : new Row(Section.WATER, "River", String.format("%.0f blocks away, water y %.1f", river[0], river[1]),
                        String.format("nearest course point %.0f %.0f; catchment %.0f", river[2], river[3], river[4])));
        double table = core.waters().table(x, z);
        rows.add(new Row(Section.WATER, "Water table", Double.isNaN(table) ? "none" : String.format("y %.0f", table),
                "caves at or below the table are flooded"));

        // ---- underground
        boolean under = y < c.surfaceY() - 3;
        if (under) {
            String band = y > 0 ? "upper (real)" : y > -250 ? "deep (heightened real)" : "abyss (Rule of Cool)";
            rows.add(new Row(Section.UNDERGROUND, "Band", band, "the vertical fantasy rule: more Rule of Cool the deeper you go"));
            rows.add(new Row(Section.UNDERGROUND, "Cave", caveAt(level, core, c, pos), ""));
            rows.add(new Row(Section.UNDERGROUND, "Cave biome", core.biomeAt(c, x, y, z).id(), ""));
            com.seaweed.grandatlas.core.Karst.KarstSystem ks = core.karst().near(x, z);
            com.seaweed.grandatlas.core.Moods.Mood mood = core.moods().at(x, y, z);
            if (mood != null) {
                String feel = switch (mood) {
                    case AWE -> "tall halls, shafts, pale rock";
                    case FEAR -> "tight, low, twisting, dark rock";
                    case COZY -> "low wide rooms, warm rock";
                    case CLEVER -> "loops, crossings, banded rock";
                    case HORROR -> "narrow, dead ends, pits, sculk and bone";
                };
                rows.add(new Row(Section.UNDERGROUND, "Mood", mood.label(), feel));
            }
            if (ks != null) {
                String spring = ks.spring == null ? "no spring (a dry system)" : String.format("spring at %.0f %.0f, cave river at y %.0f", ks.spring[0], ks.spring[1], ks.base);
                String sink = ks.swallet == null ? "" : String.format("; a stream sinks at %.0f %.0f", ks.swallet[0], ks.swallet[1]);
                rows.add(new Row(Section.UNDERGROUND, "Province", "karst branchwork", spring + sink + String.format("; %d dry levels over the river, %d pieces", Math.max(0, ks.levels - 1), ks.pieces)));
            } else if (core.solution().covers(x, z)) {
                boolean sea = core.sampleBase(x, z).isOcean();
                rows.add(new Row(Section.UNDERGROUND, "Province", sea ? "drowned solution caves" : "solution caves",
                        sea ? "under the sea floor (30 of rock kept over them), flooded by the sea; blue holes lead in"
                            : "the baseline: galleries on levels every 26 blocks, rooms, shafts, hillside mouths"));
            } else {
                rows.add(new Row(Section.UNDERGROUND, "Province", "none near the top", "a shore too shallow for caves; the deep network runs below"));
            }
        }
        return rows;
    }

    /** What cave (if any) holds this block, by source. */
    private static String caveAt(ServerLevel level, AtlasCore core, ColumnSample c, BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int state = core.waters().state(c, x, y, z);
        String wet = state == Waters.WATER ? ", flooded" : state == Waters.HELD ? ", held water" : state == Waters.RISING ? ", rising fall" : "";
        SetPieces sp = core.setPieces();
        if (sp.nearColumn(x, z) && sp.sample(x, y, z).claimed && sp.sample(x, y, z).open) {
            return "set piece " + setPieceName(sp.sample(x, y, z).kind) + wet;
        }
        if (core.megaCaverns().openColumn(x, z) && core.megaCaverns().carve(x, y, z) < 0) return "mega-cavern" + wet;
        int kind = core.passages().kindAt(x, y, z);
        if (kind >= 0 && core.passages().carve(c, x, y, z) < 0) {
            int karst = core.passages().karstLevelAt(x, y, z);
            if (karst == 0) return "karst: " + Passages.kindName(kind) + ", the cave river's level" + wet;
            if (karst == -2) return "karst: " + Passages.kindName(kind) + ", the flooded level" + wet;
            if (karst > 0) return "karst: " + Passages.kindName(kind) + ", dry level " + karst + wet;
            int prov = core.passages().provinceAt(x, y, z);
            if (prov == 3) {
                int lv = core.deep().levelAt(x, y, z);
                return (lv >= 4 ? "abyss: " : "deep: ") + Passages.kindName(kind) + (lv >= 0 ? ", level " + lv : "") + wet;
            }
            if (prov == 2) {
                return "solution caves: " + Passages.kindName(kind) + ", level " + core.solution().levelAt(x, y, z) + wet;
            }
            return "passage: " + Passages.kindName(kind) + ", level " + Passages.levelOf(y) + wet;
        }
        if (y > com.seaweed.grandatlas.core.AtlasCore.TERRAIN_FLOOR + 13 && core.caves().cavern(c, x, y, z) < 0) return "noise chamber" + wet;
        if (!core.caves().fineNone(x, z) && core.caves().fine(x, y, z) < 0) return "pit or fissure" + wet;
        if (level.getBlockState(pos).isAir()) return "vanilla cave (not Grand Atlas's)";
        return "rock";
    }

    private static String setPieceName(int kind) {
        return switch (kind) {
            case SetPieces.HOLLOW -> "the Hollow";
            case SetPieces.GEODE -> "great geode";
            case SetPieces.TITAN -> "titan's grave";
            case SetPieces.VAULT -> "aurora vault";
            case SetPieces.ROOT -> "worldroot";
            case SetPieces.PEAKS -> "hanging mountains";
            default -> "#" + kind;
        };
    }

    /** The nearest river course point within two network nodes: {distance, water y, x, z, catchment}, or null. */
    static double[] nearestRiver(AtlasCore core, double x, double z) {
        Drainage d = core.drainage();
        int ci = Drainage.cell(x), cj = Drainage.cell(z);
        double best = Double.MAX_VALUE;
        double[] out = null;
        for (int j = cj - 2; j <= cj + 2; j++) {
            for (int i = ci - 2; i <= ci + 2; i++) {
                Drainage.Node n = d.node(i, j);
                if (!n.river()) continue;
                for (double[] p : d.courseLine(n)) {
                    double dd = Math.hypot(p[0] - x, p[1] - z);
                    if (dd < best) {
                        best = dd;
                        out = new double[] {dd, p[2], p[0], p[1], n.catchment()};
                    }
                }
            }
        }
        return out;
    }

    /** The F3 lines: a heading and one compact line per row. */
    public static List<String> f3(List<Row> rows) {
        List<String> out = new ArrayList<>();
        out.add("");
        out.add(ChatFormatting.BOLD + "Grand Atlas");
        Section last = null;
        for (Row r : rows) {
            String head = r.section() != last ? r.section().colour + r.section().title.substring(0, 1) + " " : "  ";
            last = r.section();
            out.add(head + ChatFormatting.GRAY + r.label() + ": " + ChatFormatting.WHITE + r.value());
        }
        return out;
    }

    /** {@code /grandatlas where}: a coloured heading per section, rows with hover detail, clickable coordinates. */
    public static Component chat(BlockPos pos, List<Row> rows) {
        String tp = "/tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableComponent out = Component.literal("Grand Atlas at ").withStyle(ChatFormatting.BOLD)
                .append(Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(s -> s.withBold(false).withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy (" + tp + ")")))));
        Section last = null;
        for (Row r : rows) {
            if (r.section() != last) {
                out.append(Component.literal("\n" + r.section().title).withStyle(s -> s.withBold(false).withColor(r.section().colour).withUnderlined(true)));
                last = r.section();
            }
            MutableComponent line = Component.literal("\n  " + r.label() + ": ").withStyle(s -> s.withBold(false).withUnderlined(false).withColor(ChatFormatting.GRAY))
                    .append(Component.literal(r.value()).withStyle(ChatFormatting.WHITE));
            if (!r.detail().isEmpty()) {
                line = line.withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(r.detail()))));
                line.append(Component.literal(" ⓘ").withStyle(ChatFormatting.DARK_GRAY));
            }
            out.append(line);
        }
        return out;
    }
}
