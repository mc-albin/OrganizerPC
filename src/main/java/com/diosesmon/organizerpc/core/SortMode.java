package com.diosesmon.organizerpc.core;

import java.util.Comparator;

/**
 * Orden de los Pokémon dentro de unas cajas. Los órdenes por IVs no miran la especie:
 * solo los IVs (a igualdad se mantiene el orden en que estaban).
 */
public enum SortMode {
    NONE("Sin ordenar"),
    IVS_ASC("IVs: 1x31 → 6x31"),
    IVS_DESC("IVs: 6x31 → 0x31"),
    IV_STATS("IVs: PS, Ata, Def…"),
    DEX("Nº Pokédex"),
    LEVEL_DESC("Mayor nivel"),
    EGG_GROUP("Grupo huevo"),
    /**
     * En columnas, sin completar: cada IV en 31 de los 1x31 (PS, Ata…) empieza su propia columna;
     * los de 2x31 o más van juntos. Los que no caben van a las cajas de captura.
     */
    IV_COLUMNS("Columnas por IV"),
    /** En columnas, completas: de arriba abajo, sin huecos. */
    IV_COLUMNS_FILL("Columnas por IV"),
    /** Living dex: un hueco por especie de cada familia, en orden de Pokédex; los que faltan quedan vacíos. */
    LIVING_DEX("Living dex");

    public final String label;

    SortMode(String label) {
        this.label = label;
    }

    /** Siguiente orden al ir pulsando (las dos variantes de columnas cuentan como una). */
    public SortMode next() {
        SortMode[] v = values();
        SortMode n = v[(ordinal() + 1) % v.length];
        return n == IV_COLUMNS_FILL ? n.next() : n;
    }

    public boolean columns() {
        return this == IV_COLUMNS || this == IV_COLUMNS_FILL;
    }

    /** Primero los que tienen 31 en PS, luego en Ataque, Defensa, At. Esp., Def. Esp. y Velocidad. */
    public static final Comparator<PokemonInfo> STAT_ORDER = (a, b) -> {
        for (StatKey k : StatKey.values()) {
            boolean pa = a.iv(k) == 31, pb = b.iv(k) == 31;
            if (pa != pb) return pa ? -1 : 1;
        }
        return 0;
    };

    private static int count(PokemonInfo p) {
        return p.perfectIvCount();
    }

    private static boolean byCount(SortMode m) {
        return m == IVS_ASC || m == IVS_DESC;
    }

    /**
     * Normaliza dos órdenes: el número de 31 siempre manda sobre el orden por estadística
     * (si eliges «PS, Ata…» y «0x31 → 6x31», se agrupa por 0x31…6x31 y dentro, PS primero).
     */
    public static SortMode[] normalize(SortMode first, SortMode second) {
        SortMode a = first == null ? NONE : first, b = second == null ? NONE : second;
        if (a == NONE) {
            a = b;
            b = NONE;
        }
        if (b == a) b = NONE;
        if (b.columns() && a.columns()) b = NONE;
        if (b == LIVING_DEX) { // el living dex manda: el otro orden elige cuál de los repetidos va en su hueco
            b = a;
            a = LIVING_DEX;
        }
        if (b.columns()) { // las columnas siempre mandan: el otro orden solo desempata dentro de cada columna
            b = a;
            a = IV_COLUMNS;
        }
        if (a == IV_STATS && byCount(b)) {
            SortMode t = a;
            a = b;
            b = t;
        }
        return new SortMode[]{a, b};
    }

    /** Los Pokémon sin ningún IV en 31 (0x31) van siempre al final. */
    public static final Comparator<PokemonInfo> ZERO_LAST = Comparator.comparingInt(p -> p.perfectIvCount() == 0 ? 1 : 0);

    /** Dos órdenes encadenados: el primero manda y el segundo desempata. Los 0x31 siempre al final. */
    public static Comparator<PokemonInfo> chain(SortMode first, SortMode second) {
        SortMode[] n = normalize(first, second);
        if (n[0] == NONE) return NONE.comparator();
        Comparator<PokemonInfo> a = ZERO_LAST.thenComparing(n[0].comparator());
        // Sin 2º orden, los órdenes por nº de 31 desempatan por stat (PS, Ata, Def…).
        if (n[1] == NONE) return byCount(n[0]) ? a.thenComparing(STAT_ORDER) : a;
        return a.thenComparing(n[1].comparator());
    }

