package com.seaweed.grandatlas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * True darkness in the deep and the abyss (cave rework R3; the user, 2026-11-18: "totally cut off
 * visibility ... and only open up based off of lighting dynamically", pitch black with no light). From
 * y -64 the lightmap (mixin/LightTextureMixin) fades unlit rock to black, fully by y -110: light 0 is
 * black, 1-3 barely there, about 9 and up as normal. Torches, lava, glowing blocks and dynamic-lights
 * mods open it up, because they all raise light levels. Works with Sodium and Distant Horizons; under
 * an Iris shader pack it depends on whether the pack uses the vanilla lightmap. Night vision and
 * spectators see as normal. The fog colour goes black too, so the edge of sight is dark.
 */
public final class DeepDark {
    private DeepDark() {}

    /** How strongly the darkness applies here: 0 above y -64, 1 from y -110 down. */
    public static float strength() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD || mc.level.getMinBuildHeight() > -200) return 0;
        if (mc.player.isSpectator() || mc.player.hasEffect(MobEffects.NIGHT_VISION)) return 0;
        double y = mc.gameRenderer.getMainCamera().getPosition().y;
        double t = Math.max(0, Math.min(1, (-64 - y) / 46.0));
        return (float) (t * t * (3 - 2 * t));
    }

    /** What's left of a lightmap entry at this light level (0-15) in full darkness. */
    public static float factor(int light) {
        double t = Math.min(1, light / 9.0);
        return (float) Math.pow(t, 1.8);
    }

    public static void colour(ViewportEvent.ComputeFogColor e) {
        float s = strength();
        if (s <= 0) return;
        e.setRed(e.getRed() * (1 - s));
        e.setGreen(e.getGreen() * (1 - s));
        e.setBlue(e.getBlue() * (1 - s));
    }
}
