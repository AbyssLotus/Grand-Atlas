package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.macro.GreatSinkhole;
import com.seaweed.grandatlas.core.macro.Macro;
import com.seaweed.grandatlas.core.macro.MacroSite;
import com.seaweed.grandatlas.core.macro.SpireCrown;
import com.seaweed.grandatlas.world.CoreHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The dev "macroLook" run (system property grandatlas.macroLook): joins the surface-test world, finds
 * the nearest site of each macro kind from the M2 expansion (crater, monolith, fault block, shattered
 * plateau, great sinkhole, horn, and the fantasy spire crown, coiled peak, sundered land, sky pillar), and screenshots each from a viewpoint picked for its kind, then
 * quits. Screenshots land in run/screenshots as macro_*.png. Nothing is typed into the window.
 */
public final class MacroLook {
    private MacroLook() {}

    private static final String[] KINDS = {"crater", "monolith", "fault_block", "shattered_plateau", "great_sinkhole", "horn",
            "spire_crown", "coiled_peak", "sundered_land", "sky_pillar"};
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
            server("weather clear 1000000");
            server("time set noon");
            server("gamerule doDaylightCycle false");
            server("gamerule doWeatherCycle false");
            MinecraftServer s = mc.getSingleplayerServer();
            s.execute(() -> find(s));
        }
        if (!found || t < 100) return;
        int first = 100, every = 3000;
        int k = (t - first) / every, phase = (t - first) % every;
        if (k >= VIEWS.size()) {
            if (phase == 20) mc.stop();
            return;
        }
        Object[] v = VIEWS.get(k);
        if (phase == 0 || phase == 1200) {
            // Twice: the second once the chunks round the spot have generated.
            server(String.format(java.util.Locale.ROOT, "execute in minecraft:overworld run tp @s %.1f %.1f %.1f %.1f %.1f",
                    (Double) v[1], (Double) v[2], (Double) v[3], (Float) v[4], (Float) v[5]));
        }
        if (phase == every - 20) {
            Screenshot.grab(mc.gameDirectory, "macro_" + v[0] + ".png", mc.getMainRenderTarget(),
                    msg -> GrandAtlas.LOG.info("[macro look] {}", msg.getString()));
        }
    }

    private static double ground(AtlasCore core, double x, double z) {
        var c = core.column((int) Math.floor(x), (int) Math.floor(z));
        return Double.isNaN(c.water()) ? c.height() : Math.max(c.height(), c.water());
    }

    /** A view from (vx, vz), {@code up} blocks over the ground there, looking at (tx, ty, tz). */
    private static Object[] view(String name, AtlasCore core, double vx, double vz, double up, double tx, double ty, double tz) {
        // up == 0: an aerial view, 140 over the higher of the ground here and the target's ground.
        double vy = Math.min(1000, up > 0 ? ground(core, vx, vz) + up : Math.max(ground(core, vx, vz), ground(core, tx, tz)) + 140);
        float yaw = (float) Math.toDegrees(Math.atan2(-(tx - vx), tz - vz));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - vy, Math.hypot(tx - vx, tz - vz)));
        return new Object[] {name, vx, vy, vz, yaw, pitch};
    }

    private static void find(MinecraftServer server) {
        AtlasCore core = CoreHandle.coreOf(server.overworld());
        if (core != null) {
            for (String id : KINDS) {
                Macro kind = core.macro(id);
                MacroSite s = kind == null ? null : core.findMacro(kind, 0, 0);
                if (s == null) {
                    GrandAtlas.LOG.info("[macro look] no {} found", id);
                    continue;
                }
                double cx = s.x(), cz = s.z(), r = s.radius();
                double a = 0.7; // the side the camera stands on
                double ux = Math.cos(a), uz = Math.sin(a);
                Object[] v = switch (id) {
                    // On the rim, looking down the terraces at the floor.
                    case "crater" -> view(id, core, cx + ux * r * 1.02, cz + uz * r * 1.02, 30, cx + ux * r * 0.7, ground(core, cx + ux * r * 0.7, cz + uz * r * 0.7), cz + uz * r * 0.7);
                    // Out on the plain, a few hundred blocks off, looking at the rock.
                    case "monolith" -> view(id, core, cx + ux * r * 0.55, cz + uz * r * 0.55, 50, cx, ground(core, cx + ux * r * 0.55, cz + uz * r * 0.55) + 120, cz);
                    // Off the escarpment's foot, looking back up at it.
                    case "fault_block" -> {
                        double fa = s.param(2) * Math.PI * 2, fx = Math.cos(fa), fz = Math.sin(fa);
                        yield view(id, core, cx + fx * r * 1.12, cz + fz * r * 1.12, 30, cx + fx * r * 0.9,
                                ground(core, cx + fx * r * 0.9, cz + fz * r * 0.9) - 60, cz + fz * r * 0.9);
                    }
                    // High over the plateau, looking down across the canyons.
                    case "shattered_plateau" -> view(id, core, cx + ux * r * 0.7, cz + uz * r * 0.7, 120, cx + ux * r * 0.5, ground(core, cx + ux * r * 0.5, cz + uz * r * 0.5) - 80, cz + uz * r * 0.5);
                    // On the pit's rim, looking down at its floor.
                    case "great_sinkhole" -> {
                        double[] p = GreatSinkhole.pit(s);
                        yield view(id, core, p[0] + ux * (p[2] - 60), p[1] + uz * (p[2] - 60), 0, p[0] + ux * (p[2] - 200), ground(core, p[0], p[1]), p[1] + uz * (p[2] - 200));
                    }
                    // From the air over the horn's face, looking down at its summit and aretes.
                    case "horn" -> view(id, core, cx + ux * 230, cz + uz * 230, 0, cx, ground(core, cx, cz) - 120, cz);
                    // In the vale, looking out at the near side of the ring of spires.
                    case "spire_crown" -> {
                        // 150 blocks inward of the first spire, looking up at it and its neighbours.
                        double[] sp = SpireCrown.spire(s, 0);
                        double ix = cx - sp[0], iz = cz - sp[1], il = Math.max(1, Math.hypot(ix, iz));
                        double vx = sp[0] + ix / il * 150, vz = sp[1] + iz / il * 150;
                        yield view(id, core, vx, vz, 25, sp[0], ground(core, vx, vz) + 110, sp[1]);
                    }
                    // From the air, looking down on the coil.
                    case "coiled_peak" -> view(id, core, cx + ux * 260, cz + uz * 260, 0, cx, ground(core, cx, cz) - 200, cz);
                    // Over the land, looking down the biggest chasm.
                    case "sundered_land" -> {
                        double ca = s.param(10) * Math.PI, off = (s.param(20) - 0.5) * 0.5 * r;
                        double ox = cx - Math.sin(ca) * off, oz = cz + Math.cos(ca) * off;
                        yield view(id, core, ox - Math.cos(ca) * 200, oz - Math.sin(ca) * 200, 80, ox, ground(core, ox, oz) - 60, oz);
                    }
                    // From the woods, looking up the pillar.
                    default -> view(id, core, cx + ux * 240, cz + uz * 240, 70, cx, 420, cz);
                };
                GrandAtlas.LOG.info("[macro look] {} at {} {} (radius {}), view from {} {} {}", id, (int) cx, (int) cz, (int) r,
                        Math.round((Double) v[1]), Math.round((Double) v[2]), Math.round((Double) v[3]));
                VIEWS.add(v);
            }
        }
        if (!VIEWS.isEmpty()) {
            Object[] f = VIEWS.get(0);
            VIEWS.add(0, new Object[] {"warmup", f[1], f[2], f[3], f[4], f[5]});
        }
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
