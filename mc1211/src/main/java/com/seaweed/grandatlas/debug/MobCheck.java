package com.seaweed.grandatlas.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Pair;
import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /grandatlas mobcheck <biome> <category> <x> <z>}, e.g. {@code fungal_forest monster} or
 * {@code crystal_caves underground_water_creature}: finds the nearest such Grand Atlas biome to x z,
 * gathers its spawn spots (cave floors, or water for the water categories) and runs the natural
 * spawner's own checks there for picks from the biome's list in that category, with no player
 * needed (the real spawner only works near one). Reports the list, what passed and what refused;
 * keeps the Grand Atlas mobs it spawned for inspection and removes the rest.
 */
public final class MobCheck {
    private MobCheck() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> e.getDispatcher().register(Commands.literal("grandatlas")
                .then(Commands.literal("mobcheck").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("biome", StringArgumentType.word()).then(Commands.argument("category", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "biome"),
                                                StringArgumentType.getString(ctx, "category"),
                                                IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"))))))))));
    }

    private static void say(CommandSourceStack source, String line) {
        GrandAtlas.LOG.info("MOBCHECK: {}", line);
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static int run(CommandSourceStack source, String biomeName, String categoryName, int x, int z) {
        ServerLevel level = source.getServer().overworld();
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, biomeName));
        MobCategory category = MobCategory.valueOf(categoryName.toUpperCase(Locale.ROOT));
        boolean water = category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT || category == MobCategory.AXOLOTLS;
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), new BlockPos(x, -100, z), 6400, 32, 32);
        if (found == null) {
            say(source, "no " + biomeName + " within 6400 blocks of " + x + " " + z);
            return 0;
        }
        BlockPos centre = found.getFirst();
        var list = found.getSecond().value().getMobSettings().getMobs(category).unwrap();
        int total = list.stream().mapToInt(d -> d.getWeight().asInt()).sum();
        StringBuilder entries = new StringBuilder();
        list.forEach(d -> entries.append(String.format(" %s:%d(%d-%d)", EntityType.getKey(d.type).getPath(), d.getWeight().asInt(), d.minCount, d.maxCount)));
        say(source, biomeName + " at " + centre.toShortString() + "; " + categoryName + " list, total weight " + total + ":" + entries);

        // The biome fills a volume of rock as well as its caves: first gather its spawn spots from
        // columns around the centre, over the whole underground.
        RandomSource random = RandomSource.create(centre.asLong());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<BlockPos> spots = new ArrayList<>();
        for (int c = 0; c < 400; c++) {
            int cx = centre.getX() + random.nextInt(161) - 80, cz = centre.getZ() + random.nextInt(161) - 80;
            level.getChunk(cx >> 4, cz >> 4);
            for (int y = 40; y > -500; y--) {
                pos.set(cx, y, cz);
                boolean spot = water ? level.getFluidState(pos).is(FluidTags.WATER)
                        : level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir();
                if (spot && level.getBiome(pos).is(key)) spots.add(pos.immutable());
            }
        }
        say(source, spots.size() + " " + (water ? "water" : "floor") + " spots of " + biomeName + " in 400 columns around it");

        int passed = 0;
        Map<String, Integer> spawned = new TreeMap<>();
        Map<String, Integer> refused = new TreeMap<>();
        for (int i = 0; i < 6000 && !spots.isEmpty(); i++) {
            pos.set(spots.get(random.nextInt(spots.size())));
            var pick = level.getChunkSource().getGenerator().getMobsAt(level.getBiome(pos), level.structureManager(), category, pos).getRandom(random);
            if (pick.isEmpty()) continue;
            EntityType<?> type = pick.get().type;
            String id = EntityType.getKey(type).getPath();
            if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) { refused.merge(id + ": placement", 1, Integer::sum); continue; }
            if (!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)) { refused.merge(id + ": spawn rule", 1, Integer::sum); continue; }
            if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) { refused.merge(id + ": no room", 1, Integer::sum); continue; }
            if (!(type.create(level) instanceof Mob mob)) continue;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            if (!EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
                refused.merge(id + ": mob check", 1, Integer::sum);
                mob.discard();
                continue;
            }
            passed++;
            spawned.merge(EntityType.getKey(type).toString(), 1, Integer::sum);
            if (EntityType.getKey(type).getNamespace().equals(GrandAtlas.MOD_ID)) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
                level.addFreshEntity(mob);
            } else {
                mob.discard();
            }
        }
        say(source, String.format("%d passed every spawn check: %s", passed, spawned));
        say(source, "refused: " + refused);
        return passed;
    }
}
