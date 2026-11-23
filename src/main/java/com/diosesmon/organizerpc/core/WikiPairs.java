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
 * {"v":1,"crianzas":[{"id":"a","nombre":"Clefairy","color":"#FF6FB5",
 *   "pares":[{"n":1,"a":"uuid","b":"uuid","estado":"lista","ga":"M","gb":"F","ca":false,"cb":true,"bra":"31 DEF","brb":"0 ATQ"}],
 *   "reservados":[{"u":"uuid","g":"M","br":"31 PS"}]}]}
 * </pre>
 * {@code n}: número de pareja, seguido (1, 2, 3…). {@code ga}/{@code gb}: el sexo que debe tener cada uno; {@code ca}/{@code cb}:
 * si hay que pagar el cambio; {@code bra}/{@code brb}: el IV que pasa con su brazal (31 o 0).
 */
public final class WikiPairs {
    /**
     * Marca de un Pokémon: crianza, número de pareja (0 = reservado para un paso posterior), el sexo que debe tener,
     * si hay que cambiárselo pagando, el IV que pasa con su brazal, el estado y con quién se cría.
     */
    public record Mark(String crianzaId, String crianza, int color, int pair, boolean female, String brazal, String state,
                       boolean genderChange, UUID partner, boolean genderless) {
        /** Texto corto de la insignia: «C1» pareja, «C·» reservado, «C?» opción que tú decides en la wiki. */
        public String label() {
            String ini = crianza == null || crianza.isBlank() ? "C" : crianza.substring(0, 1).toUpperCase(Locale.ROOT);
            return ini + (pair > 0 ? String.valueOf(pair) : option() ? "?" : "·");
        }

        /** Opción: un Pokémon con más IV que podría reemplazar un paso; la wiki no lo usa salvo que tú lo elijas. */
        public boolean option() {
            return "opcion".equals(state);
        }

        /** Estado con palabras. */
        public String stateLabel() {
            return switch (state == null ? "" : state) {
                case "lista" -> "Lista para criar";
                case "criando" -> "Criando";
                case "incompleta" -> "Falta su pareja";
                case "evolucionar" -> "Evoluciona la cría";
                case "reservado" -> "Espera una cría";
                case "opcion" -> "Opción: tú decides en la wiki";
                default -> state == null ? "" : state;
            };
        }
    }

    /** Una crianza con sus parejas en orden (para las cajas de crianza): cada pareja, macho y hembra. */
    public record Crianza(String id, String name, int color, List<List<UUID>> pairs, List<UUID> reserved) {
    }

    public static final WikiPairs EMPTY = new WikiPairs(Map.of(), List.of());
    /** Ámbar de las opciones (distinto del dorado de la 3.ª crianza). */
    public static final int OPTION_COLOR = 0xFFFF9F1C;

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
        for (List<UUID> p : slotPairs()) for (UUID u : p) if (u != null && !out.contains(u)) out.add(u);
        return out;
    }

    /**
     * Parejas para las cajas de crianza, en orden: las de cada crianza y después sus reservados, de dos en dos.
     * Cada elemento tiene 2 lugares (macho, hembra); un lugar puede ser null.
     */
    public List<List<UUID>> slotPairs() {
        List<List<UUID>> out = new ArrayList<>();
        for (Crianza c : crianzas) {
            for (List<UUID> p : c.pairs()) {
                List<UUID> two = new ArrayList<>(2);
                two.add(p.size() > 0 ? p.get(0) : null);
                two.add(p.size() > 1 ? p.get(1) : null);
                out.add(two);
            }
            for (int i = 0; i < c.reserved().size(); i += 2) {
                List<UUID> two = new ArrayList<>(2);
                two.add(c.reserved().get(i));
                two.add(i + 1 < c.reserved().size() ? c.reserved().get(i + 1) : null);
                out.add(two);
            }
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
                String state = str(p, "estado", "lista");
                List<UUID> change = new ArrayList<>();
                if (p.has("cambio")) for (JsonElement x : p.getAsJsonArray("cambio")) {
                    UUID u = uuid(x.getAsString());
                    if (u != null) change.add(u);
                }
                UUID a = uuid(str(p, "a", "")), b = uuid(str(p, "b", ""));
                boolean fa = "F".equals(str(p, "ga", "M")), fb = !"M".equals(str(p, "gb", "F"));
                boolean ca = p.has("ca") ? p.get("ca").getAsBoolean() : change.contains(a);
                boolean cb = p.has("cb") ? p.get("cb").getAsBoolean() : change.contains(b);
                List<UUID> pair = new ArrayList<>(2);
                if (a != null) {
                    pair.add(a);
                    marks.putIfAbsent(a, new Mark(id, name, color, n, fa, str(p, "bra", ""), state, ca, b, p.has("xa") && p.get("xa").getAsBoolean()));
                }
                if (b != null) {
                    pair.add(b);
                    marks.putIfAbsent(b, new Mark(id, name, color, n, fb, str(p, "brb", ""), state, cb, a, p.has("xb") && p.get("xb").getAsBoolean()));
                }
                pairs.add(Collections.unmodifiableList(pair));
            }
            List<UUID> reserved = new ArrayList<>();
            if (c.has("reservados")) for (JsonElement re : c.getAsJsonArray("reservados")) {
                JsonObject r = re.getAsJsonObject();
                UUID u = uuid(str(r, "u", ""));
                if (u == null) continue;
                reserved.add(u);
                marks.putIfAbsent(u, new Mark(id, name, color, 0, "F".equals(str(r, "g", "M")), str(r, "br", ""), "reservado", false, null, false));
            }
            // Opciones: no van a las cajas de crianza; en brazal se guarda el paso que podría reemplazar
            if (c.has("opciones")) for (JsonElement oe : c.getAsJsonArray("opciones")) {
                JsonObject o = oe.getAsJsonObject();
                UUID u = uuid(str(o, "u", ""));
                if (u != null) marks.putIfAbsent(u, new Mark(id, name, OPTION_COLOR, -1, false, str(o, "paso", ""), "opcion", false, null, false));
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
