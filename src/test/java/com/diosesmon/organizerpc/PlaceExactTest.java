package com.diosesmon.organizerpc;

import com.diosesmon.organizerpc.core.EggGroupKey;
import com.diosesmon.organizerpc.core.PokemonInfo;
import com.diosesmon.organizerpc.core.plan.PcModel;
import com.diosesmon.organizerpc.core.plan.Plan;
import com.diosesmon.organizerpc.core.plan.Planner;
import com.diosesmon.organizerpc.core.plan.Pos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceExactTest {
    private static UUID put(PcModel m, int box, int slot) {
        UUID u = UUID.randomUUID();
        m.put(new Pos(box, slot), new PokemonInfo(u, "x", "cobblemon:x", 1, 1, new int[]{31, 0, 0, 0, 0, 0},
                EnumSet.of(EggGroupKey.FIELD), false));
        return u;
    }

    @Test
    void placesPairsInOrderAndSwapsOccupants() {
        PcModel m = new PcModel(4);
        UUID a = put(m, 0, 5), b = put(m, 1, 7), c = put(m, 2, 0), d = put(m, 3, 3);
        UUID stranger = put(m, 2, 1);   // ocupa el 2º hueco de la caja de crianza
        Plan plan = Planner.placeExact(m, List.of(c, a, b, d), List.of(2), Set.of(3));
        PcModel after = plan.after();
        assertEquals(new Pos(2, 0), after.positionOf(c));
        assertEquals(new Pos(2, 1), after.positionOf(a));
        assertEquals(new Pos(2, 2), after.positionOf(b));
        assertEquals(new Pos(3, 3), after.positionOf(d));   // caja bloqueada: no se toca
        assertEquals(new Pos(0, 5), after.positionOf(stranger));   // queda donde estaba el que llegó
        assertTrue(plan.noSpace().isEmpty());
    }

    @Test
    void missingMembersAreSkipped() {
        PcModel m = new PcModel(2);
        UUID a = put(m, 0, 0);
        Plan plan = Planner.placeExact(m, List.of(UUID.randomUUID(), a), List.of(1), Set.of());
        assertEquals(new Pos(1, 0), plan.after().positionOf(a));
    }
}
