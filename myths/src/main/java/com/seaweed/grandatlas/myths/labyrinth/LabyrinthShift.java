package com.seaweed.grandatlas.myths.labyrinth;

import com.mojang.serialization.Codec;
import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.entity.Minotaur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * The shifting Labyrinth. Every {@link #SHIFT_INTERVAL} ticks (one Minecraft day) the
 * <b>current epoch</b> goes up by one. Any region whose epoch is behind it, and that has no
 * player in it or within {@link #MARGIN} blocks of it, catches up: its walls, lab and exits
 * are redrawn (LabyrinthMaze explains why that's always safe: the doors between regions never
 * move).
 *
 * <h2>The pieces</h2>
 * <ul>
 *   <li><b>Region epochs</b>, saved with the world in a {@link SavedData} file
 *       ({@code data/grandatlas_myths_labyrinth.dat} in the Labyrinth's folder). A region first
 *       touched gets the current epoch.</li>
 *   <li><b>A stamp on every chunk</b>: a DATA ATTACHMENT (NeoForge's way to save extra data on
 *       a chunk, entity or world) listing the epochs of the regions it was built from. When a
 *       chunk loads, or its region shifts while it's loaded, a stamp that doesn't match puts the
 *       chunk in the rebuild queue.</li>
 *   <li><b>The rebuild queue</b>, worked through a few chunks per tick so a shift never causes
 *       a lag spike. A rebuild re-writes the chunk with the generator's own code, clears the old
 *       lab's chest, figures and Minotaur, and places the new lab's.</li>
 * </ul>
 */
public final class LabyrinthShift {

    /** How often the maze shifts: one Minecraft day (20 real minutes). */
    public static final long SHIFT_INTERVAL = 24_000;
    /** A player this close to a region (in blocks) holds it still. */
    public static final int MARGIN = 32;
    /** Chunks rebuilt per tick. */
    private static final int REBUILDS_PER_TICK = 4;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GrandAtlasMyths.MOD_ID);

    /** A chunk's stamp: flattened {rx, rz, epoch} triples for the regions it was built from. Empty = unknown. */
    public static final Supplier<AttachmentType<List<Integer>>> STAMP = ATTACHMENTS.register("labyrinth_stamp",
            () -> AttachmentType.<List<Integer>>builder(() -> List.of()).serialize(Codec.INT.listOf()).build());

    private static final Map<Long, Integer> EPOCHS = new ConcurrentHashMap<>();
    private static final Queue<Long> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<Long> QUEUED = ConcurrentHashMap.newKeySet();
    private static volatile int currentEpoch;
    private static volatile Data data;

    private LabyrinthShift() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(LabyrinthShift::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(LabyrinthShift::onServerStopped);
        NeoForge.EVENT_BUS.addListener(LabyrinthShift::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(LabyrinthShift::onLevelTick);
    }

    /**
     * The epoch region (rx, rz) is drawn at. Called from world-gen threads too, so the map is
     * concurrent. A region nobody has touched yet starts at the current epoch.
     */
    public static int epochFor(int rx, int rz) {
        return EPOCHS.computeIfAbsent(key(rx, rz), k -> {
            Data d = data;
            if (d != null) d.setDirty();
            return currentEpoch;
        });
    }

    // ── Events ──────────────────────────────────────────────────────────────────────────────

    /** GAME bus: a world (dimension) loaded. For the Labyrinth, read the saved epochs. */
    private static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Labyrinth.LABYRINTH) return;
        EPOCHS.clear();
        QUEUE.clear();
        QUEUED.clear();
        currentEpoch = (int) (level.getGameTime() / SHIFT_INTERVAL);
        data = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), "grandatlas_myths_labyrinth");
    }

    /** GAME bus: the server stopped (in single player, the world closed). Forget everything. */
    private static void onServerStopped(ServerStoppedEvent event) {
        EPOCHS.clear();
        QUEUE.clear();
        QUEUED.clear();
        data = null;
    }

    /** GAME bus: a chunk loaded. Stamp a new Labyrinth chunk; queue a stale one for rebuilding. */
    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Labyrinth.LABYRINTH) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        List<Integer> expected = stampFor(chunk.getPos());
        List<Integer> stamp = chunk.getData(STAMP);
        if (event.isNewChunk() && stamp.isEmpty()) {
            chunk.setData(STAMP, expected); // just generated, from the current epochs
            chunk.setUnsaved(true);
        } else if (!stamp.equals(expected)) {
            queue(chunk.getPos());
        }
    }

    /** GAME bus: end of a world's tick. In the Labyrinth: advance the epoch, shift free regions, rebuild a few chunks. */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Labyrinth.LABYRINTH) return;
        currentEpoch = (int) (level.getGameTime() / SHIFT_INTERVAL);
        if (level.getGameTime() % 100 == 0) shiftFreeRegions(level);
        for (int i = 0; i < REBUILDS_PER_TICK; i++) {
            Long next = QUEUE.poll();
            if (next == null) break;
            QUEUED.remove(next);
            rebuild(level, new ChunkPos(next));
        }
    }

    // ── Shifting ────────────────────────────────────────────────────────────────────────────

    private static void shiftFreeRegions(ServerLevel level) {
        int now = currentEpoch;
        for (Map.Entry<Long, Integer> e : EPOCHS.entrySet()) {
            if (e.getValue() >= now) continue;
            int rx = (int) (e.getKey() >> 32), rz = (int) (long) e.getKey();
            if (occupied(level, rx, rz)) continue;
            e.setValue(now);
            if (data != null) data.setDirty();
            // Rebuild the region's chunks that are loaded now; the rest catch up when they load.
            int size = LabyrinthMaze.REGION * LabyrinthMaze.CELL;
            int x0 = rx * size, z0 = rz * size;
            for (int cx = x0 >> 4; cx <= (x0 + size - 1) >> 4; cx++)
                for (int cz = z0 >> 4; cz <= (z0 + size - 1) >> 4; cz++)
                    if (level.getChunkSource().getChunkNow(cx, cz) != null) queue(new ChunkPos(cx, cz));
        }
    }

    /** Dev only (dev/LabCheck): shift region (rx, rz) now, whoever is near, and queue its loaded chunks. */
    public static void forceShift(ServerLevel level, int rx, int rz) {
        EPOCHS.merge(key(rx, rz), 1, Integer::sum);
        int size = LabyrinthMaze.REGION * LabyrinthMaze.CELL;
        int x0 = rx * size, z0 = rz * size;
        for (int cx = x0 >> 4; cx <= (x0 + size - 1) >> 4; cx++)
            for (int cz = z0 >> 4; cz <= (z0 + size - 1) >> 4; cz++)
                if (level.getChunkSource().getChunkNow(cx, cz) != null) queue(new ChunkPos(cx, cz));
    }

    /** Is any player in region (rx, rz) or within MARGIN blocks of it? */
    private static boolean occupied(ServerLevel level, int rx, int rz) {
        int size = LabyrinthMaze.REGION * LabyrinthMaze.CELL;
        double x0 = rx * size - MARGIN, z0 = rz * size - MARGIN, x1 = (rx + 1) * size + MARGIN, z1 = (rz + 1) * size + MARGIN;
        for (ServerPlayer p : level.players())
            if (p.getX() >= x0 && p.getX() < x1 && p.getZ() >= z0 && p.getZ() < z1) return true;
        return false;
    }

    private static void queue(ChunkPos pos) {
        if (QUEUED.add(pos.toLong())) QUEUE.add(pos.toLong());
    }

    /** Re-write a loaded chunk to its regions' current epochs. */
    private static void rebuild(ServerLevel level, ChunkPos cp) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (chunk == null) return; // unloaded since: it'll be checked again when it loads
        if (!(level.getChunkSource().getGenerator() instanceof LabyrinthChunkGenerator gen)) return;
        LabyrinthMaze m = gen.maze(level.getChunkSource().randomState());

        List<Integer> old = chunk.getData(STAMP), now = stampFor(cp);
        List<int[]> changed = new ArrayList<>();
        for (int[] r : LabyrinthChunkGenerator.regionsTouching(cp))
            if (!old.isEmpty() && epochIn(old, r[0], r[1]) == m.epoch(r[0], r[1])) continue;
            else changed.add(r);
        if (changed.isEmpty()) {
            chunk.setData(STAMP, now);
            return;
        }

        // The old lab's figures and Minotaur go with it.
        AABB box = new AABB(cp.getMinBlockX(), 0, cp.getMinBlockZ(), cp.getMaxBlockX() + 1, 32, cp.getMaxBlockZ() + 1);
        for (Entity e : level.getEntities((Entity) null, box, e -> e instanceof Minotaur || e.getTags().contains(LabyrinthLab.AUTOMATON_TAG))) {
            int rx = LabyrinthMaze.regionOf(Math.floorDiv(e.getBlockX(), LabyrinthMaze.CELL));
            int rz = LabyrinthMaze.regionOf(Math.floorDiv(e.getBlockZ(), LabyrinthMaze.CELL));
            if (changed.stream().anyMatch(r -> r[0] == rx && r[1] == rz)) e.discard();
        }

        // Re-write every block that differs. Containers are emptied first, so an old chest's
        // contents vanish with it instead of spilling into the new walls.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LabyrinthChunkGenerator.writeChunk(m, cp, (x, y, z, state) -> {
            if (x < cp.getMinBlockX() || x > cp.getMaxBlockX() || z < cp.getMinBlockZ() || z > cp.getMaxBlockZ()) return;
            pos.set(x, y, z);
            BlockState was = level.getBlockState(pos);
            if (was == state) return;
            if (level.getBlockEntity(pos) instanceof Container container) container.clearContent();
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }, true);

        // The new labs' chest, lecterns, figures and Minotaur, for the regions that changed.
        for (int[] r : changed) {
            LabyrinthLab.placeBlocks(level, m, r[0], r[1], cp, level.getRandom());
            LabyrinthLab.placeEntities(level, m, r[0], r[1], cp, level.getRandom());
        }

        // Anyone caught where a wall now stands is moved to the middle of their cell (always open).
        for (ServerPlayer p : level.players()) {
            if (!new ChunkPos(p.blockPosition()).equals(cp)) continue;
            if (level.getBlockState(p.blockPosition()).isAir() && level.getBlockState(p.blockPosition().above()).isAir()) continue;
            double x = Math.floorDiv(p.getBlockX(), LabyrinthMaze.CELL) * LabyrinthMaze.CELL + 3.5;
            double z = Math.floorDiv(p.getBlockZ(), LabyrinthMaze.CELL) * LabyrinthMaze.CELL + 3.5;
            p.teleportTo(x, Labyrinth.FLOOR_Y, z);
        }

        chunk.setData(STAMP, now);
        chunk.setUnsaved(true);
    }

    // ── Stamps ──────────────────────────────────────────────────────────────────────────────

    /** The stamp a chunk built right now would carry. */
    static List<Integer> stampFor(ChunkPos cp) {
        List<Integer> out = new ArrayList<>(12);
        for (int[] r : LabyrinthChunkGenerator.regionsTouching(cp)) {
            out.add(r[0]);
            out.add(r[1]);
            out.add(epochFor(r[0], r[1]));
        }
        return out;
    }

    private static int epochIn(List<Integer> stamp, int rx, int rz) {
        for (int i = 0; i + 2 < stamp.size(); i += 3)
            if (stamp.get(i) == rx && stamp.get(i + 1) == rz) return stamp.get(i + 2);
        return Integer.MIN_VALUE;
    }

    private static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    // ── Saving ──────────────────────────────────────────────────────────────────────────────

    /**
     * SAVED DATA: a small file the game writes with the world whenever it's marked dirty.
     * Holds every region's epoch, so a shifted region stays shifted across restarts.
     */
    public static final class Data extends SavedData {

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<Long, Integer> e : EPOCHS.entrySet()) {
                CompoundTag r = new CompoundTag();
                r.putInt("x", (int) (e.getKey() >> 32));
                r.putInt("z", (int) (long) e.getKey());
                r.putInt("epoch", e.getValue());
                list.add(r);
            }
            tag.put("regions", list);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = tag.getList("regions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag r = list.getCompound(i);
                EPOCHS.put(key(r.getInt("x"), r.getInt("z")), r.getInt("epoch"));
            }
            return new Data();
        }
    }
}
