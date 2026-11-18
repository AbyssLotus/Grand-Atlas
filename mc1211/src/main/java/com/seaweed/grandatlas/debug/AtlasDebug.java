package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.Passages;
import com.seaweed.grandatlas.core.hydro.Drainage;
import com.seaweed.grandatlas.world.CoreHandle;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * In-game debug (cave rework C0, docs/CAVE_RESEARCH.md). The server owns the core, so it works the
 * same in single player and on a server:
 * <ul>
 *   <li><b>F3 lines.</b> While the debug screen is open the client pings ({@link Ping}) twice a
 *       second; an operator gets the {@link DebugReport} rows back ({@link Lines}).</li>
 *   <li><b>Visual layers</b> ({@code /grandatlas show caves|water|provinces|off}): the server sends
 *       the shapes near the player ({@link Shapes}) whenever they've moved 16 blocks, and the client
 *       draws them through the rock ({@code client/DebugView}).</li>
 * </ul>
 */
public final class AtlasDebug {
    private AtlasDebug() {}

    public static final int CAVES = 1, WATER = 2, PROVINCES = 4;
    public static final String[] LAYER_NAMES = {"caves", "water", "provinces"};

    /** How far round the player shapes are sent. */
    static final int RADIUS = 128;
    /** At most this many lines in one send (a few hundred KB). */
    static final int MAX_LINES = 12000;

