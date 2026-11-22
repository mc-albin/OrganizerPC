package com.diosesmon.organizerpc.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Parejas que eligió la wiki de crianza ({@code organizerpc/parejas.json}, lo escribe WikiDiosesmonViewer).
 * Son aparte de las parejas manuales: el mod solo las señala y, si se lo pides, las lleva a las cajas de crianza.
 *
 * <pre>
 * {"v":1,"crianzas":[{"id":"a","nombre":"Lucario","color":"#FF6FB5",
 *   "pares":[{"n":1,"a":"uuid","b":"uuid","paso":"PS + VEL","estado":"lista","cambio":["uuid"]}],
 *   "reservados":[{"u":"uuid","paso":"ATQ + DEF"}]}]}
 * </pre>
 */
public final class WikiPairs {
    /** Marca de un Pokémon: a qué crianza y pareja pertenece. {@code pair} = 0 si está reservado para un paso posterior. */
    public record Mark(String crianzaId, String crianza, int color, int pair, boolean female, String step, String state, boolean genderChange) {
        /** Texto corto de la insignia: inicial de la crianza y número de pareja («L2»), o «L·» si está reservado. */
        public String label() {
            String ini = crianza == null || crianza.isBlank() ? "C" : crianza.substring(0, 1).toUpperCase(Locale.ROOT);
            return ini + (pair > 0 ? String.valueOf(pair) : "·");
        }
    }

    /** Una crianza con sus parejas en orden (para mover a las cajas de crianza). */
    public record Crianza(String id, String name, int color, List<List<UUID>> pairs, List<UUID> reserved) {
    }

    public static final WikiPairs EMPTY = new WikiPairs(Map.of(), List.of());

    private final Map<UUID, Mark> marks;
    private final List<Crianza> crianzas;

    private WikiPairs(Map<UUID, Mark> marks, List<Crianza> crianzas) {
        this.marks = marks;
        this.crianzas = crianzas;
    }

    public Mark markOf(UUID u) {
        return u == null ? null : marks.get(u);
    }

    public boolean isEmpty() {
        return marks.isEmpty();
    }

    public List<Crianza> crianzas() {
        return crianzas;
    }

    /** Todos los Pokémon de las crianzas en orden: crianza por crianza, P1 macho, P1 hembra, P2…, y al final los reservados. */
    public List<UUID> orderedMembers() {
        List<UUID> out = new ArrayList<>();
        for (Crianza c : crianzas) {
            for (List<UUID> p : c.pairs()) for (UUID u : p) if (!out.contains(u)) out.add(u);
            for (UUID u : c.reserved()) if (!out.contains(u)) out.add(u);
        }
        return out;
    }

    public static WikiPairs parse(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<UUID, Mark> marks = new HashMap<>();
        List<Crianza> list = new ArrayList<>();
        JsonArray cs = root.has("crianzas") ? root.getAsJsonArray("crianzas") : new JsonArray();
        int idx = 0;
        for (JsonElement ce : cs) {
            JsonObject c = ce.getAsJsonObject();
            String id = str(c, "id", "c" + idx);
            String name = str(c, "nombre", "Crianza");
            int color = color(str(c, "color", ""), BreedingPair.COLORS[idx % BreedingPair.COLORS.length]);
            idx++;
            List<List<UUID>> pairs = new ArrayList<>();
            if (c.has("pares")) for (JsonElement pe : c.getAsJsonArray("pares")) {
                JsonObject p = pe.getAsJsonObject();
                int n = p.has("n") ? p.get("n").getAsInt() : pairs.size() + 1;
                String step = str(p, "paso", ""), state = str(p, "estado", "lista");
                List<UUID> change = new ArrayList<>();
                if (p.has("cambio")) for (JsonElement x : p.getAsJsonArray("cambio")) {
                    UUID u = uuid(x.getAsString());
                    if (u != null) change.add(u);
                }
                UUID a = uuid(str(p, "a", "")), b = uuid(str(p, "b", ""));
                List<UUID> pair = new ArrayList<>(2);
                if (a != null) {
                    pair.add(a);
                    marks.putIfAbsent(a, new Mark(id, name, color, n, false, step, state, change.contains(a)));
                }
                if (b != null) {
                    pair.add(b);
                    marks.putIfAbsent(b, new Mark(id, name, color, n, true, step, state, change.contains(b)));
                }
                pairs.add(Collections.unmodifiableList(pair));
            }
            List<UUID> reserved = new ArrayList<>();
            if (c.has("reservados")) for (JsonElement re : c.getAsJsonArray("reservados")) {
                JsonObject r = re.getAsJsonObject();
                UUID u = uuid(str(r, "u", ""));
                if (u == null) continue;
                reserved.add(u);
                marks.putIfAbsent(u, new Mark(id, name, color, 0, false, str(r, "paso", ""), "reservado", false));
            }
            list.add(new Crianza(id, name, color, Collections.unmodifiableList(pairs), Collections.unmodifiableList(reserved)));
        }
        return new WikiPairs(Collections.unmodifiableMap(marks), Collections.unmodifiableList(list));
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static UUID uuid(String s) {
        try {
            return s == null || s.isBlank() ? null : UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int color(String hex, int def) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() != 6) return def;
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
