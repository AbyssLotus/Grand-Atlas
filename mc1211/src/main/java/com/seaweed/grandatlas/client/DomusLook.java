package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The dev "domusLook" run (system property grandatlas.domusLook): joins the test world, goes to
 * Domus Mortus and screenshots its places from fixed viewpoints with night vision (the realm is dark
 * by design), then quits. Screenshots land in run/screenshots as domus_*.png. Nothing is typed into
 * the window; it ops the player in that world.
 */
public final class DomusLook {
    private DomusLook() {}

    /** name, x, y, z, yaw, pitch (yaw: 0 south, 90 west, 180 north, -90 east). */
    private static final Object[][] VIEWS = {
            {"spawn_cave", -1072.5, 64.0, 178.5, -110f, 8f},
            {"cave_mouth", -955.5, 70.0, 172.5, 90f, 4f},
            {"acheron_to_styx", -905.5, 82.0, 120.5, 30f, 22f},
            {"asphodel", 150.5, 112.0, 350.5, 150f, 14f},
            {"platform_groves", -120.5, 175.0, -290.5, 125f, 22f},
            {"tartarus", 500.5, 118.0, 30.5, 180f, 52f},
            {"elysium", 250.5, 108.0, -330.5, 180f, 14f},
            {"cocytus", 430.5, 92.0, 560.5, 0f, 18f},
            {"peaks", 0.5, 150.0, -640.5, 180f, 8f},
            {"lethe_cypress", -150.5, 82.0, 60.5, 54f, 6f}};
    private static int t;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // A world with a mod's datapack dimension opens with a "proceed / load" prompt; in this throwaway
        // dev run, press it (it waited ten minutes for a click otherwise).
        if (mc.level == null && mc.screen != null) {
            for (net.minecraft.client.gui.components.events.GuiEventListener c : mc.screen.children()) {
                if (c instanceof net.minecraft.client.gui.components.Button b) {
                    String m = b.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
                    if (m.equals("proceed") || m.equals("load") || m.equals("yes") || m.contains("i know what")) {
                        GrandAtlas.LOG.info("[domus look] pressing \"{}\" on {}", b.getMessage().getString(), mc.screen.getClass().getSimpleName());
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
        }
        int first = 100, every = 420;
        int k = (t - first) / every, phase = (t - first) % every;
        if (t < first) return;
        if (k >= VIEWS.length) {
            if (phase == 20) mc.stop();
            return;
        }
        Object[] v = VIEWS[k];
        if (phase == 0) {
            server(String.format(java.util.Locale.ROOT, "execute in grandatlas:domus_mortus run tp @s %.1f %.1f %.1f %.0f %.0f",
                    (Double) v[1], (Double) v[2], (Double) v[3], (Float) v[4], (Float) v[5]));
        }
        if (phase == every - 20) {
            Screenshot.grab(mc.gameDirectory, "domus_" + v[0] + ".png", mc.getMainRenderTarget(),
                    msg -> GrandAtlas.LOG.info("[domus look] {}", msg.getString()));
        }
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
            GrandAtlas.LOG.info("[domus look] /{}", command);
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
