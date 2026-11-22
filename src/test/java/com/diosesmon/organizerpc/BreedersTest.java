package com.diosesmon.organizerpc;

import com.diosesmon.organizerpc.core.Breeders;
import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.PokemonInfo;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreedersTest {
    private static PokemonInfo mon(int gender, int[] ivs, EggGroupKey... groups) {
        EnumSet<EggGroupKey> g = groups.length == 0 ? EnumSet.noneOf(EggGroupKey.class) : EnumSet.of(groups[0], groups);
        return new PokemonInfo(UUID.randomUUID(), "x", "cobblemon:x", 1, 1, ivs, g, false, gender);
    }

    @Test
    void countsOneToFivePerfectIvs() {
        assertTrue(Breeders.isBreeder(mon(PokemonInfo.FEMALE, new int[]{31, 5, 5, 5, 5, 5}, EggGroupKey.FIELD), false));
        assertTrue(Breeders.isBreeder(mon(PokemonInfo.MALE, new int[]{31, 31, 31, 31, 31, 5}, EggGroupKey.FIELD), false));
        assertFalse(Breeders.isBreeder(mon(PokemonInfo.MALE, new int[]{31, 31, 31, 31, 31, 31}, EggGroupKey.FIELD), false));
        assertFalse(Breeders.isBreeder(mon(PokemonInfo.MALE, new int[]{5, 5, 5, 5, 5, 5}, EggGroupKey.FIELD), false));
    }

    @Test
    void zeroIvCountsEvenWithoutPerfect() {
        assertTrue(Breeders.isBreeder(mon(PokemonInfo.MALE, new int[]{5, 0, 5, 5, 5, 5}, EggGroupKey.MONSTER), false));
        assertTrue(Breeders.isBreeder(mon(PokemonInfo.MALE, new int[]{31, 0, 31, 31, 31, 31}, EggGroupKey.MONSTER), false));
    }

    @Test
    void genderlessWithGroupCountsBabiesAndDittoDoNot() {
        assertTrue(Breeders.isBreeder(mon(PokemonInfo.GENDERLESS, new int[]{31, 5, 5, 5, 5, 5}, EggGroupKey.MINERAL), false));
        assertFalse(Breeders.isBreeder(mon(PokemonInfo.GENDERLESS, new int[]{31, 5, 5, 5, 5, 5}, EggGroupKey.UNDISCOVERED), false));
        assertFalse(Breeders.isBreeder(mon(PokemonInfo.GENDERLESS, new int[]{31, 5, 5, 5, 5, 5}), false));
        assertFalse(Breeders.isBreeder(mon(PokemonInfo.GENDERLESS, new int[]{31, 5, 5, 5, 5, 5}, EggGroupKey.DITTO), false));
        assertTrue(Breeders.isBreeder(mon(PokemonInfo.GENDERLESS, new int[]{31, 5, 5, 5, 5, 5}, EggGroupKey.DITTO), true));
        assertFalse(Breeders.isBreeder(mon(PokemonInfo.MALE, new int[]{31, -1, 5, 5, 5, 5}, EggGroupKey.FIELD), false));
    }

    @Test
    void lineFormat() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var e = new Breeders.Entry(445, "Garchomp", PokemonInfo.FEMALE, EnumSet.of(EggGroupKey.DRAGON, EggGroupKey.MONSTER),
                new int[]{31, 31, 0, 12, 31, 5}, "Caja 3 #12", id);
        assertEquals("445 | Garchomp | Hembra | Monstruo, Dragón | 31 31 0 12 31 5 | Caja 3 #12 | " + id + " | B", Breeders.line(e));
        String text = Breeders.toText(List.of(e), List.of("fecha: hoy"));
        assertTrue(text.startsWith(Breeders.MAGIC + "\n# fecha: hoy\n# columnas:"));
    }
}
