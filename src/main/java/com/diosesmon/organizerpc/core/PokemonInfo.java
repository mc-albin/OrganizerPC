package com.diosesmon.organizerpc.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de un Pokémon de la PC, ya extraídos de Cobblemon. Independiente de Minecraft
 * para poder probar filtros y planificador sin el juego.
 *
 * @param speciesId identificador de especie en minúsculas (p. ej. "cobblemon:slowpoke").
 * @param ivs seis valores en el orden de {@link StatKey}; -1 si el valor no está disponible.
 * @param eggGroups grupos huevo reales; vacío si no se pudieron determinar.
 * @param gender {@link #MALE}, {@link #FEMALE}, {@link #GENDERLESS} o {@link #UNKNOWN}.
 * @param scale modificador de tamaño (1 = normal; lo cambia el mod de tamaños).
 */
public record PokemonInfo(UUID uuid, String name, String speciesId, int dex, int level, int[] ivs,
                          Set<EggGroupKey> eggGroups, boolean shiny, int gender, float scale) {
    public static final int UNKNOWN = 0, MALE = 1, FEMALE = 2, GENDERLESS = 3;

    public PokemonInfo(UUID uuid, String name, String speciesId, int dex, int level, int[] ivs,
                       Set<EggGroupKey> eggGroups, boolean shiny, int gender) {
        this(uuid, name, speciesId, dex, level, ivs, eggGroups, shiny, gender, 1F);
    }

    /** Sin género conocido (pruebas y datos antiguos). */
    public PokemonInfo(UUID uuid, String name, String speciesId, int dex, int level, int[] ivs,
                       Set<EggGroupKey> eggGroups, boolean shiny) {
        this(uuid, name, speciesId, dex, level, ivs, eggGroups, shiny, UNKNOWN);
    }

    public PokemonInfo {
        if (ivs == null || ivs.length != 6) throw new IllegalArgumentException("ivs debe tener 6 valores");
        ivs = ivs.clone();
        speciesId = speciesId == null ? "" : speciesId.toLowerCase(Locale.ROOT);
        eggGroups = eggGroups == null || eggGroups.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(eggGroups));
    }

    public int iv(StatKey stat) {
        return ivs[stat.ordinal()];
    }

    /** Cantidad de IVs exactamente iguales a 31. */
    public int perfectIvCount() {
        int n = 0;
        for (int v : ivs) if (v == 31) n++;
        return n;
    }

    public boolean ivsKnown() {
        for (int v : ivs) if (v < 0) return false;
        return true;
    }
}
