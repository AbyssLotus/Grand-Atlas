package com.seaweed.grandatlas.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seaweed.grandatlas.GrandAtlas;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A pack check, run only by the dev "audit" run ({@code gradlew :mc1211:runAudit}).
 *
 * <p>A mixin is applied the first time its target class loads. One that no longer fits its target
 * (an add-on built for an older version of the mod it patches) can sit unseen through startup and
 * crash the game hours in: "Carry On + Create Aeronautics Compat" did exactly that on the first
 * right click (the user, 2026-11-11), and neither the dependency audit nor a boot test could see
 * it. This reads every installed mod's mixin lists straight from its jar, works out every class
 * they patch, and loads each one now, so every such failure is in the log before anyone plays.
 *
 * <p>Classes that simply aren't there are skipped: those are integrations with mods that are not
 * installed. Run on the client, which has both the client's and the server's classes.
 */
public final class MixinAudit {
    private MixinAudit() {}

    private static final Pattern TOML_CONFIG = Pattern.compile("config\\s*=\\s*\"([^\"]+)\"");

    public static void run() {
        // target class -> the mods whose mixins patch it
        Map<String, Set<String>> targets = new TreeMap<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile file = info.getFile();
            String mod = file.getFileName();
            for (String config : configsOf(file)) {
                try {
                    Path json = file.findResource(config);
                    if (!Files.exists(json)) continue;
                    JsonObject o = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
                    String pkg = o.has("package") ? o.get("package").getAsString() : "";
                    for (String list : new String[] {"mixins", "client", "server"}) {
                        JsonArray names = o.has(list) ? o.getAsJsonArray(list) : null;
                        if (names == null) continue;
                        for (JsonElement n : names) {
                            Path cls = file.findResource((pkg + "." + n.getAsString()).replace('.', '/') + ".class");
                            if (!Files.exists(cls)) continue;
                            for (String target : targetsOf(Files.readAllBytes(cls))) {
                                targets.computeIfAbsent(target, k -> new TreeSet<>()).add(mod);
                            }
                        }
                    }
                } catch (Exception e) {
                    GrandAtlas.LOG.warn("MIXIN AUDIT: could not read {} in {}: {}", config, mod, e.toString());
                }
            }
        }
        GrandAtlas.LOG.info("MIXIN AUDIT: loading {} classes that mixins patch", targets.size());
        int failed = 0;
        ClassLoader loader = MixinAudit.class.getClassLoader();
        for (Map.Entry<String, Set<String>> e : targets.entrySet()) {
            try {
                Class.forName(e.getKey(), false, loader);
            } catch (Throwable t) {
                Throwable root = t;
                List<String> chain = new ArrayList<>();
                while (root != null && chain.size() < 6) {
                    chain.add(String.valueOf(root).lines().findFirst().orElse(""));
                    if (root.getCause() == root) break;
                    root = root.getCause();
                }
                String last = chain.get(chain.size() - 1);
                // a class that isn't there at all is an optional integration, not a broken mixin
                if (last.startsWith("java.lang.ClassNotFoundException") || last.startsWith("java.lang.NoClassDefFoundError")) continue;
                failed++;
                GrandAtlas.LOG.error("MIXIN AUDIT FAILURE in {} (patched by {}): {}", e.getKey(), e.getValue(), last);
            }
        }
        GrandAtlas.LOG.info("MIXIN AUDIT: finished, {} of {} classes failed", failed, targets.size());
    }

    /** A mod file's mixin configs: named in its mods.toml and, for older-style jars, its manifest. */
    private static Set<String> configsOf(IModFile file) {
        Set<String> out = new LinkedHashSet<>();
        try {
            Path toml = file.findResource("META-INF/neoforge.mods.toml");
            if (Files.exists(toml)) {
                Matcher m = TOML_CONFIG.matcher(Files.readString(toml));
                while (m.find()) out.add(m.group(1));
            }
            Path manifest = file.findResource("META-INF/MANIFEST.MF");
            if (Files.exists(manifest)) {
                String text = Files.readString(manifest).replace("\r\n ", "").replace("\n ", "");
                for (String line : text.split("\\R")) {
                    if (line.startsWith("MixinConfigs:")) {
                        for (String c : line.substring("MixinConfigs:".length()).split(",")) {
                            if (!c.isBlank()) out.add(c.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // a jar we can't read has nothing for us to check
        }
        return out;
    }

    /** The classes a mixin class patches, read from its {@code @Mixin} annotation without loading it. */
    private static List<String> targetsOf(byte[] mixinClass) {
        List<String> out = new ArrayList<>();
        new ClassReader(mixinClass).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String n, Object value) {
                                if (value instanceof Type type) out.add(type.getClassName());
                                else if (value instanceof String s) out.add(s.replace('/', '.'));
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }
}