    /** Explicación en palabras del orden resultante. */
    public static String describe(SortMode first, SortMode second) {
        SortMode[] n = normalize(first, second);
        String main = switch (n[0]) {
            case NONE -> "Se juntan sin huecos, en el mismo orden";
            case IVS_ASC -> "1x31, 2x31 … 6x31";
            case IVS_DESC -> "6x31, 5x31 … 0x31";
            case IV_STATS -> "Primero 31 en PS, luego Ata, Def, AtE, DfE, Vel";
            case DEX -> "Por número de Pokédex";
            case LEVEL_DESC -> "De mayor a menor nivel";
            case EGG_GROUP -> "Juntos los del mismo grupo huevo";
            case IV_COLUMNS -> "En columnas sin completar: cada 1x31 (PS, Ata, Def…) empieza columna nueva y los de 2x31 o más van juntos. Si no caben, a las cajas de captura";
            case IV_COLUMNS_FILL -> "En columnas completas, de arriba abajo y sin huecos (1x31 por estadística, luego 2x31 o más)";
            case LIVING_DEX -> "Living dex: cada familia en orden de Pokédex, dejando vacío el hueco de las especies que te faltan. Los repetidos al final";
        };
        String tie = switch (n[1]) {
            case NONE -> byCount(n[0]) ? "; a igualdad, 31 en PS primero, luego Ata…" : "";
            case IVS_ASC -> "; a igualdad, de 0x31 a 6x31";
            case IVS_DESC -> "; a igualdad, de 6x31 a 0x31";
            case IV_STATS -> "; a igualdad, 31 en PS primero, luego Ata…";
            case DEX -> "; a igualdad, por Pokédex";
            case LEVEL_DESC -> "; a igualdad, mayor nivel";
            case EGG_GROUP -> "; a igualdad, juntos por grupo huevo";
            case IV_COLUMNS, IV_COLUMNS_FILL, LIVING_DEX -> "";
        };
        return main + tie + (n[0] == NONE ? "." : ". Los 0x31 siempre al final.");
    }

    /**
     * Clave de grupo huevo: el 1º y el 2º grupo (por orden de la lista de grupos).
     * Los que no tienen 2º grupo van antes que los que sí; sin grupos conocidos, al final.
     */
    private static int eggKey(PokemonInfo p, int index) {
        if (p.eggGroups().isEmpty()) return Integer.MAX_VALUE;
        int i = 0;
        for (EggGroupKey g : p.eggGroups()) { // EnumSet: siempre en el mismo orden
            if (i++ == index) return g.ordinal();
        }
        return -1;
    }

    /** Comparador; a igualdad devuelve 0 para que se respete el orden previo (orden estable). */
    public Comparator<PokemonInfo> comparator() {
        return switch (this) {
            case NONE -> (a, b) -> 0;
            case IVS_ASC -> Comparator.comparingInt(SortMode::count);
            case IVS_DESC -> Comparator.comparingInt(SortMode::count).reversed();
            case IV_STATS -> STAT_ORDER.thenComparing(Comparator.comparingInt(SortMode::count).reversed());
            case DEX -> Comparator.comparingInt(PokemonInfo::dex);
            case LEVEL_DESC -> Comparator.comparingInt(PokemonInfo::level).reversed();
            case EGG_GROUP -> Comparator.comparingInt((PokemonInfo p) -> eggKey(p, 0))
                    .thenComparingInt(p -> eggKey(p, 1));
            case IV_COLUMNS, IV_COLUMNS_FILL -> Comparator.comparingInt(SortMode::count).thenComparing(STAT_ORDER);
            case LIVING_DEX -> Comparator.comparingInt(PokemonInfo::dex);
        };
    }

    /**
     * Grupo de columna: los 1x31 uno por estadística; todos los de 2x31 o más comparten grupo;
     * los 0x31 otro aparte (al final).
     */
    public static int columnGroup(PokemonInfo p) {
        int n = p.perfectIvCount();
        if (n == 0) return 200;
        if (n >= 2) return 100;
        return ivMask(p);
    }

    /** Bits de las estadísticas en 31 (bit 0 = PS … bit 5 = Velocidad). */
    public static int ivMask(PokemonInfo p) {
        int m = 0;
        for (StatKey k : StatKey.values()) if (p.iv(k) == 31) m |= 1 << k.ordinal();
        return m;
    }
}
