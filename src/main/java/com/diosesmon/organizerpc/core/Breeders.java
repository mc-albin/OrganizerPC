package com.diosesmon.organizerpc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Breeders para la wiki de crianza: qué Pokémon cuentan y cómo se escriben en el archivo .txt
 * que lee la página (a mano o, con WikiDiosesmonViewer, sola). Independiente de Minecraft.
 *
 * <p>Formato (una línea por Pokémon, columnas separadas por « | »):
 * <pre>
 * # OrganizerPC breeders v1
 * # fecha: 2026-11-22 18:30
 * # columnas: dex | especie | sexo | grupos huevo | PS ATQ DEF ATQ.E DEF.E VEL | lugar | id | tipo
 * 445 | Garchomp | Hembra | Monstruo, Dragón | 31 31 0 12 31 5 | Caja 3 #12 | 2f1c… | B
 * </pre>
 * Tipo «B» = breeder; «S» = solo para seguir la crianza (la wiki no lo usa como breeder).
 */
public final class Breeders {
    public static final String MAGIC = "# OrganizerPC breeders v1";

    /**
     * Un Pokémon listo para escribir. {@code place}: «Caja 3 #12» o «Equipo #2». {@code breeder}: si sirve de breeder
     * (columna «B»); si no, va igual con «S» para que la wiki pueda seguir la crianza (crías que nacen, bebés…).
     */
    public record Entry(int dex, String species, int gender, Set<EggGroupKey> eggGroups, int[] ivs, String place, UUID id,
                        boolean breeder) {
        public Entry(int dex, String species, int gender, Set<EggGroupKey> eggGroups, int[] ivs, String place, UUID id) {
            this(dex, species, gender, eggGroups, ivs, place, id, true);
        }
    }

    private Breeders() {
    }

    /**
     * ¿Sirve como breeder? IVs conocidos, grupo huevo que no sea «Desconocido» (bebés y legendarios),
     * sexo conocido (los sin sexo cuentan si tienen grupo), sin Ditto salvo que se pida, y algún IV
     * útil: de 1 a 5 en 31, o alguno en 0 (crianzas de 0 Ataque, 0 Velocidad…). Los 6x31 no cuentan.
     */
    public static boolean isBreeder(PokemonInfo p, boolean includeDitto) {
        if (p == null || !p.ivsKnown()) return false;
        Set<EggGroupKey> g = p.eggGroups();
        if (g.isEmpty() || g.contains(EggGroupKey.UNDISCOVERED)) return false;
        if (g.contains(EggGroupKey.DITTO) && !includeDitto) return false;
        if (p.gender() == PokemonInfo.UNKNOWN) return false;
        int n31 = 0, n0 = 0;
        for (int v : p.ivs()) {
            if (v == 31) n31++;
            else if (v == 0) n0++;
        }
        return n31 <= 5 && (n31 >= 1 || n0 >= 1);
    }

    public static String genderLabel(int gender) {
        return switch (gender) {
            case PokemonInfo.MALE -> "Macho";
            case PokemonInfo.FEMALE -> "Hembra";
            case PokemonInfo.GENDERLESS -> "Sin sexo";
            default -> "?";
        };
    }

    /** Texto completo del archivo. {@code meta}: líneas extra de cabecera («fecha: …»), sin «# ». */
    public static String toText(List<Entry> entries, List<String> meta) {
        StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append('\n');
        for (String m : meta) sb.append("# ").append(m).append('\n');
        sb.append("# columnas: dex | especie | sexo | grupos huevo | PS ATQ DEF ATQ.E DEF.E VEL | lugar | id | tipo\n");
        for (Entry e : entries) sb.append(line(e)).append('\n');
        return sb.toString();
    }

    public static String line(Entry e) {
        List<String> groups = new ArrayList<>();
        for (EggGroupKey k : EggGroupKey.values()) if (e.eggGroups().contains(k)) groups.add(k.label);
        StringBuilder iv = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) iv.append(' ');
            iv.append(e.ivs()[i]);
        }
        return e.dex() + " | " + clean(e.species()) + " | " + genderLabel(e.gender()) + " | " + String.join(", ", groups)
                + " | " + iv + " | " + clean(e.place()) + " | " + e.id() + " | " + (e.breeder() ? "B" : "S");
    }

    /** Sin « | » ni saltos de línea dentro de una columna. */
    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
    }
}
