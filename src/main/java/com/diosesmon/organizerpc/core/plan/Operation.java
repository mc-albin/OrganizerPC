package com.diosesmon.organizerpc.core.plan;

import java.util.UUID;

/**
 * Un paso real que se enviará al servidor con los paquetes oficiales de Cobblemon.
 * MOVE: {@code pokemon} de {@code from} a {@code to} (vacío).
 * SWAP: {@code pokemon} en {@code from} intercambia con {@code other} en {@code to}.
 */
public record Operation(Type type, UUID pokemon, Pos from, Pos to, UUID other) {
    public enum Type { MOVE, SWAP }

    public static Operation move(UUID pokemon, Pos from, Pos to) {
        return new Operation(Type.MOVE, pokemon, from, to, null);
    }

    public static Operation swap(UUID pokemon, Pos from, UUID other, Pos to) {
        return new Operation(Type.SWAP, pokemon, from, to, other);
    }
}
