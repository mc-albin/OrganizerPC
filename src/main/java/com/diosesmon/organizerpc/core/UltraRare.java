package com.diosesmon.organizerpc.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Especies «muy raras» (ultra-rare) según la Pokédex de la wiki: tienen alguna aparición Muy raro en la Pokédex o son muy raros en el Safari, con toda su familia
 * (iniciales, fósiles, Larvitar, Gible, Eevee…). Las evoluciones sin aparición propia toman la de su preevolución.
 * Lista en {@code assets/organizerpc/ultra_rare.txt} (un nº de Pokédex por línea).
 */
public final class UltraRare {
    private static Set<Integer> dex;

    private UltraRare() {
    }

    public static boolean is(int number) {
        return load().contains(number);
    }

    public static synchronized Set<Integer> load() {
        if (dex != null) return dex;
        Set<Integer> out = new HashSet<>();
        try (InputStream in = UltraRare.class.getResourceAsStream("/assets/organizerpc/ultra_rare.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String l; (l = r.readLine()) != null; ) {
                    l = l.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    try {
                        out.add(Integer.parseInt(l));
                    } catch (NumberFormatException ignored) {
                        // Línea rara: se ignora.
                    }
                }
            }
        } catch (Exception ignored) {
            // Sin lista: ninguno es muy raro.
        }
        dex = out;
        return dex;
    }
}
