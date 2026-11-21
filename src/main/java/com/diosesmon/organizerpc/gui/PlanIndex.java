package com.diosesmon.organizerpc.gui;

import com.diosesmon.organizerpc.core.plan.Plan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Índices de una vista previa calculados una sola vez (en vez de recorrer todos los
 * movimientos en cada frame para cada caja y cada Pokémon).
 */
final class PlanIndex {
    private static PlanIndex last;

    final Plan plan;
    final int[] incoming;
    final int[] outgoing;
    final boolean[] changed;
    final Map<UUID, Plan.Entry> byUuid = new HashMap<>();

    private PlanIndex(Plan plan) {
        this.plan = plan;
        int n = plan.after().boxCount();
        incoming = new int[n];
        outgoing = new int[n];
        changed = new boolean[n];
        for (Plan.Entry e : plan.entries()) {
            byUuid.put(e.uuid(), e);
            int from = e.from().box(), to = e.to().box();
            if (from != to) {
                if (to < n) incoming[to]++;
                if (from < n) outgoing[from]++;
            }
            if (to < n) changed[to] = true;
            if (from < n) changed[from] = true;
        }
    }

    /** Índice de la vista previa (se reutiliza mientras sea el mismo plan). */
    static PlanIndex of(Plan plan) {
        PlanIndex l = last;
        if (l != null && l.plan == plan) return l;
        last = new PlanIndex(plan);
        return last;
    }

    int in(int box) {
        return box >= 0 && box < incoming.length ? incoming[box] : 0;
    }

    int out(int box) {
        return box >= 0 && box < outgoing.length ? outgoing[box] : 0;
    }

    boolean changed(int box) {
        return box >= 0 && box < changed.length && changed[box];
    }

    Plan.Entry entry(UUID uuid) {
        return byUuid.get(uuid);
    }
}
