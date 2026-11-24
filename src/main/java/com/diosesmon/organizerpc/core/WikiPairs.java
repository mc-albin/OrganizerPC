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
                       boolean genderChange, UUID partner, boolean genderless, List<String> notes,
                       String childGender, String next, String with) {
        /** La cría de este cruce debe salir hembra («F»), macho («M») o es tu Pokémon final («»). */
        public boolean childFinal() {
            return childGender == null || childGender.isBlank();
        }

        /** Texto corto de la insignia: «C1» pareja, «C·» reservado, «C?» opción que tú decides en la wiki. */
        public String label() {
            String ini = crianza == null || crianza.isBlank() ? "C" : crianza.substring(0, 1).toUpperCase(Locale.ROOT);
            return ini + (pair > 0 ? String.valueOf(pair) : option() ? "?" : "·");
        }

        /** Opción: un Pokémon con más IV que podría reemplazar un paso; la wiki no lo usa salvo que tú lo elijas. */
        public boolean option() {
            return "opcion".equals(state);
        }

        /** Espera: aún no puede criar (espera la cría de otro cruce o le falta la pareja en tu PC). */
        public boolean waiting() {
            return "reservado".equals(state) || "falta".equals(state);
        }

        /** Estado con palabras. */
        public String stateLabel() {
            return switch (state == null ? "" : state) {
                case "lista" -> "Lista para criar";
                case "criando" -> "Criando";
                case "incompleta" -> "Falta su pareja";
                case "evolucionar" -> "Evoluciona la cría";
                case "reservado" -> "Espera una cría";
                case "falta" -> "Falta su pareja";
                case "opcion" -> "Opción: tú decides en la wiki";
                default -> state == null ? "" : state;
            };
        }
    }

    /**
     * Una crianza con sus cruces en el orden en que se crían (C1, C2…): cada uno con 2 lugares, macho y hembra.
     * Un cruce que espera una cría (o le falta la pareja) tiene un lugar vacío (null): ahí va la cría cuando nazca.
     */
    public record Crianza(String id, String name, int color, List<List<UUID>> slots) {
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
     * Cruces para las cajas de crianza, crianza por crianza y en el orden en que se crían.
     * Cada elemento tiene 2 lugares (macho, hembra); un lugar puede ser null (la cría que falta o la pareja que no tienes).
     */
    public List<List<UUID>> slotPairs() {
        return slotPairs("");
    }

    /** Cruces de una sola crianza (por su id; «» = todas). */
    public List<List<UUID>> slotPairs(String crianzaId) {
        List<List<UUID>> out = new ArrayList<>();
        for (Crianza c : crianzas) if (crianzaId == null || crianzaId.isEmpty() || c.id().equals(crianzaId)) out.addAll(c.slots());
        return out;
    }

    /** Pokémon de una crianza que van a las cajas («» = todas). */
    public int memberCount(String crianzaId) {
        java.util.Set<UUID> s = new java.util.HashSet<>();
        for (List<UUID> two : slotPairs(crianzaId)) for (UUID u : two) if (u != null) s.add(u);
        return s.size();
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
            // Cruces por número: los listos y los que esperan una cría van juntos, en el orden en que se crían
            java.util.TreeMap<Integer, UUID[]> slots = new java.util.TreeMap<>();
            int sueltos = 100000;
            if (c.has("pares")) for (JsonElement pe : c.getAsJsonArray("pares")) {
                JsonObject p = pe.getAsJsonObject();
                int n = p.has("n") ? p.get("n").getAsInt() : slots.size() + 1;
                List<String> notes = notes(p);
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
                String hg = str(p, "hg", ""), sig = str(p, "sig", "");
                if (a != null) marks.putIfAbsent(a, new Mark(id, name, color, n, fa, str(p, "bra", ""), state, ca, b, p.has("xa") && p.get("xa").getAsBoolean(), notes, hg, sig, ""));
                if (b != null) marks.putIfAbsent(b, new Mark(id, name, color, n, fb, str(p, "brb", ""), state, cb, a, p.has("xb") && p.get("xb").getAsBoolean(), notes, hg, sig, ""));
                if (a != null || b != null) slots.put(slots.containsKey(n) ? sueltos++ : n, new UUID[]{a, b});
            }
            // Reservados: esperan una cría (o su pareja no está en tu PC). Van en su lado, con el otro lugar vacío
            if (c.has("reservados")) for (JsonElement re : c.getAsJsonArray("reservados")) {
                JsonObject r = re.getAsJsonObject();
                UUID u = uuid(str(r, "u", ""));
                if (u == null) continue;
                int n = r.has("n") ? r.get("n").getAsInt() : 0;
                boolean f = "F".equals(str(r, "g", "M"));
                marks.putIfAbsent(u, new Mark(id, name, color, n, f, str(r, "br", ""), str(r, "estado", "reservado"), false, null,
                        r.has("x") && r.get("x").getAsBoolean(), notes(r), str(r, "hg", ""), str(r, "sig", ""), str(r, "con", "")));
                int k = n > 0 && !slots.containsKey(n) ? n : sueltos++;
                slots.put(k, f ? new UUID[]{null, u} : new UUID[]{u, null});
            }
            List<List<UUID>> ordered = new ArrayList<>();
            for (UUID[] two : slots.values()) ordered.add(Collections.unmodifiableList(java.util.Arrays.asList(two)));
            // Opciones: no van a las cajas de crianza; en brazal se guarda el paso que podría reemplazar
            if (c.has("opciones")) for (JsonElement oe : c.getAsJsonArray("opciones")) {
                JsonObject o = oe.getAsJsonObject();
                UUID u = uuid(str(o, "u", ""));
                if (u != null) marks.putIfAbsent(u, new Mark(id, name, OPTION_COLOR, -1, false, str(o, "paso", ""), "opcion", false, null, false, List.of(), "", "", ""));
            }
            list.add(new Crianza(id, name, color, Collections.unmodifiableList(ordered)));
        }
        return new WikiPairs(Collections.unmodifiableMap(marks), Collections.unmodifiableList(list));
    }

    /** Líneas que explica la wiki: con qué cría se junta, qué sale del cruce y a dónde va después. */
    private static List<String> notes(JsonObject o) {
        List<String> out = new ArrayList<>();
        if (o.has("notas") && o.get("notas").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("notas")) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return Collections.unmodifiableList(out);
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
