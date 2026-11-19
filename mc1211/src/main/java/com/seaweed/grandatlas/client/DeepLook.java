package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.Rooms;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The dev "deepLook" run (system property grandatlas.deepLook): joins the test world, finds the
 * nearest spiral gallery (R7) and the nearest room holding a found place (R6: a colossus, a lost
 * expedition, a fungal grove, a root cathedral) from the core, and screenshots them with night vision
 * (the deep's darkness would black them out). Then quits. Screenshots land in run/screenshots as
 * deep_*.png. Nothing is typed into the window. Single player only; it ops the player in that world.
 */
public final class DeepLook {
    private DeepLook() {}

    private static int t, waited;
    private static volatile double[] spiral; // x top z bottom radius
    private static volatile double[] story;  // focal x y z, room yaw, room width
    private static volatile String storyKind = "";
    private static volatile double[] rock;    // an upper cave in chalk or clay country: x y z, heading
    private static volatile String rockKind = "";

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (t == 139 && spiral == null && story == null && waited++ < 6000) return;
        t++;
        double[] s = spiral, f = story;
        switch (t) {
            case 60 -> {
                server("op");
                server("gamemode spectator @s");
                server("effect give @s night_vision infinite 0 true");
                server("time set noon");
                server("gamerule doDaylightCycle false");
                String[] at = System.getProperty("grandatlas.deepLookAt", "70000,52000").split(",");
                server("tp @s " + at[0] + " 100 " + at[1]);
            }
            case 100 -> find();
            case 140 -> {
                if (s == null) return;
                // in the void, half way down, looking out across the drum at the ledges spiralling down
                tp(s[0] - s[4] * 0.5, (s[1] + s[3]) / 2, s[2], -90, 18);
            }
            case 900 -> { if (s != null) shot("deep_spiral_across"); }
            case 910 -> { if (s != null) tp(s[0], s[1] + 3, s[2] - s[4] * 0.3, 0, 75); } // from the top, looking down the void
            case 1400 -> { if (s != null) shot("deep_spiral_down"); }
            case 1410 -> { if (s != null) tp(s[0] + s[4] + 0.5, s[1] + 1.6, s[2] + 3, 0, 10); } // on the ledge at the top
            case 1900 -> { if (s != null) shot("deep_spiral_ledge"); }
            case 1910 -> {
                if (f == null) return;
                double ux = Math.cos(f[3]), uz = Math.sin(f[3]);
                double back = Math.min(16, f[4] * 0.45);
                float yaw = (float) Math.toDegrees(Math.atan2(uz, ux)) - 90;
                tp(f[0] - ux * back, f[1] + 5, f[2] - uz * back, yaw, 18);
            }
            case 2500 -> { if (f != null) shot("deep_story_" + storyKind.replace(' ', '_')); }
            case 2510 -> { if (f != null) tp(f[0] + 6, f[1] + 9, f[2] + 6, 135, 40); }
            case 2900 -> { if (f != null) shot("deep_story_" + storyKind.replace(' ', '_') + "_2"); }
            case 2910 -> {
                double[] k = rock;
                if (k == null) return;
                tp(k[0], k[1], k[2], (float) Math.toDegrees(k[3]) - 90, 10);
            }
            case 3400 -> { if (rock != null) shot("deep_rock_" + rockKind); }
            case 3420 -> mc.stop();
            default -> { }
        }
    }

    private static void find() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(server.overworld());
            if (core == null || server.getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            double cx = p.getX(), cz = p.getZ();
            List<double[]> sps = new ArrayList<>();
            core.deep().spirals(cx - 3000, cz - 3000, cx + 3000, cz + 3000, sps);
            sps.sort((a, b) -> Double.compare(Math.hypot(a[0] - cx, a[2] - cz), Math.hypot(b[0] - cx, b[2] - cz)));
            if (!sps.isEmpty()) spiral = sps.get(0);
            List<Rooms.Room> rooms = new ArrayList<>();
            core.passages().rooms(cx - 700, cz - 700, cx + 700, cz + 700, true, rooms);
            Rooms.Room best = null;
            String bestKind = "";
            double bd = Double.MAX_VALUE;
            for (Rooms.Room rm : rooms) {
                if (rm.floorY > -70) continue; // in the deep, where the moods are
                String k = com.seaweed.grandatlas.world.DeepDetail.storyOf(core, rm);
                if (k == null) continue;
                double d = Math.hypot(rm.cx - cx, rm.cz - cz) + (k.equals("colossus") ? 0 : 400); // a colossus if there's one
                if (d < bd) {
                    bd = d;
                    best = rm;
                    bestKind = k;
                }
            }
            // an upper cave in chalk or clay country: a passage piece well under the ground there
            outer:
            for (int ring = 0; ring < 24; ring++) {
                for (int k = 0; k < 8 * Math.max(1, ring); k++) {
                    double a = k * 2 * Math.PI / (8 * Math.max(1, ring)), rx = cx + Math.cos(a) * ring * 64, rz = cz + Math.sin(a) * ring * 64;
                    int prov = core.passages().rockProvince((int) rx, (int) rz);
                    if (prov != com.seaweed.grandatlas.core.Passages.ROCK_CHALK && prov != com.seaweed.grandatlas.core.Passages.ROCK_CLAY) continue;
                    List<double[]> pieces = new ArrayList<>();
                    core.passages().piecesNear(rx, rz, 40, pieces);
                    for (double[] g : pieces) {
                        double mx = (g[0] + g[3]) / 2, my = (g[1] + g[4]) / 2, mz = (g[2] + g[5]) / 2;
                        double ground = core.column((int) mx, (int) mz).height();
                        if (g[6] > 1 || g[7] < 3 || my < -60 || my > ground - 14) continue; // a tube or canyon, roomy, above the deep, under the ground
                        if (core.passages().rockProvince((int) mx, (int) mz) != prov) continue;
                        rock = new double[] {mx, my, mz, Math.atan2(g[5] - g[2], g[3] - g[0])};
                        rockKind = prov == com.seaweed.grandatlas.core.Passages.ROCK_CHALK ? "chalk" : "clay";
                        break outer;
                    }
                }
            }
            if (best != null) {
                double[] fo = best.focal();
                story = new double[] {fo[0], fo[1], fo[2], best.yaw(), best.w};
                storyKind = bestKind;
            }
            GrandAtlas.LOG.info("[deep look] {} spirals, nearest {}; {} rooms, story {} at {}", sps.size(),
                    spiral == null ? "none" : (int) spiral[0] + " " + (int) spiral[1] + " " + (int) spiral[2], rooms.size(), storyKind,
                    story == null ? "none" : (int) story[0] + " " + (int) story[1] + " " + (int) story[2]);
        });
    }

    private static void tp(double x, double y, double z, float yaw, float pitch) {
        server(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f %.0f %.0f", x, y, z, yaw, pitch));
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrandAtlas.LOG.info("[deep look] {}", msg.getString()));
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
            GrandAtlas.LOG.info("[deep look] /{}", command);
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
