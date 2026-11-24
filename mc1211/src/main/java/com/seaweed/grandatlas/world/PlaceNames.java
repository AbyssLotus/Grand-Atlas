package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.SetPieces;
import com.seaweed.grandatlas.core.macro.MacroSite;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import javax.annotation.Nullable;

/**
 * Places have names (the user, 2026-11-10: a more "bespoke" vibe, as if the world were made by
 * hand). Every set piece, macro landform, meso site and god tree gets a name of its own, made
 * from its seed, so the same place is always called the same thing, and a player who walks into
 * one is told where they are: "The Vault of Corwyn", "The Halmer Plateau", "Fenlor Grove".
 *
 * <p>The great places (set pieces, macro landforms) are announced as a title; the smaller ones
 * (meso sites, god trees) quietly over the hotbar. Each is announced once on arriving, not again
 * until the player has been somewhere else. Server side only: nothing is stored.
 */
@EventBusSubscriber(modid = GrandAtlas.MOD_ID)
public final class PlaceNames {
    private PlaceNames() {}

    private record Place(long key, String name, String kind, boolean great) {}

    /**
     * The last great place (title) and the last small place (action bar) each player was told of,
     * kept apart: with one record, stepping out of a site inside a named province announced the
     * province again every time (the user, 2026-11-12: "fix repeat titles").
     */
    private static final Map<UUID, Long> LAST_GREAT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SMALL = new ConcurrentHashMap<>();
    private static final String[] FIRST = {"Ael", "Bar", "Cor", "Dun", "El", "Fen", "Gal", "Hal", "Ith", "Jor", "Kel", "Lor", "Mer", "Nor", "Oth", "Pel",
            "Quen", "Rhun", "Sil", "Tor", "Ul", "Vey", "Wyn", "Yr", "Zan", "Ash", "Bryn", "Cael", "Dor", "Eth"};
    private static final String[] LAST_PART = {"wyn", "mer", "dun", "lor", "ith", "gal", "fen", "oth", "ren", "vale", "mar", "thas", "wick", "holt", "den",
            "more", "is", "an", "ul", "eth"};

