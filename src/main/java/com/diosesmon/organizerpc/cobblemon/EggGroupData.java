package com.diosesmon.organizerpc.cobblemon;

import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grupos huevo reales de cada especie.
 *
 * Cobblemon 1.7.3 no envía los grupos huevo al cliente (Species.encode/decode no los incluye),
 * así que en un servidor dedicado el cliente los tiene vacíos. Para seguir siendo client-side,
 * se leen los mismos JSON de especies que usa Cobblemon (data/&lt;ns&gt;/species/**.json) desde los
 * jars instalados en el cliente: Cobblemon y cualquier addon que añada especies.
 *
 * En un mundo de un jugador se usa directamente el dato del servidor integrado.
 */
public final class EggGroupData {
    private record Entry(EnumSet<EggGroupKey> base, Map<String, EnumSet<EggGroupKey>> forms) {
    }

    private static Map<String, Entry> byId;
    private static Map<String, Entry> byName;
    /** Enlaces evolutivos (en ambos sentidos) entre identificadores de especie. */
    private static Map<String, java.util.Set<String>> evoLinks = new HashMap<>();

    /** Familia evolutiva completa de una especie (incluida ella), según los JSON de especies. */
    public static java.util.Set<String> family(String speciesId) {
        ensureLoaded();
        String start = speciesId.toLowerCase(Locale.ROOT);
        java.util.Set<String> seen = new java.util.TreeSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < 64) {
            String cur = queue.poll();
            if (!seen.add(cur)) continue;
            for (String n : evoLinks.getOrDefault(cur, java.util.Set.of())) if (!seen.contains(n)) queue.add(n);
        }
        return seen;
    }

    private static void link(String a, String b) {
        evoLinks.computeIfAbsent(a, k -> new java.util.HashSet<>()).add(b);
        evoLinks.computeIfAbsent(b, k -> new java.util.HashSet<>()).add(a);
    }

    private static String speciesToken(String ns, String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT).split("\\s+")[0];
        if (t.isEmpty()) return null;
        return t.contains(":") ? t : ns + ":" + t;
    }

    private EggGroupData() {
    }

    public static Set<EggGroupKey> lookup(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        FormData form = pokemon.getForm();

        if (MinecraftClient.getInstance().isIntegratedServerRunning()) {
            EnumSet<EggGroupKey> live = convert(form.getEggGroups());
            if (!live.isEmpty()) return live;
        }

        Set<EggGroupKey> bundled = fromBundledData(species.getResourceIdentifier().toString(), species.getName(), form.getName());
        if (bundled != null && !bundled.isEmpty()) return bundled;
        // Especie desconocida en los datos locales: usar lo que tenga el cliente (puede estar vacío).
        return convert(form.getEggGroups());
    }

    /**
     * Grupos según los JSON empaquetados (la ruta usada en servidores dedicados), o null si la
     * especie no aparece en ellos. Público para poder verificarlo contra los datos del servidor.
     */
    public static Set<EggGroupKey> fromBundledData(String speciesId, String speciesName, String formName) {
        ensureLoaded();
        Entry e = byId.get(speciesId.toLowerCase(Locale.ROOT));
        if (e == null) e = byName.get(speciesName.toLowerCase(Locale.ROOT));
        if (e == null) return null;
        EnumSet<EggGroupKey> f = formName == null ? null : e.forms.get(formName.toLowerCase(Locale.ROOT));
        return f != null && !f.isEmpty() ? f : e.base;
    }

    public static EnumSet<EggGroupKey> convertGroups(Set<EggGroup> groups) {
        return convert(groups);
    }

    private static EnumSet<EggGroupKey> convert(Set<EggGroup> groups) {
        EnumSet<EggGroupKey> out = EnumSet.noneOf(EggGroupKey.class);
        if (groups == null) return out;
        for (EggGroup g : groups) {
            EggGroupKey k = EggGroupKey.fromId(g.name());
            if (k != null) out.add(k);
        }
        return out;
    }

    /** Carga los datos de especies en segundo plano para que abrir la PC no dé tirones. */
    public static void preloadAsync() {
        Thread t = new Thread(() -> {
            try {
                ensureLoaded();
            } catch (Exception e) {
                OrganizerPCClient.LOGGER.warn("No se pudieron precargar las especies: {}", e.toString());
            }
        }, "OrganizerPC-especies");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static synchronized void ensureLoaded() {
        if (byId != null) return;
        Map<String, Entry> ids = new HashMap<>();
        Map<String, Entry> names = new HashMap<>();
        int files = 0;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) continue;
                try (Stream<Path> namespaces = Files.list(data)) {
                    for (Path nsDir : (Iterable<Path>) namespaces::iterator) {
                        Path speciesDir = nsDir.resolve("species");
                        if (!Files.isDirectory(speciesDir)) continue;
                        String ns = nsDir.getFileName().toString().replace("/", "");
                        try (Stream<Path> walk = Files.walk(speciesDir)) {
                            for (Path file : (Iterable<Path>) walk::iterator) {
                                String fn = file.getFileName().toString();
                                if (!fn.endsWith(".json")) continue;
                                if (parse(ns, fn.substring(0, fn.length() - 5), file, ids, names)) files++;
                            }
                        }
                    }
                } catch (Exception ex) {
                    OrganizerPCClient.LOGGER.warn("No se pudieron leer especies de {}: {}", mod.getMetadata().getId(), ex.toString());
                }
            }
        }
        byId = ids;
        byName = names;
        OrganizerPCClient.LOGGER.info("Grupos huevo cargados de {} archivos de especies", files);
    }

    private static boolean parse(String ns, String fileId, Path file, Map<String, Entry> ids, Map<String, Entry> names) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) return false;
            JsonObject o = el.getAsJsonObject();
            EnumSet<EggGroupKey> base = groups(o.get("eggGroups"));
            Map<String, EnumSet<EggGroupKey>> forms = new HashMap<>();
            if (o.has("forms") && o.get("forms").isJsonArray()) {
                for (JsonElement f : o.getAsJsonArray("forms")) {
                    if (!f.isJsonObject()) continue;
                    JsonObject fo = f.getAsJsonObject();
                    if (!fo.has("name") || !fo.has("eggGroups")) continue;
                    forms.put(fo.get("name").getAsString().toLowerCase(Locale.ROOT), groups(fo.get("eggGroups")));
                }
            }
            Entry e = new Entry(base, forms);
            String self = (ns + ":" + fileId).toLowerCase(Locale.ROOT);
            if (o.has("preEvolution") && o.get("preEvolution").isJsonPrimitive()) {
                String pre = speciesToken(ns, o.get("preEvolution").getAsString());
                if (pre != null) link(self, pre);
            }
            if (o.has("evolutions") && o.get("evolutions").isJsonArray()) {
                for (JsonElement ev : o.getAsJsonArray("evolutions")) {
                    if (!ev.isJsonObject() || !ev.getAsJsonObject().has("result")) continue;
                    String res = speciesToken(ns, ev.getAsJsonObject().get("result").getAsString());
                    if (res != null) link(self, res);
                }
            }
            ids.put((ns + ":" + fileId).toLowerCase(Locale.ROOT), e);
            if (o.has("name")) names.putIfAbsent(o.get("name").getAsString().toLowerCase(Locale.ROOT), e);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static EnumSet<EggGroupKey> groups(JsonElement el) {
        EnumSet<EggGroupKey> out = EnumSet.noneOf(EggGroupKey.class);
        if (el == null || !el.isJsonArray()) return out;
        JsonArray arr = el.getAsJsonArray();
        for (JsonElement g : arr) {
            EggGroupKey k = EggGroupKey.fromId(g.getAsString());
            if (k != null) out.add(k);
        }
        return out;
    }
}
