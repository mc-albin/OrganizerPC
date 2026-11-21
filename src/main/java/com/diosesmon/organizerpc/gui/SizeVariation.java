package com.diosesmon.organizerpc.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tamaños del mod «Cobblemon Size Variation»: lee los rangos de su configuración
 * (config/cobblemonsizevariation/sizes/&lt;algoritmo&gt;.json) y clasifica el modificador de
 * tamaño de cada Pokémon. Si el mod no está, usa los rangos «basic» por defecto.
 */
final class SizeVariation {
    record Size(String name, float min, float max, int color) {
    }

    private static List<Size> sizes;

    private SizeVariation() {
    }

    private static List<Size> defaults() {
        return List.of(
                new Size("Tiny", 0.2F, 0.5F, 0xFF1B88CC),
                new Size("Small", 0.51F, 0.9F, 0xFF1BCC9A),
                new Size("Average", 0.91F, 1.2F, 0xFFFFFFFF),
                new Size("Big", 1.21F, 1.6F, 0xFFE6FF2B),
                new Size("Large", 1.61F, 1.9F, 0xFFF07426),
                new Size("Huge", 1.91F, 2.0F, 0xFFF21800));
    }

    static synchronized List<Size> sizes() {
        if (sizes != null) return sizes;
        sizes = defaults();
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("cobblemonsizevariation");
            String algo = "basic";
            Path cfg = dir.resolve("config.json");
            if (Files.isRegularFile(cfg)) {
                try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    if (o.has("sizingAlgorithm")) algo = o.get("sizingAlgorithm").getAsString().replaceAll("[^A-Za-z0-9_-]", "");
                }
            }
            Path file = dir.resolve("sizes").resolve(algo + ".json");
            if (!Files.isRegularFile(file)) return sizes;
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                List<Size> out = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("sizes")) {
                    JsonObject s = e.getAsJsonObject();
                    int col = 0xFFFFFFFF;
                    try {
                        col = 0xFF000000 | Integer.parseInt(s.get("color").getAsString().replace("#", ""), 16);
                    } catch (Exception ignored) {
                    }
                    out.add(new Size(s.get("name").getAsString(), Float.parseFloat(s.get("min").getAsString()),
                            Float.parseFloat(s.get("max").getAsString()), col));
                }
                if (!out.isEmpty()) sizes = List.copyOf(out);
            }
        } catch (Exception ignored) {
            // Configuración rara: se quedan los tamaños por defecto.
        }
        return sizes;
    }

    /** Nombre del tamaño para un modificador (el rango más cercano si cae entre dos). */
    static String sizeOf(float scale) {
        Size best = null;
        float bestDist = Float.MAX_VALUE;
        for (Size s : sizes()) {
            if (scale >= s.min() - 0.005F && scale <= s.max() + 0.005F) return s.name();
            float d = Math.min(Math.abs(scale - s.min()), Math.abs(scale - s.max()));
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best == null ? "" : best.name();
    }

    /** Tamaño escrito en la búsqueda (inglés del mod o español), o null. */
    static String byToken(String token) {
        String t = token.toLowerCase(Locale.ROOT);
        String alias = switch (t) {
            case "diminuto", "minusculo" -> "tiny";
            case "pequeno", "chico" -> "small";
            case "normal", "medio", "mediano", "medium" -> "average";
            case "grande" -> "big";
            case "muygrande" -> "large";
            case "gigante", "enorme" -> "huge";
            default -> t;
        };
        for (Size s : sizes()) if (s.name().toLowerCase(Locale.ROOT).equals(alias)) return s.name();
        return null;
    }
}
