package com.seaweed.grandatlas.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The Aurora Vault's moving aurora (a test, the user 2026-11-09): inside the vault, glowing
 * ribbons of light hang just under the lake and sway and shimmer, their colours drifting from
 * green through cyan, blue and violet to rose, as an aurora's curtains do. Drawn as beacon-beam
 * geometry, which shader packs render full-bright, and which glows without shaders too. Client
 * only; anchored in the world, so you can walk under them.
 */
@EventBusSubscriber(modid = GrandAtlas.MOD_ID, value = Dist.CLIENT)
public final class AuroraRenderer {
    private AuroraRenderer() {}

    private static final ResourceKey<Biome> VAULT = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(GrandAtlas.MOD_ID, "aurora_vault"));
    private static final Identifier RIBBON = Identifier.fromNamespaceAndPath(GrandAtlas.MOD_ID, "textures/misc/aurora_ribbon.png");
    private static final int[] COLOURS = {0x3AD890, 0x40D0DC, 0x4C86EC, 0x9A62EC, 0xEE62AA, 0x3AD890};

    // Extracted each frame for the submit below.
    private static boolean active;
    private static double camX, camY, camZ, lakeY, lakeTop;
    private static float time;
    private static int lastCheckX = Integer.MIN_VALUE, lastCheckY, lastCheckZ;
    // The lake of the vault we're in: its underside (one level across the whole lake), and the
    // deepest we've seen it. The bed is wavy, so its height over the camera changes every few
    // blocks; hanging the ribbons from that made them all jump as you walked.
    private static double cachedLake = Double.NaN, cachedDepth;
    private static int foundX, foundZ;

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static boolean lastOn;

    /** The lake over a column: {underside y, top y}, from the first water above (x, y, z), or null. */
    private static double[] lakeOver(ClientLevel level, BlockPos.MutableBlockPos m, int x, int y, int z, int reach) {
        for (int dy = 1; dy < reach; dy++) {
            m.set(x, y + dy, z);
            if (level.getFluidState(m).is(FluidTags.WATER)) {
                int under = m.getY(), top = under;
                while (top < under + 80 && level.getFluidState(m.setY(top + 1)).is(FluidTags.WATER)) top++;
                return new double[] {under, top + 1};
            }
            if (level.getBlockState(m).isSolidRender()) return null;
        }
        return null;
    }

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        Camera camera = event.getCamera();
        Vec3 p = camera.position();
        camX = p.x;
        camY = p.y;
        camZ = p.z;
        time = (level.getGameTime() % 24000L) + event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BlockPos at = BlockPos.containing(p);
        // Looked up again only when we move. (long maths: the first-time sentinel is
        // Integer.MIN_VALUE, and int distances from it overflowed, so the check never ran.)
        if ((long) Math.abs((long) at.getX() - lastCheckX) + Math.abs((long) at.getY() - lastCheckY) + Math.abs((long) at.getZ() - lastCheckZ) > 3) {
            lastCheckX = at.getX();
            lastCheckY = at.getY();
            lastCheckZ = at.getZ();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            // In the vault? Cave biomes are 3D, so look up the column, not only at the camera.
            boolean vault = false;
            for (int dy = 0; dy < 200 && !vault; dy += 4) vault = level.getBiome(m.set(at.getX(), at.getY() + dy, at.getZ())).is(VAULT);
            double[] lake = vault ? lakeOver(level, m, at.getX(), at.getY(), at.getZ(), 200) : null;
            if (vault && lake == null && (Double.isNaN(cachedLake) || Math.hypot(at.getX() - foundX, at.getZ() - foundZ) > 400)) {
                // Not under the lake (the vault's outer ring, or just come in by a side passage):
                // look for it round us, so the aurora shows from the edges too.
                search:
                for (int reach = 24; reach <= 144; reach += 40) {
                    for (int k = 0; k < 8; k++) {
                        int x = at.getX() + (int) (Math.cos(k * Math.PI / 4) * reach), z = at.getZ() + (int) (Math.sin(k * Math.PI / 4) * reach);
                        lake = lakeOver(level, m, x, at.getY(), z, 160);
                        if (lake != null) break search;
                    }
                }
            }
            if (lake != null) {
                if (Double.isNaN(cachedLake) || Math.abs(lake[0] - cachedLake) > 2) cachedDepth = 0; // another vault's lake
                cachedLake = lake[0];
                cachedDepth = Math.max(cachedDepth, lake[1] - lake[0]);
                foundX = at.getX();
                foundZ = at.getZ();
            } else if (!vault) {
                cachedLake = Double.NaN;
            } // else: in the vault but not under the lake; keep the lake we know
            boolean on = !Double.isNaN(cachedLake);
            if (on != lastOn) {
                lastOn = on;
                LOG.info("[aurora] {} at {} {} {}", on ? "on: lake underside at y " + (int) cachedLake : "off", at.getX(), at.getY(), at.getZ());
            }
        }
        active = !Double.isNaN(cachedLake);
        lakeY = cachedLake;
        // The ribbons hang from most of the way up the lake's deepest water (where the bed is
        // lower, their tops are inside it and hidden).
        lakeTop = cachedLake + 1 + 0.7 * cachedDepth;
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        if (!active) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(RIBBON), AuroraRenderer::ribbons);
        // (an emissive entity type, not the beacon one: it writes depth, so the lake drawn after it
        // cannot paint over the ribbons, which shader water, near opaque from below, did)
        pose.popPose();
    }

    /** Ribbons on a world grid round the camera: each a wavy curtain 50-100 long, 8-14 tall. */
    private static void ribbons(PoseStack.Pose pose, VertexConsumer buf) {
        float t = time / 20f;
        int gx = (int) Math.floor(camX / 40), gz = (int) Math.floor(camZ / 40);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                long h = mix((gx + i) * 341873128712L ^ (gz + j) * 132897987541L);
                if ((h & 3) == 0) continue; // gaps
                double ox = (gx + i) * 40 + 40 * unit(h, 1), oz = (gz + j) * 40 + 40 * unit(h, 2);
                double a = unit(h, 3) * Math.PI, dx = Math.cos(a), dz = Math.sin(a);
                double len = 50 + 50 * unit(h, 4), phase = unit(h, 5) * 100;
                ribbon(pose, buf, ox, oz, dx, dz, len, phase, t);
            }
        }
    }

    private static void ribbon(PoseStack.Pose pose, VertexConsumer buf, double ox, double oz, double dx, double dz, double len, double phase, float t) {
        int segs = (int) (len / 2);
        // Hung from the lake's top down through the water, so they glow through it from below.
        double top = lakeTop - 0.15, depth = Math.max(4.0, lakeTop - lakeY - 0.3) + 5.0; // reaching ~5 below the lake (the user)
        double pxPrev = 0, pzPrev = 0, hPrev = 0;
        int cPrev = 0;
        float aPrev = 0;
        for (int k = 0; k <= segs; k++) {
            double s = (double) k / segs, along = (s - 0.5) * len;
            // A curtain that folds and sways slowly.
            double sway = 4.0 * Math.sin(along / 11.0 + t * 0.35 + phase) + 2.0 * Math.sin(along / 4.7 - t * 0.6 + phase * 1.3);
            double px = ox + dx * along - dz * sway, pz = oz + dz * along + dx * sway;
            double hgt = depth * (0.55 + 0.45 * (0.5 + 0.5 * Math.sin(along / 9.0 + t * 0.5 + phase * 0.7)));
            // Colour drifting along the curtain and with time; brightest mid-ribbon, fading at its ends.
            double hue = (s * 1.6 + t * 0.04 + phase * 0.01) % 1.0;
            int col = lerpColour(hue);
            float alpha = (float) (Math.sin(Math.PI * s) * (0.55 + 0.45 * Math.sin(along / 6.0 - t * 1.3 + phase)));
            alpha = Math.max(0f, Math.min(1f, alpha));
            if (k > 0) {
                quad(pose, buf, pxPrev, pzPrev, hPrev, cPrev, aPrev, px, pz, hgt, col, alpha, top, (float) ((k - 1) / 4.0), (float) (k / 4.0));
            }
            pxPrev = px;
            pzPrev = pz;
            hPrev = hgt;
            cPrev = col;
            aPrev = alpha;
        }
    }

    /** One strip of curtain between two points: bright at the top, fading to nothing at the bottom, both sides. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buf, double x0, double z0, double h0, int c0, float a0,
                             double x1, double z1, double h1, int c1, float a1, double top, float u0, float u1) {
        float rx0 = (float) (x0 - camX), rz0 = (float) (z0 - camZ), rx1 = (float) (x1 - camX), rz1 = (float) (z1 - camZ);
        float ty = (float) (top - camY), by0 = (float) (top - h0 - camY), by1 = (float) (top - h1 - camY);
        int top0 = argb(a0, c0), top1 = argb(a1, c1), bot0 = argb(0f, c0), bot1 = argb(0f, c1);
        vertex(buf, pose, rx0, ty, rz0, top0, u0, 0);
        vertex(buf, pose, rx0, by0, rz0, bot0, u0, 1);
        vertex(buf, pose, rx1, by1, rz1, bot1, u1, 1);
        vertex(buf, pose, rx1, ty, rz1, top1, u1, 0);
        vertex(buf, pose, rx1, ty, rz1, top1, u1, 0);
        vertex(buf, pose, rx1, by1, rz1, bot1, u1, 1);
        vertex(buf, pose, rx0, by0, rz0, bot0, u0, 1);
        vertex(buf, pose, rx0, ty, rz0, top0, u0, 0);
    }

    private static void vertex(VertexConsumer buf, PoseStack.Pose pose, float x, float y, float z, int colour, float u, float v) {
        buf.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 1f, 0f);
    }

    private static int lerpColour(double hue) {
        double f = hue * (COLOURS.length - 1);
        int i = (int) Math.floor(f);
        double w = f - i;
        int a = COLOURS[i], b = COLOURS[Math.min(COLOURS.length - 1, i + 1)];
        int r = (int) (((a >> 16) & 255) * (1 - w) + ((b >> 16) & 255) * w);
        int g = (int) (((a >> 8) & 255) * (1 - w) + ((b >> 8) & 255) * w);
        int bl = (int) ((a & 255) * (1 - w) + (b & 255) * w);
        return (r << 16) | (g << 8) | bl;
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double unit(long h, int k) {
        return (mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}

