package com.diosesmon.organizerpc;

import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.SortMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortModeTest {
    private static PokemonInfo mon(String name, int perfect, EggGroupKey... groups) {
        int[] ivs = new int[6];
        for (int i = 0; i < 6; i++) ivs[i] = i < perfect ? 31 : 10;
        EnumSet<EggGroupKey> g = groups.length == 0 ? EnumSet.noneOf(EggGroupKey.class) : EnumSet.of(groups[0], groups);
        return new PokemonInfo(UUID.randomUUID(), name, "cobblemon:" + name, 1, 1, ivs, g, false);
    }

    @Test
    void eggGroupSortKeepsSameGroupTogether() {
        List<PokemonInfo> list = new ArrayList<>(List.of(
                mon("a", 6, EggGroupKey.FIELD),
                mon("b", 6, EggGroupKey.MONSTER),
                mon("c", 6, EggGroupKey.FIELD, EggGroupKey.FAIRY),
                mon("d", 6),
                mon("e", 6, EggGroupKey.FIELD)));
        list.sort(SortMode.chain(SortMode.EGG_GROUP, SortMode.NONE));
        List<String> names = list.stream().map(PokemonInfo::name).toList();
        assertEquals(List.of("b", "a", "e", "c", "d"), names);
    }

    @Test
    void ivsFirstThenEggGroup() {
        List<PokemonInfo> list = new ArrayList<>(List.of(
                mon("a", 5, EggGroupKey.FIELD),
                mon("b", 6, EggGroupKey.FIELD),
                mon("c", 5, EggGroupKey.MONSTER),
                mon("d", 6, EggGroupKey.MONSTER)));
        list.sort(SortMode.chain(SortMode.IVS_DESC, SortMode.EGG_GROUP));
        assertEquals(List.of("d", "b", "c", "a"), list.stream().map(PokemonInfo::name).toList());
    }
}
