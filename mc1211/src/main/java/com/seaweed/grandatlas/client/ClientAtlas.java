package com.seaweed.grandatlas.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** What this player's Grand Atlas holds, as the server last told it; and opening the book. */
public final class ClientAtlas {
    private ClientAtlas() {}

    static final Set<String> FOUND = new LinkedHashSet<>();
    /** Found since the book last showed them: drawn with a mark until opened. */
    static final Set<String> UNSEEN = new LinkedHashSet<>();
    static String latest = "";
    /** Where this player found each kind of place: entry id to {x, y, z} in the order found. */
    static final Map<String, List<int[]>> PLACES = new HashMap<>();

    public static void receive(List<String> found, List<String> places, String fresh) {
        FOUND.clear();
        FOUND.addAll(found);
        PLACES.clear();
        for (String s : places) {
            String[] f = s.split(" ");
            if (f.length != 4) continue;
            try {
                PLACES.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new int[] {Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3])});
            } catch (NumberFormatException ignored) {
                // a malformed line: skip it
            }
        }
        if (!fresh.isEmpty()) {
            UNSEEN.add(fresh);
            latest = fresh;
        }
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new AtlasScreen());
    }
}
