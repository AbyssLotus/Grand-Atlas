package com.seaweed.grandatlas.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.debug.AtlasDebug;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client half of Grand Atlas's debug ({@link AtlasDebug}): the F3 lines, and the visual layers
 * drawn through the rock, so a cave network or a water table can be seen from anywhere near it.
 */
@EventBusSubscriber(modid = GrandAtlas.MOD_ID, value = Dist.CLIENT)
public final class DebugView {
    private DebugView() {}

    private static List<String> lines = List.of();
    private static long linesAt;
    private static AtlasDebug.Shapes shapes;
    private static boolean failed;

    public static void lines(List<String> l) {
        if (Boolean.getBoolean("grandatlas.debugCheck") && lines.isEmpty()) GrandAtlas.LOG.info("[debug check] F3 lines received: {}", l);
        lines = l;
        linesAt = System.currentTimeMillis();
    }

    public static void shapes(AtlasDebug.Shapes s) {
        shapes = s.layers() == 0 ? null : s;
        if (Boolean.getBoolean("grandatlas.debugCheck")) {
            GrandAtlas.LOG.info("[debug check] shapes: layers {}, {} lines, {} sheets", s.layers(), s.lineColours().length, s.quadColours().length);
        }
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.tickCount % 10 != 0) return;
        if (!mc.getDebugOverlay().showDebugScreen()) return;
        ClientPacketListener connection = mc.getConnection();
        if (connection != null && connection.hasChannel(AtlasDebug.Ping.TYPE)) PacketDistributor.sendToServer(new AtlasDebug.Ping());
    }

    @SubscribeEvent
    static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (lines.isEmpty() || System.currentTimeMillis() - linesAt > 3000) return;
        // On the right, above the targeted block and its tags (which can run off the bottom).
        List<String> right = event.getRight();
        int at = right.size();
        for (int i = 0; i < right.size(); i++) {
            if (right.get(i).contains("Targeted")) {
                at = i;
                break;
            }
        }
        right.addAll(at, lines);
    }

    /** Lines and sheets that ignore depth, so they show through terrain. */
    private static final class Types extends RenderType {
        private Types() {
            super("grandatlas_unused", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }

        static final RenderType LINES = create("grandatlas_debug_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1 << 16, false, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        static final RenderType SHEETS = create("grandatlas_debug_sheets", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        AtlasDebug.Shapes s = shapes;
        if (s == null || Minecraft.getInstance().level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose last = pose.last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        try {
            if (s.lines().length > 0) {
                VertexConsumer l = buffers.getBuffer(Types.LINES);
                float[] v = s.lines();
                for (int i = 0; i < s.lineColours().length; i++) {
                    int c = s.lineColours()[i];
                    int o = i * 6;
                    float dx = v[o + 3] - v[o], dy = v[o + 4] - v[o + 1], dz = v[o + 5] - v[o + 2];
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1e-4f) continue;
                    dx /= len;
                    dy /= len;
                    dz /= len;
                    l.addVertex(last, v[o], v[o + 1], v[o + 2]).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).setNormal(last, dx, dy, dz);
                    l.addVertex(last, v[o + 3], v[o + 4], v[o + 5]).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).setNormal(last, dx, dy, dz);
                }
                buffers.endBatch(Types.LINES);
            }
            if (s.quads().length > 0) {
                VertexConsumer q = buffers.getBuffer(Types.SHEETS);
                float[] v = s.quads();
                for (int i = 0; i < s.quadColours().length; i++) {
                    int c = s.quadColours()[i];
                    for (int k = 0; k < 4; k++) {
                        int o = i * 12 + k * 3;
                        q.addVertex(last, v[o], v[o + 1], v[o + 2]).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24);
                    }
                }
                buffers.endBatch(Types.SHEETS);
            }
        } catch (RuntimeException e) {
            if (!failed) GrandAtlas.LOG.error("Grand Atlas debug view failed to draw", e);
            failed = true;
        }
        pose.popPose();
    }
}
