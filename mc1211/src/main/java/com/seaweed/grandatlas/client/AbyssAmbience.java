package com.seaweed.grandatlas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cave rework R8, the abyss as another world (the user's Rule of Cool answer 13): below y -250 the
 * air itself changes. Fog can't do it (Distant Horizons turns vanilla fog off), so it's particles and
 * sound, sparse and faint, read from the rock around you: each mood wears its own rock in the abyss
 * (world/Strata), so the rock says where you are.
 * <ul>
 *   <li><b>Awe</b> (obsidian, amethyst): faint motes of light drifting; now and then a chime of
 *       amethyst somewhere off in the dark.</li>
 *   <li><b>Fear</b> (basalt, blackstone): ash falling; the deltas' rumble far away.</li>
 *   <li><b>Cozy</b> (tuff, magma): embers rising; the crimson forest's murmur.</li>
 *   <li><b>Clever</b> (gilded blackstone): small glints; the warped forest's whisper.</li>
 *   <li><b>Horror</b> (soul soil, sculk, bone): souls rising off the ground; the soul valley's wail,
 *       and very rarely a heartbeat that isn't yours.</li>
 * </ul>
 * Sounds play low (pitch 0.55-0.8), so even the familiar ones sound like somewhere else. Client only:
 * works on any server, needs nothing from the world but the blocks you can see.
 */
public final class AbyssAmbience {
    private AbyssAmbience() {}

    private enum Air { AWE, FEAR, COZY, CLEVER, HORROR }

    private static final RandomSource R = RandomSource.create();
    private static Air air = Air.FEAR;
    private static int nextSound = 200, sampleIn;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.dimension() != Level.OVERWORLD || mc.level.getMinBuildHeight() > -200) return;
        double y = mc.player.getY();
        // in from y -250, full by -290
        double s = Math.max(0, Math.min(1, (-250 - y) / 40.0));
        if (s <= 0) return;
        if (--sampleIn <= 0) {
            air = sample(mc);
            sampleIn = 40;
        }
        // particles: a few a tick at most, round the player
        // (full-bright kinds where they'd otherwise vanish in the dark: the user saw only the sparkles, 2026-11-19)
        int tries = R.nextFloat() < s ? 3 : 0;
        for (int i = 0; i < tries; i++) {
            double px = mc.player.getX() + (R.nextDouble() - 0.5) * 24, py = y + (R.nextDouble() - 0.4) * 12, pz = mc.player.getZ() + (R.nextDouble() - 0.5) * 24;
            BlockPos bp = BlockPos.containing(px, py, pz);
            if (!mc.level.getBlockState(bp).isAir()) continue;
            switch (air) {
                case AWE -> { if (R.nextInt(4) == 0) mote(mc, R.nextInt(3) == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD, px, py, pz, 0.004); }
                case FEAR -> {
                    if (R.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.FALLING_OBSIDIAN_TEAR, px, py, pz, 0, 0, 0);
                    else mote(mc, ParticleTypes.WHITE_ASH, px, py, pz, 0);
                }
                case COZY -> {
                    if (R.nextInt(2) == 0) mc.level.addParticle(ParticleTypes.SMALL_FLAME, px, py, pz, 0, 0.01 + R.nextDouble() * 0.02, 0);
                    else if (R.nextInt(12) == 0) mc.level.addParticle(ParticleTypes.LAVA, px, py, pz, 0, 0, 0);
                }
                case CLEVER -> { if (R.nextInt(3) == 0) mote(mc, ParticleTypes.ELECTRIC_SPARK, px, py, pz, 0.02); }
                case HORROR -> {
                    BlockState under = mc.level.getBlockState(bp.below());
                    if (under.isSolid() && R.nextInt(2) == 0) mc.level.addParticle(ParticleTypes.SOUL, px, bp.getY() + 0.1, pz, 0, 0.02 + R.nextDouble() * 0.02, 0);
                    else if (R.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.SCULK_SOUL, px, py, pz, 0, 0.01, 0);
                }
            }
        }
        // a sound now and then, off in the dark
        if (--nextSound > 0) return;
        nextSound = 160 + R.nextInt(240); // every 8-20 s (was 13-39 s: heard once in a whole visit, 2026-11-19)
        String id = switch (air) {
            case AWE -> "block.amethyst_block.chime";
            case FEAR -> "ambient.basalt_deltas.additions";
            case COZY -> "ambient.crimson_forest.additions";
            case CLEVER -> "ambient.warped_forest.additions";
            case HORROR -> R.nextInt(8) == 0 ? "entity.warden.heartbeat" : "ambient.soul_sand_valley.additions";
        };
        double a = R.nextDouble() * Math.PI * 2, d = 5 + R.nextDouble() * 7;
        mc.getSoundManager().play(new SimpleSoundInstance(ResourceLocation.withDefaultNamespace(id), SoundSource.AMBIENT,
                (float) (0.7 + 0.4 * s), 0.55f + R.nextFloat() * 0.25f, R, false, 0, SoundInstance.Attenuation.LINEAR,
                mc.player.getX() + Math.cos(a) * d, y + (R.nextDouble() - 0.3) * 6, mc.player.getZ() + Math.sin(a) * d, false));
    }

    private static void mote(Minecraft mc, ParticleOptions p, double x, double y, double z, double drift) {
        mc.level.addParticle(p, x, y, z, (R.nextDouble() - 0.5) * drift, (R.nextDouble() - 0.5) * drift, (R.nextDouble() - 0.5) * drift);
    }

    /** The mood the rock round the player wears (world/Strata's abyss palettes), by a few dozen samples. */
    private static Air sample(Minecraft mc) {
        int[] score = new int[Air.values().length];
        BlockPos at = mc.player.blockPosition();
        for (int i = 0; i < 48; i++) {
            BlockState b = mc.level.getBlockState(at.offset(R.nextInt(17) - 8, R.nextInt(11) - 5, R.nextInt(17) - 8));
            if (b.is(Blocks.OBSIDIAN) || b.is(Blocks.AMETHYST_BLOCK) || b.is(Blocks.CRYING_OBSIDIAN)) score[Air.AWE.ordinal()]++;
            else if (b.is(Blocks.SOUL_SOIL) || b.is(Blocks.SCULK) || b.is(Blocks.BONE_BLOCK) || b.is(Blocks.SOUL_SAND)) score[Air.HORROR.ordinal()]++;
            else if (b.is(Blocks.MAGMA_BLOCK) || b.is(Blocks.TUFF)) score[Air.COZY.ordinal()]++;
            else if (b.is(Blocks.GILDED_BLACKSTONE)) score[Air.CLEVER.ordinal()] += 3; // a rare accent: worth more
            else if (b.is(Blocks.BASALT) || b.is(Blocks.SMOOTH_BASALT)) score[Air.FEAR.ordinal()]++;
        }
        int best = Air.FEAR.ordinal();
        for (int i = 0; i < score.length; i++) if (score[i] > score[best]) best = i;
        return Air.values()[best];
    }
}
