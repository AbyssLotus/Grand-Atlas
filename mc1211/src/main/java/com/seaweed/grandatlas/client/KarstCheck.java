package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.Karst;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The dev "karstCheck" run (system property grandatlas.karstCheck): joins the test world, finds the
 * nearest karst system with a sinking stream (core/Karst), and screenshots its swallet from above and
 * down its shaft, the spring, the cave river from inside, the whole system from above with the caves
 * layer, and the F3 lines in the cave; then quits. Nothing is typed into the window. Screenshots land
 * in run/screenshots as karst_*.png. Single player only; it ops the player in that world.
 */
public final class KarstCheck {
    private KarstCheck() {}

    private static int t, waited;
    private static volatile double[] spots; // swallet x z level, spring x z level, cave x y z, centre x z

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        // Hold before the first shot until the server has found the system (it may be busy
        // generating the area for a minute first), up to five minutes.
        if (t == 139 && spots == null && waited++ < 6000) return;
        t++;
        double[] s = spots;
        switch (t) {
            case 100 -> {
                server("op");
                server("gamemode spectator @s");
                server("effect give @s night_vision infinite 0 true");
                server("time set noon");
                server("gamerule doDaylightCycle false");
                find();
            }
            case 140 -> {
                if (s == null) {
                    GrandAtlas.LOG.error("[karst check] no karst system with a sinking stream found");
                    mc.stop();
                    return;
                }
                server("grandatlas show water");
                tp(s[0] + 6, s[2] + 22, s[1] + 6, 135, 55);
            }
            case 300 -> shot("karst_swallet");
            case 310 -> tp(s[0], s[2] - 3, s[1], 0, 89);
            case 420 -> {
                shot("karst_shaft");
                dump("swallet", (int) s[0], (int) s[2], (int) s[1], 12, 16, 6);
            }
            case 430 -> tp(s[3] - 14, s[5] + 24, s[4] - 14, -45, 50);
            case 590 -> shot("karst_spring");
            case 600 -> {
                server("grandatlas show off");
                tp(s[6], s[7] + 1.6, s[8], 30, 10);
            }
            case 760 -> {
                shot("karst_cave");
                dump("cave", (int) s[6], (int) s[7], (int) s[8], 16, 8, 20);
            }
            case 770 -> mc.getDebugOverlay().toggleOverlay();
            case 840 -> shot("karst_f3");
            case 850 -> {
                mc.getDebugOverlay().toggleOverlay();
                server("grandatlas show caves");
                tp(s[9], s[2] + 120, s[10], 0, 89);
            }
            case 1010 -> shot("karst_caves");
            case 1020 -> tp(s[9] - 110, s[2] + 45, s[10] - 110, -45, 25);
            case 1150 -> shot("karst_caves_side");
            case 1170 -> mc.stop();
            default -> { }
        }
    }

    /** On the server thread: the swallet, the spring, a spot in the cave river, the system's middle. */
    private static void find() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(server.overworld());
            if (core == null) return;
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            Karst.KarstSystem ks = core.karst().nearestSink(p.getX(), p.getZ(), 24);
            if (ks == null) return;
            List<double[]> pieces = new ArrayList<>();
            core.passages().piecesNear(ks.cx, ks.cz, ks.radius, pieces);
            double[] cave = null;
            double best = -1;
            for (double[] g : pieces) {
                if (g.length < 11 || g[10] != 0 || Double.isNaN(g[9]) || g[7] < best) continue;
                // A wide piece of the cave river with air over its water, away from the spring.
                if (Math.hypot(g[0] - ks.spring[0], g[2] - ks.spring[1]) < 60) continue;
                if (g[1] - g[9] < 0.5) continue;
                best = g[7];
                cave = g;
            }
            if (cave == null) return;
            spots = new double[] {ks.swallet[0], ks.swallet[1], ks.swallet[2], ks.spring[0], ks.spring[1], ks.base,
                (cave[0] + cave[3]) / 2, cave[9], (cave[2] + cave[5]) / 2, ks.cx, ks.cz};
            GrandAtlas.LOG.info("[karst check] system {} {}: swallet {} {} (y {}), spring {} {} (y {}), cave river at {} {} {}",
                    (int) ks.cx, (int) ks.cz, (int) ks.swallet[0], (int) ks.swallet[1], ks.swallet[2], (int) ks.spring[0], (int) ks.spring[1], ks.base,
                    (int) spots[6], (int) spots[7], (int) spots[8]);
        });
    }

    /**
     * On the server thread: the blocks round (x, y, z) as text slices, top layer first, into
     * grandatlas/karst_dump_NAME.txt. . air, # stone, g grass, d dirt/mud, ~ water source, v falling
     * or flowing water, R river water, L leaves or logs, ? anything else.
     */
    private static void dump(String name, int x, int y, int z, int r, int down, int up) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.overworld();
            StringBuilder b = new StringBuilder();
            b.append(name).append(" centre ").append(x).append(' ').append(y).append(' ').append(z).append(", x across, z down\n");
            net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int yy = y + up; yy >= y - down; yy--) {
                b.append("y ").append(yy).append('\n');
                for (int zz = z - r; zz <= z + r; zz++) {
                    for (int xx = x - r; xx <= x + r; xx++) {
                        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos.set(xx, yy, zz));
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                        char ch;
                        if (st.isAir()) ch = '.';
                        else if (id.contains("river_water")) ch = 'R';
                        else if (st.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) ch = st.getFluidState().isSource() ? '~' : 'v';
                        else if (id.contains("leaves") || id.contains("log")) ch = 'L';
                        else if (id.contains("grass_block") || id.equals("podzol") || id.equals("mycelium")) ch = 'g';
                        else if (id.contains("dirt") || id.contains("mud") || id.contains("clay")) ch = 'd';
                        else if (id.contains("stone") || id.contains("deepslate") || id.contains("andesite") || id.contains("diorite") || id.contains("granite") || id.contains("tuff") || id.contains("calcite")) ch = '#';
                        else ch = '?';
                        if (xx == x && zz == z) ch = Character.toUpperCase(ch) == ch && ch != '.' ? ch : (ch == '.' ? '+' : Character.toUpperCase(ch));
                        b.append(ch);
                    }
                    b.append('\n');
                }
            }
            try {
                java.nio.file.Path out = server.getServerDirectory().resolve("grandatlas").resolve("karst_dump_" + name + ".txt");
                java.nio.file.Files.createDirectories(out.getParent());
                java.nio.file.Files.writeString(out, b);
                GrandAtlas.LOG.info("[karst check] dumped {}", out);
            } catch (java.io.IOException e) {
                GrandAtlas.LOG.error("[karst check] dump failed", e);
            }
        });
    }

    private static void tp(double x, double y, double z, float yaw, float pitch) {
        server(String.format(java.util.Locale.ROOT, "tp @s %.1f %.1f %.1f %.0f %.0f", x, y, z, yaw, pitch));
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrandAtlas.LOG.info("[karst check] {}", msg.getString()));
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
            GrandAtlas.LOG.info("[karst check] /{}", command);
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
