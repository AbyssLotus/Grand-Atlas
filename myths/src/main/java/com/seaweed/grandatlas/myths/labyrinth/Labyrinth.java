package com.seaweed.grandatlas.myths.labyrinth;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Travel into and out of the Labyrinth, shared by the commands, the fall-in chance
 * ({@link LabyrinthEntry}) and the Exit Hatches.
 *
 * <h2>Remembering where you came from</h2>
 * On the way in, the player's position and dimension are saved in their
 * <b>persistent data</b>: an NBT compound NeoForge keeps on every entity. Data under
 * {@link Player#PERSISTED_NBT_TAG} survives death too, so dying in the maze doesn't lose the way
 * home. An Exit Hatch reads it back and sends you to where you fell in.
 *
 * <h2>Why travel is queued</h2>
 * The fall-in chance fires inside {@code BlockEvent.BreakEvent}, in the middle of vanilla
 * breaking a block. Vanilla keeps working on the player's CURRENT world after the event
 * returns, so teleporting there would make it finish the break in the Labyrinth, at the same
 * coordinates, deleting a maze block. So travel is queued and done at the end of the server
 * tick ({@link #onServerTick}), when nothing is half-finished.
 */
public final class Labyrinth {

    /** The Labyrinth dimension's key (data/grandatlas_myths/dimension/labyrinth.json). */
    public static final ResourceKey<Level> LABYRINTH = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(GrandAtlasMyths.MOD_ID, "labyrinth"));

    /** Where inside each corridor you stand: the floor is y 0-1, so feet at y 2. */
    public static final int FLOOR_Y = 2;

    private static final String HOME = GrandAtlasMyths.MOD_ID + ":home";

    /** Players waiting to travel, handled at the end of the tick. */
    private static final Queue<Trip> PENDING = new ConcurrentLinkedQueue<>();

    private record Trip(UUID player, boolean in) {}

    private Labyrinth() {}

    /** Queue a trip into the Labyrinth (safe to call from inside any event). */
    public static void queueEnter(ServerPlayer player) {
        PENDING.add(new Trip(player.getUUID(), true));
    }

    /** Queue a trip home (safe to call from inside any event). */
    public static void queueLeave(ServerPlayer player) {
        PENDING.add(new Trip(player.getUUID(), false));
    }

    /** GAME bus: runs the queued trips once the tick's work is done. */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Trip trip;
        while ((trip = PENDING.poll()) != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(trip.player());
            if (player == null) continue; // logged out meanwhile
            if (trip.in()) enter(player);
            else leave(player);
        }
    }

    /**
     * Send a player into the Labyrinth now, remembering where they were. Lands them at the
     * centre of the maze cell nearest their own x and z, so players who fall in far apart
     * start far apart. Returns false if the dimension isn't loaded.
     */
    public static boolean enter(ServerPlayer player) {
        ServerLevel maze = player.server.getLevel(LABYRINTH);
        if (maze == null) return false;
        if (player.level().dimension() != LABYRINTH) saveHome(player);

        int cell = LabyrinthMaze.CELL;
        double x = Math.floorDiv(player.getBlockX(), cell) * cell + 3.5;
        double z = Math.floorDiv(player.getBlockZ(), cell) * cell + 3.5;
        player.teleportTo(maze, x, FLOOR_Y, z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("message.grandatlas_myths.fell_in"), true);
        maze.playSound(null, BlockPos.containing(x, FLOOR_Y, z), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
        return true;
    }

    /**
     * Send a player home now: to where they fell in, or, if that's unknown or its dimension
     * is gone, to the Overworld's spawn.
     */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        CompoundTag persisted = persisted(player);
        ServerLevel target = null;
        double x = 0, y = 0, z = 0;
        if (persisted.contains(HOME)) {
            CompoundTag home = persisted.getCompound(HOME);
            ResourceLocation dim = ResourceLocation.tryParse(home.getString("dim"));
            if (dim != null) target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            x = home.getDouble("x");
            y = home.getDouble("y");
            z = home.getDouble("z");
            persisted.remove(HOME);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        }
        if (target == null || target.dimension() == LABYRINTH) {
            target = server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
        }
        player.teleportTo(target, x, y, z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("message.grandatlas_myths.escaped"), true);
    }

    private static void saveHome(ServerPlayer player) {
        CompoundTag home = new CompoundTag();
        home.putString("dim", player.level().dimension().location().toString());
        home.putDouble("x", player.getX());
        home.putDouble("y", player.getY());
        home.putDouble("z", player.getZ());
        CompoundTag persisted = persisted(player);
        persisted.put(HOME, home);
        // getCompound returns a fresh tag when the key is missing, so always put it back.
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
}