    /** Client to server: the debug screen is open. */
    public record Ping() implements CustomPacketPayload {
        public static final Type<Ping> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "debug_ping"));
        public static final StreamCodec<ByteBuf, Ping> CODEC = StreamCodec.unit(new Ping());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the F3 lines. */
    public record Lines(List<String> lines) implements CustomPacketPayload {
        public static final Type<Lines> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "debug_lines"));
        public static final StreamCodec<ByteBuf, Lines> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Lines::lines, Lines::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: the shapes of the layers that are on. {@code lines}: six floats a line (two
     * ends), {@code lineColours} ARGB each; {@code quads}: twelve floats a quad (four corners),
     * {@code quadColours} ARGB each. An empty send clears the view.
     */
    public record Shapes(int layers, float[] lines, int[] lineColours, float[] quads, int[] quadColours) implements CustomPacketPayload {
        public static final Type<Shapes> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "debug_shapes"));
        public static final StreamCodec<ByteBuf, Shapes> CODEC = StreamCodec.of(AtlasDebug::write, AtlasDebug::read);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void write(ByteBuf buf, Shapes s) {
        buf.writeInt(s.layers);
        writeFloats(buf, s.lines);
        writeInts(buf, s.lineColours);
        writeFloats(buf, s.quads);
        writeInts(buf, s.quadColours);
    }

    private static Shapes read(ByteBuf buf) {
        int layers = buf.readInt();
        return new Shapes(layers, readFloats(buf), readInts(buf), readFloats(buf), readInts(buf));
    }

    private static void writeFloats(ByteBuf buf, float[] a) {
        buf.writeInt(a.length);
        for (float f : a) buf.writeFloat(f);
    }

    private static void writeInts(ByteBuf buf, int[] a) {
        buf.writeInt(a.length);
        for (int i : a) buf.writeInt(i);
    }

    private static float[] readFloats(ByteBuf buf) {
        int n = buf.readInt();
        if (n < 0 || n > MAX_LINES * 12) throw new IllegalArgumentException("debug shapes too big: " + n);
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = buf.readFloat();
        return a;
    }

    private static int[] readInts(ByteBuf buf) {
        int n = buf.readInt();
        if (n < 0 || n > MAX_LINES * 2) throw new IllegalArgumentException("debug shapes too big: " + n);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = buf.readInt();
        return a;
    }

    // ---- server state

    private static final Map<UUID, Integer> LAYERS = new HashMap<>();
    private static final Map<UUID, BlockPos> SENT_AT = new HashMap<>();

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> {
            var r = e.registrar("1").optional();
            r.playToServer(Ping.TYPE, Ping.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) ping(p);
            }));
            r.playToClient(Lines.TYPE, Lines.CODEC, (payload, ctx) -> ctx.enqueueWork(() ->
                    com.seaweed.grandatlas.client.DebugView.lines(payload.lines())));
            r.playToClient(Shapes.TYPE, Shapes.CODEC, (payload, ctx) -> ctx.enqueueWork(() ->
                    com.seaweed.grandatlas.client.DebugView.shapes(payload)));
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 10 == 3) tick(p);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            LAYERS.remove(e.getEntity().getUUID());
            SENT_AT.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> SENT_AT.remove(e.getEntity().getUUID()));
    }

    private static void ping(ServerPlayer p) {
        if (!p.hasPermissions(2)) return;
        AtlasCore core = CoreHandle.coreOf(p.serverLevel());
        if (core == null) return;
        BlockPos pos = p.blockPosition();
        PacketDistributor.sendToPlayer(p, new Lines(DebugReport.f3(DebugReport.rows(p.serverLevel(), core, pos))));
    }

    /** Turns a layer on or off ("off" clears them all); returns the layers now on. */
    public static int show(ServerPlayer p, String layer) {
        int on = LAYERS.getOrDefault(p.getUUID(), 0);
        if (layer.equals("off")) {
            on = 0;
        } else {
            for (int k = 0; k < LAYER_NAMES.length; k++) {
                if (LAYER_NAMES[k].equals(layer)) on ^= 1 << k;
            }
        }
        LAYERS.put(p.getUUID(), on);
        SENT_AT.remove(p.getUUID());
        if (on == 0) PacketDistributor.sendToPlayer(p, new Shapes(0, new float[0], new int[0], new float[0], new int[0]));
        else send(p, on);
        return on;
    }

    private static void tick(ServerPlayer p) {
        int on = LAYERS.getOrDefault(p.getUUID(), 0);
        if (on == 0) return;
        BlockPos at = SENT_AT.get(p.getUUID());
        if (at != null && at.distManhattan(p.blockPosition()) < 16) return;
        send(p, on);
    }

    private static void send(ServerPlayer p, int on) {
        AtlasCore core = CoreHandle.coreOf(p.serverLevel());
        if (core == null) return;
        SENT_AT.put(p.getUUID(), p.blockPosition());
        Builder b = new Builder();
        BlockPos pos = p.blockPosition();
        if ((on & CAVES) != 0) caves(core, pos, b);
        if ((on & WATER) != 0) water(core, pos, b);
        if ((on & PROVINCES) != 0) provinces(core, pos, b);
        PacketDistributor.sendToPlayer(p, b.build(on));
    }

    // ---- the layers

    /** Collects lines and quads, stopping quietly at the cap. */
    static final class Builder {
        final List<float[]> lines = new ArrayList<>();
        final List<Integer> lineColours = new ArrayList<>();
        final List<float[]> quads = new ArrayList<>();
        final List<Integer> quadColours = new ArrayList<>();

        void line(double ax, double ay, double az, double bx, double by, double bz, int argb) {
            if (lines.size() >= MAX_LINES) return;
            lines.add(new float[] {(float) ax, (float) ay, (float) az, (float) bx, (float) by, (float) bz});
            lineColours.add(argb);
        }

        void quad(double x0, double z0, double x1, double z1, double y, int argb) {
            if (quads.size() >= MAX_LINES) return;
            quads.add(new float[] {(float) x0, (float) y, (float) z0, (float) x1, (float) y, (float) z0,
                    (float) x1, (float) y, (float) z1, (float) x0, (float) y, (float) z1});
            quadColours.add(argb);
        }

        Shapes build(int layers) {
            float[] l = new float[lines.size() * 6];
            for (int i = 0; i < lines.size(); i++) System.arraycopy(lines.get(i), 0, l, i * 6, 6);
            float[] q = new float[quads.size() * 12];
            for (int i = 0; i < quads.size(); i++) System.arraycopy(quads.get(i), 0, q, i * 12, 12);
            return new Shapes(layers, l, lineColours.stream().mapToInt(Integer::intValue).toArray(), q,
                    quadColours.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private static final int[] KIND_COLOURS = {
        0xFFFFAA33, // tube
        0xFFFF5533, // canyon
        0xFFBBBB66, // crawl
        0xFFFFFF55, // shaft
        0xFFCC55FF, // bore
        0xFF55FF55, // chamber
        0xFFFF3399, // rift
        0xFFFF0066, // great rift
    };

    /** The passage network: every piece as a line along its axis, coloured by kind; entrances get a white post. */
    static void caves(AtlasCore core, BlockPos pos, Builder b) {
        List<double[]> pieces = new ArrayList<>();
        core.passages().piecesNear(pos.getX(), pos.getZ(), RADIUS, pieces);
        for (double[] s : pieces) {
            double midY = (s[1] + s[4]) / 2;
            if (Math.abs(midY - pos.getY()) > 160) continue; // the levels near you, not the whole column
            int kind = (int) s[6];
            int colour = kind >= 0 && kind < KIND_COLOURS.length ? KIND_COLOURS[kind] : 0xFFFFFFFF;
            if (kind == 5) { // a chamber (one point): a cross its size
                double r = Math.max(2, s[7]);
                b.line(s[0] - r, s[1], s[2], s[0] + r, s[1], s[2], colour);
                b.line(s[0], s[1], s[2] - r, s[0], s[1], s[2] + r, colour);
                b.line(s[0], s[1] - r * 0.5, s[2], s[0], s[1] + r * 0.5, s[2], colour);
            } else {
                b.line(s[0], s[1], s[2], s[3], s[4], s[5], colour);
            }
            if (s[8] > 0) { // may break the surface: an entrance
                double top = Math.max(s[1], s[4]);
                double ex = s[1] > s[4] ? s[0] : s[3], ez = s[1] > s[4] ? s[2] : s[5];
                b.line(ex, top, ez, ex, top + 12, ez, 0xFFFFFFFF);
            }
        }
        // Mega-caverns nearby: a ring at the floor and the roof of the column you stand in.
        double[] span = core.megaCaverns().span(pos.getX(), pos.getZ());
        if (span != null) {
            for (double y : span) ring(b, pos.getX() + 0.5, y, pos.getZ() + 0.5, 6, 0xFF66CCFF);
        }
    }

    /** The water: the water table as a sheet, rivers along their surface, falls as posts from lip to pool. */
    static void water(AtlasCore core, BlockPos pos, Builder b) {
        int step = 8, r = 96;
        int x0 = Math.floorDiv(pos.getX() - r, step) * step, z0 = Math.floorDiv(pos.getZ() - r, step) * step;
        for (int z = z0; z < pos.getZ() + r; z += step) {
            for (int x = x0; x < pos.getX() + r; x += step) {
                double t = core.waters().table(x + step / 2, z + step / 2);
                if (!Double.isNaN(t)) b.quad(x, z, x + step, z + step, t + 0.95, 0x503070FF);
            }
        }
        Drainage d = core.drainage();
        int ci = Drainage.cell(pos.getX()), cj = Drainage.cell(pos.getZ());
        int reach = (int) Math.ceil(RADIUS / Drainage.STEP) + 1;
        for (int j = cj - reach; j <= cj + reach; j++) {
            for (int i = ci - reach; i <= ci + reach; i++) {
                Drainage.Node n = d.node(i, j);
                if (!n.river()) continue;
                double[][] line = d.courseLine(n);
                for (int k = 0; k + 1 < line.length; k++) {
                    double[] p = line[k], q = line[k + 1];
                    if (Math.abs(p[0] - pos.getX()) > RADIUS + 16 || Math.abs(p[1] - pos.getZ()) > RADIUS + 16) continue;
                    b.line(p[0], p[2] + 0.2, p[1], q[0], q[2] + 0.2, q[1], 0xFF33E0FF);
                }
                double lake = d.lakeLevel(n);
                if (!Double.isNaN(lake)) ring(b, n.x(), lake + 0.2, n.z(), 10, 0xFF2080FF);
            }
        }
        // Karst cave water: a line along each watered piece at its water's level; the swallet and spring as tall posts.
        List<double[]> pieces = new ArrayList<>();
        core.passages().piecesNear(pos.getX(), pos.getZ(), RADIUS, pieces);
        for (double[] s : pieces) {
            if (s.length < 10 || Double.isNaN(s[9])) continue;
            b.line(s[0], s[9] + 0.1, s[2], s[3], s[9] + 0.1, s[5], 0xFF20D0A0);
        }
        com.seaweed.grandatlas.core.Karst.KarstSystem ks = core.karst().near(pos.getX(), pos.getZ());
        if (ks != null) {
            if (ks.swallet != null) b.line(ks.swallet[0], ks.swallet[2] - 30, ks.swallet[1], ks.swallet[0], ks.swallet[2] + 40, ks.swallet[1], 0xFFFFFFFF);
            if (ks.spring != null) b.line(ks.spring[0], ks.base - 20, ks.spring[1], ks.spring[0], ks.base + 40, ks.spring[1], 0xFF33E0FF);
        }
        List<Drainage.Fall> falls = new ArrayList<>();
        d.falls(pos.getX() - RADIUS, pos.getZ() - RADIUS, pos.getX() + RADIUS, pos.getZ() + RADIUS, falls);
        for (Drainage.Fall f : falls) b.line(f.lipX(), f.lip(), f.lipZ(), f.lipX(), f.level(), f.lipZ(), 0xFFFFFFFF);
    }

    /** Province edges: a line at the ground (and a post every 8 blocks) wherever the province changes, out to 384 blocks. */
    static void provinces(AtlasCore core, BlockPos pos, Builder b) {
        int step = 8, r = 3 * RADIUS; // provinces are big: look further out, more coarsely
        int x0 = Math.floorDiv(pos.getX() - r, step) * step, z0 = Math.floorDiv(pos.getZ() - r, step) * step;
        int n = 2 * r / step + 1;
        long[] id = new long[n * n];
        double[] h = new double[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                ColumnSample c = core.column(x0 + i * step, z0 + j * step);
                id[j * n + i] = ((long) c.provinceX() << 32) ^ (c.provinceZ() & 0xFFFFFFFFL);
                h[j * n + i] = Math.max(c.height(), com.seaweed.grandatlas.core.AtlasCore.SEA_LEVEL);
            }
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                long a = id[j * n + i];
                int colour = 0xFF000000 | (int) (com.seaweed.grandatlas.core.noise.GradientNoise.mix(a) & 0xFFFFFF) | 0x404040;
                double x = x0 + i * step, z = z0 + j * step, y = h[j * n + i] + 1.5;
                if (i + 1 < n && id[j * n + i + 1] != a) { // edge between this and the cell east: a north-south line
                    b.line(x + step, y, z, x + step, y, z + step, colour);
                    b.line(x + step, y, z, x + step, y + 12, z, colour);
                }
                if (j + 1 < n && id[(j + 1) * n + i] != a) {
                    b.line(x, y, z + step, x + step, y, z + step, colour);
                    b.line(x, y, z + step, x, y + 12, z + step, colour);
                }
            }
        }
    }

    private static void ring(Builder b, double cx, double y, double cz, double r, int colour) {
        int seg = 16;
        for (int k = 0; k < seg; k++) {
            double a0 = k * Math.PI * 2 / seg, a1 = (k + 1) * Math.PI * 2 / seg;
            b.line(cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, colour);
        }
    }

    /** For the command's reply. */
    public static String describe(int on) {
        if (on == 0) return "none";
        List<String> names = new ArrayList<>();
        for (int k = 0; k < LAYER_NAMES.length; k++) if ((on & (1 << k)) != 0) names.add(LAYER_NAMES[k]);
        return String.join(", ", names);
    }

    static String kind(int k) {
        return Passages.kindName(k);
    }
}