    /** A name from a seed: two parts, sometimes three. Always the same for the same seed. */
    public static String name(long seed) {
        long h = GradientNoise.mix(seed ^ 0x4E414D45L);
        String a = FIRST[(int) Long.remainderUnsigned(h, FIRST.length)];
        String b = LAST_PART[(int) Long.remainderUnsigned(h >>> 12, LAST_PART.length)];
        if (((h >>> 30) & 7) == 0) {
            String mid = FIRST[(int) Long.remainderUnsigned(h >>> 40, FIRST.length)].toLowerCase(java.util.Locale.ROOT);
            return a + mid + b;
        }
        return a + b;
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 7) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        AtlasCore core = CoreHandle.coreOf(level);
        if (core == null) return;
        Place place;
        try {
            place = at(core, player.getBlockX(), player.getBlockY(), player.getBlockZ());
        } catch (RuntimeException e) {
            return; // never let a naming hiccup touch the game
        }
        if (place == null) return;
        Long last = (place.great ? LAST_GREAT : LAST_SMALL).put(player.getUUID(), place.key);
        if (last != null && last == place.key) return;
        if (place.great) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 60, 25));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(place.kind).withStyle(ChatFormatting.GRAY)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(place.name).withStyle(ChatFormatting.GOLD)));
        } else {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(place.name).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("  " + place.kind).withStyle(ChatFormatting.GRAY))));
        }
    }

    /** The named place a block is in, or null in ordinary country. */
    private static @Nullable Place at(AtlasCore core, int x, int y, int z) {
        long sp = core.setPieces().placeKey(x, y, z);
        if (sp != 0) {
            int kind = (int) (sp & 7);
            String n = name(sp);
            return switch (kind) {
                case SetPieces.HOLLOW -> new Place(sp, "The Hollow of " + n, "a cavern of floating islands", true);
                case SetPieces.GEODE -> new Place(sp, "The " + n + " Geode", "a great geode", true);
                case SetPieces.TITAN -> new Place(sp, "The Grave of " + n, "a titan's bones", true);
                case SetPieces.VAULT -> new Place(sp, "The Vault of " + n, "an aurora vault", true);
                case SetPieces.ROOT -> new Place(sp, n + ", the Worldroot", "its root hall", true);
                default -> null;
            };
        }
        ColumnSample c = core.column(x, z);
        // M6: the sky isles, named for their great isle; up there before anything below.
        if (y > c.surfaceY() + 30) {
            java.util.List<com.seaweed.grandatlas.core.SkyIsles.Isle> isles = core.skyIsles().near(x, z);
            if (!isles.isEmpty() && core.skyIsles().claims(isles, x, y, z)) {
                com.seaweed.grandatlas.core.SkyIsles.Isle isle = core.skyIsles().nearest(x, z);
                long key = (isle == null ? isles.get(0).seed() : isle.seed()) & ~7L | 4;
                return new Place(key, "The Isles of " + name(key), "sky isles", true);
            }
        }
        if (y < c.surfaceY() - 24) return null; // the named places below are on the surface
        long tree = AtlasAssets.godTreeAt(core, x, z);
        if (tree != 0) return new Place(tree, name(tree) + ", the Elder Tree", "a god tree", false);
        MesoSite site = c.site();
        if (site != null) {
            String n = name(site.seed());
            String title = switch (site.kind()) {
                case CALDERA -> "Mount " + n;
                case LAKE_DISTRICT -> "The " + n + " Lakes";
                case ANCIENT_GROVE -> n + " Grove";
                case HOODOO_GARDEN -> "The " + n + " Hoodoos";
                case SINKHOLE_FIELD -> "The " + n + " Sinks";
                case POND_COUNTRY -> n + " Ponds";
                case CINDER_FIELD -> "The " + n + " Cinders";
                case GLADE -> n + " Glade";
                case ERRATIC_FIELD -> "The " + n + " Stones";
                case TOR_HILLS -> n + " Tors";
                case TARN_BASIN -> n + " Tarns";
                case BUTTE_FIELD -> "The " + n + " Buttes";
                case MUSHROOM_HOLLOW -> n + " Hollow";
                case DEADFALL -> n + " Deadfall";
                case PALE_GROVE -> "The Pale Grove of " + n;
                case FERN_GULLY -> n + " Gully";
                case GROTTO -> n + " Grotto";
                case TERRACE_FALLS -> "The " + n + " Terraces";
                case CRAG_TOWERS -> "The " + n + " Crags";
                case CRYSTAL_GARDEN -> "The " + n + " Crystals";
                case STANDING_STONES -> "The Stones of " + n;
                case FLOATING_ROCKS -> n + " Drift";
                case BONE_FIELD -> "The Bones of " + n;
                case PETRIFIED_FOREST -> "The Stone Wood of " + n;
                case SPOTTED_LAKE -> n + " Spotted Lake";
                default -> n;
            };
            return new Place(site.seed() | 1, title, site.kind().id().replace('_', ' '), false);
        }
        // M5: each tepui is a great place of its own, named for itself.
        if (c.landform().id().equals("tepui") && core.landform("tepui") instanceof com.seaweed.grandatlas.core.landform.Tepui tepui) {
            com.seaweed.grandatlas.core.landform.Tepui.Mesa m = tepui.tepui(core.mosaic().province(c.provinceX(), c.provinceZ()), x, z);
            if (m != null) {
                long key = GradientNoise.mix(m.seed() ^ 0x7E9011L) & ~7L | 5;
                return new Place(key, name(key) + " Tepui", "tepui", true);
            }
        }
        // M6: the fantasy landforms are great places, each province named once.
        String fantasy = switch (c.landform().id()) {
            case "rift_valley" -> "The %s Rift|rift valley";
            case "rainbow_mountains" -> "The %s Rainbow Hills|rainbow mountains";
            case "misty_peaks" -> "The %s Needles|misty peaks";
            case "volcanic_wastes" -> "The %s Wastes|volcanic wastes";
            case "silverwood" -> "The Silverwood of %s|silverwood";
            case "glimmer_vale" -> "The Vale of %s|glimmer vale";
            default -> null;
        };
        if (fantasy != null) {
            long key = GradientNoise.hash(core.seed() ^ 0xFA27A5L, c.provinceX(), c.provinceZ()) & ~7L | 5;
            String[] parts = fantasy.split("[|]");
            return new Place(key, String.format(parts[0], name(key)), parts[1], true);
        }
        if (c.macro() != null && c.macroInside() >= 0.5) {
            MacroSite m = core.macros().find(c.macro(), x, z, 2);
            if (m != null) {
                String n = name(m.seed());
                String title = switch (c.macro().id()) {
                    case "great_plateau" -> "The " + n + " Plateau";
                    case "massif" -> "The " + n + " Massif";
                    case "basin" -> "The " + n + " Basin";
                    case "elevated_mesa" -> n + " Mesa";
                    case "crater" -> "The " + n + " Crater";
                    case "monolith" -> n + " Rock";
                    case "fault_block" -> "The " + n + " Escarpment";
                    case "shattered_plateau" -> "The " + n + " Breaks";
                    case "great_sinkhole" -> "The " + n + " Abyss";
                    case "horn" -> "The " + n + " Horn";
                    case "spire_crown" -> "The Crown of " + n;
                    case "coiled_peak" -> "The " + n + " Coil";
                    case "sundered_land" -> "The Sundering of " + n;
                    case "sky_pillar" -> n + "'s Pillar";
                    default -> n;
                };
                return new Place(m.seed() | 2, title, c.macro().id().replace('_', ' '), true);
            }
        }
        return null;
    }
}
