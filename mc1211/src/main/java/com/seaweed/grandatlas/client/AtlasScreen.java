package com.seaweed.grandatlas.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.atlas.AtlasEntries;
import com.seaweed.grandatlas.atlas.AtlasEntries.Category;
import com.seaweed.grandatlas.atlas.AtlasEntries.Entry;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The Grand Atlas, open: an index of the current chapter on the left page (pages not yet found are
 * "???"), and the chosen page on the right, its picture a scene of real blocks turning slowly
 * (drag it to turn it yourself), its text under it. Arrows at the top of the left page turn chapters.
 */
public final class AtlasScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "textures/gui/atlas_book.png");
    private static final int W = 400, H = 250;
    private static final int INK = 0x3B2A1A, FAINT = 0x9A8466, GOLD = 0x9A6A12, RED = 0x7A2A1A;
    private static final int LIST_TOP = 42, LINE = 11, LIST_LINES = 15;

    private static final int TAB_Y = 147, BODY_Y = 160, PLACE_LINE = 9, PLACE_LINES = 7;

    private int category;
    private String selected;
    /** The right page shows the places found instead of the notes. */
    private boolean showPlaces;
    private int placeScroll;
    private long copiedAt;
    private int scroll;
    private float dragYaw, dragPitch;
    private long lastDrag;
    private boolean dragging;
    private double left, top, k = 1;

    public AtlasScreen() {
        super(Component.literal("Grand Atlas"));
        String latest = ClientAtlas.latest;
        Entry e = latest.isEmpty() ? null : AtlasEntries.get(latest);
        if (e != null && ClientAtlas.FOUND.contains(e.id())) {
            category = e.category().ordinal();
            select(e.id(), false);
        } else {
            select(entries().get(0).id(), false);
        }
    }

    private List<Entry> entries() {
        return AtlasEntries.in(Category.values()[category]);
    }

    private void select(String id, boolean sound) {
        if (!id.equals(selected)) placeScroll = 0;
        selected = id;
        ClientAtlas.UNSEEN.remove(id);
        List<Entry> list = entries();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                if (i < scroll) scroll = i;
                if (i >= scroll + LIST_LINES) scroll = i - LIST_LINES + 1;
            }
        }
        if (sound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    protected void init() {
        k = Math.min(1.0, Math.min((width - 8) / (double) W, (height - 8) / (double) H));
        left = (width - W * k) / 2;
        top = (height - H * k) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- input, in book coordinates

    private double bx(double mx) {
        return (mx - left) / k;
    }

    private double by(double my) {
        return (my - top) / k;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = bx(mx), y = by(my);
        if (y >= 18 && y <= 34) {
            if (x >= 26 && x <= 44) return turn(-1);
            if (x >= 170 && x <= 188) return turn(1);
        }
        if (x >= 26 && x <= 188 && y >= LIST_TOP && y < LIST_TOP + LIST_LINES * LINE) {
            int i = scroll + (int) ((y - LIST_TOP) / LINE);
            List<Entry> list = entries();
            if (i < list.size()) select(list.get(i).id(), true);
            return true;
        }
        if (ClientAtlas.FOUND.contains(selected) && y >= TAB_Y - 2 && y <= TAB_Y + 9) {
            if (x >= 222 && x <= 290) return tab(false);
            if (x >= 296 && x <= 364) return tab(true);
        }
        if (showPlaces && x >= 218 && x <= 372 && y >= BODY_Y && y < BODY_Y + PLACE_LINES * PLACE_LINE) {
            List<int[]> places = ClientAtlas.PLACES.getOrDefault(selected, List.of());
            int i = placeScroll + (int) ((y - BODY_Y) / PLACE_LINE);
            if (i < places.size()) {
                int[] at = places.get(i);
                Minecraft.getInstance().keyboardHandler.setClipboard(at[0] + " " + at[1] + " " + at[2]);
                copiedAt = System.currentTimeMillis();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        if (x >= 212 && x <= 374 && y >= 38 && y <= 142) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            dragYaw += (float) (dx / k) * 1.2F;
            dragPitch = Math.max(-40, Math.min(70, dragPitch + (float) (dy / k)));
            lastDrag = System.currentTimeMillis();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (showPlaces && bx(mx) > 200) {
            int pmax = Math.max(0, ClientAtlas.PLACES.getOrDefault(selected, List.of()).size() - PLACE_LINES);
            placeScroll = Math.max(0, Math.min(pmax, placeScroll - (int) Math.signum(sy)));
            return true;
        }
        int max = Math.max(0, entries().size() - LIST_LINES);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 263) return turn(-1);  // left arrow
        if (key == 262) return turn(1);   // right arrow
        if (key == 264 || key == 265) {   // down / up: next or previous page
            List<Entry> list = entries();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(selected)) {
                    int j = Math.max(0, Math.min(list.size() - 1, i + (key == 264 ? 1 : -1)));
                    if (j != i) select(list.get(j).id(), true);
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    private boolean tab(boolean places) {
        if (showPlaces != places) {
            showPlaces = places;
            placeScroll = 0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2F));
        }
        return true;
    }

    private boolean turn(int d) {
        category = Math.floorMod(category + d, Category.values().length);
        scroll = 0;
        select(entries().get(0).id(), true);
        return true;
    }

    // ---- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderTransparentBackground(g);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(left, top, 0);
        ps.scale((float) k, (float) k, 1);
        g.blit(BOOK, 0, 0, 0, 0, W, H, 512, 256);
        drawIndex(g, (int) bx(mouseX), (int) by(mouseY));
        drawPage(g, partial, (int) bx(mouseX), (int) by(mouseY));
        ps.popPose();
    }

    private void drawIndex(GuiGraphics g, int mx, int my) {
        Category c = Category.values()[category];
        center(g, Component.literal(c.title), 107, 23, RED);
        g.drawString(font, "<", 31, 23, mx >= 26 && mx <= 44 && my >= 18 && my <= 34 ? GOLD : INK, false);
        g.drawString(font, ">", 180, 23, mx >= 170 && mx <= 188 && my >= 18 && my <= 34 ? GOLD : INK, false);
        g.fill(36, 35, 178, 36, 0x553B2A1A);
        List<Entry> list = entries();
        for (int i = 0; i < LIST_LINES && scroll + i < list.size(); i++) {
            Entry e = list.get(scroll + i);
            int y = LIST_TOP + i * LINE;
            boolean found = ClientAtlas.FOUND.contains(e.id());
            if (e.id().equals(selected)) g.fill(30, y - 2, 184, y + 9, 0x30805020);
            else if (mx >= 26 && mx <= 188 && my >= y - 2 && my < y + 9) g.fill(30, y - 2, 184, y + 9, 0x18805020);
            String label = found ? e.title() : "? ? ?";
            if (found && ClientAtlas.UNSEEN.contains(e.id())) g.drawString(font, "*", 32, y, GOLD, false);
            g.drawString(font, label, 40, y, found ? INK : FAINT, false);
        }
        if (scroll > 0) center(g, Component.literal("^"), 107, LIST_TOP - 6, FAINT);
        if (scroll + LIST_LINES < list.size()) center(g, Component.literal("v"), 107, LIST_TOP + LIST_LINES * LINE - 2, FAINT);
        int have = 0;
        for (Entry e : list) if (ClientAtlas.FOUND.contains(e.id())) have++;
        center(g, Component.literal(have + " of " + list.size() + " in this chapter  |  " + ClientAtlas.FOUND.size() + " of " + AtlasEntries.all().size()), 107, 222, FAINT);
    }

    private void drawPage(GuiGraphics g, float partial, int mx, int my) {
        Entry e = AtlasEntries.get(selected);
        if (e == null) return;
        boolean found = ClientAtlas.FOUND.contains(e.id());
        center(g, Component.literal(found ? e.title() : "? ? ?"), 293, 23, found ? RED : FAINT);
        g.fill(222, 35, 364, 36, 0x553B2A1A);
        if (found) {
            AtlasModels.Model model = AtlasModels.get(e.id());
            if (model != null) {
                drawModel(g, model, 293, 92);
            } else {
                ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.icon())));
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(293 - 32, 96 - 32, 50);
                ps.scale(4, 4, 4);
                g.renderItem(icon, 0, 0);
                ps.popPose();
            }
            List<int[]> places = ClientAtlas.PLACES.getOrDefault(e.id(), List.of());
            drawTab(g, "Notes", 256, !showPlaces, mx >= 222 && mx <= 290 && my >= TAB_Y - 2 && my <= TAB_Y + 9);
            drawTab(g, "Places (" + places.size() + ")", 330, showPlaces, mx >= 296 && mx <= 364 && my >= TAB_Y - 2 && my <= TAB_Y + 9);
            if (showPlaces) drawPlaces(g, places, mx, my);
            else body(g, e.text(), 220, BODY_Y);
        } else {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(293, 96, 0);
            ps.scale(5, 5, 1);
            center(g, Component.literal("?"), 0, -4, 0x40705030);
            ps.popPose();
            body(g, "This page is still blank. Explore the world to fill it in, or tear the clue from a ripped map.", 220, BODY_Y - 4);
        }
    }

    private void drawTab(GuiGraphics g, String label, int cx, boolean on, boolean hover) {
        int w = font.width(label);
        if (on) g.fill(cx - w / 2 - 4, TAB_Y + 9, cx + w / 2 + 4, TAB_Y + 10, 0xFF7A2A1A);
        else if (hover) g.fill(cx - w / 2 - 4, TAB_Y - 2, cx + w / 2 + 4, TAB_Y + 9, 0x18805020);
        center(g, Component.literal(label), cx, TAB_Y, on ? RED : (hover ? GOLD : FAINT));
    }

    /** The places this player found one of these, as coordinates; click one to copy it. */
    private void drawPlaces(GuiGraphics g, List<int[]> places, int mx, int my) {
        if (places.isEmpty()) {
            body(g, "No place noted yet. Walk into one and the book marks where it lies.", 220, BODY_Y);
            return;
        }
        for (int i = 0; i < PLACE_LINES && placeScroll + i < places.size(); i++) {
            int[] at = places.get(placeScroll + i);
            int y = BODY_Y + i * PLACE_LINE;
            boolean hover = mx >= 218 && mx <= 372 && my >= y && my < y + PLACE_LINE;
            if (hover) g.fill(218, y - 1, 372, y + PLACE_LINE - 1, 0x18805020);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(222, y, 0);
            ps.scale(0.8F, 0.8F, 1);
            g.drawString(font, (placeScroll + i + 1) + ".", 0, 0, FAINT, false);
            g.drawString(font, "x " + at[0] + "   y " + at[1] + "   z " + at[2], 18, 0, hover ? GOLD : INK, false);
            ps.popPose();
        }
        String foot = System.currentTimeMillis() - copiedAt < 1500 ? "Copied to the clipboard"
                : places.size() > PLACE_LINES ? "Scroll for more. Click one to copy it." : "Click one to copy it.";
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(293, BODY_Y + PLACE_LINES * PLACE_LINE + 2, 0);
        ps.scale(0.75F, 0.75F, 1);
        center(g, Component.literal(foot), 0, 0, FAINT);
        ps.popPose();
    }

    /** Body text, a little smaller than the headings, wrapped to the page. */
    private void body(GuiGraphics g, String text, int x, int y) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        float s = 0.8F;
        ps.scale(s, s, 1);
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) (148 / s));
        int ly = 0;
        for (FormattedCharSequence line : lines) {
            if (ly > 64 / s) break;
            g.drawString(font, line, 0, ly, INK, false);
            ly += 10;
        }
        ps.popPose();
    }

    private void center(GuiGraphics g, Component text, int cx, int y, int colour) {
        g.drawString(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    /** A scene of blocks, turning, fitted into the picture box round (cx, cy). */
    private void drawModel(GuiGraphics g, AtlasModels.Model m, int cx, int cy) {
        List<Map.Entry<Long, BlockState>> blocks = m.visible();
        double sx = m.maxX - m.minX + 1, sy = m.maxY - m.minY + 1, sz = m.maxZ - m.minZ + 1;
        double diag = Math.sqrt(sx * sx + sy * sy + sz * sz);
        float s = (float) (100 / diag);
        long now = System.currentTimeMillis();
        boolean idle = now - lastDrag > 2500;
        float yaw = dragYaw + (idle ? (now % 36000L) / 100F - (lastDrag == 0 ? 0 : 0) : 0) + 35;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 300);
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(25 + dragPitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-(m.minX + sx / 2), -(m.minY + sy / 2), -(m.minZ + sz / 2));
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        RandomSource rand = RandomSource.create(42);
        for (Map.Entry<Long, BlockState> e : blocks) {
            BlockPos p = BlockPos.of(e.getKey());
            BlockState state = e.getValue();
            BakedModel model = dispatcher.getBlockModel(state);
            int tint = tint(m, state);
            float r = (tint >> 16 & 255) / 255F, gr = (tint >> 8 & 255) / 255F, b = (tint & 255) / 255F;
            ps.pushPose();
            ps.translate(p.getX(), p.getY(), p.getZ());
            rand.setSeed(42);
            for (RenderType rt : model.getRenderTypes(state, rand, ModelData.EMPTY)) {
                dispatcher.getModelRenderer().renderModel(ps.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(rt, false)), state, model,
                        r, gr, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
            }
            ps.popPose();
        }
        buffers.endBatch();
        ps.popPose();
    }

    private static int tint(AtlasModels.Model m, BlockState s) {
        if (s.is(Blocks.BIRCH_LEAVES)) return 0x80A755;
        if (s.is(Blocks.SPRUCE_LEAVES)) return 0x619961;
        if (s.is(Blocks.OAK_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES) || s.is(Blocks.JUNGLE_LEAVES) || s.is(Blocks.ACACIA_LEAVES) || s.is(Blocks.VINE)) return m.foliage;
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.TALL_GRASS)) return m.grass;
        if (s.is(BlockTags.LEAVES)) return 0xFFFFFF;
        return 0xFFFFFF;
    }
}
