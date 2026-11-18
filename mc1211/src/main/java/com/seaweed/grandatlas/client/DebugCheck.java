package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * The dev "debugCheck" run (system property grandatlas.debugCheck): joins the test world, then
 * drives the C0 debug tools itself and takes a screenshot of each (F3 lines, the caves, water and
 * provinces layers, /grandatlas where), then quits. Nothing is typed into the window. Screenshots
 * land in run/screenshots as ga_*.png. Single player only; it ops the player in that world.
 */
public final class DebugCheck {
    private DebugCheck() {}

    private static int t;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        t++;
        switch (t) {
            case 100 -> {
                server("op");
                server("gamemode spectator @s");
                server("tp @s 4573 20 3347 30 20");
            }
            case 200 -> mc.getDebugOverlay().toggleOverlay();
            case 300 -> shot("ga_f3");
            case 310 -> {
                mc.getDebugOverlay().toggleOverlay();
                server("grandatlas show caves");
            }
            case 420 -> shot("ga_caves");
            case 430 -> {
                server("grandatlas show off");
                server("grandatlas show water");
                server("tp @s 4573 185 3347 0 75");
            }
            case 560 -> shot("ga_water");
            case 570 -> {
                server("grandatlas show off");
                server("grandatlas show provinces");
                server("tp @s 4573 185 3347 0 15");
            }
            case 640 -> shot("ga_provinces_s");
            case 645 -> server("tp @s 4573 185 3347 90 15");
            case 665 -> shot("ga_provinces_w");
            case 670 -> server("tp @s 4573 185 3347 180 15");
            case 690 -> shot("ga_provinces_n");
            case 695 -> server("tp @s 4573 185 3347 270 15");
            case 705 -> shot("ga_provinces_e");
            case 712 -> {
                server("grandatlas show off");
                server("tp @s 4573 20 3347 30 20");
            }
            case 760 -> server("grandatlas where");
            case 800 -> shot("ga_where");
            case 820 -> mc.stop();
            default -> { }
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrandAtlas.LOG.info("[debug check] {}", msg.getString()));
    }

    /** Runs a command as the player on the integrated server, at full permission ("op" ops them). */
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
            GrandAtlas.LOG.info("[debug check] /{}", command);
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
