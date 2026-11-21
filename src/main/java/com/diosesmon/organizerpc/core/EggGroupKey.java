package com.diosesmon.organizerpc.core;

import java.util.Locale;

/**
 * Grupos huevo de Cobblemon 1.7.3. Los nombres de las constantes coinciden con
 * com.cobblemon.mod.common.api.pokemon.egg.EggGroup y con los ids de los JSON
 * de especies ("water_1", "human_like", ...).
 */
public enum EggGroupKey {
    MONSTER("Monstruo"),
    WATER_1("Agua 1"),
    WATER_2("Agua 2"),
    WATER_3("Agua 3"),
    BUG("Bicho"),
    FLYING("Volador"),
    FIELD("Campo"),
    FAIRY("Hada"),
    GRASS("Planta"),
    HUMAN_LIKE("Humanoide"),
    MINERAL("Mineral"),
    AMORPHOUS("Amorfo"),
    DITTO("Ditto"),
    DRAGON("Dragón"),
    UNDISCOVERED("Desconocido");

    public final String label;

    EggGroupKey(String label) {
        this.label = label;
    }

    /** Convierte un id de Cobblemon ("water_1", "WATER_1") en la constante, o null si no existe. */
    public static EggGroupKey fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
