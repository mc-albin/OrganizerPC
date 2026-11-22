package com.diosesmon.organizerpc.config;

import com.diosesmon.organizerpc.OrganizerPCClient;
import com.diosesmon.organizerpc.core.Category;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.OrganizerConfig;
import com.diosesmon.organizerpc.core.SortMode;
import com.diosesmon.organizerpc.core.StatKey;
import com.diosesmon.organizerpc.core.filter.Comparison;
import com.diosesmon.organizerpc.core.filter.Condition;
import com.diosesmon.organizerpc.core.filter.ConditionType;
import com.diosesmon.organizerpc.core.filter.EggGroupCondition;
import com.diosesmon.organizerpc.core.filter.IvPatternCondition;
import com.diosesmon.organizerpc.core.filter.PerfectIvCountCondition;
import com.diosesmon.organizerpc.core.filter.SpeciesCondition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Guarda la configuración en config/organizerpc.json (antes organizerpcdiosesmon.json). Las cajas se guardan empezando en 1. */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 2;

    private ConfigStore() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("organizerpc.json");
    }

    public static OrganizerConfig load() {
        Path f = file();
        // Antes el mod se llamaba OrganizerPCDiosesmon: se reutiliza su configuración.
        Path old = f.resolveSibling("organizerpcdiosesmon.json");
        if (!Files.exists(f) && Files.exists(old)) f = old;
        if (!Files.exists(f)) return new OrganizerConfig();
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return decode(JsonParser.parseReader(r).getAsJsonObject());
        } catch (Exception e) {
            OrganizerPCClient.LOGGER.error("Configuración ilegible, se usa una vacía (copia en .bak)", e);
            try {
                Files.copy(f, f.resolveSibling("organizerpc.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            return new OrganizerConfig();
        }
    }

    public static void save(OrganizerConfig cfg) {
        Path f = file();
        Path tmp = f.resolveSibling("organizerpc.json.tmp");
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(encode(cfg), w);
            }
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnsupported) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            OrganizerPCClient.LOGGER.error("No se pudo guardar la configuración", e);
        }
    }

    private static JsonArray boxesOut(Iterable<Integer> boxes) {
        JsonArray a = new JsonArray();
        for (int b : boxes) a.add(b + 1);
        return a;
    }

    public static JsonObject encode(OrganizerConfig cfg) {
        JsonObject o = new JsonObject();
        o.addProperty("version", VERSION);
        o.add("lockedBoxes", boxesOut(cfg.lockedBoxes));
        o.add("captureBoxes", boxesOut(cfg.captureBoxes));
        o.addProperty("unsortedMode", cfg.unsortedMode.name());
        o.addProperty("unsortedBox", cfg.unsortedBox + 1);
        o.addProperty("showIvBadges", cfg.showIvBadges);
        o.addProperty("highlightCategory", cfg.highlightCategory);
        o.addProperty("tutorialDone", cfg.tutorialDone);
        o.addProperty("afkCommand", cfg.afkCommand);
        o.addProperty("hidden", cfg.hidden);
        o.addProperty("ivMarksWhenHidden", cfg.ivMarksWhenHidden);
        o.addProperty("eggGroupHover", cfg.eggGroupHover);
        o.addProperty("evictStrays", cfg.evictStrays);
        o.addProperty("compact", cfg.compact);
        o.addProperty("ivMarkSize", cfg.ivMarkSize);
        o.addProperty("captureSort", cfg.captureSort.name());
        o.addProperty("captureSort2", cfg.captureSort2.name());
        o.addProperty("themeBase", String.format("#%06X", cfg.themeBase & 0xFFFFFF));
        o.addProperty("themeAccent", String.format("#%06X", cfg.themeAccent & 0xFFFFFF));
        o.addProperty("uiSize", cfg.uiSize);
        o.addProperty("ivMarksParty", cfg.ivMarksParty);
        o.addProperty("sidePanelCollapsed", cfg.sidePanelCollapsed);
        JsonArray pairs = new JsonArray();
        for (com.diosesmon.organizerpc.core.BreedingPair bp : cfg.pairs) {
            JsonObject po = new JsonObject();
            po.addProperty("number", bp.number);
            po.addProperty("color", String.format("#%06X", bp.color & 0xFFFFFF));
            JsonArray m = new JsonArray();
            for (java.util.UUID u : bp.members) m.add(u.toString());
            po.add("members", m);
            pairs.add(po);
        }
        o.add("pairs", pairs);
        JsonArray noWiki = new JsonArray();
        for (java.util.UUID u : cfg.noWiki) noWiki.add(u.toString());
        o.add("noWiki", noWiki);
        o.addProperty("breedDitto", cfg.breedDitto);
        JsonArray breedBoxes = new JsonArray();
        for (int b : cfg.breedBoxes) breedBoxes.add(b);
        o.add("breedBoxes", breedBoxes);
        JsonArray cats = new JsonArray();
        for (Category c : cfg.categories) cats.add(encode(c));
        o.add("categories", cats);
        return o;
    }

    private static JsonObject encode(Category c) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id);
        o.addProperty("name", c.name);
        o.addProperty("color", String.format("#%08X", c.color));
        o.addProperty("enabled", c.enabled);
        o.add("boxes", boxesOut(c.boxes));
        o.addProperty("logic", c.logic.name());
        o.addProperty("sort", c.sortMode.name());
        o.addProperty("sort2", c.sortMode2.name());
        JsonArray conds = new JsonArray();
        for (Condition cond : c.conditions) if (!cond.isEmpty()) conds.add(encode(cond));
        o.add("conditions", conds);
        return o;
    }

    private static JsonObject encode(Condition c) {
        JsonObject o = new JsonObject();
        o.addProperty("type", c.type().name());
        switch (c) {
            case PerfectIvCountCondition p -> {
                o.addProperty("op", p.op.name());
                o.addProperty("count", p.count);
            }
            case IvPatternCondition p -> {
                for (StatKey k : StatKey.values()) {
                    if (p.get(k) != IvPatternCondition.State.ANY) o.addProperty(k.name(), p.get(k) == IvPatternCondition.State.PERFECT ? 31 : 0);
                }
            }
            case SpeciesCondition sp -> {
                JsonArray a = new JsonArray();
                for (String id : sp.species) a.add(id);
                o.add("species", a);
            }
            case EggGroupCondition e -> {
                JsonArray a = new JsonArray();
                for (EggGroupKey k : e.groups) a.add(k.name());
                o.add("groups", a);
                o.addProperty("mode", e.mode.name());
            }
            default -> {
            }
        }
        return o;
    }

    public static OrganizerConfig decode(JsonObject o) {
        OrganizerConfig cfg = new OrganizerConfig();
        int version = o.has("version") ? o.get("version").getAsInt() : 1;
        if (o.has("lockedBoxes")) for (JsonElement e : o.getAsJsonArray("lockedBoxes")) cfg.lockedBoxes.add(e.getAsInt() - 1);
        if (o.has("captureBoxes")) for (JsonElement e : o.getAsJsonArray("captureBoxes")) cfg.captureBoxes.add(e.getAsInt() - 1);
        cfg.unsortedMode = enumOr(OrganizerConfig.UnsortedMode.class, str(o, "unsortedMode"), OrganizerConfig.UnsortedMode.KEEP);
        if (o.has("unsortedBox")) cfg.unsortedBox = Math.max(0, o.get("unsortedBox").getAsInt() - (version >= 2 ? 1 : 0));
        cfg.showIvBadges = !o.has("showIvBadges") || o.get("showIvBadges").getAsBoolean();
        cfg.highlightCategory = !o.has("highlightCategory") || o.get("highlightCategory").getAsBoolean();
        cfg.tutorialDone = o.has("tutorialDone") && o.get("tutorialDone").getAsBoolean();
        if (o.has("afkCommand")) cfg.afkCommand = o.get("afkCommand").getAsString();
        cfg.hidden = o.has("hidden") && o.get("hidden").getAsBoolean();
        cfg.ivMarksWhenHidden = o.has("ivMarksWhenHidden") && o.get("ivMarksWhenHidden").getAsBoolean();
        cfg.eggGroupHover = !o.has("eggGroupHover") || o.get("eggGroupHover").getAsBoolean();
        cfg.evictStrays = !o.has("evictStrays") || o.get("evictStrays").getAsBoolean();
        cfg.compact = !o.has("compact") || o.get("compact").getAsBoolean();
        if (o.has("ivMarkSize")) cfg.ivMarkSize = Math.max(0, Math.min(2, o.get("ivMarkSize").getAsInt()));
        cfg.captureSort = enumOr(SortMode.class, str(o, "captureSort"), SortMode.NONE);
        cfg.captureSort2 = enumOr(SortMode.class, str(o, "captureSort2"), SortMode.NONE);
        if (o.has("pairs")) {
            for (JsonElement e : o.getAsJsonArray("pairs")) {
                try {
                    JsonObject po = e.getAsJsonObject();
                    var bp = new com.diosesmon.organizerpc.core.BreedingPair(Math.max(1, po.get("number").getAsInt()));
                    Integer col = hex(str(po, "color"));
                    if (col != null) bp.color = col;
                    if (po.has("members")) for (JsonElement m : po.getAsJsonArray("members")) {
                        if (bp.members.size() < 2) bp.members.add(java.util.UUID.fromString(m.getAsString()));
                    }
                    cfg.pairs.add(bp);
                } catch (Exception ignored) {
                    // Pareja ilegible: se ignora.
                }
            }
        }
        if (o.has("noWiki")) {
            for (JsonElement e : o.getAsJsonArray("noWiki")) {
                try {
                    cfg.noWiki.add(java.util.UUID.fromString(e.getAsString()));
                } catch (Exception ignored) {
                    // UUID ilegible: se ignora.
                }
            }
        }
        cfg.breedDitto = o.has("breedDitto") && o.get("breedDitto").getAsBoolean();
        if (o.has("breedBoxes")) {
            for (JsonElement e : o.getAsJsonArray("breedBoxes")) {
                try {
                    int b = e.getAsInt();
                    if (b >= 0 && !cfg.breedBoxes.contains(b) && cfg.breedBoxes.size() < 2) cfg.breedBoxes.add(b);
                } catch (Exception ignored) {
                    // Caja ilegible: se ignora.
                }
            }
        }
        Integer tb = hex(str(o, "themeBase")), ta = hex(str(o, "themeAccent"));
        if (tb != null) cfg.themeBase = tb;
        if (ta != null) cfg.themeAccent = ta;
        cfg.ivMarksParty = !o.has("ivMarksParty") || o.get("ivMarksParty").getAsBoolean();
        cfg.sidePanelCollapsed = o.has("sidePanelCollapsed") && o.get("sidePanelCollapsed").getAsBoolean();
        if (o.has("uiSize")) cfg.uiSize = Math.max(0, Math.min(4, o.get("uiSize").getAsInt()));
        if (o.has("categories")) {
            for (JsonElement e : o.getAsJsonArray("categories")) cfg.categories.add(decodeCategory(e.getAsJsonObject()));
        }
        return cfg;
    }

    private static Category decodeCategory(JsonObject o) {
        Category c = new Category();
        if (o.has("id")) c.id = o.get("id").getAsString();
        if (o.has("name")) c.name = o.get("name").getAsString();
        if (o.has("color")) {
            try {
                c.color = (int) Long.parseLong(o.get("color").getAsString().replace("#", ""), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        c.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        if (o.has("boxes")) {
            for (JsonElement e : o.getAsJsonArray("boxes")) c.boxes.add(e.getAsInt() - 1);
        } else if (o.has("box")) {
            // Formato v1: caja inicial + número de cajas consecutivas.
            int start = o.get("box").getAsInt() - 1;
            int count = o.has("boxCount") ? Math.max(1, o.get("boxCount").getAsInt()) : 1;
            for (int i = 0; i < count; i++) c.boxes.add(start + i);
        }
        c.logic = enumOr(Category.Logic.class, str(o, "logic"), Category.Logic.AND);
        c.sortMode = enumOr(SortMode.class, str(o, "sort"), SortMode.NONE);
        c.sortMode2 = enumOr(SortMode.class, str(o, "sort2"), SortMode.NONE);
        if (o.has("conditions")) {
            for (JsonElement e : o.getAsJsonArray("conditions")) decodeCondition(c, e.getAsJsonObject());
        }
        return c;
    }

    private static void decodeCondition(Category c, JsonObject o) {
        String type = str(o, "type");
        if (type == null) return;
        switch (type) {
            case "PERFECT_IV_COUNT" -> {
                PerfectIvCountCondition p = c.condition(ConditionType.PERFECT_IV_COUNT);
                p.op = enumOr(Comparison.class, str(o, "op"), Comparison.EQ);
                p.count = o.has("count") ? Math.max(-1, Math.min(6, o.get("count").getAsInt())) : -1;
            }
            case "IV_PATTERN" -> {
                IvPatternCondition p = c.condition(ConditionType.IV_PATTERN);
                for (StatKey k : StatKey.values()) {
                    if (o.has(k.name())) p.set(k, o.get(k.name()).getAsInt() == 31 ? IvPatternCondition.State.PERFECT : IvPatternCondition.State.ZERO);
                }
            }
            case "STAT_IV" -> { // v1: solo se conservan 31 y 0 exactos.
                StatKey k = enumOr(StatKey.class, str(o, "stat"), null);
                int v = o.has("value") ? o.get("value").getAsInt() : -1;
                boolean eq = "EQ".equals(str(o, "op"));
                if (k != null && eq && (v == 31 || v == 0)) {
                    IvPatternCondition p = c.condition(ConditionType.IV_PATTERN);
                    p.set(k, v == 31 ? IvPatternCondition.State.PERFECT : IvPatternCondition.State.ZERO);
                }
            }
            case "PERFECT_STATS" -> { // v1
                IvPatternCondition p = c.condition(ConditionType.IV_PATTERN);
                if (o.has("stats")) for (JsonElement e : o.getAsJsonArray("stats")) {
                    StatKey k = enumOr(StatKey.class, e.getAsString(), null);
                    if (k != null) p.set(k, IvPatternCondition.State.PERFECT);
                }
            }
            case "SPECIES" -> {
                SpeciesCondition sp = c.condition(ConditionType.SPECIES);
                if (o.has("species")) for (JsonElement e : o.getAsJsonArray("species")) sp.add(e.getAsString());
            }
            case "EGG_GROUP" -> {
                EggGroupCondition g = c.condition(ConditionType.EGG_GROUP);
                if (o.has("groups")) for (JsonElement e : o.getAsJsonArray("groups")) {
                    EggGroupKey k = EggGroupKey.fromId(e.getAsString());
                    if (k != null) g.groups.add(k);
                }
                if (o.has("mode")) g.mode = enumOr(EggGroupCondition.Mode.class, str(o, "mode"), EggGroupCondition.Mode.ANY);
                else g.mode = o.has("requireAll") && o.get("requireAll").getAsBoolean() ? EggGroupCondition.Mode.ALL : EggGroupCondition.Mode.ANY;
            }
            default -> {
            }
        }
    }

    /** "#RRGGBB" o "RRGGBB" a color opaco; null si no es válido. */
    public static Integer hex(String s) {
        if (s == null) return null;
        String t = s.trim().replace("#", "");
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (t.length() != 6) return null;
        try {
            return 0xFF000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> cls, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(cls, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
