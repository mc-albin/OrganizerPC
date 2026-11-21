package com.diosesmon.organizerpc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pareja de crianza marcada a mano por el jugador (P1, P2…). Solo es una ayuda visual: el mod
 * nunca elige ni mueve las parejas. Guarda hasta dos Pokémon por su UUID.
 */
public final class BreedingPair {
    /** Colores de las parejas, en orden (P1 rosa, P2 turquesa, P3 dorado…). */
    public static final int[] COLORS = {0xFFFF6FB5, 0xFF3DD6E0, 0xFFFFC857, 0xFF4CD07D, 0xFFB07CFF, 0xFFFF9F43,
            0xFF5AA9FF, 0xFFFF5C5C, 0xFFC6E05A, 0xFFE0E3E7};

    public int number;
    public int color;
    public final List<UUID> members = new ArrayList<>(2);

    public BreedingPair(int number) {
        this.number = number;
        this.color = COLORS[(number - 1) % COLORS.length];
    }

    public String label() {
        return "P" + number;
    }

    public boolean full() {
        return members.size() >= 2;
    }
}
