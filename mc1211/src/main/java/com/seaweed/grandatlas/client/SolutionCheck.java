package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The dev "solutionCheck" run (system property grandatlas.solutionCheck): joins the test world, goes to
 * fresh ground (grandatlas.solutionAt, "x,z", default 4573,3347), finds a big room, a wide gallery and
 * an entrance of the solution caves (core/Solution) from the core, and screenshots each, the F3 lines,
 * and the caves layer from above; then quits. Nothing is typed into the window. Screenshots land in
 * run/screenshots as solution_*.png. Single player only; it ops the player in that world.
 */
public final class SolutionCheck {
    private SolutionCheck() {}

    private static int t, waited;
    private static volatile double[] spots; // room x y z r vr, gallery ax ay az bx bz r, mouth x y z ux uz, centre x z

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (t == 139 && spots == null && waited++ < 6000) return;
        t++;
        double[] s = spots;
        switch (t) {
            case 60 -> {
                server("op");
                server("gamemode spectator @s");
                server("effect give @s night_vision infinite 0 true");
                server("time set noon");
                server("gamerule doDaylightCycle false");
                String[] at = System.getProperty("grandatlas.solutionAt", "4573,3347").split(",");
                server("tp @s " + at[0] + " 220 " + at[1]);
            }
            case 100 -> find();
            case 140 -> {
                if (s == null) {
                    GrandAtlas.LOG.error("[solution check] found nothing to look at");
                    mc.stop();
                    return;
                }
                tp(s[0] - s[3] * 0.7, s[1] + s[3] * s[4] * 0.4, s[2], -90, 15);
            }
            case 700 -> shot("solution_room");
            case 710 -> mc.getDebugOverlay().toggleOverlay();
            case 760 -> shot("solution_f3");
            case 770 -> {
                mc.getDebugOverlay().toggleOverlay();
                float yaw = (float) Math.toDegrees(Math.atan2(s[10] - s[7], s[9] - s[5])) - 90;
                tp(s[5], s[6] + 0.5, s[7], yaw, 5);
            }
            case 1300 -> shot("solution_gallery");
            case 1310 -> {
                if (s[12] == s[12]) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-s[16], -s[15])) - 90;
                    tp(s[12] + s[15] * 12, s[13] + 4, s[14] + s[16] * 12, yaw, 15);
                }
            }
            case 1600 -> shot("solution_mouth");
            case 1610 -> {
                server("grandatlas show caves");
                tp(s[17] - 90, s[1] + 60, s[18] - 90, -45, 20);
            }
            case 2100 -> shot("solution_caves_side");
            case 2120 -> mc.stop();
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
            List<double[]> pieces = new ArrayList<>();
            core.passages().piecesNear(cx, cz, 120, pieces);
            double[] room = null, gallery = null, mouth = null;
            for (double[] g : pieces) {
                if (g.length < 13 || g[11] != 2) continue; // solution caves only
                int kind = (int) g[6];
                if (kind == 5 && g[1] < core.column((int) g[0], (int) g[2]).height() - 25 && (room == null || g[7] > room[7])) room = g;
                if (kind == 0 && g[8] == 0 && g[12] < 0.6 && (gallery == null || g[7] > gallery[7])) gallery = g;
                if (g[8] == 1 && (mouth == null || g[4] > mouth[4])) mouth = g;
            }
            if (room == null || gallery == null) return;
            double mx = Double.NaN, my = 0, mz = 0, ux = 1, uz = 0;
            if (mouth != null) {
                mx = mouth[3];
                my = mouth[4];
                mz = mouth[5];
                double l = Math.max(0.01, Math.hypot(mouth[3] - mouth[0], mouth[5] - mouth[2]));
                ux = (mouth[3] - mouth[0]) / l;
                uz = (mouth[5] - mouth[2]) / l;
            }
            spots = new double[] {room[0], room[1], room[2], room[7], room[12],
                gallery[0], gallery[1], gallery[2], 0, gallery[3], gallery[5], gallery[7],
                mx, my, mz, ux, uz, cx, cz};
            spots[8] = gallery[2];
            GrandAtlas.LOG.info("[solution check] room at {} {} {} (r {}), gallery at {} {} {} (r {}), mouth at {} {} {}",
                    (int) room[0], (int) room[1], (int) room[2], room[7], (int) gallery[0], (int) gallery[1], (int) gallery[2], gallery[7],
                    (int) mx, (int) my, (int) mz);
        });
    }

    private static void tp(double x, double y, double z, float yaw, float pitch) {
        server(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f %.0f %.0f", x, y, z, yaw, pitch));
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrandAtlas.LOG.info("[solution check] {}", msg.getString()));
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
            GrandAtlas.LOG.info("[solution check] /{}", command);
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
