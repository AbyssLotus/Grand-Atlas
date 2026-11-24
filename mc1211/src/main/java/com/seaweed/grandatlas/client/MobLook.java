package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.entity.CrystalEel;
import com.seaweed.grandatlas.entity.ModEntities;
import com.seaweed.grandatlas.entity.PottedMonster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * The dev "mobLook" run (system property grandatlas.mobLook): joins the test world, builds a stage
 * in the air above the player (a stone floor, a water tank), puts a Potted Monster and a Crystal Eel
 * on it with no AI, and screenshots them: idle from the front and the side, the Potted Monster's
 * attack (to check it lunges forward), the eel swimming, and the eel's death. Screenshots land in
 * run/screenshots as mob_*.png; then it quits. Nothing is typed into the window.
 */
public final class MobLook {
    private MobLook() {}

    private static int t;
    private static BlockPos base;
    private static UUID potted, eel;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        t++;
        switch (t) {
            case 100 -> {
                mc.options.hideGui = true;
                base = mc.player.blockPosition().above(40);
                server("op");
                server("gamemode spectator @s");
                server("time set noon");
                server("weather clear");
                cmd(String.format("fill %d %d %d %d %d %d air", base.getX() - 12, base.getY() - 1, base.getZ() - 12, base.getX() + 12, base.getY() + 8, base.getZ() + 12));
                cmd(String.format("fill %d %d %d %d %d %d polished_andesite", base.getX() - 6, base.getY() - 1, base.getZ() - 6, base.getX() + 8, base.getY() - 1, base.getZ() + 6));
                // the eel's tank: glass, filled with water
                cmd(String.format("fill %d %d %d %d %d %d glass", base.getX() + 2, base.getY(), base.getZ() - 3, base.getX() + 8, base.getY() + 4, base.getZ() + 3));
                cmd(String.format("fill %d %d %d %d %d %d water", base.getX() + 3, base.getY(), base.getZ() - 2, base.getX() + 7, base.getY() + 3, base.getZ() + 2));
                cmd(String.format("tp @s %d %.1f %d 180 15", base.getX(), base.getY() + 2.0, base.getZ() + 7));
            }
            case 160 -> spawn(mc);
            // Facing check: the Potted Monster faces south (+Z), towards this first camera.
            case 260 -> shot("mob_front");
            case 265 -> camera(base.getX() - 7.0, base.getY() + 1.5, base.getZ(), -90, 10);  // from the west, looking east
            case 300 -> shot("mob_side");
            case 305 -> attack();
            case 309 -> shot("mob_side_attack");
            case 330 -> camera(base.getX() - 1.0, base.getY() + 1.8, base.getZ() + 5.0, 180, 15);
            case 360 -> attack();
            case 364 -> shot("mob_front_attack");
            case 380 -> camera(base.getX() + 5.0, base.getY() + 2.5, base.getZ() + 6.0, 180, 25);  // the tank
            case 420 -> shot("mob_eel_1");
            case 428 -> shot("mob_eel_2");
            case 436 -> shot("mob_eel_3");
            case 440 -> camera(base.getX() + 5.0, base.getY() + 6.5, base.getZ(), 0, 90);  // straight down into the tank
            case 470 -> shot("mob_eel_top_1");
            case 478 -> shot("mob_eel_top_2");
            case 485 -> camera(base.getX() + 5.0, base.getY() + 2.5, base.getZ() + 6.0, 180, 25);
            case 500 -> killEel();
            case 510 -> shot("mob_eel_death_1");
            case 522 -> shot("mob_eel_death_2");
            case 560 -> mc.stop();
            default -> { }
        }
    }

    private static void spawn(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            PottedMonster p = ModEntities.POTTED_MONSTER.get().create(level);
            place(p, base.getX() - 1.5, base.getY(), base.getZ() + 0.5);
            level.addFreshEntity(p);
            potted = p.getUUID();
            CrystalEel e = ModEntities.CRYSTAL_EEL.get().create(level);
            place(e, base.getX() + 5.5, base.getY() + 1.5, base.getZ() + 0.5);
            e.setNoGravity(true);
            level.addFreshEntity(e);
            eel = e.getUUID();
        });
    }

    /** Facing south (+Z, yaw 0), no AI, so it holds still for the camera. */
    private static void place(Mob mob, double x, double y, double z) {
        mob.moveTo(x, y, z, 0.0F, 0.0F);
        mob.setYBodyRot(0.0F);
        mob.setYHeadRot(0.0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
    }

    private static void attack() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getEntity(potted) instanceof PottedMonster p) server.overworld().broadcastEntityEvent(p, (byte) 4);
        });
    }

    private static void killEel() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getEntity(eel) instanceof CrystalEel e) e.hurt(server.overworld().damageSources().genericKill(), 100.0F);
        });
    }

    private static void camera(double x, double y, double z, float yaw, float pitch) {
        server(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrandAtlas.LOG.info("[mob look] {}", msg.getString()));
    }

    /** A command at full permission from the server itself. */
    private static void cmd(String command) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command));
    }

    /** A command as the player at full permission ("op" ops them). */
    private static void server(String command) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            if (command.equals("op")) {
                server.getPlayerList().op(p.getGameProfile());
                return;
            }
            GrandAtlas.LOG.info("[mob look] /{}", command);
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
        });
    }
}
