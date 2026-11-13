package com.seaweed.grandatlas.atlas;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.SetPieces;
import com.seaweed.grandatlas.core.SkyIsles;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.macro.Macro;
import com.seaweed.grandatlas.core.macro.MacroSite;
import com.seaweed.grandatlas.core.micro.MesoKind;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.province.Province;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The Grand Atlas's pages: every place Grand Atlas makes that is worth a page, what the page says,
 * and how a player finds it ({@link #at}: the entries for where a player stands) or where a ripped
 * map points ({@link #hint}). The book itself is client side (client/AtlasScreen); its pictures are
 * client/AtlasModels, keyed by entry id.
 */
public final class AtlasEntries {
    private AtlasEntries() {}

    public enum Category {
        LANDFORMS("Landforms"), GREAT_PLACES("Great Places"), WONDERS("Wonders"), SITES("Sites"),
        UNDERGROUND("The Underground"), SEAS("Island Seas");

        public final String title;

        Category(String title) {
            this.title = title;
        }
    }

    /** One page. {@code icon} is an item id shown when the page has no model of its own. */
    public record Entry(String id, Category category, String title, String text, String icon) {}

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

    private static void add(Category c, String id, String title, String icon, String text) {
        BY_ID.put(id, new Entry(id, c, title, text, icon));
    }

    static {
        Category L = Category.LANDFORMS;
        add(L, "megarange", "Megaranges", "minecraft:snow_block",
                "Mountain ranges that climb past y 700: knife-edged ridges, horns where three cirques meet, snowfields "
                        + "and glaciers. Some are smooth and rolling, some savage, with towers of rock and overhanging crags. "
                        + "They run in belts across a continent.");
        add(L, "canyon_plateau", "Canyon Plateaus", "minecraft:orange_terracotta",
                "High tableland of banded rock, cut by a river canyon hundreds of blocks deep. Side canyons branch from it, "
                        + "and buttes and mesas stand where the land between them wore away. Warm, dry country.");
        add(L, "dune_sea", "Dune Seas", "minecraft:sand",
                "An erg: an ocean of sand. Transverse dunes in long rows, barchans, and star dunes up to 150 blocks tall, "
                        + "gentle on the windward side and steep on the slip face. Now and then a lake lies between them.");
        add(L, "rock_desert", "Rock Deserts", "minecraft:red_sandstone",
                "Red sand running between wind-carved sandstone: domed jebels, towers, fins and arches, holes worn "
                        + "through them, feet undercut by blowing sand. Some districts are open sand, some a maze.");
        add(L, "playa", "Salt Playas", "minecraft:calcite",
                "Basin and range: parallel bare ranges, fans of gravel at their feet, and between them dead-flat white "
                        + "salt pans in the lowest ground, crusted in polygons.");
        add(L, "misty_peaks", "The Misty Peaks", "minecraft:andesite",
                "A high upland cut into gorges so deep the clouds drift through them, and out of it a forest of sheer "
                        + "granite needles up to 320 blocks tall, pines clinging to their flat summits. Stand on one and look "
                        + "down on a sea of cloud. Found in damp, temperate country.");
        add(L, "volcanic_wastes", "The Volcanic Wastes", "minecraft:magma_block",
                "Black basalt plains, ridged by old lava flows, under great volcanoes up to 450 blocks tall. Each holds a "
                        + "lake of lava in its crater, and channels of lava run down its gullied flanks. Ash drifts in the air.");
        add(L, "silverwood", "The Silverwood", "minecraft:birch_log",
                "Soft hills and folds under colossal silver-barked trees whose leaves turn gold, streams in every hollow, "
                        + "flowers in the clearings, and at night a faint glow in the air. Temperate country.");
        add(L, "glimmer_vale", "Glimmer Vales", "minecraft:sea_pickle",
                "A sheltered valley winding between steep green ridges, lush with flowers, moss and flowering azalea. Down "
                        + "its length runs a turquoise river whose bed glows with sea pickles, and motes drift over it.");

        add(L, "tepui", "Tepuis", "minecraft:mossy_cobblestone",
                "Table mountains hundreds of blocks across, rising sheer out of the jungle on walls 230 to 400 blocks tall. "
                        + "Streams run across their cracked, mossy tops and pour off the rim in falls that drop all the way "
                        + "to the forest below. Each is a world of its own. Found in hot, wet country.");
        add(L, "rift_valley", "Rift Valleys", "minecraft:coarse_dirt",
                "The land pulled apart: a flat floor of savanna hundreds of blocks wide, sunk between walls that climb "
                        + "in great steps, each a sheer cliff with a tilted shelf above it. Volcanoes stand on the floor.");
        add(L, "rainbow_mountains", "Rainbow Mountains", "minecraft:red_terracotta",
                "Steep, sharp-crested ridges striped from foot to crest in red, orange, gold, cream, green, teal and "
                        + "lavender, the bands folding gently along each flank. Dry country.");

        Category G = Category.GREAT_PLACES;
        add(G, "great_plateau", "Great Plateaus", "minecraft:stone",
                "A tableland several kilometres across, raised on sheer walls with cave mouths in them. Whole countries "
                        + "of hills and forest lie on top, and a river pours off its rim in a great fall.");
        add(G, "massif", "Massifs", "minecraft:cobblestone",
                "A mountain massif: one great block of high country, its summit standing over everything for kilometres, "
                        + "its own ranges and valleys on its flanks.");
        add(G, "basin", "Basins", "minecraft:mud",
                "An enclosed basin, its floor sunk below the country round it, ringed by heights. Water gathers in it, and "
                        + "the climate inside is its own.");
        add(G, "elevated_mesa", "Elevated Mesas", "minecraft:terracotta",
                "A mesa raised high over the land on cliffs of banded rock, its top a flat world of its own.");
        add(G, "sky_isles", "The Sky Isles", "minecraft:grass_block",
                "A great island hanging hundreds of blocks over the land, ringed by a flock of smaller isles at other "
                        + "heights. Gardens and woods on top, a root of rock tapering to jagged points beneath, glow berries "
                        + "hanging from it, and streams falling from its rim to the ground far below.");

        Category W = Category.WONDERS;
        add(W, "the_hollow", "The Hollow", "grandatlas:glowcap",
                "A cavern deep underground where islands float over an underground lake, banded rock hanging beneath them "
                        + "and natural bridges between, glowcaps lighting the dark.");
        add(W, "great_geode", "The Great Geodes", "minecraft:amethyst_cluster",
                "A geode the size of a cathedral, buried in the deep: a shell of smooth basalt and calcite round walls of "
                        + "amethyst, crystals growing inward from every face.");
        add(W, "titans_grave", "Titan's Graves", "minecraft:bone_block",
                "The bones of something enormous lie in a cavern of their own, ribs arching overhead like the vault of a "
                        + "hall, smothered in moss, vines and azalea. Its skull lies somewhere nearby.");
        add(W, "aurora_vault", "The Aurora Vaults", "grandatlas:aurora_crystal_cyan",
                "A domed hall 300 to 480 across with a lake hanging from its roof. The aurora ripples through the water in "
                        + "green, cyan, blue, violet and rose, and great spikes of aurora crystal hang from the lake bed toward "
                        + "a garden of crystal on the floor. A crystal-lined well drops into it from the surface.");
        add(W, "worldroot", "The Worldroots", "minecraft:dark_oak_log",
                "A world tree on the surface, its trunk veined with glowing amber sap. Its taproot is hollow: climb down it "
                        + "and its roots open into a great hall of roots far below.");
        add(W, "hanging_mountains", "The Hanging Mountains", "minecraft:packed_ice",
                "A cavern in the abyss so vast that a whole mountain range hangs from its roof, peaks pointing down, ice on "
                        + "their tips, a cold pale mist between them.");

        Category S = Category.SITES;
        add(S, "pond_country", "Pond Country", "minecraft:lily_pad",
                "Humid, gentle country dotted with small ponds in hollows of their own, reeds on their shores.");
        add(S, "sinkhole_field", "Sinkhole Fields", "minecraft:stone",
                "Karst country: round pits with undercut lips, and where it is wet, cenotes with clear turquoise pools at "
                        + "the bottom.");
        add(S, "hoodoo_garden", "Hoodoo Gardens", "minecraft:red_terracotta",
                "Dry country with clusters of banded rock pillars, each balancing a cap of harder stone on a narrow neck.");
        add(S, "cinder_field", "Cinder Fields", "minecraft:basalt",
                "A small volcanic field: cinder cones with craters on top, columns of basalt and black ground.");
        add(S, "glade", "Glades", "minecraft:poppy",
                "A clearing in the forest: open meadow ringed by boulders and fallen trees, sometimes a fairy ring.");
        add(S, "erratic_field", "Erratic Fields", "minecraft:diorite",
                "Cool open country strewn with great boulders the glaciers carried here and left behind.");
        add(S, "tor_hills", "Tor Hills", "minecraft:granite",
                "Open hills crowned with stacks of rounded granite boulders.");
        add(S, "tarn_basin", "Tarn Basins", "minecraft:water_bucket",
                "High mountain hollows holding small cold lakes with stony shores.");
        add(S, "butte_field", "Butte Fields", "minecraft:terracotta",
                "Dry country with a scatter of small flat-topped buttes of banded rock, each on an apron of talus.");
        add(S, "ancient_grove", "Ancient Groves", "minecraft:oak_log",
                "Old-growth forest round a few giant trees, far bigger than anything else in the wood: buttress roots, "
                        + "great branches, a broad crown.");
        add(S, "mushroom_hollow", "Mushroom Hollows", "minecraft:red_mushroom",
                "A dim hollow under dark forest where giant mushrooms grow over mycelium.");
        add(S, "deadfall", "Deadfalls", "minecraft:stripped_oak_log",
                "Woods where trees fall and stand dead: snags, fallen trunks and moss.");
        add(S, "pale_grove", "Pale Groves", "minecraft:white_wool",
                "A patch of pale garden inside an ordinary forest: grey trees, pale moss and an eerie hush.");
        add(S, "fern_gully", "Fern Gullies", "minecraft:fern",
                "Woods cut by winding ravines floored with ferns and moss.");
        add(S, "grotto", "Grottos", "minecraft:moss_block",
                "A collapsed cave: a round pit holding a turquoise pool under a great overhang, a stream falling from its rim.");
        add(S, "terrace_falls", "Terrace Falls", "minecraft:calcite",
                "A chain of turquoise pools stepping down a wooded hillside, each held by a curved rim of travertine that "
                        + "the water spills over in a sheet.");
        add(S, "crag_towers", "Crag Towers", "minecraft:stone_bricks",
                "One to three great rock towers rising sheer out of open country, the tallest split at the top into two summits.");
        add(S, "lake_district", "Lake Districts", "minecraft:water_bucket",
                "Low, rocky, cool country crowded with lakes of every size, bare rock shores, islands in the big ones.");
        add(S, "caldera", "Calderas", "minecraft:blue_ice",
                "The stump of a great volcano: a ring of forested rim standing high over the land, sheer cliffs inside dropping "
                        + "to a deep blue lake, often with a cinder cone island.");
        add(S, "crystal_garden", "Crystal Gardens", "grandatlas:aurora_shard_green",
                "Giant spires of aurora crystal thrust up out of the ground at angles, glowing in their colours, shards "
                        + "scattered at their feet.");
        add(S, "standing_stones", "Standing Stones", "minecraft:chiseled_stone_bricks",
                "A ring of tall stones cut with runes round an altar where candles still burn. Who lit them?");
        add(S, "floating_rocks", "Floating Rocks", "minecraft:mossy_cobblestone",
                "Boulders and crags floating over the ground, turf on top, glow berries and roots hanging beneath.");
        add(S, "bone_field", "Bone Fields", "minecraft:bone",
                "The bones of giants: rib cages arching out of the ground along their spines, skulls half buried.");
        add(S, "spotted_lake", "Spotted Lakes", "minecraft:lime_terracotta",
                "A shallow lake in dry grassland that has dried back into dozens of round pools, each a different "
                        + "colour from the minerals left in it, green, gold and blue, walled apart by a white crust you "
                        + "can walk across.");
        add(S, "petrified_forest", "Petrified Forests", "minecraft:tuff",
                "Trunks turned to stone, tuff and dripstone round a heart of calcite, a few still standing, most fallen and broken.");

        Category U = Category.UNDERGROUND;
        add(U, "root_caves", "Root Caves", "minecraft:hanging_roots",
                "Caves under the forests, their ceilings hung with roots that reach down from the trees above.");
        add(U, "ice_caves", "Ice Caves", "grandatlas:icicle",
                "Caves under cold country, walls of packed and blue ice, icicles hanging, hoarfrost underfoot.");
        add(U, "sandstone_caves", "Sandstone Caves", "minecraft:sandstone",
                "Warm, dry caves under the deserts, carved through layers of sandstone.");
        add(U, "fungal_forest", "Fungal Forests", "grandatlas:glowcap_block",
                "Forests of giant mushrooms in the deep: red and brown caps, and huge glowcaps that light the dark.");
        add(U, "crystal_caves", "Crystal Caves", "grandatlas:selenite_block",
                "Hot, deep caverns crossed by giant beams of selenite, clusters of crystal on every face.");
        add(U, "magma_chambers", "Magma Chambers", "minecraft:magma_block",
                "Under volcanoes and hot country, caverns of basalt and magma, lava seeping between them.");
        add(U, "abyssal_shores", "Abyssal Shores", "grandatlas:abyssal_sand",
                "Dark beaches of abyssal sand on the shores of the underground seas, far down in the abyss.");
        add(U, "glowworm_grotto", "Glowworm Grottos", "grandatlas:glowworm_silk",
                "Damp caves whose ceilings are hung with threads of glowing silk, like a night sky underground.");
        add(U, "mega_cavern", "Mega-Caverns", "minecraft:dripstone_block",
                "Caverns hundreds of blocks across, with vaults of rock overhead and spires hanging from them.");
        add(U, "underground_sea", "Underground Seas", "minecraft:prismarine",
                "Lakes and seas lying in the mega-caverns, black water under a stone sky.");

        Category O = Category.SEAS;
        add(O, "archipelago", "Archipelagos", "minecraft:sand",
                "A scatter of islands over the open sea, some big enough to hold hills and woods of their own.");
        add(O, "atoll_field", "Atoll Fields", "minecraft:brain_coral_block",
                "Rings of reef and sand round turquoise lagoons, out in warm water.");
        add(O, "volcanic_chain", "Volcanic Chains", "minecraft:basalt",
                "A line of steep volcanic islands strung out across the sea, deep water close in to their shores.");
    }

    public static List<Entry> all() {
        return List.copyOf(BY_ID.values());
    }

    public static @Nullable Entry get(String id) {
        return BY_ID.get(id);
    }

    public static List<Entry> in(Category c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : BY_ID.values()) if (e.category() == c) out.add(e);
        return out;
    }

    // ---- discovery

    private static String setPieceId(int kind) {
        return switch (kind) {
            case SetPieces.HOLLOW -> "the_hollow";
            case SetPieces.GEODE -> "great_geode";
            case SetPieces.TITAN -> "titans_grave";
            case SetPieces.VAULT -> "aurora_vault";
            case SetPieces.ROOT -> "worldroot";
            case SetPieces.PEAKS -> "hanging_mountains";
            default -> null;
        };
    }

    private static @Nullable String caveId(BiomeKind b) {
        return switch (b) {
            case ROOT_CAVES -> "root_caves";
            case ICE_CAVES -> "ice_caves";
            case SANDSTONE_CAVES -> "sandstone_caves";
            case FUNGAL_FOREST -> "fungal_forest";
            case CRYSTAL_CAVES -> "crystal_caves";
            case MAGMA_CHAMBERS -> "magma_chambers";
            case ABYSSAL_SHORES -> "abyssal_shores";
            case GLOWWORM_GROTTO -> "glowworm_grotto";
            default -> null;
        };
    }

    /**
     * One thing found where a player stands: the entry, and where that one lies (its centre where it
     * has one, else the player's spot), and how close another find must be to count as the same one.
     */
    public record Hit(String id, int x, int y, int z, int same) {}

    /** The entries a player standing at (x, y, z) has found. */
    public static List<String> at(AtlasCore core, int x, int y, int z) {
        List<String> out = new ArrayList<>(4);
        for (Hit h : hits(core, x, y, z)) out.add(h.id());
        return out;
    }

    /** The entries a player standing at (x, y, z) has found, each with where that one lies. */
    public static List<Hit> hits(AtlasCore core, int x, int y, int z) {
        List<Hit> out = new ArrayList<>(4);
        ColumnSample c = core.column(x, z);
        int surface = c.surfaceY();
        // Wonders first: they are underground, or (the Worldroot) reach up to the surface.
        long sp = core.setPieces().placeKey(x, y, z);
        if (sp != 0) {
            int kind = (int) (sp & 7);
            String id = setPieceId(kind);
            if (id != null) {
                double[] m = core.setPieces().nearest(kind, x, z);
                if (m != null) out.add(new Hit(id, (int) m[0], (int) m[1], (int) m[2], 200));
                else out.add(new Hit(id, x, y, z, 300));
            }
        }
        if (y > surface + 24) {
            List<SkyIsles.Isle> isles = core.skyIsles().near(x, z);
            if (!isles.isEmpty() && core.skyIsles().claims(isles, x, y, z)) {
                SkyIsles.Isle main = isles.get(0);
                for (SkyIsles.Isle i : isles) if (i.main()) main = i;
                out.add(new Hit("sky_isles", (int) main.x(), (int) main.top(), (int) main.z(), 700));
            }
        }
        if (y > surface - 24) {
            // On the surface: the landform, the great place, the site.
            com.seaweed.grandatlas.core.landform.Tepui.Mesa mesa = c.landform().id().equals("tepui")
                    && core.landform("tepui") instanceof com.seaweed.grandatlas.core.landform.Tepui tepui
                    ? tepui.tepui(core.mosaic().province(c.provinceX(), c.provinceZ()), x, z) : null;
            if (mesa != null) {
                // Each table mountain is its own find.
                com.seaweed.grandatlas.core.landform.Tepui.Mesa m = mesa;
                double[] at = ((com.seaweed.grandatlas.core.landform.Tepui) core.landform("tepui")).centre(m);
                out.add(new Hit("tepui", (int) at[0], core.column((int) at[0], (int) at[1]).surfaceY(), (int) at[1], 200));
            } else if (BY_ID.containsKey(c.landform().id())) {
                Province p = core.mosaic().province(c.provinceX(), c.provinceZ());
                int px = p == null ? x : (int) p.x(), pz = p == null ? z : (int) p.z();
                out.add(new Hit(c.landform().id(), px, core.column(px, pz).surfaceY(), pz, 1500));
            }
            if (c.macro() != null && c.macroInside() >= 0.5 && BY_ID.containsKey(c.macro().id())) {
                MacroSite m = core.macros().find(c.macro(), x, z, 1);
                int mx = m == null ? x : (int) m.x(), mz = m == null ? z : (int) m.z();
                out.add(new Hit(c.macro().id(), mx, core.column(mx, mz).surfaceY(), mz, 1200));
            }
            MesoSite site = c.site();
            if (site != null && site.kind() == MesoKind.SPOTTED_LAKE) {
                // Only at one of its basins: a site whose ground was too uneven for any holds none.
                com.seaweed.grandatlas.core.micro.MicroFeature m = c.micro();
                if (m != null && m.present()) out.add(new Hit("spotted_lake", (int) m.x, core.column((int) m.x, (int) m.z).surfaceY(), (int) m.z, 40));
            } else if (site != null && BY_ID.containsKey(site.kind().id())) {
                out.add(new Hit(site.kind().id(), (int) site.x(), core.column((int) site.x(), (int) site.z()).surfaceY(), (int) site.z(), 64));
            }
            for (String sea : com.seaweed.grandatlas.core.world.WorldField.ISLAND_SEAS) {
                double[] s = core.world().nearestIslandSea(sea, x, z);
                if (s != null && Math.hypot(s[0] - x, s[1] - z) < s[2]) out.add(new Hit(sea, (int) s[0], 63, (int) s[1], (int) Math.max(600, s[2])));
            }
        } else {
            // Underground: the cave biome, the mega-caverns and their seas.
            String cave = caveId(core.biomeAt(c, x, y, z));
            if (cave != null) out.add(new Hit(cave, x, y, z, 300));
            if (core.megaCaverns().inside(x, y, z) != 0) {
                double[] m = core.megaCaverns().nearest(x, z);
                int mx = m == null ? x : (int) m[0], mz = m == null ? z : (int) m[1];
                out.add(new Hit("mega_cavern", mx, y, mz, 400));
                double water = core.megaCaverns().water(x, z);
                if (!Double.isNaN(water) && y < water + 24) out.add(new Hit("underground_sea", mx, (int) water, mz, 400));
            }
        }
        return out;
    }

    /**
     * Where a ripped map points for an entry: {x, z} of the nearest one to (x, z) the core can find
     * quickly, or null (the underground's biomes, which have no single place).
     */
    public static @Nullable int[] hint(AtlasCore core, String id, int x, int z) {
        Landform landform = core.landform(id);
        if (landform != null) {
            Province p = core.mosaic().find(landform, x, z, 12);
            return p == null ? null : new int[] {(int) p.x(), (int) p.z()};
        }
        Macro macro = core.macro(id);
        if (macro != null) {
            MacroSite m = core.macros().find(macro, x, z, 6);
            return m == null ? null : new int[] {(int) m.x(), (int) m.z()};
        }
        if (id.equals("sky_isles")) {
            SkyIsles.Isle isle = core.skyIsles().nearest(x, z);
            return isle == null ? null : new int[] {(int) isle.x(), (int) isle.z()};
        }
        for (MesoKind k : MesoKind.values()) {
            if (k.id().equals(id)) {
                MesoSite s = core.features().find(k, x, z, 24);
                return s == null ? null : new int[] {(int) s.x(), (int) s.z()};
            }
        }
        for (int kind = SetPieces.HOLLOW; kind <= SetPieces.PEAKS; kind++) {
            if (id.equals(setPieceId(kind))) {
                double[] m = core.setPieces().nearest(kind, x, z);
                return m == null ? null : new int[] {(int) m[0], (int) m[2]};
            }
        }
        if (com.seaweed.grandatlas.core.world.WorldField.ISLAND_SEAS.contains(id)) {
            double[] s = core.world().nearestIslandSea(id, x, z);
            return s == null ? null : new int[] {(int) s[0], (int) s[1]};
        }
        if (id.equals("underground_sea")) {
            double[] s = core.megaCaverns().nearestSea(x, z);
            return s == null ? null : new int[] {(int) s[0], (int) s[1]};
        }
        if (id.equals("mega_cavern")) {
            double[] s = core.megaCaverns().nearest(x, z);
            return s == null ? null : new int[] {(int) s[0], (int) s[1]};
        }
        return null;
    }
}
