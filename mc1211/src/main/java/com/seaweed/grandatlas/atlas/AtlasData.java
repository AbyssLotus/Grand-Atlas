package com.seaweed.grandatlas.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.world.CoreHandle;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Grand Atlas guide book's server side: the two items, each player's discoveries (kept on the
 * player, through death), the packet that tells the player's client, and the tick that notices
 * when a player has found something new.
 */
public final class AtlasData {
    private AtlasData() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandAtlas.MOD_ID);
    public static final DeferredItem<Item> GRAND_ATLAS = ITEMS.register("grand_atlas",
            () -> new GrandAtlasItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> RIPPED_MAP = ITEMS.register("ripped_map",
            () -> new RippedMapItem(new Item.Properties().stacksTo(16)));

    /** Most places of one kind the book keeps; past this it stops noting new ones. */
    public static final int MAX_PLACES = 24;

    /**
     * What a player has found: entry ids in the order found, and for each entry the places the
     * player found one ({x, y, z} triples, in the order found).
     */
    public static final class Progress {
        final Set<String> found;
        final Map<String, List<Integer>> places;

        public Progress() {
            this(new LinkedHashSet<>(), new LinkedHashMap<>());
        }

        Progress(Set<String> found, Map<String, List<Integer>> places) {
            this.found = found;
            this.places = places;
        }

        public Set<String> found() {
            return found;
        }

        /** Notes a place for an entry unless one within {@code same} blocks is already noted. */
        boolean note(String id, int x, int y, int z, int same) {
            List<Integer> l = places.computeIfAbsent(id, k -> new ArrayList<>());
            for (int i = 0; i + 2 < l.size(); i += 3) {
                if (Math.hypot(l.get(i) - x, l.get(i + 2) - z) < same) return false;
            }
            if (l.size() >= MAX_PLACES * 3) return false;
            l.add(x);
            l.add(y);
            l.add(z);
            return true;
        }

        private static final Codec<Progress> FULL = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("found").forGetter(p -> List.copyOf(p.found)),
                Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()).optionalFieldOf("places", Map.of()).forGetter(p -> p.places)
        ).apply(i, (f, pl) -> {
            Map<String, List<Integer>> m = new LinkedHashMap<>();
            pl.forEach((k, v) -> m.put(k, new ArrayList<>(v)));
            return new Progress(new LinkedHashSet<>(f), m);
        }));
        /** Books from before places were kept were a bare list of ids. */
        private static final Codec<Progress> OLD = Codec.STRING.listOf().xmap(l -> new Progress(new LinkedHashSet<>(l), new LinkedHashMap<>()), p -> List.copyOf(p.found));
        static final Codec<Progress> CODEC = Codec.withAlternative(FULL, OLD);
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GrandAtlas.MOD_ID);
    public static final Supplier<AttachmentType<Progress>> PROGRESS = ATTACHMENTS.register("atlas_progress",
            () -> AttachmentType.builder(() -> new Progress()).serialize(Progress.CODEC).copyOnDeath().build());

    /**
     * Server to client: everything the player has found, the places noted ("id x y z" each), and
     * the id just found (or "").
     */
    public record Sync(List<String> found, List<String> places, String fresh) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GrandAtlas.MOD_ID, "atlas_sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::found,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::places,
                ByteBufCodecs.STRING_UTF8, Sync::fresh,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("2").optional().playToClient(Sync.TYPE, Sync.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.seaweed.grandatlas.client.ClientAtlas.receive(payload.found(), payload.places(), payload.fresh()))));
        modBus.addListener(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent.class, e -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(GRAND_ATLAS.get());
                e.accept(RIPPED_MAP.get());
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 40 == 17) explore(p);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) sync(p, "");
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) sync(p, "");
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) sync(p, "");
        });
    }

    /** Notes a place for an entry (a ripped map's mark), unless one near it is noted already. */
    public static void notePlace(ServerPlayer p, String id, int x, int y, int z) {
        Progress prog = progress(p);
        if (prog.note(id, x, y, z, 300)) p.setData(PROGRESS.get(), prog);
    }

    public static Progress progress(ServerPlayer p) {
        return p.getData(PROGRESS.get());
    }

    public static void sync(ServerPlayer p, String fresh) {
        Progress prog = progress(p);
        List<String> places = new ArrayList<>();
        prog.places.forEach((id, l) -> {
            for (int i = 0; i + 2 < l.size(); i += 3) places.add(id + " " + l.get(i) + " " + l.get(i + 1) + " " + l.get(i + 2));
        });
        PacketDistributor.sendToPlayer(p, new Sync(new ArrayList<>(prog.found), places, fresh));
    }

    /** Records an entry for a player; tells them if it is new. Returns whether it was new. */
    public static boolean discover(ServerPlayer p, String id, boolean announce) {
        AtlasEntries.Entry entry = AtlasEntries.get(id);
        if (entry == null) return false;
        Progress prog = progress(p);
        if (!prog.found.add(id)) return false;
        p.setData(PROGRESS.get(), prog); // marks it dirty
        sync(p, id);
        if (announce) {
            p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Grand Atlas: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(entry.title()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  (" + prog.found.size() + "/" + AtlasEntries.all().size() + ")").withStyle(ChatFormatting.DARK_GRAY))));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
            p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 0.7F);
        }
        return true;
    }

    private static void explore(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        AtlasCore core = CoreHandle.coreOf(level);
        if (core == null) return;
        List<AtlasEntries.Hit> here;
        try {
            here = AtlasEntries.hits(core, p.getBlockX(), p.getBlockY(), p.getBlockZ());
        } catch (RuntimeException ex) {
            return; // never let the book touch the game
        }
        for (AtlasEntries.Hit h : here) {
            Progress prog = progress(p);
            boolean noted = prog.note(h.id(), h.x(), h.y(), h.z(), h.same());
            if (noted) p.setData(PROGRESS.get(), prog);
            if (discover(p, h.id(), true) || !noted) continue; // a first find announces itself (and syncs)
            sync(p, "");
            AtlasEntries.Entry e = AtlasEntries.get(h.id());
            if (e != null) {
                p.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Grand Atlas: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("another " + e.title() + " noted").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  (" + h.x() + ", " + h.z() + ")").withStyle(ChatFormatting.DARK_GRAY))));
            }
        }
    }
}
