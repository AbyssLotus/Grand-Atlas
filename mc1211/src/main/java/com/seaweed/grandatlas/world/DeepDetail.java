package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.Moods;
import com.seaweed.grandatlas.core.Rooms;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.Fluids;

/**
 * {@code grandatlas:deep_detail} (cave rework R4, the user's Rule of Cool: "keep players on their toes",
 * ordinary passages "need to have something", surprise over spectacle): small things to stumble on in
 * the passages of the deep and the abyss, about one every few dozen blocks of passage, each picked by
 * the region's mood (core/Moods). Vanilla blocks only.
 *
 * <p>Per chunk, a few points are drawn inside the core's passages (2 in the deep, 3 in the abyss) and
 * each drops to its floor and gets a vignette. Everything stays within 6 blocks of a point inside the
 * chunk, and only adds to open air or (for the fossil) sets into the wall rock.
 */
public record DeepDetail() implements AtlasFeature {
    public static final MapCodec<DeepDetail> CODEC = MapCodec.unit(DeepDetail::new);
    /** Vignettes a chunk: in the deep, and in the abyss. */
    static final int DEEP = 1, ABYSS = 2; // (passages only: the rooms carry their own dressing plans now)

    @Override
    public MapCodec<DeepDetail> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        // R5, vertical drama and fire: the falls' sources, magma rivers, hanging forests.
        boolean placed = falls(level, core, x0, z0) | magmaRivers(level, core, x0, z0) | hangingForests(level, core, random, x0, z0);
        // The rooms' dressing plans (cave redesign, layer 4): background dressing for every room that
        // meets the chunk, and a focal piece with its supporting pieces for those whose focal spot is here.
        java.util.List<Rooms.Room> meet = new java.util.ArrayList<>(), here = new java.util.ArrayList<>();
        core.passages().rooms(x0, z0, x0 + 16, z0 + 16, false, meet);
        core.passages().rooms(x0, z0, x0 + 16, z0 + 16, true, here);
        for (Rooms.Room rm : meet) placed |= ambient(level, rm, x0, z0);
        for (Rooms.Room rm : here) placed |= dress(level, core, rm);
        int deep = 0, abyss = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int t = 0; t < 160 && (deep < DEEP || abyss < ABYSS); t++) {
            int x = x0 + 2 + random.nextInt(12), z = z0 + 2 + random.nextInt(12);
            int y = AtlasCore.MIN_Y + 14 + random.nextInt(Moods.TOP - AtlasCore.MIN_Y - 14);
            boolean ab = y < AtlasCore.ABYSS_TOP;
            if (ab ? abyss >= ABYSS : deep >= DEEP) continue;
            ColumnSample c = core.column(x, z);
            if (core.passages().carve(c, x, y, z) >= 0 || !level.getBlockState(pos.set(x, y, z)).isAir()) continue;
            if (inRoom(meet, x, y, z)) continue; // rooms have their plans
            if (core.passages().inSpiral(x, y, z)) continue; // spiral galleries stay clear (their ledge is the path)
            // Down to the floor (and the roof over it), within a passage's height.
            int floor = y;
            while (floor > y - 24 && level.getBlockState(pos.set(x, floor - 1, z)).isAir()) floor--;
            if (!solid(level, pos.set(x, floor - 1, z))) continue;
            int roof = y;
            while (roof < y + 30 && level.getBlockState(pos.set(x, roof + 1, z)).isAir()) roof++;
            if (!solid(level, pos.set(x, roof + 1, z))) roof = -9999;
            Moods.Mood mood = core.moods().at(x, floor, z);
            if (mood == null) continue;
            BlockPos at = new BlockPos(x, floor, z);
            boolean done = ab ? abyss(level, random, mood, at, roof) : deep(level, random, mood, at, roof);
            if (done) {
                placed = true;
                if (ab) abyss++;
                else deep++;
            }
        }
        return placed;
    }

    // ---- the rooms' dressing plans (cave redesign, docs/CAVE_REDESIGN.md layer 4)

    /** A vignette placed at a floor block (the air on it), with the roof's y over it (-9999: none in reach). */
    private interface V {
        boolean place(WorldGenLevel level, RandomSource r, BlockPos at, int roof);
    }

    // Focal pieces: the big ones, at the room's focal spot (where its template says you see it walking in).
    private static final V[] F_UPPER = {(l, r, a, f) -> dripstones(l, r, a, f, 6, true), DeepDetail::curtain, (l, r, a, f) -> rimstones(l, r, a),
            DeepDetail::dripPool, (l, r, a, f) -> mushrooms(l, r, a), (l, r, a, f) -> fossil(l, r, a), (l, r, a, f) -> geode(l, r, a),
            DeepDetail::calciteColumn, DeepDetail::roots};
    private static final V[][] F_DEEP = {
            /* AWE */ {DeepDetail::calciteColumn, (l, r, a, f) -> geode(l, r, a), (l, r, a, f) -> rimstones(l, r, a), DeepDetail::curtain,
                    (l, r, a, f) -> dripstones(l, r, a, f, 7, true)},
            /* FEAR */ {DeepDetail::deadCamp, DeepDetail::chains, (l, r, a, f) -> rubble(l, r, a), (l, r, a, f) -> remains(l, r, a, false)},
            /* COZY */ {DeepDetail::sporeGrotto, DeepDetail::mossNook, DeepDetail::dripPool, (l, r, a, f) -> mushrooms(l, r, a),
                    (l, r, a, f) -> seep(l, r, a, false), DeepDetail::roots},
            /* CLEVER */ {(l, r, a, f) -> fossil(l, r, a), (l, r, a, f) -> oreSeam(l, r, a), (l, r, a, f) -> amethyst(l, r, a),
                    (l, r, a, f) -> steppingStones(l, r, a)},
            /* HORROR */ {(l, r, a, f) -> sculk(l, r, a, true), (l, r, a, f) -> veinWeb(l, r, a), (l, r, a, f) -> spawnerDen(l, r, a),
                    (l, r, a, f) -> bones(l, r, a, false)}};
    private static final V[][] F_ABYSS = {
            /* AWE */ {DeepDetail::spire, DeepDetail::organ, DeepDetail::arch, DeepDetail::lavaCurtain, (l, r, a, f) -> weepingWall(l, r, a)},
            /* FEAR */ {(l, r, a, f) -> cage(l, r, a), DeepDetail::maw, DeepDetail::chainedRemains, (l, r, a, f) -> lantern(l, a, f), DeepDetail::lavaCurtain},
            /* COZY */ {(l, r, a, f) -> hearth(l, r, a), (l, r, a, f) -> hotSpring(l, r, a), (l, r, a, f) -> netherGarden(l, r, a),
                    (l, r, a, f) -> seep(l, r, a, true)},
            /* CLEVER */ {DeepDetail::balanced, (l, r, a, f) -> causeway(l, r, a), DeepDetail::floatingStones, (l, r, a, f) -> tintedWindow(l, r, a)},
            /* HORROR */ {(l, r, a, f) -> ritual(l, r, a), DeepDetail::ribcage, (l, r, a, f) -> witherRoses(l, r, a), (l, r, a, f) -> soulFire(l, r, a),
                    (l, r, a, f) -> bones(l, r, a, true)}};
    // Supporting pieces: small, near the focal one, in keeping with it.
    private static final V[] S_UPPER = {(l, r, a, f) -> dripstones(l, r, a, f, 3, false), (l, r, a, f) -> mushrooms(l, r, a)};
    private static final V[][] S_DEEP = {
            {(l, r, a, f) -> dripstones(l, r, a, f, 3, false), DeepDetail::curtain},
            {DeepDetail::cobwebs, (l, r, a, f) -> remains(l, r, a, false)},
            {(l, r, a, f) -> mushrooms(l, r, a), DeepDetail::roots},
            {(l, r, a, f) -> amethyst(l, r, a), (l, r, a, f) -> oreSeam(l, r, a)},
            {(l, r, a, f) -> bones(l, r, a, false), (l, r, a, f) -> infested(l, r, a), DeepDetail::cobwebs}};
    private static final V[][] S_ABYSS = {
            {(l, r, a, f) -> dripstones(l, r, a, f, 3, false)},
            {(l, r, a, f) -> remains(l, r, a, true), DeepDetail::cobwebs},
            {(l, r, a, f) -> netherGarden(l, r, a)},
            {(l, r, a, f) -> causeway(l, r, a)},
            {(l, r, a, f) -> bones(l, r, a, true), (l, r, a, f) -> witherRoses(l, r, a)}};

    /** Whether (x, y, z) is inside one of the rooms (the passages' darts leave rooms to their plans). */
    private static boolean inRoom(java.util.List<Rooms.Room> rooms, int x, int y, int z) {
        for (Rooms.Room rm : rooms) {
            if (x < rm.minX || x > rm.maxX || z < rm.minZ || z > rm.maxZ || y < rm.minY || y > rm.maxY) continue;
            if (rm.sdf(x + 0.5, y + 0.5, z + 0.5) < 0) return true;
        }
        return false;
    }

    /**
     * A room's plan: most rooms get no focal piece (plain makes the special ones special); great halls
     * always do, big rooms often. Rooms near each other never repeat a focal kind: within a 192-block
     * square the kinds are dealt out in a shuffled order, one to each room. One to three supporting
     * pieces stand near the focal one.
     */
    private static boolean dress(WorldGenLevel level, AtlasCore core, Rooms.Room rm) {
        RandomSource r = RandomSource.create(rm.seed());
        double chance = rm.great() ? 1 : rm.w > 26 ? 0.6 : 0.3;
        if (r.nextDouble() >= chance) return false;
        double[] f = rm.focal();
        int fx = (int) Math.floor(f[0]), fz = (int) Math.floor(f[2]);
        Moods.Mood mood = core.moods().at(fx, rm.floorY, fz);
        boolean ab = rm.floorY < AtlasCore.ABYSS_TOP;
        // R6: half the great halls and some big rooms hold a found place instead (a colossus, a lost
        // expedition, a fungal grove, a root cathedral).
        double storyChance = rm.great() ? 0.5 : rm.w > 22 ? 0.15 : 0;
        if (storyChance > 0 && r.nextDouble() < storyChance) {
            BlockPos at0 = floorNear(level, fx, (int) Math.floor(f[1]), fz);
            if (at0 != null && story(level, r, rm, mood, ab, at0, Math.floorDiv(fx, 16) * 16, Math.floorDiv(fz, 16) * 16)) return true;
        }
        V[] focal = mood == null ? F_UPPER : (ab ? F_ABYSS : F_DEEP)[mood.ordinal()];
        V[] support = mood == null ? S_UPPER : (ab ? S_ABYSS : S_DEEP)[mood.ordinal()];
        // deal the kinds out round the rooms of the square, so neighbours differ
        int ci = Math.floorDiv(fx, 192), cj = Math.floorDiv(fz, 192);
        java.util.List<Rooms.Room> cell = new java.util.ArrayList<>();
        core.passages().rooms(ci * 192, cj * 192, ci * 192 + 192, cj * 192 + 192, true, cell);
        cell.sort((a, b) -> a.cx != b.cx ? Double.compare(a.cx, b.cx) : a.cz != b.cz ? Double.compare(a.cz, b.cz) : Double.compare(a.floorY, b.floorY));
        int ord = 0;
        for (int i = 0; i < cell.size(); i++) {
            Rooms.Room c = cell.get(i);
            if (c == rm || c.cx == rm.cx && c.cz == rm.cz && c.floorY == rm.floorY) ord = i;
        }
        int[] perm = shuffled(focal.length, GradientNoise.hash(0xF0CAL, ci, cj));
        V pick = focal[perm[ord % focal.length]];
        boolean any = false;
        BlockPos at = floorNear(level, fx, (int) Math.floor(f[1]), fz);
        if (at != null) any = pick.place(level, r, at, roofOver(level, at));
        int n = 1 + r.nextInt(3);
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 4 + r.nextDouble() * Math.min(8, rm.w * 0.3);
            BlockPos p = floorNear(level, (int) Math.floor(f[0] + Math.cos(a) * d), (int) Math.floor(f[1]), (int) Math.floor(f[2] + Math.sin(a) * d));
            if (p != null) any |= support[r.nextInt(support.length)].place(level, r, p, roofOver(level, p));
        }
        return any;
    }

    private static int[] shuffled(int n, long seed) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        RandomSource r = RandomSource.create(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    /** The floor (the air block on it) at (x, z) within a dozen below y, or a few above. */
    private static BlockPos floorNear(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + 4, z);
        for (int i = 0; i < 18; i++, p.move(0, -1, 0)) {
            if (level.getBlockState(p).isAir() && solid(level, p.below())) return p.immutable();
        }
        return null;
    }

    private static int roofOver(WorldGenLevel level, BlockPos at) {
        int roof = roofAt(level, at.getX(), at.getY(), at.getZ());
        return roof == Integer.MIN_VALUE ? -9999 : roof;
    }

    /**
     * Background dressing in patches, by how the room formed (its archetype): rubble on breakdown
     * floors, flowstone in the wet domes and avens, a scoured gravel bed in canyons and keyholes, clay
     * flats in galleries and bowls. Only on the part of the room inside this chunk.
     */
    private static boolean ambient(WorldGenLevel level, Rooms.Room rm, int x0, int z0) {
        int a = rm.template.archetype;
        int lx = Math.max(x0, (int) Math.floor(rm.minX)), hx = Math.min(x0 + 15, (int) Math.floor(rm.maxX));
        int lz = Math.max(z0, (int) Math.floor(rm.minZ)), hz = Math.min(z0 + 15, (int) Math.floor(rm.maxZ));
        if (lx > hx || lz > hz) return false;
        RandomSource r = RandomSource.create(rm.seed() ^ ((long) x0 * 341873128712L + (long) z0 * 132897987541L));
        long ns = rm.seed() ^ 0xA3B1L;
        int samples = Math.min(60, (hx - lx + 1) * (hz - lz + 1) / 5);
        boolean any = false;
        for (int t = 0; t < samples; t++) {
            int x = lx + r.nextInt(hx - lx + 1), z = lz + r.nextInt(hz - lz + 1);
            if (GradientNoise.noise2(ns, x / 7.0, z / 7.0) < 0.15) continue; // patches, not speckle
            BlockPos at = floorNear(level, x, (int) Math.floor(rm.floorY) + 2, z);
            if (at == null || rm.sdf(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > 0.5) continue;
            BlockPos bed = at.below();
            switch (a) {
                case Rooms.BREAKDOWN, Rooms.FINS -> { // fallen blocks
                    if (r.nextInt(3) == 0) {
                        level.setBlock(at, (r.nextBoolean() ? Blocks.COBBLED_DEEPSLATE : Blocks.TUFF).defaultBlockState(), 2);
                        if (r.nextInt(4) == 0 && level.getBlockState(at.above()).isAir()) level.setBlock(at.above(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 2);
                    } else if (stone(level, bed)) {
                        level.setBlock(bed, Blocks.GRAVEL.defaultBlockState(), 2);
                    }
                    any = true;
                }
                case Rooms.DOME, Rooms.AVEN, Rooms.PILLARS, Rooms.BRIDGE -> { // flowstone: wet rooms
                    if (stone(level, bed)) level.setBlock(bed, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 2);
                    if (r.nextInt(3) == 0) {
                        int top = roofAt(level, x, at.getY(), z);
                        if (top != Integer.MIN_VALUE && top - at.getY() > 5) hang(level, x, top, z, 1 + r.nextInt(4), Direction.DOWN, false);
                    }
                    any = true;
                }
                case Rooms.CANYON, Rooms.KEYHOLE -> { // a stream bed, scoured
                    if (stone(level, bed)) {
                        level.setBlock(bed, (r.nextInt(4) == 0 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState(), 2);
                        any = true;
                    }
                }
                case Rooms.GALLERY, Rooms.BOWL, Rooms.TERRACES -> { // still water once stood here: clay flats
                    if (stone(level, bed)) {
                        level.setBlock(bed, (a == Rooms.TERRACES && r.nextBoolean() ? Blocks.CALCITE : Blocks.CLAY).defaultBlockState(), 2);
                        any = true;
                    }
                }
                default -> { // a few drips
                    if (r.nextInt(6) == 0) {
                        int top = roofAt(level, x, at.getY(), z);
                        if (top != Integer.MIN_VALUE && top - at.getY() > 4) {
                            hang(level, x, top, z, 1 + r.nextInt(3), Direction.DOWN, false);
                            any = true;
                        }
                    }
                }
            }
        }
        return any;
    }

    // ---- R6: story and life (found places, in the rooms; the user's Rule of Cool answer 6:
    // environmental storytelling, "huge fossils and remains are in"; answer 11: cave ecosystems).
    // Nothing here is architecture (generated buildings are never designed: docs/M7_UNDERGROUND_PLAN.md):
    // bones, a camp's leavings, fungi, roots.

    /** Debug (client/DeepLook): which found place a room's plan holds, drawn exactly as {@link #dress} draws it, or null. */
    public static String storyOf(AtlasCore core, Rooms.Room rm) {
        RandomSource r = RandomSource.create(rm.seed());
        double chance = rm.great() ? 1 : rm.w > 26 ? 0.6 : 0.3;
        if (r.nextDouble() >= chance) return null;
        double[] f = rm.focal();
        Moods.Mood mood = core.moods().at((int) Math.floor(f[0]), rm.floorY, (int) Math.floor(f[2]));
        double storyChance = rm.great() ? 0.5 : rm.w > 22 ? 0.15 : 0;
        if (storyChance <= 0 || r.nextDouble() >= storyChance) return null;
        int pick = r.nextInt(2);
        if (mood == null) return switch (r.nextInt(3)) {
            case 0 -> "expedition";
            case 1 -> "fungal grove";
            default -> "root cathedral";
        };
        return switch (mood) {
            case AWE -> "colossus";
            case FEAR -> "expedition";
            case COZY -> pick == 0 ? "root cathedral" : "fungal grove";
            case CLEVER -> pick == 0 ? "colossus" : "expedition";
            case HORROR -> pick == 0 ? "colossus" : "fungal grove";
        };
    }

    /** Sets a block if it's in the chunks a feature may write (this one and its neighbours) and the spot is air. */
    private static boolean put(WorldGenLevel level, int x0, int z0, BlockPos p, BlockState st) {
        if (p.getX() < x0 - 16 || p.getX() > x0 + 31 || p.getZ() < z0 - 16 || p.getZ() > z0 + 31) return false;
        if (!level.getBlockState(p).isAir()) return false;
        level.setBlock(p, st, 2);
        return true;
    }

    /** As {@link #put}, replacing natural rock instead of air. */
    private static boolean putRock(WorldGenLevel level, int x0, int z0, BlockPos p, BlockState st) {
        if (p.getX() < x0 - 16 || p.getX() > x0 + 31 || p.getZ() < z0 - 16 || p.getZ() > z0 + 31) return false;
        if (!stone(level, p)) return false;
        level.setBlock(p, st, 2);
        return true;
    }

    /** A found place in place of the room's focal piece, by mood (null: above the deep), or false if none fits. */
    private static boolean story(WorldGenLevel level, RandomSource r, Rooms.Room rm, Moods.Mood mood, boolean ab, BlockPos at, int x0, int z0) {
        int pick = r.nextInt(2);
        if (mood == null) {
            return switch (r.nextInt(3)) {
                case 0 -> expedition(level, r, rm, at, x0, z0, 0);
                case 1 -> fungalGrove(level, r, rm, at, x0, z0, false);
                default -> rootCathedral(level, r, rm, at, x0, z0);
            };
        }
        return switch (mood) {
            case AWE -> colossus(level, r, rm, at, x0, z0, ab);
            case FEAR -> expedition(level, r, rm, at, x0, z0, ab ? 2 : 1);
            case COZY -> pick == 0 ? rootCathedral(level, r, rm, at, x0, z0) : fungalGrove(level, r, rm, at, x0, z0, false);
            case CLEVER -> pick == 0 ? colossus(level, r, rm, at, x0, z0, ab) : expedition(level, r, rm, at, x0, z0, ab ? 2 : 1);
            case HORROR -> pick == 0 ? colossus(level, r, rm, at, x0, z0, ab) : fungalGrove(level, r, rm, at, x0, z0, true);
        };
    }

    /**
     * A colossus: the bones of something enormous lying along the room, half sunk in the floor. A
     * spine of bone, ribs arching over it (some broken), a skull at one end, a tapering tail.
     */
    private static boolean colossus(WorldGenLevel level, RandomSource r, Rooms.Room rm, BlockPos at, int x0, int z0, boolean ab) {
        double yaw = rm.yaw() + (r.nextDouble() - 0.5) * 0.6, ux = Math.cos(yaw), uz = Math.sin(yaw), px = -uz, pz = ux;
        double len = Math.min(28, rm.w * 0.75);
        int fy = at.getY();
        int roof = roofAt(level, at.getX(), fy, at.getZ());
        double hr = Math.max(3, Math.min(roof == Integer.MIN_VALUE ? 8 : roof - fy - 2, 5 + len * 0.18));
        BlockState spineX = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Math.abs(ux) > Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z);
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        boolean any = false;
        // the spine, a little raised in the middle, sunk at the ends; a knob each few blocks
        for (double t = -len / 2; t <= len / 2; t += 0.5) {
            double u = t / (len / 2);
            int y = fy + (int) Math.round(1.2 * (1 - u * u)) - (Math.abs(u) > 0.8 ? 1 : 0);
            BlockPos p = BlockPos.containing(at.getX() + 0.5 + ux * t, y, at.getZ() + 0.5 + uz * t);
            any |= put(level, x0, z0, p, spineX);
            if (((int) Math.floor(t)) % 3 == 0) any |= put(level, x0, z0, p.above(), bone);
        }
        // ribs: arches over the spine from floor to floor, a few missing or snapped short
        for (double t = -len * 0.28; t <= len * 0.3; t += 3) {
            if (r.nextInt(6) == 0) continue;
            double u = t / (len / 2);
            double span = (4 + len * 0.16) * Math.sqrt(Math.max(0.2, 1 - u * u));
            double h = hr * Math.sqrt(Math.max(0.3, 1 - u * u));
            double broken = r.nextInt(4) == 0 ? 0.35 + r.nextDouble() * 0.4 : 1; // how much of the arch is left
            for (int side = -1; side <= 1; side += 2) {
                double phiEnd = Math.PI / 2 * (side == 1 ? broken : 1);
                for (double phi = 0; phi <= phiEnd; phi += 0.06) {
                    double lat = span * Math.cos(phi) * side, ht = h * Math.sin(phi);
                    BlockPos p = BlockPos.containing(at.getX() + 0.5 + ux * t + px * lat, fy + ht, at.getZ() + 0.5 + uz * t + pz * lat);
                    any |= put(level, x0, z0, p, bone);
                }
            }
        }
        // the skull: a hollow lump of bone at the head end, two eye holes, the jaw a slab beneath
        double hx = at.getX() + 0.5 + ux * (len / 2 + 3), hz = at.getZ() + 0.5 + uz * (len / 2 + 3);
        double sr = 3 + len * 0.05;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = 0; dy <= 6; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double a = (dx * ux + dz * uz) / (sr * 1.2), b = (dx * px + dz * pz) / sr, c = (dy - sr * 0.7) / (sr * 0.8);
                    double q = a * a + b * b + c * c;
                    if (q > 1 || q < 0.55) continue; // a shell
                    BlockPos p = BlockPos.containing(hx + dx, fy + dy, hz + dz);
                    // the eyes: two holes forward, either side
                    double fwd = dx * ux + dz * uz, lat = dx * px + dz * pz;
                    if (fwd > sr * 0.5 && Math.abs(Math.abs(lat) - sr * 0.45) < 1 && Math.abs(dy - sr * 0.8) < 1) continue;
                    any |= put(level, x0, z0, p, bone);
                }
            }
        }
        // the tail, tapering off behind
        for (double t = 0; t < 6 + len * 0.3; t += 0.5) {
            double bend = Math.sin(t / 5) * 2;
            BlockPos p = BlockPos.containing(at.getX() + 0.5 - ux * (len / 2 + t) + px * bend, fy, at.getZ() + 0.5 - uz * (len / 2 + t) + pz * bend);
            if (r.nextInt(5) > 0) any |= put(level, x0, z0, p, bone);
        }
        if (ab) { // in the abyss its bed is soul soil
            for (int k = 0; k < 40; k++) {
                BlockPos p = BlockPos.containing(at.getX() + (r.nextDouble() - 0.5) * len, fy - 1, at.getZ() + (r.nextDouble() - 0.5) * len);
                putRock(level, x0, z0, p, Blocks.SOUL_SOIL.defaultBlockState());
            }
        }
        return any;
    }

    /**
     * A lost expedition: a cold campfire, bedrolls, barrels and a chest whose loot is richer the deeper
     * it lies (Terraria's rule: deeper is riskier and richer), a crafting table, rails that go nowhere.
     * band 0 above the deep, 1 deep, 2 abyss. In fear, someone didn't leave.
     */
    private static boolean expedition(WorldGenLevel level, RandomSource r, Rooms.Room rm, BlockPos at, int x0, int z0, int band) {
        if (!level.getBlockState(at).isAir()) return false;
        double yaw = rm.yaw() + r.nextDouble(), ux = Math.cos(yaw), uz = Math.sin(yaw), px = -uz, pz = ux;
        boolean any = put(level, x0, z0, at, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        BlockState[] carpets = {Blocks.BROWN_CARPET.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState()};
        java.util.function.BiFunction<Double, Double, BlockPos> spot = (f, l) -> {
            BlockPos q = floorNear(level, (int) Math.floor(at.getX() + 0.5 + ux * f + px * l), at.getY(), (int) Math.floor(at.getZ() + 0.5 + uz * f + pz * l));
            return q != null && Math.abs(q.getY() - at.getY()) <= 2 ? q : null;
        };
        // two bedrolls
        for (int k = 0; k < 2; k++) {
            BlockPos b = spot.apply(-1.0 + k * 2, 3.0);
            if (b == null) continue;
            BlockState c = carpets[r.nextInt(carpets.length)];
            any |= put(level, x0, z0, b, c);
            BlockPos b2 = spot.apply(-1.0 + k * 2, 4.0);
            if (b2 != null) put(level, x0, z0, b2, c);
            if (band >= 1 && k == 0 && r.nextBoolean()) { // someone who didn't get up
                BlockPos sk = spot.apply(-1.0, 2.0);
                if (sk != null) put(level, x0, z0, sk, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)));
            }
        }
        // barrels and the chest
        BlockPos br = spot.apply(2.5, -2.5);
        if (br != null) {
            any |= put(level, x0, z0, br, Blocks.BARREL.defaultBlockState());
            if (r.nextBoolean()) put(level, x0, z0, br.above(), Blocks.BARREL.defaultBlockState());
        }
        BlockPos ch = spot.apply(1.0, -3.0);
        if (ch != null && put(level, x0, z0, ch, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,
                Direction.Plane.HORIZONTAL.getRandomDirection(r)))) {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot = switch (band) {
                case 0 -> net.minecraft.world.level.storage.loot.BuiltInLootTables.ABANDONED_MINESHAFT;
                case 1 -> net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON;
                default -> net.minecraft.world.level.storage.loot.BuiltInLootTables.ANCIENT_CITY;
            };
            net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, r, ch, loot);
            any = true;
        }
        BlockPos ct = spot.apply(-2.5, -2.0);
        if (ct != null) put(level, x0, z0, ct, Blocks.CRAFTING_TABLE.defaultBlockState());
        // rails that go nowhere, a few missing
        for (int k = 4; k < 12; k++) {
            if (r.nextInt(5) == 0) continue;
            BlockPos rl = spot.apply((double) k, 0.0);
            if (rl != null && solid(level, rl.below())) put(level, x0, z0, rl, Blocks.RAIL.defaultBlockState());
        }
        if (band >= 1) {
            for (int k = 0; k < 3; k++) {
                BlockPos w = spot.apply((r.nextDouble() - 0.5) * 8, (r.nextDouble() - 0.5) * 8);
                if (w != null) put(level, x0, z0, w.above(1 + r.nextInt(2)), Blocks.COBWEB.defaultBlockState());
            }
        }
        return any;
    }

    /** A grove of giant mushrooms over mycelium; in horror, crimson and warped growth under weeping vines. */
    private static boolean fungalGrove(WorldGenLevel level, RandomSource r, Rooms.Room rm, BlockPos at, int x0, int z0, boolean horror) {
        double rad = Math.max(5, Math.min(11, rm.w * 0.35));
        boolean any = false;
        BlockState ground = horror ? (r.nextBoolean() ? Blocks.CRIMSON_NYLIUM : Blocks.WARPED_NYLIUM).defaultBlockState() : Blocks.MYCELIUM.defaultBlockState();
        for (int dx = (int) -rad; dx <= rad; dx++) {
            for (int dz = (int) -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad || r.nextInt(10) < 2) continue;
                BlockPos f = floorNear(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (f == null || Math.abs(f.getY() - at.getY()) > 3) continue;
                if (!putRock(level, x0, z0, f.below(), ground)) continue;
                any = true;
                int roll = r.nextInt(100);
                if (horror) {
                    if (roll < 12) put(level, x0, z0, f, (ground.is(Blocks.CRIMSON_NYLIUM) ? Blocks.CRIMSON_FUNGUS : Blocks.WARPED_FUNGUS).defaultBlockState());
                    else if (roll < 30) put(level, x0, z0, f, (ground.is(Blocks.CRIMSON_NYLIUM) ? Blocks.CRIMSON_ROOTS : Blocks.WARPED_ROOTS).defaultBlockState());
                    if (r.nextInt(9) == 0) {
                        int top = roofAt(level, f.getX(), f.getY(), f.getZ());
                        if (top != Integer.MIN_VALUE && top - f.getY() > 4) {
                            int len = 2 + r.nextInt(Math.min(8, top - f.getY() - 2));
                            for (int i = 0; i < len; i++) put(level, x0, z0, new BlockPos(f.getX(), top - i, f.getZ()),
                                    (i == len - 1 ? Blocks.WEEPING_VINES : Blocks.WEEPING_VINES_PLANT).defaultBlockState());
                        }
                    }
                } else if (roll < 20) {
                    put(level, x0, z0, f, (r.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
                }
            }
        }
        if (!horror) {
            int n = 2 + r.nextInt(3);
            for (int k = 0; k < n; k++) {
                double a = r.nextDouble() * 2 * Math.PI, d = r.nextDouble() * rad * 0.8;
                BlockPos f = floorNear(level, (int) Math.floor(at.getX() + Math.cos(a) * d), at.getY(), (int) Math.floor(at.getZ() + Math.sin(a) * d));
                if (f != null) any |= giantMushroom(level, r, f, x0, z0, r.nextBoolean());
            }
        }
        return any;
    }

    /** A giant mushroom from the floor (as vanilla's: red, a dome; brown, a broad flat cap), if there's room. */
    private static boolean giantMushroom(WorldGenLevel level, RandomSource r, BlockPos f, int x0, int z0, boolean red) {
        int top = roofAt(level, f.getX(), f.getY(), f.getZ());
        int room = top == Integer.MIN_VALUE ? 30 : top - f.getY();
        int stem = 3 + r.nextInt(4);
        if (room < stem + 3) return false;
        for (int i = 0; i < stem; i++) put(level, x0, z0, f.above(i), Blocks.MUSHROOM_STEM.defaultBlockState());
        BlockState cap = (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
        int cy = f.getY() + stem;
        if (red) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 0; dy++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (dy == 0 ? (Math.abs(dx) == 2 && Math.abs(dz) == 2) : !edge || Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                put(level, x0, z0, new BlockPos(f.getX() + dx, cy + dy, f.getZ() + dz), cap);
            }
        } else {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                put(level, x0, z0, new BlockPos(f.getX() + dx, cy, f.getZ() + dz), cap);
            }
        }
        return true;
    }

    /**
     * A root cathedral: roots of something far above come down through the roof as columns to the
     * floor (mangrove roots, wandering a little, flared at the foot), rooted dirt and hanging roots
     * overhead, moss underfoot.
     */
    private static boolean rootCathedral(WorldGenLevel level, RandomSource r, Rooms.Room rm, BlockPos at, int x0, int z0) {
        double rad = Math.max(5, Math.min(11, rm.w * 0.35));
        boolean any = false;
        int n = 3 + r.nextInt(4);
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * 2 * Math.PI, d = r.nextDouble() * rad;
            BlockPos f = floorNear(level, (int) Math.floor(at.getX() + Math.cos(a) * d), at.getY(), (int) Math.floor(at.getZ() + Math.sin(a) * d));
            if (f == null) continue;
            int top = roofAt(level, f.getX(), f.getY(), f.getZ());
            if (top == Integer.MIN_VALUE) continue;
            int x = f.getX(), z = f.getZ();
            if (top - f.getY() > 22) { // too tall to reach the floor: a hanging root curtain instead
                for (int i = 0; i < 4 + r.nextInt(5); i++) any |= put(level, x0, z0, new BlockPos(x, top - i, z), Blocks.HANGING_ROOTS.defaultBlockState());
                continue;
            }
            for (int y = top; y >= f.getY(); y--) {
                if ((top - y) % 4 == 3 && r.nextInt(3) == 0) {
                    int nx = x + r.nextInt(3) - 1, nz = z + r.nextInt(3) - 1;
                    put(level, x0, z0, new BlockPos(nx, y, nz), Blocks.MANGROVE_ROOTS.defaultBlockState()); // a step sideways
                    x = nx;
                    z = nz;
                }
                any |= put(level, x0, z0, new BlockPos(x, y, z), Blocks.MANGROVE_ROOTS.defaultBlockState());
            }
            for (Direction dd : Direction.Plane.HORIZONTAL) {
                if (r.nextBoolean()) put(level, x0, z0, new BlockPos(x, f.getY(), z).relative(dd), Blocks.MANGROVE_ROOTS.defaultBlockState()); // the flared foot
            }
            putRock(level, x0, z0, new BlockPos(x, top + 1, z), Blocks.ROOTED_DIRT.defaultBlockState());
        }
        // overhead and underfoot
        for (int dx = (int) -rad; dx <= rad; dx++) {
            for (int dz = (int) -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad || r.nextInt(3) > 0) continue;
                BlockPos f = floorNear(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (f == null) continue;
                if (putRock(level, x0, z0, f.below(), Blocks.MOSS_BLOCK.defaultBlockState()) && r.nextInt(3) == 0) put(level, x0, z0, f, Blocks.MOSS_CARPET.defaultBlockState());
                int top = roofAt(level, f.getX(), f.getY(), f.getZ());
                if (top != Integer.MIN_VALUE && putRock(level, x0, z0, new BlockPos(f.getX(), top + 1, f.getZ()), Blocks.ROOTED_DIRT.defaultBlockState())
                        && r.nextBoolean()) {
                    put(level, x0, z0, new BlockPos(f.getX(), top, f.getZ()), Blocks.HANGING_ROOTS.defaultBlockState());
                    any = true;
                }
            }
        }
        return any;
    }

    // ---- R5: vertical drama and fire

    /**
     * The sources of the falls (core/Deep): an arc of the shaft's wall a few blocks under the top
     * junction becomes water (or lava), so it pours down the shaft to the pool (or magma) at its foot.
     */
    private static boolean falls(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        java.util.List<double[]> fs = new java.util.ArrayList<>();
        core.deep().falls(x0, z0, x0 + 16, z0 + 16, fs);
        boolean any = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double[] f : fs) {
            boolean lava = f[3] > 0.5;
            int cx = (int) Math.floor(f[0]), cz = (int) Math.floor(f[2]);
            double r = f[4];
            double start = (cx * 31 + cz * 17) % 360 * Math.PI / 180;
            int placed = 0, y = 0;
            // under the level's floor, in the shaft's wall; deeper if a room or passage opens the rock there
            for (int down = 6; down <= 30 && placed == 0; down += 3) {
            y = (int) Math.floor(f[1]) - down;
            // walk round the shaft at that height; the wall blocks with the shaft's air beside them
            for (int i = 0; i < 48 && placed < (r > 4.5 ? 4 : 2); i++) {
                double a = start + i * Math.PI / 24, ux = Math.cos(a), uz = Math.sin(a);
                // Out from the middle along this heading to the first rock (the wall, wherever the
                // shaft's rough side puts it), with the shaft's air just inside it.
                int wx = 0, wz = 0;
                BlockPos in = null;
                for (double d = Math.max(1, r - 2); d <= r + 4; d += 0.5) {
                    int qx = (int) Math.floor(cx + 0.5 + ux * d), qz = (int) Math.floor(cz + 0.5 + uz * d);
                    if (!level.getBlockState(p.set(qx, y, qz)).isAir()) {
                        int ix = (int) Math.floor(cx + 0.5 + ux * (d - 0.8)), iz = (int) Math.floor(cz + 0.5 + uz * (d - 0.8));
                        if (level.getBlockState(p.set(ix, y, iz)).isAir()) {
                            wx = qx;
                            wz = qz;
                            in = new BlockPos(ix, y, iz);
                        }
                        break;
                    }
                }
                if (in == null || wx < x0 - 8 || wx > x0 + 23 || wz < z0 - 8 || wz > z0 + 23) continue;
                p.set(wx, y, wz);
                // (any solid wall: the abyss's walls wear their mood's rock, obsidian, sculk, soul soil)
                if (!solid(level, p) || !solid(level, p.above()) || !solid(level, p.below())) continue;
                if (Math.abs(in.getX() - wx) + Math.abs(in.getZ() - wz) != 1) continue; // its own face toward the shaft
                level.setBlock(p, (lava ? Blocks.LAVA : Blocks.WATER).defaultBlockState(), 2);
                level.scheduleTick(p.immutable(), lava ? Fluids.LAVA : Fluids.WATER, 0);
                placed++;
                any = true;
            }
            }
            if (placed == 0) {
                // No wall to pour from (the shaft opens straight into a room): pour from the roof over
                // the shaft instead, a sealed roof block within 40 above, so the fall drops through the
                // room and on down the shaft (1 fall in 5 came out dry, test46).
                roof:
                for (int k = 0; k < 9; k++) {
                    int rx = cx + (k % 3) - 1, rz = cz + (k / 3) - 1;
                    if (rx < x0 || rx > x0 + 15 || rz < z0 || rz > z0 + 15) continue;
                    for (int yy = (int) Math.floor(f[1]); yy < (int) Math.floor(f[1]) + 40; yy++) {
                        if (!level.getBlockState(p.set(rx, yy, rz)).isAir()) {
                            if (!level.getBlockState(p.set(rx, yy - 1, rz)).isAir()) break;
                            p.set(rx, yy, rz);
                            boolean held = solid(level, p.above());
                            for (Direction d : Direction.Plane.HORIZONTAL) held &= solid(level, p.relative(d));
                            if (!held) break;
                            level.setBlock(p, (lava ? Blocks.LAVA : Blocks.WATER).defaultBlockState(), 2);
                            level.scheduleTick(p.immutable(), lava ? Fluids.LAVA : Fluids.WATER, 0);
                            y = yy;
                            any = true;
                            break roof;
                        }
                    }
                }
            }
            if (lava) {
                // magma where the lava lands
                for (int fy = y - 1; fy > y - 200; fy--) {
                    BlockPos q = new BlockPos(cx, fy, cz);
                    if (level.getBlockState(q).isAir()) continue;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            BlockPos m = q.offset(dx, 0, dz);
                            if (dx * dx + dz * dz <= 5 && stone(level, m) && level.getBlockState(m.above()).isAir()) {
                                level.setBlock(m, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                            }
                        }
                    }
                    break;
                }
            }
        }
        return any;
    }

    /**
     * Magma rivers: in the abyss, long stretches of the bigger galleries (picked by a broad noise, so a
     * stretch runs on through the next piece) carry a channel of lava in their floor.
     */
    private static boolean magmaRivers(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        java.util.List<double[]> pieces = new java.util.ArrayList<>();
        core.passages().piecesNear(x0 + 8, z0 + 8, 14, pieces);
        boolean any = false;
        for (double[] g : pieces) {
            if (g.length < 13 || g[11] != 3 || g[6] > 1 || g[7] < 5) continue; // deep network tubes and canyons, wide
            double mx = (g[0] + g[3]) / 2, my = (g[1] + g[4]) / 2, mz = (g[2] + g[5]) / 2;
            if (my > AtlasCore.ABYSS_TOP) continue;
            double n = com.seaweed.grandatlas.core.noise.GradientNoise.noise2(0x1A7AL, mx / 160.0, mz / 160.0 + my / 55.0);
            if (n < 0.28) continue;
            double len = Math.hypot(g[3] - g[0], g[5] - g[2]);
            if (len < 1) continue;
            double ux = (g[3] - g[0]) / len, uz = (g[5] - g[2]) / len;
            for (double t = 0; t <= len; t += 0.7) {
                double ax = g[0] + ux * t, az = g[2] + uz * t, ay = g[1] + (g[4] - g[1]) * t / len;
                for (int side = 0; side <= 1; side++) {
                    int x = (int) Math.floor(ax - uz * side), z = (int) Math.floor(az + ux * side);
                    if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                    // down from the axis to the floor
                    int y = (int) Math.floor(ay);
                    BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos(x, y, z);
                    int k = 0;
                    while (k < 14 && level.getBlockState(q).isAir()) {
                        q.move(0, -1, 0);
                        k++;
                    }
                    if (k == 0 || k >= 14) continue;
                    BlockPos bed = q.immutable(); // the floor block
                    if (!stone(level, bed) || !solid(level, bed.below())) continue;
                    boolean held = true;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockState sb = level.getBlockState(bed.relative(d));
                        if (!(sb.isSolid() && sb.getFluidState().isEmpty()) && !sb.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) held = false;
                    }
                    if (!held) continue;
                    level.setBlock(bed, Blocks.LAVA.defaultBlockState(), 2);
                    any = true;
                }
            }
        }
        return any;
    }

    /** Hanging forests: in tall rooms, a dense patch of stalactites, vines in cozy places, weeping vines in the abyss's horror. */
    private static boolean hangingForests(WorldGenLevel level, AtlasCore core, RandomSource r, int x0, int z0) {
        if (r.nextInt(100) >= 40) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int t = 0; t < 30; t++) {
            int x = x0 + 4 + r.nextInt(8), z = z0 + 4 + r.nextInt(8);
            int y = AtlasCore.MIN_Y + 14 + r.nextInt(Moods.TOP - AtlasCore.MIN_Y - 14);
            if (!level.getBlockState(pos.set(x, y, z)).isAir() || core.passages().carve(core.column(x, z), x, y, z) >= 0) continue;
            if (core.passages().inSpiral(x, y, z)) continue;
            int fy = y, cy = y;
            while (fy > y - 30 && level.getBlockState(pos.set(x, fy - 1, z)).isAir()) fy--;
            while (cy < y + 40 && level.getBlockState(pos.set(x, cy + 1, z)).isAir()) cy++;
            if (!solid(level, pos.set(x, cy + 1, z)) || cy - fy < 9) continue;
            Moods.Mood mood = core.moods().at(x, cy, z);
            if (mood == null) continue;
            boolean ab = cy < AtlasCore.ABYSS_TOP;
            int rad = 4 + r.nextInt(3);
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz > rad * rad || r.nextInt(100) >= 45) continue;
                    int px = x + dx, pz = z + dz;
                    int top = roofAt(level, px, fy + 2, pz);
                    if (top == Integer.MIN_VALUE || top - fy < 6) continue;
                    int len = 1 + r.nextInt(Math.min(7, (top - fy) / 2));
                    if (mood == Moods.Mood.COZY && r.nextInt(3) == 0) {
                        for (int i = 0; i < len; i++) {
                            BlockPos q = new BlockPos(px, top - i, pz);
                            if (!level.getBlockState(q).isAir()) break;
                            level.setBlock(q, (i == len - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, r.nextInt(4) == 0), 2);
                        }
                    } else if (ab && mood == Moods.Mood.HORROR && r.nextInt(3) == 0) {
                        for (int i = 0; i < len; i++) {
                            BlockPos q = new BlockPos(px, top - i, pz);
                            if (!level.getBlockState(q).isAir()) break;
                            level.setBlock(q, (i == len - 1 ? Blocks.WEEPING_VINES : Blocks.WEEPING_VINES_PLANT).defaultBlockState(), 2);
                        }
                    } else {
                        hang(level, px, top, pz, len, Direction.DOWN, false);
                    }
                }
            }
            return true;
        }
        return false;
    }

    // ---- the deep: heightened real

    // About 47 kinds in all (the user: triple how many different ones there are, 2026-11-18), four to
    // six a mood in each band, picked evenly.
    private static boolean deep(WorldGenLevel level, RandomSource r, Moods.Mood mood, BlockPos at, int roof) {
        return switch (mood) {
            case AWE -> switch (r.nextInt(5)) {
                case 0 -> dripstones(level, r, at, roof, 3 + r.nextInt(4), true);
                case 1 -> curtain(level, r, at, roof);
                case 2 -> rimstones(level, r, at);
                case 3 -> geode(level, r, at);
                default -> calciteColumn(level, r, at, roof);
            };
            case FEAR -> switch (r.nextInt(5)) {
                case 0 -> cobwebs(level, r, at, roof);
                case 1 -> remains(level, r, at, false);
                case 2 -> rubble(level, r, at);
                case 3 -> deadCamp(level, r, at, roof);
                default -> chains(level, r, at, roof);
            };
            case COZY -> switch (r.nextInt(6)) {
                case 0 -> dripPool(level, r, at, roof);
                case 1 -> mossNook(level, r, at, roof);
                case 2 -> mushrooms(level, r, at);
                case 3 -> seep(level, r, at, false);
                case 4 -> sporeGrotto(level, r, at, roof);
                default -> roots(level, r, at, roof);
            };
            case CLEVER -> switch (r.nextInt(4)) {
                case 0 -> amethyst(level, r, at);
                case 1 -> fossil(level, r, at);
                case 2 -> steppingStones(level, r, at);
                default -> oreSeam(level, r, at);
            };
            case HORROR -> switch (r.nextInt(4)) {
                case 0 -> sculk(level, r, at, r.nextInt(4) == 0);
                case 1 -> veinWeb(level, r, at);
                case 2 -> infested(level, r, at);
                default -> r.nextInt(3) == 0 ? spawnerDen(level, r, at) : bones(level, r, at, false);
            };
        };
    }

    // ---- the abyss: another world

    private static boolean abyss(WorldGenLevel level, RandomSource r, Moods.Mood mood, BlockPos at, int roof) {
        return switch (mood) {
            case AWE -> switch (r.nextInt(5)) {
                case 0 -> spire(level, r, at, roof);
                case 1 -> organ(level, r, at, roof);
                case 2 -> arch(level, r, at, roof);
                case 3 -> lavaCurtain(level, r, at, roof);
                default -> weepingWall(level, r, at);
            };
            case FEAR -> switch (r.nextInt(6)) {
                case 5 -> lavaCurtain(level, r, at, roof);
                case 0 -> remains(level, r, at, true);
                case 1 -> lantern(level, at, roof);
                case 2 -> cage(level, r, at);
                case 3 -> chainedRemains(level, r, at, roof);
                default -> maw(level, r, at, roof);
            };
            case COZY -> switch (r.nextInt(5)) {
                case 4 -> lavaCurtain(level, r, at, roof);
                case 0 -> hearth(level, r, at);
                case 1 -> hotSpring(level, r, at);
                case 2 -> netherGarden(level, r, at);
                default -> seep(level, r, at, true);
            };
            case CLEVER -> switch (r.nextInt(4)) {
                case 0 -> balanced(level, r, at, roof);
                case 1 -> causeway(level, r, at);
                case 2 -> floatingStones(level, r, at, roof);
                default -> tintedWindow(level, r, at);
            };
            case HORROR -> switch (r.nextInt(5)) {
                case 0 -> bones(level, r, at, true);
                case 1 -> soulFire(level, r, at);
                case 2 -> ritual(level, r, at);
                case 3 -> witherRoses(level, r, at);
                default -> ribcage(level, r, at, roof);
            };
        };
    }

    // ---- vignettes

    /** Stalactites over stalagmites, some joined into columns where the gap is small. */
    private static boolean dripstones(WorldGenLevel level, RandomSource r, BlockPos at, int roof, int n, boolean columns) {
        if (roof == -9999) return false;
        boolean any = false;
        for (int k = 0; k < n; k++) {
            int x = at.getX() + r.nextInt(7) - 3, z = at.getZ() + r.nextInt(7) - 3;
            int fy = floorAt(level, x, at.getY(), z), cy = roofAt(level, x, at.getY(), z);
            if (fy == Integer.MIN_VALUE || cy == Integer.MIN_VALUE) continue;
            int gap = cy - fy + 1;
            if (gap < 3) continue;
            if (columns && gap <= 7 && r.nextInt(3) == 0) {
                int down = gap / 2, up = gap - down;
                hang(level, x, cy, z, down, Direction.DOWN, true);
                hang(level, x, fy, z, up, Direction.UP, true);
            } else {
                hang(level, x, cy, z, Math.min(gap / 2, 1 + r.nextInt(4)), Direction.DOWN, false);
                if (r.nextBoolean()) hang(level, x, fy, z, Math.min(gap / 3, 1 + r.nextInt(3)), Direction.UP, false);
            }
            any = true;
        }
        return any;
    }

    /** One spike of pointed dripstone from (x, y, z) going {@code dir}; merged: the tip meets another. */
    private static void hang(WorldGenLevel level, int x, int y, int z, int len, Direction dir, boolean merged) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < len; i++) {
            p.set(x, y + (dir == Direction.UP ? i : -i), z);
            if (!level.getBlockState(p).isAir()) return;
            DripstoneThickness th = i == len - 1 ? (merged ? DripstoneThickness.TIP_MERGE : DripstoneThickness.TIP)
                    : i == len - 2 ? DripstoneThickness.FRUSTUM : i == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            level.setBlock(p, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, dir).setValue(PointedDripstoneBlock.THICKNESS, th), 2);
        }
    }

    private static boolean cobwebs(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        boolean any = false;
        for (int k = 0; k < 4 + r.nextInt(4); k++) {
            int x = at.getX() + r.nextInt(5) - 2, z = at.getZ() + r.nextInt(5) - 2;
            int y = roof != -9999 && r.nextBoolean() ? roofAt(level, x, at.getY(), z) : at.getY() + r.nextInt(2);
            if (y == Integer.MIN_VALUE) continue;
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).isAir() && touchesSolid(level, p)) {
                level.setBlock(p, Blocks.COBWEB.defaultBlockState(), 2);
                any = true;
            }
        }
        return any;
    }

    /** Someone who didn't make it: a skull and a few bones. */
    private static boolean remains(WorldGenLevel level, RandomSource r, BlockPos at, boolean abyss) {
        if (!level.getBlockState(at).isAir()) return false;
        level.setBlock(at, (abyss && r.nextInt(3) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState()
                .setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
        for (int k = 0; k < 2 + r.nextInt(2); k++) {
            BlockPos p = at.offset(r.nextInt(3) - 1, 0, r.nextInt(3) - 1);
            if (p.equals(at) || !level.getBlockState(p).isAir() || !solid(level, p.below())) continue;
            level.setBlock(p, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                    r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z), 2);
        }
        return true;
    }

    /** A still pool under a stalactite that drips into it. */
    private static boolean dripPool(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        BlockPos bed = at.below();
        boolean any = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) == 2 && r.nextBoolean()) continue;
                BlockPos p = bed.offset(dx, 0, dz);
                if (!solid(level, p) || !solid(level, p.below())) continue;
                boolean held = true;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos s = p.relative(d);
                    if (!solid(level, s) && !level.getFluidState(s).is(Fluids.WATER) && !isPoolCell(s, bed)) held = false;
                }
                if (!held || !level.getBlockState(p.above()).isAir()) continue;
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
                any = true;
            }
        }
        if (any && roof != -9999) {
            int cy = roofAt(level, at.getX(), at.getY(), at.getZ());
            if (cy != Integer.MIN_VALUE && cy - at.getY() >= 3) hang(level, at.getX(), cy, at.getZ(), 1 + r.nextInt(2), Direction.DOWN, false);
        }
        return any;
    }

    private static boolean isPoolCell(BlockPos p, BlockPos bed) {
        return p.getY() == bed.getY() && Math.abs(p.getX() - bed.getX()) <= 1 && Math.abs(p.getZ() - bed.getZ()) <= 1;
    }

    /** Moss underfoot, glow lichen on the walls, glow berries hanging: faint light, a place to rest. */
    private static boolean mossNook(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        boolean any = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || r.nextInt(4) == 0) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                BlockPos p = new BlockPos(at.getX() + dx, fy, at.getZ() + dz);
                level.setBlock(p.below(), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                if (r.nextInt(3) == 0) level.setBlock(p, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                any = true;
            }
        }
        // glow lichen on a wall beside
        for (int k = 0; k < 6; k++) {
            BlockPos p = at.offset(r.nextInt(7) - 3, r.nextInt(4), r.nextInt(7) - 3);
            if (!level.getBlockState(p).isAir()) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (solid(level, p.relative(d))) {
                    level.setBlock(p, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true), 2);
                    break;
                }
            }
        }
        if (roof != -9999 && r.nextBoolean()) {
            int x = at.getX() + r.nextInt(3) - 1, z = at.getZ() + r.nextInt(3) - 1;
            int cy = roofAt(level, x, at.getY(), z);
            if (cy != Integer.MIN_VALUE) {
                int len = Math.min(cy - at.getY() - 1, 1 + r.nextInt(4));
                for (int i = 0; i < len; i++) {
                    BlockPos p = new BlockPos(x, cy - i, z);
                    if (!level.getBlockState(p).isAir()) break;
                    level.setBlock(p, (i == len - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                            .setValue(CaveVines.BERRIES, r.nextInt(3) == 0), 2);
                }
            }
        }
        return any;
    }

    /** Amethyst grown in a hollow: small buds on the rock round it, dark until a light finds them. */
    private static boolean amethyst(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int k = 0; k < 10; k++) {
            BlockPos p = at.offset(r.nextInt(5) - 2, r.nextInt(4), r.nextInt(5) - 2);
            if (!level.getBlockState(p).isAir()) continue;
            for (Direction d : Direction.values()) {
                BlockPos s = p.relative(d.getOpposite());
                if (!solid(level, s)) continue;
                level.setBlock(s, Blocks.AMETHYST_BLOCK.defaultBlockState(), 2);
                BlockState bud = (r.nextBoolean() ? Blocks.SMALL_AMETHYST_BUD : Blocks.MEDIUM_AMETHYST_BUD) // light 1-2: found by torchlight, not seen from afar
                        .defaultBlockState().setValue(AmethystClusterBlock.FACING, d);
                level.setBlock(p, bud, 2);
                any = true;
                break;
            }
        }
        return any;
    }

    /** A creature's spine and ribs set into the wall: what lived here before. */
    private static boolean fossil(WorldGenLevel level, RandomSource r, BlockPos at) {
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        // find the wall on that side
        BlockPos w = at.above();
        int k = 0;
        while (k < 6 && level.getBlockState(w).isAir()) {
            w = w.relative(side);
            k++;
        }
        if (!solid(level, w)) return false;
        Direction along = side.getClockWise();
        int len = 5 + r.nextInt(5);
        boolean any = false;
        for (int i = -len / 2; i <= len / 2; i++) {
            BlockPos face = w.relative(along, i);
            if (!stone(level, face)) continue;
            level.setBlock(face, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis()), 2);
            any = true;
            if (i % 2 == 0) {
                for (int dy : new int[] {1, -1}) {
                    BlockPos rib = face.above(dy);
                    if (stone(level, rib)) level.setBlock(rib, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 2);
                }
            }
        }
        return any;
    }

    /** Sculk spreading over the floor and walls round a sensor; now and then a shrieker (it can't summon a warden). */
    private static boolean sculk(WorldGenLevel level, RandomSource r, BlockPos at, boolean shrieker) {
        boolean any = false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10 || r.nextInt(3) == 0) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                BlockPos below = new BlockPos(at.getX() + dx, fy - 1, at.getZ() + dz);
                if (stone(level, below)) {
                    level.setBlock(below, Blocks.SCULK.defaultBlockState(), 2);
                    any = true;
                }
            }
        }
        if (any && level.getBlockState(at).isAir()) {
            level.setBlock(at, (shrieker ? Blocks.SCULK_SHRIEKER : Blocks.SCULK_SENSOR).defaultBlockState(), 2);
        }
        return any;
    }

    /** A heap of bones, a skull or two on top. */
    private static boolean bones(WorldGenLevel level, RandomSource r, BlockPos at, boolean abyss) {
        boolean any = false;
        for (int k = 0; k < 6 + r.nextInt(5); k++) {
            int x = at.getX() + r.nextInt(5) - 2, z = at.getZ() + r.nextInt(5) - 2;
            int fy = floorAt(level, x, at.getY(), z);
            if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
            BlockPos p = new BlockPos(x, fy, z);
            int h = Math.abs(x - at.getX()) + Math.abs(z - at.getZ()) <= 1 && r.nextBoolean() ? 2 : 1;
            for (int i = 0; i < h; i++) {
                if (!level.getBlockState(p.above(i)).isAir()) break;
                level.setBlock(p.above(i), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.values()[r.nextInt(3)]), 2);
                any = true;
            }
        }
        BlockPos top = at;
        while (level.getBlockState(top).is(Blocks.BONE_BLOCK)) top = top.above();
        if (any && level.getBlockState(top).isAir()) {
            level.setBlock(top, (abyss ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
        }
        return any;
    }

    /** An obsidian spire rising from the floor. */
    private static boolean spire(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        int h = 3 + r.nextInt(6);
        if (roof != -9999) h = Math.min(h, roof - at.getY() - 1);
        if (h < 3) return dripstones(level, r, at, roof, 3, true);
        for (int i = 0; i < h; i++) {
            BlockPos p = at.above(i);
            if (!level.getBlockState(p).isAir()) return i > 0;
            level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 2);
            if (i < h / 3) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos s = p.relative(d);
                    if (r.nextInt(3) > 0 && level.getBlockState(s).isAir() && solid(level, s.below())) level.setBlock(s, Blocks.OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    /** A soul lantern hanging alone in the dark: someone left it. */
    private static boolean lantern(WorldGenLevel level, BlockPos at, int roof) {
        if (roof == -9999) return false;
        int cy = roofAt(level, at.getX(), at.getY(), at.getZ());
        if (cy == Integer.MIN_VALUE || cy - at.getY() < 3) return false;
        BlockPos p = new BlockPos(at.getX(), cy, at.getZ());
        level.setBlock(p, Blocks.CHAIN.defaultBlockState(), 2);
        if (level.getBlockState(p.below()).isAir()) {
            level.setBlock(p.below(), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
        }
        return true;
    }

    /** A small lava hearth in the floor, its rim of blackstone: warmth in the abyss. */
    private static boolean hearth(WorldGenLevel level, RandomSource r, BlockPos at) {
        BlockPos bed = at.below();
        if (!solid(level, bed) || !solid(level, bed.below())) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) if (!solid(level, bed.relative(d))) return false;
        level.setBlock(bed, Blocks.LAVA.defaultBlockState(), 2);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos s = at.relative(d);
            if (r.nextBoolean() && level.getBlockState(s).isAir() && solid(level, s.below())) level.setBlock(s, Blocks.BLACKSTONE_SLAB.defaultBlockState(), 2);
        }
        return true;
    }

    /** A boulder balanced on a thin basalt stalk: how is it still standing? */
    private static boolean balanced(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        int stalk = 2 + r.nextInt(3);
        if (roof != -9999 && roof - at.getY() < stalk + 4) return false;
        for (int i = 0; i < stalk; i++) {
            if (!level.getBlockState(at.above(i)).isAir()) return false;
        }
        for (int i = 0; i < stalk; i++) level.setBlock(at.above(i), Blocks.BASALT.defaultBlockState(), 2);
        BlockPos c = at.above(stalk + 1);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 3 && r.nextBoolean()) continue;
                    BlockPos p = c.offset(dx, dy, dz);
                    if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.BLACKSTONE.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    /** Soul soil underfoot and pale blue fire on it. */
    private static boolean soulFire(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int k = 0; k < 5; k++) {
            int x = at.getX() + r.nextInt(5) - 2, z = at.getZ() + r.nextInt(5) - 2;
            int fy = floorAt(level, x, at.getY(), z);
            if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
            BlockPos p = new BlockPos(x, fy, z);
            if (!stone(level, p.below())) continue;
            level.setBlock(p.below(), Blocks.SOUL_SOIL.defaultBlockState(), 2);
            if (r.nextInt(3) == 0) level.setBlock(p, Blocks.SOUL_FIRE.defaultBlockState(), 2);
            any = true;
        }
        return any;
    }

    // ---- more vignettes (the second and third thirds)

    /** A flowstone curtain: a row of stalactites hanging along the roof beside a wall. */
    private static boolean curtain(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999) return false;
        Direction side = wall(level, at.above(), 6);
        if (side == null) return false;
        BlockPos w = toWall(level, at.above(), side).relative(side.getOpposite());
        Direction along = side.getClockWise();
        boolean any = false;
        for (int i = -3; i <= 3; i++) {
            BlockPos c = w.relative(along, i);
            int cy = roofAt(level, c.getX(), at.getY(), c.getZ());
            if (cy == Integer.MIN_VALUE || cy - at.getY() < 3) continue;
            int len = Math.max(1, (int) Math.round((2.5 + 1.5 * Math.cos(i * 0.9)) * (0.7 + 0.6 * r.nextFloat())));
            hang(level, c.getX(), cy, c.getZ(), Math.min(len, cy - at.getY() - 1), Direction.DOWN, false);
            any = true;
        }
        return any;
    }

    /** Rimstone: a pool held up by a lip of calcite on the floor. */
    private static boolean rimstones(WorldGenLevel level, RandomSource r, BlockPos at) {
        int w = 1 + r.nextInt(2);
        for (int dx = -w - 1; dx <= w + 1; dx++) {
            for (int dz = -w - 1; dz <= w + 1; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                if (!solid(level, p.below())) return false;
            }
        }
        for (int dx = -w - 1; dx <= w + 1; dx++) {
            for (int dz = -w - 1; dz <= w + 1; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                boolean rim = Math.abs(dx) == w + 1 || Math.abs(dz) == w + 1;
                if (!level.getBlockState(p).isAir()) continue;
                level.setBlock(p, rim ? Blocks.CALCITE.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** A geode opened by the passage: a hollow lined with amethyst in calcite in smooth basalt. */
    private static boolean geode(WorldGenLevel level, RandomSource r, BlockPos at) {
        Direction side = wall(level, at.above(), 5);
        if (side == null) return false;
        BlockPos c = toWall(level, at.above(), side).relative(side, 1);
        double rr = 3.0 + r.nextFloat();
        int n = (int) Math.ceil(rr);
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > rr) continue;
                    BlockPos p = c.offset(dx, dy, dz);
                    if (!stone(level, p)) continue;
                    BlockState b = d < rr - 1.6 ? Blocks.AIR.defaultBlockState() : d < rr - 1.0 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                            : d < rr - 0.45 ? Blocks.CALCITE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
                    level.setBlock(p, b, 2);
                }
            }
        }
        for (int dx = -n; dx <= n; dx++) {
            for (int dy = -n; dy <= n; dy++) {
                for (int dz = -n; dz <= n; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir() || r.nextInt(3) != 0) continue;
                    for (Direction d : Direction.values()) {
                        if (level.getBlockState(p.relative(d.getOpposite())).is(Blocks.AMETHYST_BLOCK)) {
                            level.setBlock(p, (r.nextBoolean() ? Blocks.SMALL_AMETHYST_BUD : Blocks.MEDIUM_AMETHYST_BUD).defaultBlockState()
                                    .setValue(AmethystClusterBlock.FACING, d), 2);
                            break;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** A pale calcite column from floor to roof, flecked with dripstone. */
    private static boolean calciteColumn(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999 || roof - at.getY() > 14 || roof - at.getY() < 3) return false;
        for (int y = at.getY(); y <= roof; y++) {
            BlockPos p = new BlockPos(at.getX(), y, at.getZ());
            if (!level.getBlockState(p).isAir()) return false;
        }
        for (int y = at.getY(); y <= roof; y++) {
            BlockPos p = new BlockPos(at.getX(), y, at.getZ());
            level.setBlock(p, Blocks.CALCITE.defaultBlockState(), 2);
            int th = y - at.getY() < 2 || roof - y < 2 ? 1 : 0; // flared at foot and head
            if (th == 1) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (r.nextInt(3) > 0 && level.getBlockState(p.relative(d)).isAir()) level.setBlock(p.relative(d), Blocks.CALCITE.defaultBlockState(), 2);
                }
            } else if (r.nextInt(3) == 0) {
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
                if (level.getBlockState(p.relative(d)).isAir() && solid(level, p.relative(d).below())) {
                    level.setBlock(p.relative(d), Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 2);
                }
            }
        }
        return true;
    }

    /** Rubble from a collapse heaped against a wall, a bone sticking out of it. */
    private static boolean rubble(WorldGenLevel level, RandomSource r, BlockPos at) {
        BlockState[] mix = {Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()};
        boolean any = false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int h = (int) Math.round(3 - Math.sqrt(dx * dx + dz * dz) + r.nextFloat() - 0.3);
                for (int i = 0; i < h; i++) {
                    int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                    if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) break;
                    level.setBlock(new BlockPos(at.getX() + dx, fy, at.getZ() + dz), mix[r.nextInt(mix.length)], 2);
                    any = true;
                }
            }
        }
        BlockPos top = at.above(3);
        while (top.getY() > at.getY() && level.getBlockState(top).isAir()) top = top.below();
        if (any && level.getBlockState(top.above()).isAir()) {
            level.setBlock(top.above(), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), 2);
        }
        return any;
    }

    /** A camp that went wrong: a cold campfire, a skull, webs gathering. */
    private static boolean deadCamp(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (!level.getBlockState(at).isAir()) return false;
        level.setBlock(at, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 2);
        BlockPos s = at.offset(r.nextBoolean() ? 2 : -2, 0, r.nextInt(3) - 1);
        if (level.getBlockState(s).isAir() && solid(level, s.below())) {
            level.setBlock(s, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
        }
        BlockPos b = at.offset(r.nextInt(3) - 1, 0, r.nextBoolean() ? 2 : -2);
        if (level.getBlockState(b).isAir() && solid(level, b.below())) level.setBlock(b, Blocks.BARREL.defaultBlockState(), 2);
        cobwebs(level, r, at, roof);
        return true;
    }

    /** Chains hanging from the roof, some to the floor: what were they for? */
    private static boolean chains(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999) return false;
        boolean any = false;
        for (int k = 0; k < 3 + r.nextInt(4); k++) {
            int x = at.getX() + r.nextInt(5) - 2, z = at.getZ() + r.nextInt(5) - 2;
            int cy = roofAt(level, x, at.getY(), z);
            if (cy == Integer.MIN_VALUE) continue;
            int len = r.nextInt(3) == 0 ? cy - at.getY() + 1 : 2 + r.nextInt(Math.max(1, cy - at.getY() - 1));
            for (int i = 0; i < len; i++) {
                BlockPos p = new BlockPos(x, cy - i, z);
                if (!level.getBlockState(p).isAir()) break;
                level.setBlock(p, Blocks.CHAIN.defaultBlockState(), 2);
                any = true;
            }
        }
        return any;
    }

    /** A little grove of mushrooms on a patch of mycelium, one grown into a small cap. */
    private static boolean mushrooms(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 6) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                BlockPos p = new BlockPos(at.getX() + dx, fy, at.getZ() + dz);
                if (!stone(level, p.below())) continue;
                level.setBlock(p.below(), (r.nextBoolean() ? Blocks.MYCELIUM : Blocks.PODZOL).defaultBlockState(), 2);
                if (r.nextInt(3) == 0) level.setBlock(p, (r.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState(), 2);
                any = true;
            }
        }
        // a small cap on a short stem
        if (any && level.getBlockState(at).isAir() && level.getBlockState(at.above(1)).isAir() && level.getBlockState(at.above(2)).isAir()) {
            level.setBlock(at, Blocks.MUSHROOM_STEM.defaultBlockState(), 2);
            level.setBlock(at.above(), Blocks.MUSHROOM_STEM.defaultBlockState(), 2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = at.offset(dx, 2, dz);
                    if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState(), 2);
                }
            }
        }
        return any;
    }

    /** A spring seeping out of the wall a few blocks up and running down it (lava in the abyss). */
    private static boolean seep(WorldGenLevel level, RandomSource r, BlockPos at, boolean lava) {
        Direction side = wall(level, at.above(3), 6);
        if (side == null) return false;
        BlockPos face = toWall(level, at.above(3), side);
        if (!stone(level, face)) return false;
        for (Direction d : Direction.values()) {
            if (d == side.getOpposite()) continue;
            BlockPos n = face.relative(d);
            if (!solid(level, n)) return false; // the seep's other sides must hold it
        }
        if (!level.getBlockState(face.relative(side.getOpposite())).isAir()) return false;
        level.setBlock(face, lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
        level.scheduleTick(face, lava ? Fluids.LAVA : Fluids.WATER, 0);
        if (lava) {
            BlockPos under = face.below();
            if (stone(level, under)) level.setBlock(under, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        } else {
            for (int k = 0; k < 4; k++) {
                BlockPos p = face.offset(r.nextInt(3) - 1, r.nextInt(3) - 1, 0).relative(side.getOpposite());
                if (level.getBlockState(p).isAir() && touchesSolid(level, p)) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (solid(level, p.relative(d))) {
                            level.setBlock(p, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true), 2);
                            break;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** A spore blossom drifting spores over moss and a flowering azalea: the coziest corner of the deep. */
    private static boolean sporeGrotto(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999) return false;
        int cy = roofAt(level, at.getX(), at.getY(), at.getZ());
        if (cy == Integer.MIN_VALUE || cy - at.getY() < 3) return false;
        level.setBlock(new BlockPos(at.getX(), cy, at.getZ()), Blocks.SPORE_BLOSSOM.defaultBlockState(), 2);
        mossNook(level, r, at, -9999);
        BlockPos b = at.offset(r.nextInt(3) - 1, 0, r.nextInt(3) - 1);
        int fy = floorAt(level, b.getX(), at.getY(), b.getZ());
        if (fy != Integer.MIN_VALUE) {
            BlockPos p = new BlockPos(b.getX(), fy, b.getZ());
            if (level.getBlockState(p.below()).is(Blocks.MOSS_BLOCK) || stone(level, p.below())) {
                level.setBlock(p.below(), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                level.setBlock(p, (r.nextBoolean() ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA).defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** Roots of something far above, hanging through the roof. */
    private static boolean roots(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999) return false;
        boolean any = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || r.nextInt(4) == 0) continue;
                int cy = roofAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (cy == Integer.MIN_VALUE) continue;
                BlockPos ceil = new BlockPos(at.getX() + dx, cy + 1, at.getZ() + dz);
                if (!stone(level, ceil)) continue;
                level.setBlock(ceil, Blocks.ROOTED_DIRT.defaultBlockState(), 2);
                level.setBlock(ceil.below(), Blocks.HANGING_ROOTS.defaultBlockState(), 2);
                any = true;
            }
        }
        return any;
    }

    /** A seam of ore running along the wall: the rock showing how it formed. */
    private static boolean oreSeam(WorldGenLevel level, RandomSource r, BlockPos at) {
        Direction side = wall(level, at.above(), 6);
        if (side == null) return false;
        BlockPos w = toWall(level, at.above(), side);
        Direction along = side.getClockWise();
        BlockState ore = switch (r.nextInt(4)) {
            case 0 -> Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
            case 1 -> Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
            case 2 -> Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
            default -> Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        };
        boolean any = false;
        double slope = (r.nextFloat() - 0.5) * 0.6;
        for (int i = -5; i <= 5; i++) {
            BlockPos p = w.relative(along, i).above((int) Math.round(i * slope));
            // set into the wall face: the first rock from the passage
            for (int k = 0; k < 3; k++) {
                if (stone(level, p)) {
                    level.setBlock(p, ore, 2);
                    any = true;
                    break;
                }
                p = p.relative(side);
            }
        }
        return any;
    }

    /** A shallow pool across the floor with stones to step on. */
    private static boolean steppingStones(WorldGenLevel level, RandomSource r, BlockPos at) {
        BlockPos bed = at.below();
        int w = 2;
        for (int dx = -w - 1; dx <= w + 1; dx++) {
            for (int dz = -w - 1; dz <= w + 1; dz++) {
                if (!solid(level, bed.offset(dx, 0, dz)) || !solid(level, bed.offset(dx, -1, dz))) return false;
            }
        }
        for (int dx = -w; dx <= w; dx++) {
            for (int dz = -w; dz <= w; dz++) {
                BlockPos p = bed.offset(dx, 0, dz);
                boolean stoneStep = (dx + dz) % 2 == 0 && Math.abs(dx - dz) <= 1 && r.nextBoolean();
                level.setBlock(p, stoneStep ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** Sculk veins creeping over the floor and walls from a catalyst: it feeds on what dies here. */
    private static boolean veinWeb(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int k = 0; k < 24; k++) {
            BlockPos p = at.offset(r.nextInt(7) - 3, r.nextInt(4), r.nextInt(7) - 3);
            if (!level.getBlockState(p).isAir()) continue;
            for (Direction d : Direction.values()) {
                if (solid(level, p.relative(d))) {
                    level.setBlock(p, Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true), 2);
                    any = true;
                    break;
                }
            }
        }
        if (any && (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.SCULK_VEIN))) {
            if (stone(level, at.below())) level.setBlock(at.below(), Blocks.SCULK.defaultBlockState(), 2);
            level.setBlock(at, Blocks.SCULK_CATALYST.defaultBlockState(), 2);
            any = true;
        }
        return any;
    }

    /** Silverfish in the walls: nothing shows, until you mine. A skull warns, if you notice. */
    private static boolean infested(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int k = 0; k < 40; k++) {
            BlockPos p = at.offset(r.nextInt(9) - 4, r.nextInt(6) - 1, r.nextInt(9) - 4);
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.DEEPSLATE)) level.setBlock(p, Blocks.INFESTED_DEEPSLATE.defaultBlockState(), 2);
            else if (s.is(Blocks.STONE)) level.setBlock(p, Blocks.INFESTED_STONE.defaultBlockState(), 2);
            else continue;
            any = true;
        }
        if (any && level.getBlockState(at).isAir()) level.setBlock(at, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
        return any;
    }

    /** A den: a spawner among bones and webs. */
    private static boolean spawnerDen(WorldGenLevel level, RandomSource r, BlockPos at) {
        if (!level.getBlockState(at).isAir()) return false;
        level.setBlock(at, Blocks.SPAWNER.defaultBlockState(), 2);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity sbe) {
            net.minecraft.world.entity.EntityType<?> type = switch (r.nextInt(3)) {
                case 0 -> net.minecraft.world.entity.EntityType.SKELETON;
                case 1 -> net.minecraft.world.entity.EntityType.CAVE_SPIDER;
                default -> net.minecraft.world.entity.EntityType.ZOMBIE;
            };
            sbe.setEntityId(type, r);
        }
        bones(level, r, at.offset(2, 0, 0), false);
        cobwebs(level, r, at, -9999);
        return true;
    }

    /** Basalt pipes standing in a cluster, like an organ no one plays. */
    private static boolean organ(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        boolean any = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (r.nextInt(3) == 0) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                int h = 2 + r.nextInt(6) - (Math.abs(dx) + Math.abs(dz));
                for (int i = 0; i < h; i++) {
                    BlockPos p = new BlockPos(at.getX() + dx, fy + i, at.getZ() + dz);
                    if (!level.getBlockState(p).isAir() || roof != -9999 && p.getY() >= roof) break;
                    level.setBlock(p, (i == h - 1 && r.nextInt(3) == 0 ? Blocks.POLISHED_BASALT : Blocks.BASALT).defaultBlockState(), 2);
                    any = true;
                }
            }
        }
        return any;
    }

    /** An arch of obsidian over the floor: a gate to nowhere. */
    private static boolean arch(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        int h = 4 + r.nextInt(3), w = 2 + r.nextInt(2);
        if (roof != -9999 && roof - at.getY() < h + 1) return false;
        Direction across = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        boolean any = false;
        for (int i = 0; i <= 12; i++) {
            double a = Math.PI * i / 12;
            int ox = (int) Math.round(Math.cos(a) * w), oy = (int) Math.round(Math.sin(a) * h);
            for (int y = (i == 0 || i == 12) ? 0 : oy; y <= oy; y++) {
                BlockPos p = at.relative(across, ox).above(y);
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 2);
                    any = true;
                }
            }
        }
        return any;
    }

    /** A wall split by a seam of obsidian running down from high up: dark glass in the rock. */
    private static boolean weepingWall(WorldGenLevel level, RandomSource r, BlockPos at) {
        Direction side = wall(level, at.above(2), 6);
        if (side == null) return false;
        BlockPos w = toWall(level, at.above(), side);
        Direction along = side.getClockWise();
        boolean any = false;
        int off = 0;
        for (int i = 0; i < 6 + r.nextInt(4); i++) {
            off += r.nextInt(3) - 1;
            BlockPos p = w.relative(along, off).above(i);
            for (int k = 0; k < 2; k++) {
                if (stone(level, p)) {
                    level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 2);
                    any = true;
                    break;
                }
                p = p.relative(side);
            }
        }
        return any;
    }

    /** An iron-barred cell against the wall, a skull inside. */
    private static boolean cage(WorldGenLevel level, RandomSource r, BlockPos at) {
        Direction side = wall(level, at, 5);
        if (side == null) return false;
        BlockPos in = toWall(level, at, side).relative(side.getOpposite());
        if (!solid(level, in.below())) return false;
        level.setBlock(in, (r.nextBoolean() ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
        for (int y = 0; y < 3; y++) {
            for (Direction d : new Direction[] {side.getOpposite(), side.getClockWise(), side.getCounterClockWise()}) {
                BlockPos p = in.above(y).relative(d);
                if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 2);
            }
            BlockPos top = in.above(2);
            if (y == 2 && level.getBlockState(top).isAir()) level.setBlock(top, Blocks.IRON_BARS.defaultBlockState(), 2);
        }
        return true;
    }

    /** Chains from the roof down to a heap of bones. */
    private static boolean chainedRemains(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999) return false;
        bones(level, r, at, true);
        for (int k = 0; k < 2 + r.nextInt(2); k++) {
            int x = at.getX() + r.nextInt(3) - 1, z = at.getZ() + r.nextInt(3) - 1;
            int cy = roofAt(level, x, at.getY() + 2, z);
            if (cy == Integer.MIN_VALUE) continue;
            for (int y = cy; y > at.getY(); y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!level.getBlockState(p).isAir()) break;
                level.setBlock(p, Blocks.CHAIN.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** A maw: rows of dripstone teeth above and below a stretch of wall. */
    private static boolean maw(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999 || roof - at.getY() < 4) return false;
        Direction side = wall(level, at.above(), 6);
        if (side == null) return false;
        BlockPos w = toWall(level, at.above(), side).relative(side.getOpposite());
        Direction along = side.getClockWise();
        boolean any = false;
        for (int i = -3; i <= 3; i++) {
            BlockPos c = w.relative(along, i);
            int cy = roofAt(level, c.getX(), at.getY(), c.getZ()), fy = floorAt(level, c.getX(), at.getY(), c.getZ());
            if (cy == Integer.MIN_VALUE || fy == Integer.MIN_VALUE || cy - fy < 4) continue;
            hang(level, c.getX(), cy, c.getZ(), 1 + r.nextInt(2), Direction.DOWN, false);
            hang(level, c.getX(), fy, c.getZ(), 1 + r.nextInt(2), Direction.UP, false);
            any = true;
        }
        return any;
    }

    /** A hot spring: a pool over magma, bubbles rising through it. */
    private static boolean hotSpring(WorldGenLevel level, RandomSource r, BlockPos at) {
        BlockPos bed = at.below();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!solid(level, bed.offset(dx, 0, dz)) || !solid(level, bed.offset(dx, -1, dz))) return false;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(bed.offset(dx, -1, dz), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                level.setBlock(bed.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), 2);
                level.scheduleTick(bed.offset(dx, 0, dz), Fluids.WATER, 0);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                if (stone(level, bed.offset(dx, 0, dz))) level.setBlock(bed.offset(dx, 0, dz), Blocks.SMOOTH_BASALT.defaultBlockState(), 2);
            }
        }
        return true;
    }

    /** A garden of warped or crimson fungus: life, but not as we know it. */
    private static boolean netherGarden(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean warped = r.nextBoolean();
        boolean any = false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10 || r.nextInt(4) == 0) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                BlockPos p = new BlockPos(at.getX() + dx, fy, at.getZ() + dz);
                if (!stone(level, p.below())) continue;
                level.setBlock(p.below(), (warped ? Blocks.WARPED_NYLIUM : Blocks.CRIMSON_NYLIUM).defaultBlockState(), 2);
                int roll = r.nextInt(6);
                if (roll == 0) level.setBlock(p, (warped ? Blocks.WARPED_FUNGUS : Blocks.CRIMSON_FUNGUS).defaultBlockState(), 2);
                else if (roll < 3) level.setBlock(p, (warped ? Blocks.WARPED_ROOTS : Blocks.CRIMSON_ROOTS).defaultBlockState(), 2);
                else if (roll == 3 && warped) level.setBlock(p, Blocks.NETHER_SPROUTS.defaultBlockState(), 2);
                any = true;
            }
        }
        return any;
    }

    /** Columnar basalt underfoot: hexagon-like stepping tops, set by the cooling of old lava. */
    private static boolean causeway(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 11) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                BlockPos p = new BlockPos(at.getX() + dx, fy, at.getZ() + dz);
                if (stone(level, p.below())) level.setBlock(p.below(), Blocks.POLISHED_BASALT.defaultBlockState(), 2);
                int cell = Math.floorMod(Math.floorDiv(at.getX() + dx, 2) * 3 + Math.floorDiv(at.getZ() + dz, 2) * 5, 4);
                for (int i = 0; i < cell - 1; i++) {
                    if (!level.getBlockState(p.above(i)).isAir()) break;
                    level.setBlock(p.above(i), Blocks.BASALT.defaultBlockState(), 2);
                }
                any = true;
            }
        }
        return any;
    }

    /** Stones hanging in the air over the floor, touching nothing. */
    private static boolean floatingStones(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        int top = roof == -9999 ? at.getY() + 8 : roof - 2;
        if (top - at.getY() < 4) return false;
        boolean any = false;
        for (int k = 0; k < 4 + r.nextInt(5); k++) {
            BlockPos p = at.offset(r.nextInt(7) - 3, 2 + r.nextInt(top - at.getY() - 2), r.nextInt(7) - 3);
            boolean clear = level.getBlockState(p).isAir();
            for (Direction d : Direction.values()) clear &= level.getBlockState(p.relative(d)).isAir();
            if (!clear) continue;
            level.setBlock(p, (r.nextInt(5) == 0 ? Blocks.GILDED_BLACKSTONE : Blocks.BLACKSTONE).defaultBlockState(), 2);
            any = true;
        }
        return any;
    }

    /** A window of tinted glass in the wall, an ember glowing behind it. */
    private static boolean tintedWindow(WorldGenLevel level, RandomSource r, BlockPos at) {
        Direction side = wall(level, at.above(2), 5);
        if (side == null) return false;
        BlockPos w = toWall(level, at.above(2), side);
        Direction along = side.getClockWise();
        boolean any = false;
        for (int i = -1; i <= 1; i++) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos p = w.relative(along, i).above(dy);
                if (!stone(level, p)) continue;
                level.setBlock(p, Blocks.TINTED_GLASS.defaultBlockState(), 2);
                any = true;
            }
        }
        BlockPos behind = w.relative(side);
        if (any && stone(level, behind)) level.setBlock(behind, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        return any;
    }

    /** A ritual: candles burning in a ring round skulls on soul soil. */
    private static boolean ritual(WorldGenLevel level, RandomSource r, BlockPos at) {
        if (!stone(level, at.below())) return false;
        level.setBlock(at.below(), Blocks.SOUL_SOIL.defaultBlockState(), 2);
        level.setBlock(at, Blocks.WITHER_SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x = at.getX() + (int) Math.round(Math.cos(a) * 2), z = at.getZ() + (int) Math.round(Math.sin(a) * 2);
            int fy = floorAt(level, x, at.getY(), z);
            if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 1) continue;
            level.setBlock(new BlockPos(x, fy, z), Blocks.CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + r.nextInt(3))
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, r.nextInt(4) > 0), 2);
            any = true;
        }
        return any;
    }

    /** Wither roses on soul soil: don't touch them. */
    private static boolean witherRoses(WorldGenLevel level, RandomSource r, BlockPos at) {
        boolean any = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || r.nextInt(3) == 0) continue;
                int fy = floorAt(level, at.getX() + dx, at.getY(), at.getZ() + dz);
                if (fy == Integer.MIN_VALUE || Math.abs(fy - at.getY()) > 2) continue;
                BlockPos p = new BlockPos(at.getX() + dx, fy, at.getZ() + dz);
                if (!stone(level, p.below())) continue;
                level.setBlock(p.below(), Blocks.SOUL_SOIL.defaultBlockState(), 2);
                if (r.nextBoolean()) level.setBlock(p, Blocks.WITHER_ROSE.defaultBlockState(), 2);
                any = true;
            }
        }
        return any;
    }

    /** The ribcage of something huge, arching over the passage. */
    private static boolean ribcage(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        int w = 3 + r.nextInt(2);
        if (roof != -9999 && roof - at.getY() < w) w = roof - at.getY() - 1;
        if (w < 2) return bones(level, r, at, true);
        Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(r), across = along.getClockWise();
        boolean any = false;
        for (int rib = -2; rib <= 2; rib++) {
            for (int i = 0; i <= 14; i++) {
                double a = Math.PI * i / 14;
                BlockPos p = at.relative(along, rib * 2).relative(across, (int) Math.round(Math.cos(a) * w)).above((int) Math.round(Math.sin(a) * w));
                if (level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, i > 3 && i < 11 ? across.getAxis() : Direction.Axis.Y), 2);
                    any = true;
                }
            }
        }
        // the spine along the top
        for (int i = -5; i <= 5; i++) {
            BlockPos p = at.relative(along, i).above(w);
            if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis()), 2);
        }
        return any;
    }

    /**
     * A lava curtain: lava pouring from the roof straight across the passage, with a two-block gap in
     * it to walk through (the user, 2026-11-18). Each lava column lands in a trench of still lava cut
     * into the floor under it, so nothing spreads into the gap, whose floor stays rock.
     */
    private static boolean lavaCurtain(WorldGenLevel level, RandomSource r, BlockPos at, int roof) {
        if (roof == -9999 || roof - at.getY() < 4) return false;
        // The passage runs the way the open air goes furthest; the curtain crosses it.
        Direction along = null;
        int best = -1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int n = 0;
            BlockPos q = at;
            while (n < 24 && level.getBlockState(q.relative(d)).isAir()) {
                q = q.relative(d);
                n++;
            }
            if (n > best) {
                best = n;
                along = d;
            }
        }
        if (best < 6) return false;
        Direction across = along.getClockWise();
        // Wall to wall across, through this point.
        int lo = 0, hi = 0;
        while (lo > -12 && level.getBlockState(at.relative(across, lo - 1)).isAir()) lo--;
        while (hi < 12 && level.getBlockState(at.relative(across, hi + 1)).isAir()) hi++;
        int width = hi - lo + 1;
        if (width < 5 || width > 20) return false;
        int gap = lo + width / 2 - 1; // the gap: offsets gap and gap + 1
        java.util.List<int[]> cols = new java.util.ArrayList<>(); // {offset, floor y, roof y}
        for (int o = lo; o <= hi; o++) {
            if (o == gap || o == gap + 1) continue;
            BlockPos c = at.relative(across, o);
            int fy = floorAt(level, c.getX(), at.getY(), c.getZ());
            int cy = roofAt(level, c.getX(), Math.max(at.getY(), fy == Integer.MIN_VALUE ? at.getY() : fy), c.getZ());
            if (fy == Integer.MIN_VALUE || cy == Integer.MIN_VALUE || cy - fy < 3) continue;
            cols.add(new int[] {o, fy, cy});
        }
        if (cols.size() < 3) return false;
        boolean any = false;
        for (int[] c : cols) {
            BlockPos base = at.relative(across, c[0]);
            BlockPos src = new BlockPos(base.getX(), c[2] + 1, base.getZ()), bed = new BlockPos(base.getX(), c[1] - 1, base.getZ());
            // the source in the roof: held on every side but its open underside
            boolean held = solid(level, src.above());
            for (Direction d : Direction.Plane.HORIZONTAL) held &= solid(level, src.relative(d)) || level.getFluidState(src.relative(d)).is(net.minecraft.tags.FluidTags.LAVA);
            // the trench in the floor: held along the passage by rock, and over rock
            boolean trench = solid(level, bed.below()) && solid(level, bed.relative(along)) && solid(level, bed.relative(along.getOpposite()));
            if (!held || !trench || !solid(level, src) || !solid(level, bed)) continue;
            level.setBlock(bed, Blocks.LAVA.defaultBlockState(), 2);
            level.setBlock(src, Blocks.LAVA.defaultBlockState(), 2);
            level.scheduleTick(src, Fluids.LAVA, 0);
            any = true;
        }
        return any;
    }

    /** The first side (of four) with a wall within n blocks of p, or null. */
    private static Direction wall(WorldGenLevel level, BlockPos p, int n) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = p;
            for (int k = 0; k < n; k++) {
                q = q.relative(d);
                if (solid(level, q)) return d;
                if (!level.getBlockState(q).isAir()) break;
            }
        }
        return null;
    }

    /** The first solid block from p toward side. */
    private static BlockPos toWall(WorldGenLevel level, BlockPos p, Direction side) {
        BlockPos q = p;
        for (int k = 0; k < 8 && !solid(level, q); k++) q = q.relative(side);
        return q;
    }

    // ---- helpers

    private static boolean solid(WorldGenLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && s.isSolid();
    }

    /** Plain rock that may be replaced (not ores' neighbours of note: any natural stone). */
    private static boolean stone(WorldGenLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.GRANITE) || s.is(Blocks.PACKED_MUD);
    }

    private static boolean touchesSolid(WorldGenLevel level, BlockPos p) {
        for (Direction d : Direction.values()) if (solid(level, p.relative(d))) return true;
        return false;
    }

    /** The air block on the floor at (x, z) near y (within 4), or MIN_VALUE. */
    private static int floorAt(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + 3, z);
        for (int i = 0; i < 8; i++, p.move(0, -1, 0)) {
            if (level.getBlockState(p).isAir() && solid(level, p.below())) return p.getY();
        }
        return Integer.MIN_VALUE;
    }

    /** The air block under the roof at (x, z) above y (within 30), or MIN_VALUE. */
    private static int roofAt(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        if (!level.getBlockState(p).isAir()) return Integer.MIN_VALUE;
        for (int i = 0; i < 30; i++) {
            if (solid(level, p.above())) return p.getY();
            p.move(0, 1, 0);
            if (!level.getBlockState(p).isAir()) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }
}
