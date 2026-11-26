package com.seaweed.grandatlas.myths.dev;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.entity.Minotaur;
import com.seaweed.grandatlas.myths.labyrinth.Labyrinth;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthChunkGenerator;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthLab;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthMaze;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthShift;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev only: {@code gradlew :myths:runLabCheck} opens the test world myths-check in a client and,
 * in the real Labyrinth dimension, generates a lab nobody has seen, checks its chest (with loot
 * table), lecterns and Minotaur, then forces that region to shift and checks the lab moved with
 * them. Writes "[Lab check] ..." lines to the log and quits. Never active in a normal game.
 */
public final class LabCheck {

    private static final List<String> failures = new ArrayList<>();
    private static int ticks = -1, rx, rz;
    private static int[] firstLab;

    private LabCheck() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(LabCheck::onStarted);
        NeoForge.EVENT_BUS.addListener(LabCheck::onTick);
    }

    private static void onStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().getLevel(Labyrinth.LABYRINTH);
        if (level == null || !(level.getChunkSource().getGenerator() instanceof LabyrinthChunkGenerator)) {
            log("FAIL: the Labyrinth isn't loaded with the maze generator: " + (level == null ? "no level" : level.getChunkSource().getGenerator()));
            quit(event.getServer());
            return;
        }
        rx = 40 + (int) (System.nanoTime() % 50);
        rz = -40;
        firstLab = maze(level).labCentre(rx, rz);
        forceLoad(level, firstLab, true);
        ticks = 0;
        log("generating region " + rx + "," + rz + " (epoch " + maze(level).epoch(rx, rz) + "), lab at " + firstLab[0] + " " + firstLab[1]
                + ", theme " + LabyrinthLab.Theme.values()[maze(level).labTheme(rx, rz)]);
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (ticks < 0) return;
        ticks++;
        ServerLevel level = event.getServer().getLevel(Labyrinth.LABYRINTH);
        if (ticks == 60) {
            checkLab(level, firstLab, "fresh");
            LabyrinthShift.forceShift(level, rx, rz);
            int[] next = maze(level).labCentre(rx, rz);
            forceLoad(level, next, true);
            log("forced a shift: epoch now " + maze(level).epoch(rx, rz) + ", lab moves to " + next[0] + " " + next[1]
                    + ", theme " + LabyrinthLab.Theme.values()[maze(level).labTheme(rx, rz)]);
        }
        if (ticks == 260) {
            int[] next = maze(level).labCentre(rx, rz);
            checkLab(level, next, "after shift");
            if (Math.abs(next[0] - firstLab[0]) > 9 || Math.abs(next[1] - firstLab[1]) > 9) {
                BlockPos oldChest = new BlockPos(firstLab[0], 3, firstLab[1]);
                if (level.getBlockState(oldChest).is(Blocks.CHEST)) failures.add("the old lab's chest is still there after the shift");
                List<Minotaur> old = level.getEntitiesOfClass(Minotaur.class, box(firstLab));
                if (!old.isEmpty()) failures.add(old.size() + " Minotaur(s) still in the old lab after the shift");
            }
            log(failures.isEmpty() ? "ALL PASSED" : "FAILED: " + failures);
            forceLoad(level, firstLab, false);
            forceLoad(level, next, false);
            ticks = -1;
            quit(event.getServer());
        }
    }

    private static void checkLab(ServerLevel level, int[] c, String when) {
        BlockPos chest = new BlockPos(c[0], 3, c[1]);
        if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity be)) {
            failures.add(when + ": no chest at " + chest + " (found " + level.getBlockState(chest) + ")");
        } else if (be.getLootTable() == null) {
            failures.add(when + ": chest at " + chest + " has no loot table");
        }
        LabyrinthMaze m = maze(level);
        if (LabyrinthLab.Theme.values()[m.labTheme(rx, rz)] == LabyrinthLab.Theme.DRAFTING) {
            for (int dz : new int[]{-5, 5}) {
                BlockPos at = new BlockPos(c[0], 2, c[1] + dz);
                if (!(level.getBlockEntity(at) instanceof LecternBlockEntity l) || !l.hasBook())
                    failures.add(when + ": no lectern with a book at " + at);
            }
        }
        int minotaurs = level.getEntitiesOfClass(Minotaur.class, box(c)).size();
        int automata = level.getEntities((net.minecraft.world.entity.Entity) null, box(c), e -> e.getTags().contains(LabyrinthLab.AUTOMATON_TAG)).size();
        if (minotaurs != 1) failures.add(when + ": " + minotaurs + " Minotaurs in the lab (expected 1)");
        if (LabyrinthLab.Theme.values()[m.labTheme(rx, rz)] == LabyrinthLab.Theme.AUTOMATA && automata != 8)
            failures.add(when + ": " + automata + " automata (expected 8)");
        log(when + ": chest " + level.getBlockState(chest).getBlock() + ", Minotaurs " + minotaurs + ", automata " + automata
                + ", wings " + level.getBlockState(new BlockPos(c[0] + 1, 12, c[1])).getBlock()
                + ", floor " + level.getBlockState(new BlockPos(c[0], 1, c[1])).getBlock());
    }

    private static AABB box(int[] c) {
        return new AABB(c[0] - 9, 0, c[1] - 9, c[0] + 10, 16, c[1] + 10);
    }

    private static void forceLoad(ServerLevel level, int[] c, boolean on) {
        ChunkPos centre = new ChunkPos(new BlockPos(c[0], 0, c[1]));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            level.setChunkForced(centre.x + dx, centre.z + dz, on);
    }

    private static LabyrinthMaze maze(ServerLevel level) {
        return ((LabyrinthChunkGenerator) level.getChunkSource().getGenerator()).maze(level.getChunkSource().randomState());
    }

    private static void log(String line) {
        GrandAtlasMyths.LOG.info("[Lab check] {}", line);
    }

    private static void quit(MinecraftServer server) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(mc::stop);
    }
}
