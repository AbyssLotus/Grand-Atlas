package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.world.CoreHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The dev "surfaceLook" run (system property grandatlas.surfaceLook): joins the test world, finds
 * kinds of surface from the terrain core (a wet rock face, a cold one, a dry one, a stony shore:
 * world/RockFaceLife), and screenshots each from a little way downhill, then quits. Screenshots land
 * in run/screenshots as surface_*.png. Nothing is typed into the window; it ops the player.
 */
public final class SurfaceLook {
    private SurfaceLook() {}

    /** name, x, y, z, yaw, pitch, filled on the server thread. */
    private static final List<Object[]> VIEWS = new ArrayList<>();
    private static volatile boolean found;
    private static int t;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && mc.screen != null) {
            for (net.minecraft.client.gui.components.events.GuiEventListener c : mc.screen.children()) {
                if (c instanceof net.minecraft.client.gui.components.Button b) {
                    String m = b.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
                    if (m.equals("proceed") || m.equals("load") || m.equals("yes") || m.contains("i know what")) {
                        b.onPress();
                        return;
                    }
                }
            }
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        t++;
        if (t == 60) {
            server("op");
            server("gamemode spectator @s");
            server("effect give @s night_vision infinite 0 true");
            MinecraftServer s = mc.getSingleplayerServer();
            s.execute(() -> find(s));
        }
        if (!found || t < 100) return;
        int first = 100, every = 400;
        int k = (t - first) / every, phase = (t - first) % every;
        if (k >= VIEWS.size()) {
            if (phase == 20) mc.stop();
            return;
        }
        Object[] v = VIEWS.get(k);
        if (phase == 0) {
            server(String.format(java.util.Locale.ROOT, "execute in minecraft:overworld run tp @s %.1f %.1f %.1f %.0f %.0f",
                    (Double) v[1], (Double) v[2], (Double) v[3], (Float) v[4], (Float) v[5]));
        }
        if (phase == every - 20) {
            Screenshot.grab(mc.gameDirectory, "surface_" + v[0] + ".png", mc.getMainRenderTarget(),
                    msg -> GrandAtlas.LOG.info("[surface look] {}", msg.getString()));
        }
    }

    private static double slope(AtlasCore core, int x, int z) {
        return Math.max(Math.abs(core.column(x + 1, z).height() - core.column(x - 1, z).height()),
                Math.abs(core.column(x, z + 1).height() - core.column(x, z - 1).height())) / 2;
    }

    private static void find(MinecraftServer server) {
        AtlasCore core = CoreHandle.coreOf(server.overworld());
        String[] kinds = {"wet_rock", "cold_rock", "dry_rock", "stony_shore", "wet_rock_2"};
        Object[][] best = new Object[kinds.length][];
        if (core != null) {
            outer:
            for (int r = 0; r <= 2400; r += 16) {
                for (int i = -r; i <= r; i += 16) {
                    for (int[] p : new int[][] {{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                        int x = p[0], z = p[1];
                        ColumnSample c = core.column(x, z);
                        if (!Double.isNaN(c.water()) && c.water() > c.height()) continue;
                        int kind = -1;
                        if (c.biome() == BiomeKind.STONY_SHORE) kind = 3;
                        else {
                            double sl = slope(core, x, z);
                            if (sl < 2.2) continue;
                            BiomeKind b = c.biome();
                            if ((b == BiomeKind.JUNGLE || b == BiomeKind.SPARSE_JUNGLE || b == BiomeKind.DARK_FOREST || b == BiomeKind.SWAMP) && c.height() < 170) kind = best[0] == null ? 0 : 4;
                            else if (b == BiomeKind.TAIGA || b == BiomeKind.GROVE || b == BiomeKind.SNOWY_SLOPES || b == BiomeKind.STONY_PEAKS || b == BiomeKind.OLD_GROWTH_PINE_TAIGA) kind = 1;
                            else if (b == BiomeKind.BADLANDS || b == BiomeKind.ERODED_BADLANDS || b == BiomeKind.SAVANNA_PLATEAU || b == BiomeKind.WINDSWEPT_SAVANNA || b == BiomeKind.DESERT) kind = 2;
                        }
                        if (kind < 0 || best[kind] != null) continue;
                        if (kind == 4 && Math.hypot(x - (Double) best[0][1], z - (Double) best[0][3]) < 400) continue;
                        // stand downhill of it, a little above, looking back up at it
                        double gx = core.column(x + 2, z).height() - core.column(x - 2, z).height();
                        double gz = core.column(x, z + 2).height() - core.column(x, z - 2).height();
                        double len = Math.max(1e-6, Math.hypot(gx, gz));
                        double ox = -gx / len * 22, oz = -gz / len * 22;
                        if (kind == 3) {
                            ox = 10;
                            oz = 10;
                        }
                        double vx = x + ox, vz = z + oz;
                        double vy = Math.max(core.column((int) vx, (int) vz).height(), Double.isNaN(core.column((int) vx, (int) vz).water()) ? 0 : core.column((int) vx, (int) vz).water()) + (kind == 3 ? 8 : 12);
                        float yaw = (float) Math.toDegrees(Math.atan2(-(x - vx), z - vz));
                        float pitch = (float) -Math.toDegrees(Math.atan2(c.height() + 2 - vy, Math.hypot(x - vx, z - vz)));
                        best[kind] = new Object[] {kinds[kind], vx, vy, vz, yaw, pitch};
                        GrandAtlas.LOG.info("[surface look] {} at {} {} (temp {}, humidity {})", kinds[kind], x, z, c.temperature(), c.humidity());
                        boolean all = true;
                        for (Object[] b : best) all &= b != null;
                        if (all) break outer;
                    }
                }
            }
        }
        if (!java.util.Arrays.stream(best).allMatch(java.util.Objects::isNull)) {
            Object[] f = java.util.Arrays.stream(best).filter(java.util.Objects::nonNull).findFirst().get();
            VIEWS.add(new Object[] {"warmup", f[1], f[2], f[3], f[4], f[5]});
        }
        for (Object[] b : best) if (b != null) VIEWS.add(b);
        found = true;
    }

    private static void server(String command) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            if (command.equals("op")) {
                server.getPlayerList().op(p.getGameProfile());
                return;
            }
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
