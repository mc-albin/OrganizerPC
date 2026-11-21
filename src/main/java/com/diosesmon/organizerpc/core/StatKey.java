package com.diosesmon.organizerpc.core;

/** Las seis estadísticas con IV. El orden coincide con el array de IVs de {@link PokemonInfo}. */
public enum StatKey {
    HP("PS"),
    ATTACK("Ata"),
    DEFENCE("Def"),
    SPECIAL_ATTACK("AtE"),
    SPECIAL_DEFENCE("DfE"),
    SPEED("Vel");

    public final String shortLabel;

    StatKey(String shortLabel) {
        this.shortLabel = shortLabel;
    }

    public StatKey next() {
        StatKey[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
